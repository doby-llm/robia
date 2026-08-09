#!/usr/bin/env python3
"""Static guardrails for one-time Google Drive setup recommendation persistence."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def require(relative: str, *needles: str) -> None:
    text = read(relative)
    missing = [needle for needle in needles if needle not in text]
    if missing:
        formatted = "\n".join(f"  - {needle}" for needle in missing)
        raise AssertionError(f"{relative} missing expected guardrails:\n{formatted}")


def main() -> None:
    require(
        "app/src/main/java/com/gusanitolabs/robia/data/SettingsRepository.kt",
        "cloudSetupPromptInteracted = preferences[cloudSetupPromptInteractedKey] ?: false",
        "preferences[cloudSetupPromptInteractedKey] = true",
        "if (status != DriveSyncConnectionStatus.NotConfigured)",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt",
        "collectAsState(initial = null)",
        "val settingsLoaded = persistedSettings != null",
        "RobiaSettings(cloudSetupPromptInteracted = true)",
        "LaunchedEffect(\n        settingsLoaded,",
        "if (settingsLoaded &&",
        "!settings.cloudSetupPromptInteracted",
        "settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured",
        "cloudSetupGuard.isFirstRunRecommendation",
    )
    require(
        "app/src/test/java/com/gusanitolabs/robia/RegressionSourceContractTest.kt",
        "cloudSetupRecommendation_waitsForDurableSettingsBeforeShowing",
    )
    print("Drive setup prompt persistence static guardrails passed")


if __name__ == "__main__":
    main()
