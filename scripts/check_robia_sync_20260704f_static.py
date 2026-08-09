#!/usr/bin/env python3
"""Static guardrails for Robia Drive sync feedback batch 2026-07-04f."""
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
        "app/src/main/java/com/gusanitolabs/robia/data/LocalRepositories.kt",
        "archiveItemsWithTombstones",
        "syncTombstone(entityType = \"garment\"",
        "revision = deletedAtEpochMillis + 1",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/sync/LocalWardrobeSyncSnapshotRepository.kt",
        ".filterNot(ClothingItemEntity::isArchived)",
        "ref.clothingItemId in activeItemIds",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/sync/GoogleDriveWardrobeRepository.kt",
        "listBlobPathsWithPrefix",
        "deletedPhotoBlobPurgeCandidates",
        "api.deleteBlob(accessToken, blobPath)",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/data/SettingsRepository.kt",
        "driveFreshInstallRestoreAttempted",
        "preferences[cloudSetupPromptInteractedKey] = true",
        "markDriveFreshInstallRestoreAttempted",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt",
        "settings.driveFreshInstallRestoreAttempted",
        "settingsRepository.markDriveFreshInstallRestoreAttempted()",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt",
        "hasVisibleSyncActivity",
        "pendingOperationCount > 0",
        "content_cloud_sync_active",
        "showSyncActivity",
    )
    require(
        "app/src/test/java/com/gusanitolabs/robia/sync/DriveRestoreDiagnosticsTest.kt",
        "deletedPhotoBlobPurgeCandidates_includeGarmentTombstonesAndExcludeActiveBlobs",
    )
    print("Robia sync 2026-07-04f static guardrails passed")


if __name__ == "__main__":
    main()
