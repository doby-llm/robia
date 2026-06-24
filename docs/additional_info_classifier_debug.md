# Robia additional-info classifier local debug harness

This harness is for Raspberry Pi/local debugging of the additional-info classifier without forking classifier business logic away from the Android app.

## Shared-code boundary

Shared Kotlin/JVM module: `:additional-info-core`

Reused by both Android and the local CLI:

- Manifest parsing and validation (`AdditionalInfoModelManifest`)
- Input contract policy: shape `1x224x224x3`, alpha-over-white before resize, MobileNetV3 normalization (`AdditionalInfoPreprocessingPolicy`)
- RGB float32 NHWC tensor creation and tensor stats/checksum (`AdditionalInfoTensorBuilder`)
- Output-head mapping and tag selection (`AdditionalInfoTagMapper`)
- Debug/result models (`AdditionalInfoPrediction`, `AdditionalInfoTensorStats`, `AdditionalInfoDetectionDebug`)

Thin platform wrappers that remain intentionally separate:

- Android: `ContentResolver`/`BitmapFactory` image decode and Android `Bitmap` resize.
- Local CLI: JVM `ImageIO`/Java2D image decode and resize.
- Android model loading still uses assets. CLI model loading uses a filesystem path.

That is the practical non-divergence boundary: image IO APIs are platform-specific, but the classifier contract, tensor math after resize, manifest interpretation, output mapping, and selection policy are shared source.

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
