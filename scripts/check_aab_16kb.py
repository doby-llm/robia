#!/usr/bin/env python3
"""Check Robia's release AAB: arm64-v8a native libraries only, every PT_LOAD aligned to 16 KB pages."""
from __future__ import annotations

import argparse
import io
import struct
import sys
import zipfile
from pathlib import Path

PAGE_SIZE_16KB = 16384
ARM64_ABI = "arm64-v8a"
EM_AARCH64 = 183
PT_LOAD = 1
PT_DYNAMIC = 2
ELF_MAGIC = b"\x7fELF"
ELF64_HEADER = struct.Struct("<16sHHIQQQIHHHHHH")
ELF64_PROGRAM_HEADER = struct.Struct("<IIQQQQQQ")


def load_alignments(elf: bytes) -> list[int]:
    """Return p_align of every PT_LOAD segment of a little-endian ELF64 AArch64 file."""
    if len(elf) < ELF64_HEADER.size or elf[:4] != ELF_MAGIC or elf[4] != 2 or elf[5] != 1:
        raise ValueError("not a little-endian ELF64 file")
    _, _, e_machine, _, _, e_phoff, _, _, _, e_phentsize, e_phnum, _, _, _ = ELF64_HEADER.unpack_from(elf)
    if e_machine != EM_AARCH64:
        raise ValueError(f"e_machine is {e_machine}, not AArch64")
    if e_phentsize != ELF64_PROGRAM_HEADER.size:
        raise ValueError(f"unexpected program header size {e_phentsize}")
    alignments = []
    for index in range(e_phnum):
        offset = e_phoff + index * e_phentsize
        if offset + ELF64_PROGRAM_HEADER.size > len(elf):
            raise ValueError("program header table runs past the end of the file")
        p_type, _, _, _, _, _, _, p_align = ELF64_PROGRAM_HEADER.unpack_from(elf, offset)
        if p_type == PT_LOAD:
            alignments.append(p_align)
    return alignments


def audit_library(elf: bytes) -> tuple[list[int], str | None]:
    """Return (PT_LOAD alignments, failure reason or None when the library passes)."""
    try:
        alignments = load_alignments(elf)
    except ValueError as error:
        return [], str(error)
    if not alignments:
        return alignments, "no PT_LOAD segment"
    smallest = min(alignments)
    if smallest < PAGE_SIZE_16KB:
        return alignments, f"PT_LOAD p_align {smallest} < {PAGE_SIZE_16KB}"
    return alignments, None


def audit_archive(archive: zipfile.ZipFile) -> tuple[list[tuple[str, str, str, str]], list[str]]:
    """Return (table rows, failures) for every <module>/lib/<abi>/ entry of an AAB."""
    rows = []
    failures = []
    arm64_count = 0
    for info in archive.infolist():
        parts = info.filename.split("/")
        if len(parts) < 4 or parts[1] != "lib":
            continue
        abi = parts[2]
        if abi != ARM64_ABI:
            failures.append(f"{info.filename}: native library for {abi} is not allowed")
            continue
        if not info.filename.endswith(".so"):
            continue
        arm64_count += 1
        alignments, reason = audit_library(archive.read(info))
        smallest = str(min(alignments)) if alignments else "-"
        rows.append((info.filename, str(len(alignments)), smallest, "OK" if reason is None else reason))
        if reason is not None:
            failures.append(f"{info.filename}: {reason}")
    if arm64_count == 0:
        failures.append(f"no {ARM64_ABI} native libraries found")
    return rows, failures


def print_table(rows: list[tuple[str, str, str, str]]) -> None:
    header = ("library", "PT_LOAD", "min p_align", "verdict")
    widths = [max(len(row[column]) for row in [header, *rows]) for column in range(len(header))]
    for row in [header, *rows]:
        print("  ".join(cell.ljust(width) for cell, width in zip(row, widths)).rstrip())


def synthetic_elf(segments: list[tuple[int, int]], machine: int = EM_AARCH64) -> bytes:
    """Build a minimal ELF64 header with (p_type, p_align) program headers, for the self-test."""
    ident = ELF_MAGIC + bytes([2, 1, 1]) + bytes(9)
    header = ELF64_HEADER.pack(
        ident, 3, machine, 1, 0, ELF64_HEADER.size, 0, 0, ELF64_HEADER.size,
        ELF64_PROGRAM_HEADER.size, len(segments), 0, 0, 0,
    )
    program_headers = b"".join(
        ELF64_PROGRAM_HEADER.pack(p_type, 4, 0, 0, 0, 0x1000, 0x1000, p_align)
        for p_type, p_align in segments
    )
    return header + program_headers


def synthetic_aab(entries: dict[str, bytes]) -> zipfile.ZipFile:
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_STORED) as writer:
        for name, data in entries.items():
            writer.writestr(name, data)
    buffer.seek(0)
    return zipfile.ZipFile(buffer)


def self_test() -> int:
    aligned = synthetic_elf([(PT_LOAD, 16384), (PT_LOAD, 16384)])
    unaligned = synthetic_elf([(PT_LOAD, 16384), (PT_LOAD, 4096)])
    cases = [
        ("library: every PT_LOAD at 16 KB passes", audit_library(aligned)[1] is None),
        ("library: one PT_LOAD at 4 KB fails", audit_library(unaligned)[1] is not None),
        ("library: 64 KB alignment passes", audit_library(synthetic_elf([(PT_LOAD, 65536)]))[1] is None),
        ("library: non-PT_LOAD segments are ignored", audit_library(synthetic_elf([(PT_DYNAMIC, 8), (PT_LOAD, 16384)]))[1] is None),
        ("library: no PT_LOAD fails", audit_library(synthetic_elf([(PT_DYNAMIC, 8)]))[1] is not None),
        ("library: x86_64 ELF fails", audit_library(synthetic_elf([(PT_LOAD, 16384)], machine=62))[1] is not None),
        ("library: non-ELF bytes fail", audit_library(b"PK\x03\x04 not an ELF")[1] is not None),
        (
            "archive: aligned arm64 libraries pass",
            not audit_archive(synthetic_aab({"base/lib/arm64-v8a/libaligned.so": aligned}))[1],
        ),
        (
            "archive: one unaligned arm64 library fails",
            bool(audit_archive(synthetic_aab({
                "base/lib/arm64-v8a/libaligned.so": aligned,
                "base/lib/arm64-v8a/libunaligned.so": unaligned,
            }))[1]),
        ),
        (
            "archive: x86_64 directory fails",
            bool(audit_archive(synthetic_aab({
                "base/lib/arm64-v8a/libaligned.so": aligned,
                "base/lib/x86_64/libx.so": aligned,
            }))[1]),
        ),
        (
            "archive: armeabi-v7a directory fails",
            bool(audit_archive(synthetic_aab({
                "base/lib/arm64-v8a/libaligned.so": aligned,
                "base/lib/armeabi-v7a/libv7.so": aligned,
            }))[1]),
        ),
        ("archive: no arm64 libraries fails", bool(audit_archive(synthetic_aab({"classes.dex": b"dex"}))[1])),
    ]
    failed = 0
    for label, ok in cases:
        print(f"{'PASS' if ok else 'FAIL'}  {label}")
        failed += not ok
    if failed:
        print(f"{failed} self-test case(s) failed", file=sys.stderr)
        return 1
    print(f"all {len(cases)} self-test cases passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("aab", nargs="?", type=Path, help="release .aab to check")
    parser.add_argument("--self-test", action="store_true", help="check the verdicts on synthetic ELF headers and archives")
    args = parser.parse_args()
    if args.self_test:
        return self_test()
    if args.aab is None:
        parser.error("pass an .aab path, or --self-test")
    with zipfile.ZipFile(args.aab) as archive:
        rows, failures = audit_archive(archive)
    print_table(rows)
    if failures:
        for failure in failures:
            print(f"FAIL: {failure}", file=sys.stderr)
        return 1
    print(f"OK: {len(rows)} arm64-v8a libraries, all PT_LOAD aligned to {PAGE_SIZE_16KB} bytes, no other ABI")
    return 0


if __name__ == "__main__":
    sys.exit(main())
