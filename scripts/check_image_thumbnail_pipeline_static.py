#!/usr/bin/env python3
"""Static guardrails for Robia's bounded garment-thumbnail preview pipeline.

This check intentionally avoids Gradle, Android, emulators, and network access.
It protects the card-H contract that preview surfaces must use generated bounded
thumbnails without first forcing ImageView to decode the canonical full-size
photo, while detail/share paths keep the canonical image available.
"""
from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REQUIRED_IMAGE_STAGES = (
    "resolve",
    "decode",
    "bind",
    "first_draw",
    "in_flight_wait",
    "placeholder_visible",
    "eviction",
)


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.exists():
        raise AssertionError(f"missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def require(relative: str, *needles: str) -> None:
    text = read(relative)
    missing = [needle for needle in needles if needle not in text]
    if missing:
        formatted = "\n".join(f"  - {needle}" for needle in missing)
        raise AssertionError(f"{relative} missing expected thumbnail guardrails:\n{formatted}")


def forbid(relative: str, *needles: str) -> None:
    text = read(relative)
    present = [needle for needle in needles if needle in text]
    if present:
        formatted = "\n".join(f"  - {needle}" for needle in present)
        raise AssertionError(f"{relative} contains disallowed thumbnail pipeline markers:\n{formatted}")


def require_single_occurrence(text: str, needle: str, label: str) -> None:
    count = text.count(needle)
    if count != 1:
        raise AssertionError(f"expected exactly one {label}, found {count}: {needle}")


def main() -> None:
    image_store = read("app/src/main/java/com/gusanitolabs/robia/media/ClothingImageStore.kt")
    bounded_image = read("app/src/main/java/com/gusanitolabs/robia/ui/BoundedGarmentImage.kt")
    pipeline = read("app/src/main/java/com/gusanitolabs/robia/media/ImagePipeline.kt")
    app = read("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
    batch = read("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt")
    add_edit = read("app/src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt")
    color_review = read("app/src/main/java/com/gusanitolabs/robia/ui/ColorReviewScreen.kt")

    require(
        "app/src/main/java/com/gusanitolabs/robia/media/ClothingImageStore.kt",
        "THUMBNAIL_DIRECTORY = \"robia_thumbnails\"",
        "fun getOrCreateBoundedThumbnail(",
        "inJustDecodeBounds = true",
        "inSampleSize = thumbnailDecodeSampleSize",
        "Bitmap.createScaledBitmap",
        "data class ImageMetrics",
        "data class BoundedThumbnail",
    )
    require_single_occurrence(image_store, "contentUriFor(context, outputFile)", "thumbnail FileProvider URI return")
    require_single_occurrence(image_store, "readImageMetrics(context, existingUri)", "cached thumbnail metric read")
    require_single_occurrence(image_store, "outputFile.length().takeIf", "fresh thumbnail byte-size measurement")
    require("app/src/main/res/xml/file_paths.xml", "name=\"private_thumbnails\"", "path=\"robia_thumbnails/\"")

    require(
        "app/src/main/java/com/gusanitolabs/robia/ui/BoundedGarmentImage.kt",
        "thumbnailMaxEdgePx: Int?",
        "ImagePipeline.shared",
        "ImageTargetBounds",
        "onSizeChanged",
    )
    forbid("app/src/main/java/com/gusanitolabs/robia/ui/BoundedGarmentImage.kt", "setImageURI", "ImageView")
    require(
        "app/src/main/java/com/gusanitolabs/robia/media/ImagePipeline.kt",
        "ImageLoader.Builder",
        "MemoryCache.Builder",
        "DiskCache.Builder",
        "memoryCacheKey",
        "diskCacheKey",
        "decoderCoroutineContext",
        "prefetchPermit",
        "ownedInFlightRequests",
        "CoroutineStart.LAZY",
        "completeInFlight",
        "eventListenerFactory",
        "Size.ORIGINAL",
        "placeholder_visible",
        "in_flight_wait",
        "blankDurationMs",
        "activeDecodeCount",
        "first_draw",
        "TrackingMemoryCache",
        "recordEvictions",
        "onEvictions(keysBefore - delegate.keys)",
        "evictionCount.addAndGet",
        "diskCacheEntries = diskCacheEntryCount()",
        "errorRecorded",
    )
    for stage in REQUIRED_IMAGE_STAGES:
        if f'"{stage}"' not in pipeline:
            raise AssertionError(f"ImagePipeline.kt must record required sanitized image stage: {stage}")

    # Preview and detail display surfaces route through the bounded helper; explicit
    # export/share paths keep the canonical image available outside the display pipeline.
    for relative, text in (
        ("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt", app),
        ("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt", batch),
        ("app/src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt", add_edit),
        ("app/src/main/java/com/gusanitolabs/robia/ui/ColorReviewScreen.kt", color_review),
    ):
        if "ImageView(" in text or "AndroidView(" in text:
            raise AssertionError(f"{relative} must use BoundedGarmentImage instead of direct ImageView previews")

    require(
        "app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt",
        "GRID_THUMBNAIL_MAX_EDGE_PX",
        "DETAIL_DISPLAY_MAX_EDGE_PX",
        "GRID_THUMBNAIL_MAX_EDGE_PX * 3 / 4",
        "thumbnailMaxEdgePx = GRID_THUMBNAIL_MAX_EDGE_PX",
        "thumbnailMaxEdgePx = DETAIL_DISPLAY_MAX_EDGE_PX",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt",
        "BATCH_THUMBNAIL_MAX_EDGE_PX",
        "ImagePurpose.BatchPreview",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt",
        "EDITOR_PREVIEW_MAX_EDGE_PX",
        "QUICK_EDIT_PREVIEW_MAX_EDGE_PX",
        "ImagePurpose.Editor",
    )
    require(
        "app/src/main/java/com/gusanitolabs/robia/ui/ColorReviewScreen.kt",
        "COLOR_REVIEW_THUMBNAIL_MAX_EDGE_PX",
        "ImagePurpose.ColorReview",
    )

    require(
        ".github/workflows/android-performance-baseline.yml",
        "dumpsys meminfo com.gusanitolabs.robia > performance-artifacts/meminfo-before.txt",
        "dumpsys meminfo com.gusanitolabs.robia > performance-artifacts/meminfo-after.txt",
        "RobiaPerformance:I",
    )
    require(
        "scripts/summarize_performance_baseline.py",
        "REQUIRED_IMAGE_STAGES =",
        "IMAGE_STAGE_PATTERN",
        "IMAGE_STAGE_NAME_PATTERN",
        "image_stage_records =",
        "missing required image stages",
        "Image pipeline records captured",
        "read_meminfo",
        "meminfo-before.txt",
        "meminfo-after.txt",
    )
    summary = read("scripts/summarize_performance_baseline.py")
    workflow = read(".github/workflows/android-performance-baseline.yml")
    for stage in REQUIRED_IMAGE_STAGES:
        if stage not in summary:
            raise AssertionError(f"summarize_performance_baseline.py must parse image stage: {stage}")
        if stage not in workflow:
            raise AssertionError(f"android-performance-baseline workflow must recognize image stage: {stage}")

    print("Robia image thumbnail pipeline static guardrails passed")


if __name__ == "__main__":
    main()
