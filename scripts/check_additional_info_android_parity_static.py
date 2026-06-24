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
CONFIG = REPO_ROOT / "app/src/main/java/com/gusanitolabs/robia/media/additionalinfo/AdditionalInfoModelConfig.kt"
DETECTOR = REPO_ROOT / "app/src/main/java/com/gusanitolabs/robia/media/additionalinfo/TfliteAdditionalInfoDetector.kt"
ADD_EDIT = REPO_ROOT / "app/src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt"
DEBUG_SCRIPT = REPO_ROOT / "scripts/debug_additional_info_inference.py"


def main() -> int:
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    assert_equal(manifest["input"]["shape"], [1, 224, 224, 3], "manifest input shape")
    assert_equal(manifest["input"]["dtype"], "float32", "manifest input dtype")
    normalization = manifest["input"]["normalization"]
    assert_equal(normalization["type"], "mobilenet_v3_preprocess_input", "manifest normalization type")
    assert_equal(normalization["formula"], "rgb / 127.5 - 1.0", "manifest normalization formula")
    assert_equal(normalization.get("transparentPixels"), "composite_over_white", "transparent pixel policy")

    preprocessor_source = PREPROCESSOR.read_text(encoding="utf-8")
    assert_contains(preprocessor_source, "compositeAlphaOverWhite(source)", "composite alpha before resize")
    assert_contains(preprocessor_source, "Bitmap.createScaledBitmap(composited", "resize composited RGB image")
    assert_contains(preprocessor_source, "preprocessWithDiagnostics", "diagnostic preprocessing entry point")
    assert_contains(preprocessor_source, "AdditionalInfoTensorStats", "tensor stats collected")
    assert_contains(preprocessor_source, "inputSpec.normalizationType != NORMALIZATION_TYPE", "normalization type guarded")

    config_source = CONFIG.read_text(encoding="utf-8")
    assert_contains(config_source, "config.input.normalizationType != \"mobilenet_v3_preprocess_input\"", "manifest validation guards normalization type")

    detector_source = DETECTOR.read_text(encoding="utf-8")
    assert_contains(detector_source, "preprocessWithDiagnostics", "detector uses diagnostic preprocessor")
    assert_contains(detector_source, "debug = debug", "detector returns debug metadata")
    assert_contains(detector_source, "outputShapes = rawScores.mapValues", "detector reports output shapes")

    add_edit_source = ADD_EDIT.read_text(encoding="utf-8")
    assert_contains(add_edit_source, "val classifierUri = Uri.parse(originalPhotoUri ?: uriString)", "additional-info classifier defaults to original photo")
    assert_contains(add_edit_source, "falling back to cropped foreground", "cropped foreground fallback is diagnostic-visible")
    assert_contains(add_edit_source, "Additional info tensor:", "developer diagnostics show tensor stats")
    assert_contains(add_edit_source, "Additional info sourceUri:", "developer diagnostics show classifier source")

    debug_script_source = DEBUG_SCRIPT.read_text(encoding="utf-8")
    assert_contains(debug_script_source, "alpha-over-white before resize", "offline harness documents fixed preprocessing order")
    assert_contains(debug_script_source, "select_tags(outputs, manifest)", "offline harness mirrors Android tag policy")

    print("Android additional-info parity static checks passed")
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
