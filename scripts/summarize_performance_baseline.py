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
    gfxinfo = (args.input / "gfxinfo.txt").read_text(errors="replace") if (args.input / "gfxinfo.txt").exists() else "unavailable"
    janky = re.search(r"Janky frames:\s*(\d+)\s*\(([^)]+)\)", gfxinfo)

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
        "- `batch-stages.log` contains only fixture ordinal, input dimensions/bytes, and elapsed stage milliseconds; it deliberately excludes image names, URIs, clothing metadata, and pixels.",
        "- A missing stage record is an infrastructure/fixture failure, not a successful measurement. The current app has no persisted thumbnail-write stage, so the report must not claim one until a thumbnail pipeline exists.",
        "",
        "## Interpretation",
        "1. Compare the same API level, AVD profile, Gradle/AGP versions, fixture manifest, and scroll duration before calling a change better or worse.",
        "2. Use Perfetto plus raw gfxinfo for diagnosis; do not infer decode causality from a rendered smoothness impression.",
        "3. Promote a finding only after a repeat on a representative low/mid physical device and a current device under release-like/Play-signed conditions.",
    ]
    args.output.write_text("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
