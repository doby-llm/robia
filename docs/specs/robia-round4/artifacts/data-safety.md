# Data safety facts and licence register (P03)

Status: from code facts on `main` @ `c3f997e` (after P02), checked 2026-11-01. This is an input for Manu and for POL. It is not a Play Console entry (Manu, out of scope).
Tags: **[C]** = read from code, a manifest, or a live HTTP response · **[H]** = not verified here · **verify in Play Console** = Manu must check the Play question before answering.

## 1. Method and limits

- Release variant: no gradle on this machine, so the release merged manifest was not built. The app's `AndroidManifest.xml` has no debug or staging overrides [C], so the release merge equals the app manifest plus the library manifests.
- Library manifests were read read-only from `~/.gradle/caches` (versions from `gradle/libs.versions.toml`). The staging merged manifest was parsed earlier in `inv-r4-play-readiness.md` §1.
- Network hosts in app source [C]: `huggingface.co` (model downloads), `github.com` (u2netp download), `www.googleapis.com` (Drive API), `one.google.com` (opened as a link only). No developer server, no analytics, crash or ads SDK in the catalog [C].

## 2. Licence register: downloaded and bundled items

| Item | Status | Licence | Upstream source | Resolved? |
|---|---|---|---|---|
| EmbeddingGemma 2 LiteRT-LM (commit e301f74) | downloaded | Apache-2.0 (card of `litert-community/…-litert-lm` and base `google/embeddinggemma-2`) [C] | https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm | Yes. The base card links the Gemma Prohibited Use Policy (https://ai.google.dev/gemma/prohibited_use_policy). **Unresolved — needs Manu:** whether the app must show that policy. |
| u2netp.onnx (rembg v0.0.0) | downloaded | MIT for the rembg project (`LICENSE.txt`, Copyright 2020 Daniel Gatis) [C]; model from U-2-Net, Apache-2.0 (`xuebinqin/U-2-Net` LICENSE) [C] | https://github.com/xuebinqin/U-2-Net, https://github.com/danielgatis/rembg | Yes. The weights' own licence is not stated in the file [H]. |
| **D-FINE** dfine_s_obj365 ONNX (commit a61e4cd) | downloaded | **Apache-2.0**: upstream `Peterande/D-FINE` LICENSE [C]; base card `ustc-community/dfine_s_obj365` has `license: apache-2.0` [C]. The ONNX card has no licence field [C]. | https://github.com/Peterande/D-FINE, https://huggingface.co/ustc-community/dfine_s_obj365 | **Resolved: Apache-2.0.** Training-data terms of the COCO/Objects365 weights were not checked [H]. |
| MobileSAM encoder and mask decoder (commit 0d3b403) | downloaded | MIT on the `Acly/MobileSAM` card [C]. Upstream `ChaoningZhang/MobileSAM` LICENSE is **Apache-2.0** [C]. | https://github.com/ChaoningZhang/MobileSAM, https://huggingface.co/Acly/MobileSAM | Yes. Both are permissive; the register lists both. Manu may pick one for the notice. |
| **magic_touch.tflite** (bundled, 6,227,884 bytes) | bundled | **unresolved — needs Manu.** | https://storage.googleapis.com/mediapipe-models/interactive_segmenter/magic_touch/float32/1/magic_touch.tflite | Provenance **resolved** [C]: sha256 `e24338a717c1b7ad8d159666677ef400babb7f33b8ad60c4d96db4ecf694cd25` matches the bundled file. Licence not stated on the MediaPipe Interactive Segmenter page (its CC-BY/Apache-2.0 notice covers the documentation and code samples) [C]. |

Note for Manu: `media/cutout/InteractiveSelector.kt:19` says `magic_touch` is "Apache-2.0". No source found in this round supports that statement. Do not rely on it until Manu confirms [C].

## 3. Libraries that ship in the APK (from `gradle/libs.versions.toml`)

Licences come from the POM `<licenses>` block in the Gradle cache [C] unless marked.

| Library | Version | Licence |
|---|---|---|
| LiteRT-LM (`litertlm-android`) | 0.18.0 | Apache-2.0 |
| MediaPipe Tasks Vision (`tasks-vision`) | 1.1.1 | Apache-2.0 |
| ONNX Runtime Android | 1.31.0 | MIT |
| ML Kit subject segmentation | 16.0.0-beta1 | ML Kit Terms of Service (not open source) |
| Play services Auth / Base | 21.3.0 / 18.11.1 | Android SDK License (not open source) |
| Coil (`coil-compose`) | 3.0.0 | Apache-2.0 |
| colorpicker-compose | 1.1.2 | Apache-2.0 |
| AndroidX (Room, WorkManager, DataStore, Lifecycle, Activity, Core), Compose BOM | as in `libs.versions.toml` | Apache-2.0 (POMs of Room, WorkManager, DataStore, Lifecycle checked [C]) |
| Inter font (`res/font/inter_*.ttf`) | — | SIL OFL 1.1 (`assets/licenses/inter-OFL.txt`) [C] |

Not listed: transitive libraries (for example Kotlin stdlib and coroutines) **unresolved — needs Manu** whether the list must cover them. Room compiler and KSP are build-time only.

## 4. Merged-manifest providers and services that can send data

Source: library AAR manifests in `~/.gradle/caches` (read-only) [C]. "Sends data" = what we could verify from bytes; everything else is marked.

| Component (declared by) | Kind | What it does | Sends data off device? | Action |
|---|---|---|---|---|
| `ai.onnxruntime.TelemetryInitializer` (onnxruntime-android 1.31.0) | ContentProvider | Loads the native library. The AAR also contains an HTTP client for telemetry (`ai/onnxruntime/telemetry/`) and a `PowerInfoReceiver` [C]. | **Unknown [H].** No endpoint string was found in the AAR classes or native libraries checked. | verify in Play Console; check ORT's `TelemetryInitializer` source for default behaviour |
| `TransportBackendDiscovery`, `backend:CctBackendFactory` (transport-runtime 3.1.0, transport-backend-cct 3.1.0) | Service / meta-data | Google's logging transport used by Google SDKs [C]. | **Unknown [H].** Depends on which SDK logs through it; ML Kit is a candidate. | verify in Play Console |
| `JobInfoSchedulerService`, `AlarmManagerSchedulerBroadcastReceiver` (transport-runtime) | Service / receiver | Local scheduling of transport jobs [C]. | No by itself; sending depends on the backend row above. | none |
| `MlKitInitProvider`, `MlKitComponentDiscoveryService`, `CommonComponentRegistrar`, `VisionCommonRegistrar`, `SubjectSegmentationRegistrar` (ML Kit common 18.10.0, vision-common 17.3.0, subject-segmentation 16.0.0-beta1) | ContentProvider / Service | On-device subject segmentation [C]. | **Unknown [H].** Whether ML Kit sends usage metrics was not verified. | verify in Play Console (ML Kit Terms apply) |
| `RevocationBoundService` (play-services-auth 21.3.0), exported, signin permission | Service | Google account revocation during sign-in [C]. | Yes, to Google, during the user-initiated Drive sign-in and revoke. | declare as the Google sign-in flow |
| Play services (basement, base, auth-api-phone, auth-base) | platform SDK | Google Play services runtime [C]. | **Unknown [H].** Play services' own device telemetry is governed by Google's terms. | verify in Play Console |
| WorkManager: `InitializationProvider`, `WorkManagerInitializer`, `SystemJobService`, `SystemForegroundService`, `RescheduleReceiver`, `DiagnosticsReceiver`, `ForceStopRunnable$BroadcastReceiver` (work-runtime 2.11.2) | Provider / Service / Receiver | Local background work [C]. `DiagnosticsReceiver` is exported only with the `DUMP` permission [C]. | No network. | none |
| `ProfileInstallerInitializer`, `ProfileInstallReceiver` (profileinstaller 1.4.1) | Provider / Receiver | Local profile install [C]. | No network. | none |
| `FileProvider`, `MainActivity` (app) | Provider / Activity | `exported=false` FileProvider with narrow paths; launcher activity [C]. | No. | none |
| LiteRT-LM, MediaPipe Tasks Core (AARs) | — | No providers or services declared in the AAR manifests [C]. | On-device inference. Network use was not checked [H]. | none |

## 5. Draft answers for the Play Data safety form

Draft only. Each row says what the code does, then what Manu should check.

| Play question | Draft | Basis | Status |
|---|---|---|---|
| Photos (garment images) | On device. Copied to the user's own Google Drive appdata only when the user enables backup and connects Drive (`drive.appdata` scope) [C]. No developer server. | §1; `drive.appdata` only (audit §5) | **verify in Play Console:** is user-initiated backup to the user's own Drive "collected"? Declare conservatively as collected, optional, for app functionality and backup if unsure. |
| Other garment data (names, tags, collections, sync records) | Same as photos. | Drive sync (audit §5) | verify with the row above |
| Email address of the Google account | Read from the Drive `about` endpoint (`GoogleDriveWardrobeRepository.kt:1863`) [C] and stored on device in DataStore (`SettingsRepository.kt:262-267`) [C]. Shown in Drive settings. Not sent to the developer. | code | **verify in Play Console:** data that only comes from Google and stays on device is probably not "collected". |
| Model downloads (huggingface.co, github.com) | HTTP GET with no user data from the app [C]. The servers see the IP address and standard HTTP headers. | §1, SPEC §7.3 | verify: IP addresses sent to third-party download hosts are probably not a declaration item |
| Encryption in transit | Yes. HTTPS everywhere; no cleartext (manifest, audit §1) [C]. | audit §1 | confirm |
| Deletion | In-app: disconnect Drive (`ui/RobiaApp.kt:495`); delete cloud backup (`ui/RobiaApp.kt:521` → `WardrobeSyncOperation.DeleteCloudBackup`); delete downloaded models (audit §3). Local data also goes with app uninstall or clear data [C]. | audit §5 | verify whether Play needs a deletion URL; the app creates no account of its own [H] |
| Analytics, ads, crash reporting | None in the catalog or code [C]. | §1 | confirm |
| Location, contacts, camera, microphone, health, files | Not requested. The camera is used only through the system camera intent; photos come through SAF pickers [C]. | merged manifest (audit §1) | confirm |
| Shared with third parties | Only the user's own Drive backup, through Google APIs. Nothing else is sent [C]. | §1 | **verify in Play Console** together with the photos row |

## 6. Open items (for Manu)

1. **magic_touch.tflite licence — unresolved — needs Manu.** Provenance is confirmed; the licence is not.
2. **EmbeddingGemma: Gemma Prohibited Use Policy — unresolved — needs Manu.** Whether the app must show it or link to it.
3. **Transitive libraries not listed in the licence asset — unresolved — needs Manu.**
4. **MobileSAM: MIT card vs Apache-2.0 upstream.** Both permissive. Manu chooses the notice wording.
5. **ORT telemetry, ML Kit / transport telemetry, Play services telemetry — verify in Play Console.**
6. **Photo backup to Drive: collected or not — verify in Play Console.**
7. **Email from Drive `about`: collected or not — verify in Play Console.**
8. **OFL full text:** `assets/licenses/inter-OFL.txt` is in the repository only. The in-app list shows the copyright line and licence name, with a pointer to the file. Manu decides whether the full text must appear in the app.
