#!/usr/bin/env python3
"""Static parity checks for Robia Android additional-info inference wiring.

This intentionally avoids Gradle and TFLite runtime so it is safe on the ARM
worker host. CI/full Android validation still owns compilation.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import NoReturn

REPO_ROOT = Path(__file__).resolve().parents[1]
MANIFEST = REPO_ROOT / "app/src/main/assets/additional_info/mobilenet_v3_large.json"
PREPROCESSOR = REPO_ROOT / "app/src/main/java/com/gusanitolabs/robia/media/additionalinfo/AdditionalInfoImagePreprocessor.kt"
EXPORTER = REPO_ROOT / "app/src/main/java/com/gusanitolabs/robia/media/additionalinfo/AdditionalInfoInputImageExporter.kt"
CONFIG_LOADER = REPO_ROOT / "app/src/main/java/com/gusanitolabs/robia/media/additionalinfo/AdditionalInfoModelConfig.kt"
DETECTOR = REPO_ROOT / "app/src/main/java/com/gusanitolabs/robia/media/additionalinfo/TfliteAdditionalInfoDetector.kt"
SHARED_CONFIG = REPO_ROOT / "additional-info-core/src/main/kotlin/com/gusanitolabs/robia/media/additionalinfo/AdditionalInfoModelConfig.kt"
SHARED_TENSOR = REPO_ROOT / "additional-info-core/src/main/kotlin/com/gusanitolabs/robia/media/additionalinfo/AdditionalInfoTensorBuilder.kt"
SHARED_MAPPER = REPO_ROOT / "additional-info-core/src/main/kotlin/com/gusanitolabs/robia/media/additionalinfo/AdditionalInfoTagMapper.kt"
CLI = REPO_ROOT / "additional-info-cli/src/main/kotlin/com/gusanitolabs/robia/additionalinfo/cli/AdditionalInfoCli.kt"
ADD_EDIT = REPO_ROOT / "app/src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt"
DEBUG_SCRIPT = REPO_ROOT / "scripts/debug_additional_info_inference.py"
DOC = REPO_ROOT / "docs/additional_info_classifier_debug.md"


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assert_equal(manifest["input"]["shape"], [1, 224, 224, 3], "manifest input shape")
    assert_equal(manifest["input"]["dtype"], "float32", "manifest input dtype")
    normalization = manifest["input"]["normalization"]
    assert_equal(normalization["type"], "mobilenet_v3_preprocess_input", "manifest normalization type")
    assert_equal(normalization["formula"], "rgb / 127.5 - 1.0", "manifest normalization formula")
    assert_equal(
        normalization.get("transparentPixels"),
        "auto_composite_black_or_white_after_square_pad",
        "transparent pixel policy",
    )

    shared_config = SHARED_CONFIG.read_text(encoding="utf-8")
    assert_contains(shared_config, "object AdditionalInfoModelManifest", "shared manifest parser exists")
    assert_contains(shared_config, "AdditionalInfoPreprocessingPolicy.expectedShape", "manifest validation uses shared input policy")
    assert_contains(shared_config, "AdditionalInfoPreprocessingPolicy.normalizationType", "manifest validation uses shared normalization policy")

    shared_tensor = SHARED_TENSOR.read_text(encoding="utf-8")
    assert_contains(shared_tensor, "object AdditionalInfoPreprocessingPolicy", "shared preprocessing policy exists")
    assert_contains(shared_tensor, "fun fromRgbPixels", "shared tensor builder exists")
    assert_contains(shared_tensor, "channel / 127.5f - 1f", "shared MobileNetV3 normalization math")
    assert_contains(shared_tensor, "AdditionalInfoTensorStats", "shared tensor stats collected")

    shared_mapper = SHARED_MAPPER.read_text(encoding="utf-8")
    assert_contains(shared_mapper, "object AdditionalInfoTagMapper", "shared mapper exists")
    assert_contains(shared_mapper, "selectCategory", "category policy shared")
    assert_contains(shared_mapper, "selectMultiHead", "season/occasion policy shared")

    preprocessor_source = PREPROCESSOR.read_text(encoding="utf-8")
    assert_contains(preprocessor_source, "chooseCompositeBackground(source)", "auto black/white composite background heuristic")
    assert_contains(preprocessor_source, "squarePadAndComposite(source, background.color)", "square pad before resize")
    assert_contains(preprocessor_source, "Bitmap.createScaledBitmap", "Android wrapper still owns Bitmap resize")
    assert_contains(preprocessor_source, "AdditionalInfoTensorBuilder.fromRgbPixels", "Android uses shared tensor builder")
    assert_contains(preprocessor_source, "AdditionalInfoPreprocessingPolicy.normalizationType", "Android guards normalization via shared policy")
    assert_contains(preprocessor_source, "fun createExactInputBitmap", "Android exposes exact preprocessed bitmap for developer export")
    assert_contains(preprocessor_source, "ExactInputBitmap", "developer export receives the same resized composited bitmap used for tensor creation")

    exporter_source = EXPORTER.read_text(encoding="utf-8")
    assert_contains(exporter_source, "createExactInputBitmap", "developer export reuses exact preprocessor bitmap")
    assert_contains(exporter_source, "MediaStore.Images.Media", "developer export saves through MediaStore gallery")
    assert_contains(exporter_source, "Bitmap.CompressFormat.PNG", "developer export writes lossless PNG")

    loader_source = CONFIG_LOADER.read_text(encoding="utf-8")
    assert_contains(loader_source, "AdditionalInfoModelManifest.parse", "Android loader delegates parsing to shared manifest parser")
    assert_contains(loader_source, "AdditionalInfoModelManifest.validate", "Android loader delegates validation to shared manifest parser")

    detector_source = DETECTOR.read_text(encoding="utf-8")
    assert_contains(detector_source, "preprocessWithDiagnostics", "detector uses diagnostic preprocessor")
    assert_contains(detector_source, "availableTags.map(GarmentTag::id).toSet()", "detector passes available tag ids to shared mapper")
    assert_contains(detector_source, "outputShapes = rawScores.mapValues", "detector reports output shapes")

    cli_source = CLI.read_text(encoding="utf-8")
    assert_contains(cli_source, "AdditionalInfoModelManifest.parse", "CLI uses shared manifest parser")
    assert_contains(cli_source, "AdditionalInfoTensorBuilder.fromRgbPixels", "CLI uses shared tensor builder")
    assert_contains(cli_source, "AdditionalInfoTagMapper.map", "CLI uses shared tag mapper")

    add_edit_source = ADD_EDIT.read_text(encoding="utf-8")
    assert_contains(add_edit_source, "val classifierUri = croppedUri", "additional-info classifier defaults to cropped foreground photo")
    assert_contains(add_edit_source, "falling back to original", "original fallback is diagnostic-visible")
    assert_contains(add_edit_source, "exportAdditionalInfoInputImage", "developer mode can export exact NN input image")
    assert_contains(add_edit_source, "developerModeEnabled", "developer input-image export remains gated by developer mode")
    assert_contains(add_edit_source, "Additional info tensor:", "developer diagnostics show tensor stats")
    assert_contains(add_edit_source, "Additional info sourceUri:", "developer diagnostics show classifier source")

    debug_script_source = DEBUG_SCRIPT.read_text(encoding="utf-8")
    assert_contains(debug_script_source, "square-pad then auto-composite", "offline harness documents fixed preprocessing order")
    assert_contains(debug_script_source, "select_tags(outputs, manifest)", "Python harness remains available as comparison fallback")

    doc_source = DOC.read_text(encoding="utf-8")
    assert_contains(doc_source, "./gradlew :additional-info-cli:run", "Raspberry Pi CLI command documented")
    assert_contains(doc_source, "Developer Mode export", "developer input-image export documented")
    assert_contains(doc_source, "Shared-code boundary", "shared-code boundary documented")

    print("Android additional-info shared-core static checks passed")
    return 0


def assert_equal(actual: object, expected: object, label: str) -> None:
    if actual != expected:
        fail(f"Unexpected {label}: expected {expected!r}, got {actual!r}")


def assert_contains(source: str, needle: str, label: str) -> None:
    if needle not in source:
        fail(f"Missing source contract: {label}")


def fail(message: str) -> NoReturn:
    raise SystemExit(f"static parity check failed: {message}")


if __name__ == "__main__":
    sys.exit(main())
