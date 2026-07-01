#!/usr/bin/env python3
"""CI smoke/drift check for Robia's bundled additional-info TFLite model."""

from __future__ import annotations

import json
import math
import re
import sys
from pathlib import Path
from typing import Any

import numpy as np

try:
    from tflite_runtime.interpreter import Interpreter
except ImportError as exc:  # pragma: no cover - exercised by CI environment setup.
    raise SystemExit(
        "Missing tflite_runtime. Install CI dependencies with: "
        "python -m pip install numpy==1.26.4 tflite-runtime==2.14.0"
    ) from exc


REPO_ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "additional_info"
MANIFEST_PATH = ASSET_DIR / "mobilenet_v3_large.json"
RUNTIME_DETECTOR_PATH = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "com"
    / "gusanitolabs"
    / "robia"
    / "media"
    / "additionalinfo"
    / "TfliteAdditionalInfoDetector.kt"
)
EXPECTED_NORMALIZATION = "raw_rgb_0_255_embedded_mobilenet_v3_preprocess_input"
EXPECTED_FORMULA = "rgb"
EXPECTED_GENERATOR = "python numpy default_rng(seed=123), raw RGB float32 [0,255]; graph embeds MobileNetV3 rescale"
EXPECTED_INPUT_SHAPE = [1, 224, 224, 3]
SAFE_MODEL_FILE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]*\.tflite")


def main() -> int:
    manifest = load_manifest(MANIFEST_PATH)
    model_path = resolve_model_path(manifest)
    assert_manifest_contract(manifest)
    assert_manifest_driven_model_selection()

    interpreter = Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()

    input_details = interpreter.get_input_details()
    if len(input_details) != 1:
        fail(f"Expected one model input, found {len(input_details)}")
    input_detail = input_details[0]
    assert_equal(input_detail["shape"].tolist(), EXPECTED_INPUT_SHAPE, "input tensor shape")
    if input_detail["dtype"] != np.float32:
        fail(f"Expected float32 input tensor, found {input_detail['dtype']}")

    input_tensor = deterministic_raw_rgb_noise(EXPECTED_INPUT_SHAPE)
    interpreter.set_tensor(input_detail["index"], input_tensor)
    interpreter.invoke()

    outputs = read_outputs_by_head(interpreter, manifest)
    tolerance = float(manifest["deterministicNoiseBaseline"].get("tolerance", 0.0005))
    for head in manifest["outputs"]:
        name = head["name"]
        actual = outputs[name]
        baseline = manifest["deterministicNoiseBaseline"][name]
        assert_softmax(name, actual)
        assert_equal(list(actual.shape), baseline["shape"], f"{name} output shape")
        actual_argmax = int(np.argmax(actual[0]))
        actual_max_score = float(np.max(actual[0]))
        assert_equal(actual_argmax, int(baseline["argmax"]), f"{name} argmax")
        if not math.isclose(actual_max_score, float(baseline["maxScore"]), abs_tol=tolerance):
            fail(
                f"{name} maxScore drifted: expected {baseline['maxScore']} ± {tolerance}, "
                f"got {actual_max_score:.8f}"
            )
        print(f"{name}: shape={list(actual.shape)} argmax={actual_argmax} maxScore={actual_max_score:.8f}")

    print("TFLite additional-info smoke/drift check passed")
    return 0


def load_manifest(path: Path) -> dict[str, Any]:
    if not path.is_file():
        fail(f"Missing manifest: {path}")
    with path.open(encoding="utf-8") as manifest_file:
        return json.load(manifest_file)


def resolve_model_path(manifest: dict[str, Any]) -> Path:
    model_file = manifest.get("modelFile")
    if not isinstance(model_file, str):
        fail("manifest modelFile must be a string")
    if not is_safe_model_file(model_file):
        fail(f"Unsafe manifest modelFile: {model_file!r}")
    model_path = ASSET_DIR / model_file
    if not model_path.is_file():
        fail(f"Missing model selected by manifest modelFile: {model_path}")
    return model_path


def is_safe_model_file(model_file: str) -> bool:
    return (
        SAFE_MODEL_FILE.fullmatch(model_file) is not None
        and "/" not in model_file
        and "\\" not in model_file
        and ".." not in model_file
    )


def assert_manifest_contract(manifest: dict[str, Any]) -> None:
    input_spec = manifest.get("input", {})
    assert_equal(input_spec.get("shape"), EXPECTED_INPUT_SHAPE, "manifest input shape")
    assert_equal(input_spec.get("dtype"), "float32", "manifest input dtype")
    normalization = input_spec.get("normalization", {})
    assert_equal(normalization.get("type"), EXPECTED_NORMALIZATION, "normalization type")
    assert_equal(normalization.get("range"), [0.0, 255.0], "normalization range")
    assert_equal(normalization.get("formula"), EXPECTED_FORMULA, "normalization formula")
    assert_equal(
        manifest.get("deterministicNoiseBaseline", {}).get("generator"),
        EXPECTED_GENERATOR,
        "baseline generator",
    )


def assert_manifest_driven_model_selection() -> None:
    """Static guard: runtime and CI must both select the model from manifest.modelFile."""
    runtime_source = RUNTIME_DETECTOR_PATH.read_text(encoding="utf-8")
    script_source = Path(__file__).read_text(encoding="utf-8")
    main_source = script_source[script_source.index("def main()"): script_source.index("\n\ndef load_manifest")]

    assert_contains(runtime_source, "loadModel(context, config)", "runtime passes parsed manifest config to loadModel")
    assert_contains(
        runtime_source,
        "AdditionalInfoModelAssets.modelAssetPath(config.modelFile)",
        "runtime opens manifest-selected model asset",
    )
    assert_not_contains(
        runtime_source,
        "openFd(AdditionalInfoModelAssets.MODEL_FILE)",
        "runtime must not hard-code the model asset path",
    )
    assert_contains(main_source, "model_path = resolve_model_path(manifest)", "CI resolves model path from manifest")
    assert_contains(main_source, "Interpreter(model_path=str(model_path))", "CI opens manifest-selected model path")


def deterministic_raw_rgb_noise(shape: list[int]) -> np.ndarray:
    # The graph starts with MobileNetV3 rescaling, so the host feeds raw RGB float32.
    rng = np.random.default_rng(seed=123)
    return (rng.random(shape, dtype=np.float32) * np.float32(255.0)).astype(np.float32)


def read_outputs_by_head(interpreter: Interpreter, manifest: dict[str, Any]) -> dict[str, np.ndarray]:
    expected_heads = {tuple(output["shape"]): output["name"] for output in manifest["outputs"]}
    outputs: dict[str, np.ndarray] = {}
    for output_detail in interpreter.get_output_details():
        shape = tuple(int(dimension) for dimension in output_detail["shape"].tolist())
        if shape not in expected_heads:
            fail(f"Unexpected output tensor {output_detail['name']} with shape {list(shape)}")
        head_name = expected_heads[shape]
        outputs[head_name] = interpreter.get_tensor(output_detail["index"])

    expected_names = {output["name"] for output in manifest["outputs"]}
    assert_equal(set(outputs), expected_names, "output head names")
    return outputs


def assert_softmax(name: str, scores: np.ndarray) -> None:
    if np.any(~np.isfinite(scores)):
        fail(f"{name} output contains non-finite scores")
    if np.any(scores < -1e-6) or np.any(scores > 1.0 + 1e-6):
        fail(f"{name} output is not probability-like: min={scores.min()} max={scores.max()}")
    total = float(np.sum(scores[0]))
    if not math.isclose(total, 1.0, abs_tol=0.001):
        fail(f"{name} softmax sum drifted: expected 1.0 ± 0.001, got {total:.8f}")


def assert_equal(actual: Any, expected: Any, label: str) -> None:
    if actual != expected:
        fail(f"Unexpected {label}: expected {expected!r}, got {actual!r}")


def assert_contains(source: str, needle: str, label: str) -> None:
    if needle not in source:
        fail(f"Missing source contract: {label}")


def assert_not_contains(source: str, needle: str, label: str) -> None:
    if needle in source:
        fail(f"Unexpected source contract violation: {label}")


def fail(message: str) -> None:
    raise SystemExit(f"TFLite drift check failed: {message}")


if __name__ == "__main__":
    sys.exit(main())
