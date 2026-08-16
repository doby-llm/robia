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
FILE_PATHS = ROOT / "app/src/main/res/xml/file_paths.xml"
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
    file_paths = read(FILE_PATHS)
    main_activity = read(MAIN_ACTIVITY)
    robia_app = read(ROBIA_APP)
    fixtures = read(FIXTURE_SCRIPT)
    summary = read(SUMMARY_SCRIPT)

    # Remote-only manual comparative emulator workflow; never local device or push CI.
    require(workflow, "workflow_dispatch:", "manual remote trigger")
    forbid(workflow, "pull_request:", "non-manual trigger")
    forbid(workflow, "push:", "non-manual trigger")
    require(workflow, "name: Comparative emulator baseline", "comparative emulator job name")
    require(workflow, "python3 scripts/check_image_thumbnail_pipeline_static.py", "thumbnail pipeline static contract step")
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
    require(summary, "Image pipeline records captured", "report image pipeline evidence count")
    require(summary, "meminfo-before.txt", "report before-scroll meminfo evidence")
    require(summary, "meminfo-after.txt", "report after-scroll meminfo evidence")
    require(summary, "require_valid_baseline_evidence(", "report refuses missing measurement evidence")
    require(summary, "No process found", "report treats platform no-process dumps as invalid")

    # Synthetic fixtures and debug-only instrumentation/extras.
    require(workflow, "python scripts/generate_performance_fixtures.py --output performance-fixtures", "fixture generation step")
    require(workflow, "performance-fixtures/manifest.json", "fixture manifest use")
    require(fixtures, "FIXTURE_COUNT = 60", "60-item fixture contract")
    require(fixtures, '"synthetic": True', "synthetic fixture manifest flag")
    require(workflow, '"$PACKAGE.PERFORMANCE_FIXTURE_URIS"', "fixture URI debug extra")
    require(workflow, '"$PACKAGE.PERFORMANCE_BATCH"', "batch mode debug extra")
    require(workflow, "debug-only synthetic fixture extras", "debug-only workflow documentation")
    require(main_activity, "EXTRA_PERFORMANCE_FIXTURE_URIS", "activity fixture extra constant")
    require(main_activity, "EXTRA_PERFORMANCE_BATCH", "activity batch extra constant")
    require(main_activity, "if (BuildConfig.DEBUG)", "release builds reject fixture extras")
    require(main_activity, "intent.performanceFixtureUriStrings(EXTRA_PERFORMANCE_FIXTURE_URIS)", "fixture URI reader uses safe raw-extra adapter")
    require(main_activity, "is Array<*> -> extra.toStringListOrEmpty()", "adb --esa String[] fixture extra support")
    require(main_activity, "is ArrayList<*> -> extra.toStringListOrEmpty()", "ArrayList fixture extra compatibility")
    require(main_activity, "value as? String ?: return emptyList()", "unexpected fixture extra values are rejected")
    forbid(main_activity, "getStringArrayListExtra(EXTRA_PERFORMANCE_FIXTURE_URIS).orEmpty()", "String[] benchmark extra ClassCastException regression")
    require(robia_app, "performanceFixtureMode = BuildConfig.DEBUG && performanceFixtureUris.isNotEmpty()", "app fixture mode debug gate")
    require(robia_app, "Synthetic fixture", "synthetic fixture display data")
    require(robia_app, "initialPerformanceBatchFixtureUris", "batch auto-start fixture seam")
    capture_script = require_single_command_capture_script(workflow)

    # API 35 scoped storage: stage through adb-readable /data/local/tmp, then
    # copy into the debug app's private files/ directory with run-as so the
    # FileProvider <files-path name="private_clothing_images"> mapping resolves.
    require(file_paths, 'name="private_clothing_images"', "private clothing image FileProvider name")
    require(file_paths, 'path="robia_clothing_images/"', "private clothing image FileProvider path")
    require(capture_script, "STAGING_DIR=/data/local/tmp/robia-performance-fixtures", "adb-accessible temporary fixture staging")
    require(capture_script, "APP_FIXTURE_DIR=files/robia_clothing_images", "app-private fixture destination")
    require(capture_script, 'adb push performance-fixtures/. "$STAGING_DIR"', "fixture push into temporary staging")
    require(capture_script, 'adb shell chmod -R 755 "$STAGING_DIR"', "run-as readable staging permissions")
    require(capture_script, "run-as com.gusanitolabs.robia sh -c", "debug app-owned run-as copy")
    require(capture_script, "rm -rf ${APP_FIXTURE_DIR}; mkdir -p ${APP_FIXTURE_DIR}; cp ${STAGING_DIR}/fixture-*.jpg ${APP_FIXTURE_DIR}/", "private fixture copy command")
    require(capture_script, 'fixture_count=$(adb shell "run-as com.gusanitolabs.robia sh -c', "private fixture count validation")
    require(capture_script, '[ "$fixture_count" = "60" ]', "60 staged private fixtures")
    require(workflow, 'base = "content://com.gusanitolabs.robia.fileprovider/private_clothing_images/"', "private FileProvider fixture URI base")
    forbid(workflow, "adb shell mkdir -p /sdcard/Android/data", "direct app-specific external storage mkdir")
    forbid(workflow, "adb push performance-fixtures/. /sdcard/Android/data", "direct app-specific external storage push")
    forbid(workflow, "/sdcard/Android/data/com.gusanitolabs.robia/files/Pictures/robia_clothing_images", "direct app-specific external storage fixture staging")
    forbid(workflow, 'content://com.gusanitolabs.robia.fileprovider/clothing_images/', "external-files FileProvider fixture URI base")

    # POSIX /bin/sh capture script and failure artifacts.
    require(workflow, "android-emulator-runner invokes each script line with /bin/sh -c", "per-line shell warning")
    require(workflow, "set -eu", "POSIX shell strict mode")
    forbid(workflow, "pipefail", "bash-only pipefail in /bin/sh script")
    forbid(workflow, "record_capture_failure() {", "multiline shell function in per-line emulator script")
    forbid_regex(workflow, r"^\s*(?:function\s+)?[A-Za-z_][A-Za-z0-9_]*\s*\(\)\s*\{", "multiline shell function in per-line emulator script")
    forbid(workflow, "trap record_capture_failure EXIT", "function-backed trap in per-line emulator script")
    forbid(workflow, "<<'PY'", "multiline heredoc in per-line emulator script")
    require(capture_script, "trap 'status=$?; if [ \"$status\" -ne 0 ]; then", "inline capture failure trap")
    require(capture_script, "[ -s performance-artifacts/capture-failure.txt ] || printf", "failure trap preserves specific diagnostics")
    require(capture_script, 'exit "$status"', "failure trap preserves capture exit status")
    require(workflow, "capture-failure.txt", "failure marker artifact")
    require(workflow, "adb devices > performance-artifacts/adb-devices.txt 2>&1 || true", "ADB diagnostics on capture failure")
    require(workflow, "adb logcat -d -v threadtime > performance-artifacts/logcat.txt 2>&1 || true", "logcat diagnostics on capture failure")
    require(workflow, "- name: Prepare failure artifact directory", "artifact directory exists before capture")
    require(workflow, "if: always()", "artifact upload runs after failed capture")
    require(workflow, "if-no-files-found: error", "missing artifact evidence remains visible")
    require(workflow, "retention-days: 30", "artifact retention")
    require(capture_script, "PACKAGE=com.gusanitolabs.robia", "single package launch/readiness variable")
    require(capture_script, 'ACTIVITY="$PACKAGE/.MainActivity"', "single activity launch/readiness variable")
    require(capture_script, "LAUNCH_OUTPUT=performance-artifacts/launch-output.txt", "launch output diagnostic artifact")
    require(capture_script, "LAUNCH_STATUS=0", "captured launch exit status default")
    require(capture_script, 'adb shell am start -n "$ACTIVITY"', "non-blocking activity launch command")
    require(capture_script, "|| LAUNCH_STATUS=$?", "captured launch exit status")
    require(capture_script, 'printf "launch_exit_status=%s\\n" "$LAUNCH_STATUS" > performance-artifacts/launch-status.txt', "launch exit status diagnostic artifact")
    require(capture_script, '[ "$LAUNCH_STATUS" -ne 0 ]', "launch command exit-status rejection")
    require(capture_script, 'grep -Eq "Error:|Exception" "$LAUNCH_OUTPUT"', "launch output error rejection")
    require(capture_script, "READINESS_TIMEOUT_SECONDS=45", "explicit launch readiness timeout")
    require(capture_script, "READINESS_DEADLINE=$(( $(date +%s) + READINESS_TIMEOUT_SECONDS ))", "bounded launch readiness deadline")
    require(capture_script, 'ROBIA_PID=$(adb shell pidof "$PACKAGE"', "package process readiness check")
    require(capture_script, "performance-artifacts/activity-state.txt", "activity readiness diagnostic artifact")
    require(capture_script, 'grep -Fq "$ACTIVITY" performance-artifacts/activity-state.txt', "activity readiness check")
    require(capture_script, 'grep -Eq "topResumedActivity|mResumedActivity|ResumedActivity" performance-artifacts/activity-state.txt', "resumed activity readiness check")
    require(capture_script, "performance-artifacts/launch-readiness.txt", "launch readiness success artifact")
    require(capture_script, 'adb shell dumpsys package "$PACKAGE" > performance-artifacts/package-dump.txt 2>&1 || true', "package diagnostics on readiness failure")
    require(capture_script, 'adb shell ps -A | grep "$PACKAGE" > performance-artifacts/robia-ps.txt 2>&1 || true', "process diagnostics on readiness failure")
    require(capture_script, "adb shell dumpsys window windows > performance-artifacts/window-state.txt 2>&1 || true", "window diagnostics on readiness failure")
    require(capture_script, "PACKAGE_REGEX=com[.]gusanitolabs[.]robia", "stable readiness package regex")
    require(capture_script, "LAST_ROBIA_PID=", "stable readiness previous-pid state")
    require(capture_script, "STABLE_READINESS_SAMPLES=0", "stable readiness sample counter")
    require(capture_script, 'elif [ "$ROBIA_PID" != "$LAST_ROBIA_PID" ]; then', "pid stability gate")
    require(capture_script, 'grep -Eq "app=ProcessRecord\\\\{[^}]*[[:space:]]${ROBIA_PID}:${PACKAGE_REGEX}" performance-artifacts/activity-state.txt', "activity attached to stable process gate")
    require(capture_script, 'grep -Fq "reportedDrawn=true" performance-artifacts/activity-state.txt', "reported-drawn readiness gate")
    require(capture_script, 'grep -Fq "firstWindowDrawn=true" performance-artifacts/activity-state.txt', "first-window-drawn readiness gate")
    require(capture_script, 'grep -Eq "mCurrentFocus=.*Splash Screen|mFocusedApp=.*Splash Screen" performance-artifacts/window-state.txt', "splash-focused rejection gate")
    require(capture_script, 'grep -Eq "mCurrentFocus=.*${PACKAGE_REGEX}/|mFocusedApp=.*${PACKAGE_REGEX}/[.]MainActivity|mFocusedApp=.*${PACKAGE_REGEX}/${PACKAGE_REGEX}[.]MainActivity" performance-artifacts/window-state.txt', "visible Robia window readiness gate")
    require(capture_script, 'adb shell dumpsys meminfo "$PACKAGE" > performance-artifacts/readiness-meminfo.txt 2>&1 || true', "readiness meminfo probe diagnostic")
    require(capture_script, 'adb shell dumpsys gfxinfo "$PACKAGE" > performance-artifacts/readiness-gfxinfo.txt 2>&1 || true', "readiness gfxinfo probe diagnostic")
    require(capture_script, 'grep -Eq "^[[:space:]]*TOTAL[[:space:]]+[0-9]+" performance-artifacts/readiness-meminfo.txt', "readiness meminfo parse gate")
    require(capture_script, 'grep -Eq "Janky frames:|Total frames rendered:" performance-artifacts/readiness-gfxinfo.txt', "readiness gfxinfo parse gate")
    require(capture_script, "Robia stable visible readiness failed after", "readiness-specific failure diagnostic")
    require(capture_script, "reason=%s; pid=%s; expected_activity=%s", "actionable readiness failure reason")
    require(capture_script, "Robia stable visible readiness pid=%s activity=%s stable_samples=%s reason=%s", "stable readiness success diagnostic")
    forbid(capture_script, 'if [ -n "$ROBIA_PID" ] && grep -Fq "$ACTIVITY" performance-artifacts/activity-state.txt && grep -Eq "topResumedActivity|mResumedActivity|ResumedActivity" performance-artifacts/activity-state.txt; then break; fi', "old pid-plus-resumed-only readiness gate")
    forbid(capture_script, 'printf "Robia ready pid=%s activity=%s\\n"', "old loose readiness success diagnostic")
    require_regex(
        capture_script,
        r'printf "Robia stable visible readiness pid=%s activity=%s stable_samples=%s reason=%s.*performance-artifacts/launch-readiness\.txt; sleep 8; DURATION=20; TRACE_DURATION=.*adb shell dumpsys gfxinfo com\.gusanitolabs\.robia reset; adb shell am send-trim-memory com\.gusanitolabs\.robia RUNNING_CRITICAL \|\| true; adb shell dumpsys meminfo com\.gusanitolabs\.robia > performance-artifacts/meminfo-before\.txt',
        "stable visible readiness before measurement capture",
    )
    forbid(capture_script, "adb shell am start -W", "blocking am start wait regression")
    forbid(capture_script, "LaunchState: UNKNOWN", "launch-state based readiness regression")
    forbid(capture_script, "Status: timeout", "am start wait-timeout regression")
    require(workflow, "adb shell am send-trim-memory com.gusanitolabs.robia RUNNING_CRITICAL || true", "pre-scroll trim-memory measurement setup")
    require(workflow, "adb shell dumpsys meminfo com.gusanitolabs.robia > performance-artifacts/meminfo-before.txt", "before-scroll meminfo capture")
    require(workflow, "adb shell dumpsys meminfo com.gusanitolabs.robia > performance-artifacts/meminfo-after.txt", "after-scroll meminfo capture")
    require(capture_script, 'grep -Eq "^[[:space:]]*TOTAL[[:space:]]+[0-9]+" performance-artifacts/meminfo-before.txt', "before-scroll parseable memory gate")
    require(capture_script, 'grep -Eq "^[[:space:]]*TOTAL[[:space:]]+[0-9]+" performance-artifacts/meminfo-after.txt', "after-scroll parseable memory gate")
    require(capture_script, 'grep -Eq "Janky frames:|Total frames rendered:" performance-artifacts/gfxinfo.txt', "parseable frame evidence gate")
    require(capture_script, 'grep -q "batch_stage" performance-artifacts/batch-stages.log', "non-empty batch stage evidence gate")
    require(
        capture_script,
        "for image_stage in resolve decode bind first_draw in_flight_wait placeholder_visible eviction; do",
        "all required sanitized RobiaPerformance image stages evidence gate",
    )
    require(
        capture_script,
        'grep -Eq "stage=${image_stage}([[:space:]]|$)" performance-artifacts/batch-stages.log',
        "per-stage sanitized RobiaPerformance image evidence check",
    )

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
