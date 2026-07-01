# Robia additional-info classifier local debug harness

This harness is for Raspberry Pi/local debugging of the additional-info classifier without forking classifier business logic away from the Android app.

## Shared-code boundary

Shared Kotlin/JVM module: `:additional-info-core`

Reused by both Android and the local CLI:

- Manifest parsing and validation (`AdditionalInfoModelManifest`)
- Input contract policy: shape `1x224x224x3`, square-pad aspect preservation, auto black/white background compositing, raw RGB float32 [0,255] external tensor values for the embedded MobileNetV3 graph preprocessing (`AdditionalInfoPreprocessingPolicy`). Do not externally normalize to `[-1,1]` for the current `mobilenet_v3_large.tflite` artifact.
- Raw RGB float32 NHWC tensor creation and tensor stats/checksum (`AdditionalInfoTensorBuilder`)
- Output-head mapping and tag selection (`AdditionalInfoTagMapper`)
- Debug/result models (`AdditionalInfoPrediction`, `AdditionalInfoTensorStats`, `AdditionalInfoDetectionDebug`)

Thin platform wrappers that remain intentionally separate:

- Android: `ContentResolver`/`BitmapFactory` image decode and Android `Bitmap` resize.
- Android Developer Mode export: Add/Edit diagnostics can save the exact 224x224 square-padded, auto-composited, resized RGB bitmap handed to `AdditionalInfoTensorBuilder` into the phone gallery as PNG for PC/Pi-side comparison. This is the pre-normalization visualization of the tensor input; it shares `AdditionalInfoImagePreprocessor.createExactInputBitmap` with inference and is not the Compose preview or cropped display thumbnail.
- Local CLI: JVM `ImageIO`/Java2D image decode and resize.
- Android model loading still uses assets. CLI model loading uses a filesystem path.

That is the practical non-divergence boundary: image IO APIs are platform-specific, but the classifier contract, tensor math after resize, manifest interpretation, output mapping, and selection policy are shared source. For the local `image_nn.png` forensic sample, the raw contract yields a Shirts/Tops-like result (`Shirts` 0.487181, `Tops` 0.377286, `Sweaters` 0.064816); the old externally normalized path's Coats-heavy result is not expected for this model artifact.

## Non-square resize contract

The Android/CLI classifier path intentionally uses `square_pad_preserve_aspect_then_resize_224`: it centers the decoded source on a square canvas, auto-selects a black or white composite background from foreground luminance, then resizes the square to `224x224`. This preserves garment aspect ratio and makes transparent/background handling visible in diagnostics (`preprocessing`, `resizeStrategy`, `backgroundStrategy`, tensor stats, output shapes, and top-k labels).

The current `robia_ai` training loader (`fashion.preprocessing.make_image_loader`) composites transparent pixels over a fixed white background and then calls `tf.image.resize(image, [target_height, target_width])` directly, so non-square training images are stretched rather than square-padded. That is a known app-vs-training deviation. It is left unchanged in this batch because the normalization contract had definitive forensic evidence and the non-square policy needs a model-quality comparison or retraining/export decision before changing production preprocessing. Track future work as either: align `robia_ai` train/eval/predict to square-pad before the next model export, or re-export/validate a model trained with the app's current aspect-preserving contract.

## Raspberry Pi command

From the repo root:

```bash
./gradlew :additional-info-cli:run --args='/path/to/image.jpg'
```

Optional flags:

```bash
./gradlew :additional-info-cli:run --args='/path/to/image.png --no-model'
./gradlew :additional-info-cli:run --args='/path/to/image.png --top-k 10'
./gradlew :additional-info-cli:run --args='/path/to/image.png --manifest app/src/main/assets/additional_info/mobilenet_v3_large.json --model app/src/main/assets/additional_info/mobilenet_v3_large.tflite'
```

`--no-model` is useful when the Pi does not have a TensorFlow Lite runtime compatible with the bundled dependency; it still exercises the shared manifest/preprocessing/tensor-stats path.

The older Python tool remains available for comparison/fallback:

```bash
python3 scripts/debug_additional_info_inference.py /path/to/image.png --no-model
```

Prefer the Gradle CLI when investigating Android-vs-Pi classifier behavior because it executes the same Kotlin core used by the app.
