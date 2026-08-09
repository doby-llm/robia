#!/usr/bin/env python3
"""Static guardrails for Robia's Drive-only backup policy."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID_NS = "http://schemas.android.com/apk/res/android"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require_contains(relative: str, *needles: str) -> None:
    text = read(relative)
    missing = [needle for needle in needles if needle not in text]
    if missing:
        formatted = "\n".join(f"  - {needle}" for needle in missing)
        raise AssertionError(f"{relative} missing expected Drive-only backup policy markers:\n{formatted}")


def require_not_contains(relative: str, *needles: str) -> None:
    text = read(relative)
    present = [needle for needle in needles if needle in text]
    if present:
        formatted = "\n".join(f"  - {needle}" for needle in present)
        raise AssertionError(f"{relative} contains disallowed Android platform backup markers:\n{formatted}")


def main() -> None:
    manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
    manifest_text = manifest_path.read_text(encoding="utf-8")
    manifest = ET.fromstring(manifest_text)
    application = manifest.find("application")
    if application is None:
        raise AssertionError("AndroidManifest.xml missing <application>")

    android_attr = f"{{{ANDROID_NS}}}"
    allow_backup = application.get(android_attr + "allowBackup")
    if allow_backup != "false":
        raise AssertionError(f"android:allowBackup must be false, got {allow_backup!r}")

    for forbidden in ("dataExtractionRules", "fullBackupContent"):
        if application.get(android_attr + forbidden) is not None:
            raise AssertionError(f"AndroidManifest.xml must not reference android:{forbidden}")

    require_contains(
        "app/src/main/AndroidManifest.xml",
        "Google Drive-only and explicit opt-in inside the app",
        "Disable Android Auto Backup and device transfer",
    )
    require_not_contains(
        "app/src/main/AndroidManifest.xml",
        "@xml/backup_rules",
        "@xml/data_extraction_rules",
    )

    for removed_resource in (
        ROOT / "app/src/main/res/xml/backup_rules.xml",
        ROOT / "app/src/main/res/xml/data_extraction_rules.xml",
    ):
        if removed_resource.exists():
            raise AssertionError(f"Removed platform backup resource still exists: {removed_resource}")

    require_contains(
        "docs/google_drive_sync_setup_plan.md",
        "Google Drive-only and opt-in",
        "Android Auto Backup and Android device-to-device transfer must not copy",
        "android:allowBackup=\"false\"",
        "Do not re-enable Android platform backup rules",
    )
    require_contains(
        "app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt",
        "androidPlatformBackup_isDisabledForDriveOnlyOptInPolicy",
    )
    print("Android platform backup disabled static guardrails passed")


if __name__ == "__main__":
    main()
