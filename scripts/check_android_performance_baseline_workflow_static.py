#!/usr/bin/env python3
"""Static contract checks for the comparative Android emulator baseline workflow.

This is intentionally text/YAML-shape validation only: it must stay safe for
local ARM workers and must not run Gradle, Android, emulator, or networked CI.
"""
from __future__ import annotations

import re
import shlex
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT / ".github/workflows/android-performance-baseline.yml"
MAIN_ACTIVITY = ROOT / "app/src/main/java/com/gusanitolabs/robia/MainActivity.kt"
ROBIA_APP = ROOT / "app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt"
FIXTURE_SCRIPT = ROOT / "scripts/generate_performance_fixtures.py"
SUMMARY_SCRIPT = ROOT / "scripts/summarize_performance_baseline.py"


def read(path: Path) -> str:
    if not path.exists():
        fail(f"missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def require(text: str, needle: str, label: str) -> None:
    if needle not in text:
        fail(f"missing {label}: {needle}")


def forbid(text: str, needle: str, label: str) -> None:
    if needle in text:
        fail(f"forbidden {label}: {needle}")


def forbid_regex(text: str, pattern: str, label: str) -> None:
    if re.search(pattern, text, flags=re.MULTILINE):
        fail(f"forbidden {label}: /{pattern}/")


def require_regex(text: str, pattern: str, label: str) -> None:
    if not re.search(pattern, text, flags=re.MULTILINE):
        fail(f"missing {label}: /{pattern}/")


def extract_folded_emulator_script(workflow: str) -> str:
    lines = workflow.splitlines()
    emulator_step_index = next(
        (
            index
            for index, line in enumerate(lines)
            if "uses: reactivecircus/android-emulator-runner@v2" in line
        ),
        None,
    )
    if emulator_step_index is None:
        fail("missing android-emulator-runner step")

    step_indent = len(lines[emulator_step_index]) - len(lines[emulator_step_index].lstrip())
    for index in range(emulator_step_index + 1, len(lines)):
        line = lines[index]
        line_indent = len(line) - len(line.lstrip())
        if line.strip().startswith("-") and line_indent <= step_indent:
            break
        if not re.match(r"\s*script:\s*>-\s*$", line):
            continue

        script_indent = len(line) - len(line.lstrip())
        body: list[str] = []
        for body_line in lines[index + 1 :]:
            if not body_line.strip():
                body.append("")
                continue

            body_indent = len(body_line) - len(body_line.lstrip())
            if body_indent <= script_indent:
                break
            body.append(body_line[script_indent + 2 :])

        if not body:
            fail("empty android-emulator-runner script")
        return "\n".join(body)

    fail("missing folded single-command android-emulator-runner script: script: >-")


def validate_sh_syntax(command: str, label: str) -> None:
    candidate = command.replace("${{ inputs.duration_seconds }}", "20")
    result = subprocess.run(
        ["sh", "-n", "-c", candidate],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        fail(f"{label} is not valid /bin/sh syntax: {result.stderr.strip()}")


def require_single_command_capture_script(workflow: str) -> str:
    script = extract_folded_emulator_script(workflow)
    commands = [line.strip() for line in script.splitlines() if line.strip()]
    if len(commands) != 1:
        fail("android-emulator-runner script must be exactly one non-empty command line")

    command = commands[0]
    if not command.startswith("sh -eu -c "):
        fail("android-emulator-runner script must wrap capture in one sh -eu -c command")

    validate_sh_syntax(command, "android-emulator-runner per-line command")
    parts = shlex.split(command.replace("${{ inputs.duration_seconds }}", "20"))
    try:
        command_index = parts.index("-c")
        inner_script = parts[command_index + 1]
    except (ValueError, IndexError) as exc:
        fail(f"missing inner sh -c script: {exc}")
    validate_sh_syntax(inner_script, "inner capture script")
    return inner_script


def main() -> None:
    workflow = read(WORKFLOW)
    main_activity = read(MAIN_ACTIVITY)
    robia_app = read(ROBIA_APP)
    fixtures = read(FIXTURE_SCRIPT)
    summary = read(SUMMARY_SCRIPT)

    # Remote-only manual comparative emulator workflow; never local device or push CI.
    require(workflow, "workflow_dispatch:", "manual remote trigger")
    forbid(workflow, "pull_request:", "non-manual trigger")
    forbid(workflow, "push:", "non-manual trigger")
    require(workflow, "name: Comparative emulator baseline", "comparative emulator job name")
    require(workflow, "uses: reactivecircus/android-emulator-runner@v2", "GitHub-hosted emulator action")
    require(workflow, "api-level: 35", "API 35 emulator image")
    require(workflow, "target: google_apis", "Google APIs emulator target")
    require(workflow, "profile: pixel_2", "stable AVD profile")
    forbid(workflow, "self-hosted", "self-hosted runner")
    old_macos_runner = "macos-" + "13"
    require(workflow, "runs-on: macos-15-intel", "dispatchable Intel macOS 15 runner")
    require_regex(workflow, r"runs-on:\s+macos-[^\n#]+", "macOS GitHub-hosted runner")
    forbid(workflow, old_macos_runner, "retired macOS 13 runner regression")
    forbid(workflow, "runs-on: ubuntu", "Linux/KVM-disabled runner regression")
    require(workflow, "previous Linux runner booted API 35 without KVM and", "KVM boot-timeout rationale")
    require(workflow, "not a physical-device benchmark", "explicit non-physical-device semantics")
    require(summary, "Comparative emulator baseline only", "report comparative-emulator warning")
    require(summary, "not physical-device", "report excludes physical-device proof")
    require(summary, "Do not apply the physical target", "report forbids physical target interpretation")

    # Synthetic fixtures and debug-only instrumentation/extras.
    require(workflow, "python scripts/generate_performance_fixtures.py --output performance-fixtures", "fixture generation step")
    require(workflow, "performance-fixtures/manifest.json", "fixture manifest use")
    require(fixtures, "FIXTURE_COUNT = 60", "60-item fixture contract")
    require(fixtures, '"synthetic": True', "synthetic fixture manifest flag")
    require(workflow, "com.gusanitolabs.robia.PERFORMANCE_FIXTURE_URIS", "fixture URI debug extra")
    require(workflow, "com.gusanitolabs.robia.PERFORMANCE_BATCH", "batch mode debug extra")
    require(workflow, "debug-only synthetic fixture extras", "debug-only workflow documentation")
    require(main_activity, "EXTRA_PERFORMANCE_FIXTURE_URIS", "activity fixture extra constant")
    require(main_activity, "EXTRA_PERFORMANCE_BATCH", "activity batch extra constant")
    require(main_activity, "if (BuildConfig.DEBUG)", "release builds reject fixture extras")
    require(robia_app, "performanceFixtureMode = BuildConfig.DEBUG && performanceFixtureUris.isNotEmpty()", "app fixture mode debug gate")
    require(robia_app, "Synthetic fixture", "synthetic fixture display data")
    require(robia_app, "initialPerformanceBatchFixtureUris", "batch auto-start fixture seam")

    # POSIX /bin/sh capture script and failure artifacts.
    capture_script = require_single_command_capture_script(workflow)
    require(workflow, "android-emulator-runner invokes each script line with /bin/sh -c", "per-line shell warning")
    require(workflow, "set -eu", "POSIX shell strict mode")
    forbid(workflow, "pipefail", "bash-only pipefail in /bin/sh script")
    forbid(workflow, "record_capture_failure() {", "multiline shell function in per-line emulator script")
    forbid_regex(workflow, r"^\s*(?:function\s+)?[A-Za-z_][A-Za-z0-9_]*\s*\(\)\s*\{", "multiline shell function in per-line emulator script")
    forbid(workflow, "trap record_capture_failure EXIT", "function-backed trap in per-line emulator script")
    forbid(workflow, "<<'PY'", "multiline heredoc in per-line emulator script")
    require(capture_script, "trap 'status=$?; if [ \"$status\" -ne 0 ]; then", "inline capture failure trap")
    require(capture_script, 'exit "$status"', "failure trap preserves capture exit status")
    require(workflow, "capture-failure.txt", "failure marker artifact")
    require(workflow, "adb devices > performance-artifacts/adb-devices.txt 2>&1 || true", "ADB diagnostics on capture failure")
    require(workflow, "adb logcat -d -v threadtime > performance-artifacts/logcat.txt 2>&1 || true", "logcat diagnostics on capture failure")
    require(workflow, "- name: Prepare failure artifact directory", "artifact directory exists before capture")
    require(workflow, "if: always()", "artifact upload runs after failed capture")
    require(workflow, "if-no-files-found: error", "missing artifact evidence remains visible")
    require(workflow, "retention-days: 30", "artifact retention")

    # Bounded Perfetto capture: keep the scroll input duration, extend only the
    # trace window, and wait for the locally backgrounded adb process before
    # pulling the trace artifact. The old quoted remote-background form can
    # return before Perfetto finishes writing the trace.
    require(workflow, "DURATION=${{ inputs.duration_seconds }}", "workflow duration input assigned to shell variable")
    require(workflow, "TRACE_DURATION=$((DURATION + 5))", "Perfetto trace duration padding")
    require(workflow, "end=$(( $(date +%s) + DURATION ))", "scroll loop bounded by input duration")
    require(
        workflow,
        'adb shell perfetto -o /data/misc/perfetto-traces/robia-baseline.perfetto-trace -t "${TRACE_DURATION}s" sched freq gfx view wm am >/dev/null 2>&1 &',
        "locally backgrounded foreground Perfetto adb process",
    )
    require(workflow, "PERFETTO_ADB_PID=$!", "Perfetto adb PID capture")
    require(workflow, 'wait "$PERFETTO_ADB_PID" || true', "Perfetto adb wait before trace pull")
    require_regex(
        capture_script,
        r'while \[ "\$\(date \+%s\)" -lt "\$end" \]; do [^;]+; [^;]+; done; wait "\$PERFETTO_ADB_PID" \|\| true; adb shell dumpsys gfxinfo',
        "Perfetto adb wait after scroll loop",
    )
    require_regex(
        capture_script,
        r'wait "\$PERFETTO_ADB_PID" \|\| true; adb shell dumpsys gfxinfo[^;]*; adb pull /data/misc/perfetto-traces/robia-baseline\.perfetto-trace',
        "Perfetto adb wait before trace pull",
    )
    old_remote_perfetto = "adb shell " + "'perfetto"
    old_hardcoded_trace_duration = "-t " + "25s"
    forbid(workflow, old_remote_perfetto, "quoted remote Perfetto background shell")
    forbid_regex(workflow, r"adb shell ['\"][^'\"]*perfetto[^'\"]*&", "quoted remote Perfetto background shell")
    forbid(workflow, old_hardcoded_trace_duration, "hard-coded Perfetto trace duration")
    forbid(workflow, ">&1 &' || true", "remote-backgrounded Perfetto fire-and-forget")

    print("Android performance baseline workflow static contract checks passed")


def fail(message: str) -> None:
    raise SystemExit(f"static workflow contract check failed: {message}")


if __name__ == "__main__":
    main()
