#!/usr/bin/env python3
"""Offline parity/debug fallback for Robia's Android additional-info classifier.

Prefer the Kotlin/JVM CLI (`scripts/run_additional_info_shared_cli.sh`) when Gradle
is available because it reuses the Android app's shared classifier core. This
Python fallback remains useful on machines where Kotlin/TFLite cannot run; it is
manifest-driven and intentionally mirrors the same documented contract.

Given an image path, this script reproduces the Android model-input contract from
app/src/main/assets/additional_info/mobilenet_v3_large.json: RGBA decode,
square-pad then auto-composite transparent/padded pixels over black or white,
RGB float32 NHWC, and MobileNetV3 normalization (rgb / 127.5 - 1.0). When tflite_runtime or TensorFlow is
installed it also runs the bundled TFLite model and prints top-k scores plus the
same selection policy as the Android mapper.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import sys
from pathlib import Path
from typing import Any, NoReturn

try:
    import numpy as np
except ImportError as exc:  # pragma: no cover - operator environment dependent.
    raise SystemExit("Missing numpy. Install with: python -m pip install numpy pillow") from exc

try:
    from PIL import Image
except ImportError as exc:  # pragma: no cover - operator environment dependent.
    raise SystemExit("Missing Pillow. Install with: python -m pip install pillow") from exc

REPO_ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = REPO_ROOT / "app" / "src" / "main" / "assets" / "additional_info"
DEFAULT_MANIFEST = ASSET_DIR / "mobilenet_v3_large.json"
EXPECTED_SHAPE = [1, 224, 224, 3]
EXPECTED_NORMALIZATION = "mobilenet_v3_preprocess_input"


def main() -> int:
    args = parse_args()
    manifest = load_manifest(args.manifest)
    assert_manifest_contract(manifest)

    tensor, image_debug = preprocess_image(args.image, args.preprocess)
    result: dict[str, Any] = {
        "image": str(args.image),
        "manifest": str(args.manifest),
        "modelVersion": manifest.get("modelVersion"),
        "preprocessMode": args.preprocess,
        "input": image_debug,
        "tensorStats": tensor_stats(tensor),
    }

    if args.dump_tensor:
        args.dump_tensor.parent.mkdir(parents=True, exist_ok=True)
        np.save(args.dump_tensor, tensor)
        result["dumpTensor"] = str(args.dump_tensor)

    model_path = args.model or ASSET_DIR / manifest["modelFile"]
    if args.no_model:
        result["modelSkipped"] = True
    else:
        outputs = run_tflite(model_path, tensor)
        result["model"] = str(model_path)
        result["outputs"] = summarize_outputs(outputs, manifest, top_k=args.top_k)
        result["selectedTagIds"] = select_tags(outputs, manifest)

    print(json.dumps(result, indent=2, sort_keys=True))
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path, help="Input image to classify/debug")
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST, help="Android model manifest JSON")
    parser.add_argument("--model", type=Path, default=None, help="TFLite model path; defaults to manifest modelFile asset")
    parser.add_argument("--top-k", type=int, default=5, help="Scores to show per output head")
    parser.add_argument("--no-model", action="store_true", help="Only print preprocessing/tensor stats")
    parser.add_argument("--dump-tensor", type=Path, default=None, help="Optional .npy output for the NHWC float32 tensor")
    parser.add_argument(
        "--preprocess",
        choices=("android", "android_legacy_resize_then_composite"),
        default="android",
        help="android matches the app path: square-pad then auto-composite; legacy shows the old stretch/white path.",
    )
    return parser.parse_args()


def load_manifest(path: Path) -> dict[str, Any]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def assert_manifest_contract(manifest: dict[str, Any]) -> None:
    input_spec = manifest.get("input", {})
    if input_spec.get("shape") != EXPECTED_SHAPE:
        fail(f"Unsupported input shape: {input_spec.get('shape')!r}")
    normalization = input_spec.get("normalization", {})
    if normalization.get("type") != EXPECTED_NORMALIZATION:
        fail(f"Unsupported normalization: {normalization.get('type')!r}")
    if normalization.get("formula") != "rgb / 127.5 - 1.0":
        fail(f"Unsupported normalization formula: {normalization.get('formula')!r}")


def preprocess_image(path: Path, mode: str) -> tuple[np.ndarray, dict[str, Any]]:
    with Image.open(path) as image:
        rgba = image.convert("RGBA")
        source_size = rgba.size
        alpha_channel = np.asarray(rgba.getchannel("A"), dtype=np.uint8)
        has_alpha = bool(alpha_channel.min() < 255)
        if mode == "android":
            rgb, background = square_pad_and_auto_composite(rgba)
            rgb = rgb.resize((224, 224), Image.Resampling.BILINEAR)
        else:
            resized_rgba = rgba.resize((224, 224), Image.Resampling.BILINEAR)
            rgb = composite_over_white(resized_rgba)
            background = "white"

    rgb_array = np.asarray(rgb, dtype=np.float32)
    tensor = (rgb_array / np.float32(127.5) - np.float32(1.0))[np.newaxis, ...].astype(np.float32)
    return tensor, {
        "sourceSize": list(source_size),
        "targetSize": [224, 224],
        "hasAlpha": has_alpha,
        "colorMode": f"RGBA->square RGB over {background}",
        "normalization": "rgb / 127.5 - 1.0",
        "shape": list(tensor.shape),
        "dtype": str(tensor.dtype),
    }


def composite_over_white(rgba: Image.Image) -> Image.Image:
    white = Image.new("RGBA", rgba.size, (255, 255, 255, 255))
    white.alpha_composite(rgba)
    return white.convert("RGB")


def square_pad_and_auto_composite(rgba: Image.Image) -> tuple[Image.Image, str]:
    background_name, background_rgb = choose_background(rgba)
    size = max(rgba.size)
    background = Image.new("RGBA", (size, size), (*background_rgb, 255))
    background.alpha_composite(rgba, ((size - rgba.width) // 2, (size - rgba.height) // 2))
    return background.convert("RGB"), background_name


def choose_background(rgba: Image.Image) -> tuple[str, tuple[int, int, int]]:
    array = np.asarray(rgba, dtype=np.float32)
    alpha = array[..., 3]
    mask = alpha >= 64
    if not np.any(mask) or float(alpha[mask].sum() / 255.0) < 8.0:
        return "white", (255, 255, 255)

    rgb = array[..., :3][mask]
    weights = alpha[mask] / 255.0
    luminance = (rgb[:, 0] * 0.2126 + rgb[:, 1] * 0.7152 + rgb[:, 2] * 0.0722) / 255.0
    foreground = float(np.average(luminance, weights=weights))
    contrast_white = contrast_ratio(foreground, 1.0)
    contrast_black = contrast_ratio(foreground, 0.0)
    if foreground >= 0.58 and contrast_black - contrast_white >= 1.0:
        return "black", (0, 0, 0)
    return "white", (255, 255, 255)


def contrast_ratio(first: float, second: float) -> float:
    lighter = max(first, second)
    darker = min(first, second)
    return (lighter + 0.05) / (darker + 0.05)


def tensor_stats(tensor: np.ndarray) -> dict[str, Any]:
    channel_axis = (0, 1, 2)
    finite = np.isfinite(tensor)
    return {
        "min": float(np.nanmin(tensor)),
        "max": float(np.nanmax(tensor)),
        "mean": float(np.nanmean(tensor)),
        "std": float(np.nanstd(tensor)),
        "channelMeans": [float(value) for value in np.nanmean(tensor, axis=channel_axis)],
        "channelMins": [float(value) for value in np.nanmin(tensor, axis=channel_axis)],
        "channelMaxs": [float(value) for value in np.nanmax(tensor, axis=channel_axis)],
        "nonFiniteCount": int(tensor.size - np.count_nonzero(finite)),
        "sha256": hashlib.sha256(tensor.tobytes()).hexdigest(),
    }


def run_tflite(model_path: Path, tensor: np.ndarray) -> dict[tuple[int, ...], np.ndarray]:
    try:
        from tflite_runtime.interpreter import Interpreter  # type: ignore[import-not-found]
    except ImportError:
        try:
            from tensorflow.lite.python.interpreter import Interpreter  # type: ignore
        except ImportError as exc:  # pragma: no cover - operator environment dependent.
            raise SystemExit(
                "Missing TFLite interpreter. Install either tflite_runtime or tensorflow, "
                "or pass --no-model for preprocessing-only diagnostics."
            ) from exc

    interpreter = Interpreter(model_path=str(model_path))
    interpreter.allocate_tensors()
    input_details = interpreter.get_input_details()
    if len(input_details) != 1:
        fail(f"Expected one input tensor, got {len(input_details)}")
    detail = input_details[0]
    if list(detail["shape"]) != EXPECTED_SHAPE:
        fail(f"Model input shape mismatch: {list(detail['shape'])}")
    interpreter.set_tensor(detail["index"], tensor)
    interpreter.invoke()
    return {
        tuple(int(dimension) for dimension in output_detail["shape"]): interpreter.get_tensor(output_detail["index"])[0]
        for output_detail in interpreter.get_output_details()
    }


def summarize_outputs(outputs: dict[tuple[int, ...], np.ndarray], manifest: dict[str, Any], top_k: int) -> dict[str, Any]:
    shape_to_head = {tuple(output["shape"]): output for output in manifest["outputs"]}
    summary: dict[str, Any] = {}
    for shape, scores in outputs.items():
        head = shape_to_head.get(shape)
        if head is None:
            fail(f"Unexpected output shape from model: {shape}")
        order = np.argsort(scores)[::-1][:top_k]
        summary[head["name"]] = {
            "shape": list(shape),
            "sum": float(np.sum(scores)),
            "topK": [
                {
                    "index": int(index),
                    "label": head["labels"][int(index)],
                    "tagId": head["tagIds"][int(index)],
                    "score": float(scores[int(index)]),
                }
                for index in order
            ],
        }
    return summary


def select_tags(outputs: dict[tuple[int, ...], np.ndarray], manifest: dict[str, Any]) -> list[str]:
    shape_to_scores = {shape: scores for shape, scores in outputs.items()}
    selected: list[str] = []
    for head in manifest["outputs"]:
        scores = shape_to_scores.get(tuple(head["shape"]))
        if scores is None:
            continue
        if head["name"] == "category":
            selected.extend(select_category(scores, head))
        else:
            selected.extend(select_multi_head(scores, head))
    return sorted(set(selected))


def select_category(scores: np.ndarray, head: dict[str, Any]) -> list[str]:
    order = np.argsort(scores)[::-1]
    top = int(order[0])
    second_score = float(scores[int(order[1])]) if len(order) > 1 else 0.0
    margin = float(head.get("margin") or 0.0)
    tag_id = head["tagIds"][top]
    if tag_id and float(scores[top]) >= float(head["threshold"]) and float(scores[top]) - second_score >= margin:
        return [tag_id]
    return []


def select_multi_head(scores: np.ndarray, head: dict[str, Any]) -> list[str]:
    top = int(np.argmax(scores))
    if head.get("multiSeasonLabel") and head["labels"][top] == head["multiSeasonLabel"] and float(scores[top]) >= float(head["threshold"]):
        return list(head.get("multiSeasonTagIds") or [])
    if float(scores[top]) < float(head["threshold"]):
        return []
    multi_threshold = float(head.get("multiSelectThreshold") or head["threshold"])
    near_tie_margin = float(head.get("nearTieMargin") or 0.0)
    selected = []
    for index, score in enumerate(scores):
        tag_id = head["tagIds"][index]
        if tag_id and (float(score) >= multi_threshold or float(scores[top]) - float(score) <= near_tie_margin):
            selected.append(tag_id)
    return selected


def fail(message: str) -> NoReturn:
    raise SystemExit(f"additional-info debug failed: {message}")


if __name__ == "__main__":
    sys.exit(main())
