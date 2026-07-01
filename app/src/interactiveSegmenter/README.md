# MediaPipe Magic Touch interactive segmenter

The Quick Edit segment eraser can bundle Google's MediaPipe Magic Touch
interactive segmenter model from:

https://storage.googleapis.com/mediapipe-models/interactive_segmenter/magic_touch/float32/1/magic_touch.tflite

Pinned artifact in this repository:

- Path: `app/src/interactiveSegmenter/assets/mediapipe/magic_touch.tflite`
- Byte count: `6,227,884` (`6227884` bytes)
- SHA-256: `e24338a717c1b7ad8d159666677ef400babb7f33b8ad60c4d96db4ecf694cd25`

Build inclusion is controlled by the Gradle property
`robia.bundleInteractiveSegmenter` and defaults to `true`. Set
`-Probia.bundleInteractiveSegmenter=false` to exclude this source-set asset and
emit `BuildConfig.ROBIA_INTERACTIVE_SEGMENTER_ENABLED=false`; the app will keep
Quick Edit brightness, temperature, save, and fallback flows available while
showing the eraser as unavailable.

License/terms note before merge: this file is redistributed from Google's
MediaPipe model hosting. Confirm the current upstream MediaPipe model license and
Google terms for redistribution are acceptable for Robia before pushing/releasing
builds that bundle it.
