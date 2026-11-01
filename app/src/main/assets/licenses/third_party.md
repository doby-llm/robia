# Open-source licences and third-party models

Robia uses the components below. Models marked "downloaded at runtime" are fetched on first use from the address shown and checked against a pinned SHA-256 before the app uses them. Everything else ships inside the app.

## Downloaded at runtime

## EmbeddingGemma 2 (LiteRT-LM model)
- Used for: smart suggestions (on-device embeddings)
- Status: downloaded at runtime
- Version: commit e301f74d5551b0c2641bd5cb4652a76239d5c5f8, file embeddinggemma-2-text-vision-440m.litertlm
- Licence: Apache License 2.0 (model card)
- Upstream source: https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm
- Download address: https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/e301f74d5551b0c2641bd5cb4652a76239d5c5f8/embeddinggemma-2-text-vision-440m.litertlm
- Base model: https://huggingface.co/google/embeddinggemma-2 (Apache License 2.0)

## u2netp (cutout model)
- Used for: precise cutout (subject selection)
- Status: downloaded at runtime
- Version: rembg release v0.0.0, file u2netp.onnx
- Licence: Apache License 2.0 for the U-2-Net model; MIT License for the rembg project that distributes the file
- Upstream source: https://github.com/xuebinqin/U-2-Net (model), https://github.com/danielgatis/rembg (distribution)
- Download address: https://github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx

## D-FINE object detector (cutout model)
- Used for: precise cutout (object detection)
- Status: downloaded at runtime
- Version: commit a61e4cdfe4f9d3188a305d91e37dbf38688ffbb8 of onnx-community/dfine_s_obj365-ONNX (converted from ustc-community/dfine_s_obj365)
- Licence: Apache License 2.0 (upstream D-FINE repository and the base model card). The ONNX conversion's own card has no licence field.
- Upstream source: https://github.com/Peterande/D-FINE, https://huggingface.co/ustc-community/dfine_s_obj365
- Download address: https://huggingface.co/onnx-community/dfine_s_obj365-ONNX/resolve/a61e4cdfe4f9d3188a305d91e37dbf38688ffbb8/onnx/model.onnx

## MobileSAM image encoder (cutout model)
- Used for: precise cutout (segmentation)
- Status: downloaded at runtime
- Version: commit 0d3b403339b4674a82493d5e97964dd78089ddc8 of Acly/MobileSAM, file mobile_sam_image_encoder.onnx
- Licence: MIT License (licence on the Acly/MobileSAM model card). The upstream MobileSAM repository is Apache License 2.0.
- Upstream source: https://github.com/ChaoningZhang/MobileSAM, https://huggingface.co/Acly/MobileSAM
- Download address: https://huggingface.co/Acly/MobileSAM/resolve/0d3b403339b4674a82493d5e97964dd78089ddc8/mobile_sam_image_encoder.onnx

## MobileSAM mask decoder (cutout model)
- Used for: precise cutout (segmentation)
- Status: downloaded at runtime
- Version: commit 0d3b403339b4674a82493d5e97964dd78089ddc8 of Acly/MobileSAM, file sam_mask_decoder_multi.onnx
- Licence: MIT License (licence on the Acly/MobileSAM model card). The upstream MobileSAM repository is Apache License 2.0.
- Upstream source: https://github.com/ChaoningZhang/MobileSAM, https://huggingface.co/Acly/MobileSAM
- Download address: https://huggingface.co/Acly/MobileSAM/resolve/0d3b403339b4674a82493d5e97964dd78089ddc8/sam_mask_decoder_multi.onnx

## Bundled in the app

## magic_touch.tflite (interactive segmenter model)
- Used for: precise cutout (tap to select an object)
- Status: bundled
- Version: SHA-256 e24338a717c1b7ad8d159666677ef400babb7f33b8ad60c4d96db4ecf694cd25, identical to the MediaPipe file below
- Licence: Apache License 2.0 (official model card: "LICENSED UNDER Apache License, Version 2.0")
- Authors: Valentin Bazarevsky and Ben Hahn (Google), model card dated 13 Mar 2023
- Upstream source: https://storage.googleapis.com/mediapipe-assets/Model%20Card%20MagicTouch.pdf (model card); model file: https://storage.googleapis.com/mediapipe-models/interactive_segmenter/magic_touch/float32/1/magic_touch.tflite
- Documentation: https://ai.google.dev/edge/mediapipe/solutions/vision/interactive_segmenter (its page states no licence for the model file; the licence above comes from the model card)

## LiteRT-LM 0.18.0
- Licence: Apache License 2.0
- Upstream source: https://github.com/google-ai-edge/LiteRT-LM

## MediaPipe Tasks Vision 1.1.1
- Licence: Apache License 2.0
- Upstream source: https://mediapipe.dev/

## ONNX Runtime Android 1.31.0
- Licence: MIT License
- Upstream source: https://microsoft.github.io/onnxruntime/

## ML Kit subject segmentation 16.0.0-beta1
- Licence: ML Kit Terms of Service (not an open-source licence)
- Upstream source: https://developers.google.com/ml-kit/terms

## Google Play services (Auth 21.3.0, Base 18.11.1)
- Licence: Android Software Development Kit License (not an open-source licence)
- Upstream source: https://developer.android.com/studio/terms.html

## Coil 3.0.0 (coil-compose)
- Licence: Apache License 2.0
- Upstream source: https://github.com/coil-kt/coil

## colorpicker-compose 1.1.2
- Licence: Apache License 2.0
- Upstream source: https://github.com/skydoves/colorpicker-compose/

## AndroidX and Jetpack Compose
- Versions: Room 2.8.4, WorkManager 2.11.2, DataStore 1.1.1, Activity Compose 1.9.3, Lifecycle 2.8.7, Core KTX 1.15.0, Compose BOM 2024.12.01
- Licence: Apache License 2.0
- Upstream source: https://developer.android.com/jetpack/androidx

## Inter (font)
- Licence: SIL Open Font License 1.1
- Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter)
- Full licence text: licenses/inter-OFL.txt in the source repository

## Not yet listed
- Libraries pulled in indirectly by the items above are not listed yet — unresolved — needs Manu.
