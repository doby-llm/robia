#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  cat >&2 <<'USAGE'
Usage: scripts/run_additional_info_shared_cli.sh /path/to/image [--manifest path] [--model path] [--no-model] [--top-k n]

Runs the Kotlin/JVM local classifier harness that shares the Android app's
manifest parsing, preprocessing tensor policy, tensor stats, output mapping,
and tag selection code via :additional-info-core.
USAGE
  exit 2
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

./gradlew :additional-info-cli:run --args="$*"
