#!/usr/bin/env python3
"""Turn CI-collected Android performance evidence into a sanitized Markdown report."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    fixture_manifest = json.loads((args.input / "fixture-manifest.json").read_text())
    stages = (args.input / "batch-stages.log").read_text(errors="replace") if (args.input / "batch-stages.log").exists() else ""
    records = [line for line in stages.splitlines() if "batch_stage" in line]
    thumbnail_records = [line for line in stages.splitlines() if "thumbnail_stage" in line]
    gfxinfo = (args.input / "gfxinfo.txt").read_text(errors="replace") if (args.input / "gfxinfo.txt").exists() else "unavailable"
    janky = re.search(r"Janky frames:\s*(\d+)\s*\(([^)]+)\)", gfxinfo)
    meminfo_before = read_meminfo(args.input / "meminfo-before.txt")
    meminfo_after = read_meminfo(args.input / "meminfo-after.txt")

    lines = [
        "# Robia Android performance baseline",
        "",
        "> **Comparative emulator baseline only.** These numbers are not physical-device or release/Play-signed proof; compare like-for-like CI runs and validate a regression or target claim on the agreed physical reference device.",
        "",
        "## Fixture provenance",
        f"- {len(fixture_manifest['fixtures'])} deterministic synthetic JPEG fixtures; no personal images or source URIs are retained.",
        "- Input dimensions/bytes and SHA-256 values are in `fixture-manifest.json`.",
        "",
        "## Frame timing and jank",
        "- `gfxinfo.txt` is the raw platform evidence. Import `robia-baseline.perfetto-trace` into Perfetto for frame timelines, allocation/GC evidence where available, and scheduling attribution.",
        f"- dumpsys janky-frame summary: {janky.group(1) + ' (' + janky.group(2) + ')' if janky else 'not emitted by this emulator image'}.",
        "- Do not apply the physical target (p90 ≤16.7 ms, p99 ≤33.3 ms, ≤1% jank) to this emulator run.",
        "",
        "## Batch stage timing",
        f"- Sanitized per-stage records captured: {len(records)}.",
        f"- Bounded-thumbnail records captured: {len(thumbnail_records)}.",
        "- `batch-stages.log` contains sanitized batch and bounded-thumbnail measurements: fixture/stage timing where emitted plus thumbnail source/thumbnail dimensions/bytes, max edge, cache-hit state, and elapsed milliseconds; it deliberately excludes image names, URIs, clothing metadata, and pixels.",
        "- A missing stage record is an infrastructure/fixture failure, not a successful measurement.",
        "",
        "## Memory evidence",
        f"- `meminfo-before.txt` total PSS: {format_kb(meminfo_before)}.",
        f"- `meminfo-after.txt` total PSS: {format_kb(meminfo_after)}.",
        "",
        "## Interpretation",
        "1. Compare the same API level, AVD profile, Gradle/AGP versions, fixture manifest, and scroll duration before calling a change better or worse.",
        "2. Use Perfetto plus raw gfxinfo for diagnosis; do not infer decode causality from a rendered smoothness impression.",
        "3. Promote a finding only after a repeat on a representative low/mid physical device and a current device under release-like/Play-signed conditions.",
    ]
    args.output.write_text("\n".join(lines) + "\n")


def read_meminfo(path: Path) -> int | None:
    if not path.exists():
        return None
    match = re.search(r"^\s*TOTAL\s+(\d+)", path.read_text(errors="replace"), flags=re.MULTILINE)
    return int(match.group(1)) if match else None


def format_kb(value: int | None) -> str:
    return f"{value} KB" if value is not None else "unavailable"


if __name__ == "__main__":
    main()
