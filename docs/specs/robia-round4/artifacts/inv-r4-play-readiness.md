# Robia — Google Play readiness, code/build audit (read-only)

- Repo: `<planning>/workers/r4-inv`, HEAD `0ceb916` (after `git pull -q`). No edits, no commits, no gradle run.
- Artifact audited: `r3-exec/apk-bf25366/app-staging.apk` (82,798,398 bytes). `git diff bf25366 HEAD -- app/ gradle/` is empty, so the source matches the APK. This APK is staging, not release.
- Tags: **[C]** = verified from code, bytes, or a live HTTP response. **[H]** = hypothesis or external fact not verified here.
- Tooling caveat: `aapt2`/`apksigner` in the SDK are x86-64 and cannot run on this aarch64 host. The merged manifest was parsed with a small AXML parser (`/tmp/r4-axml.py`). The ELF/zip checks are in `/tmp/r4-apk-audit-script.py` and `/tmp/r4-aar-align.py`.
- **The release AAB was not built** (gradle is off-limits). Release-specific conclusions below are inferred from configuration.

---

## 0. Bottom line

Blockers before a first Play upload:
1. **targetSdk 35** — Google Play requires **API 36 for new apps and updates from 2026-08-31** (today is 2026-11-01). [C in code: `app/build.gradle.kts:53`; deadline from the Google Play help page, verified by search snippet, confirm in Play Console.]
2. **No privacy policy anywhere** in the app or repo. Play requires a privacy-policy URL in Console. [C: no "privacy" in `app/src/main/res/values/strings.xml` or Kotlin sources.]
3. **Release AAB has never been produced or verified in this audit.** Signing depends on four CI secrets. [C: `build.gradle.kts:19-45`, `.github/workflows/android-apk.yml:130-188`.]
4. **Google Sign-In/Drive for Play builds:** the Android OAuth client must be registered with the Play App Signing SHA-1, not the upload-key SHA-1. The repo's own plan says so (`docs/google_drive_sync_setup_plan.md:52`). No evidence in repo that this was done. [C that the doc says it; unknown whether it was done.]

Must fix before the second upload: `versionCode = 1` is hard-coded (`app/build.gradle.kts:54`).

No Play-restricted media/camera/all-files/install permissions are declared. Model downloads are sha256-verified and Wi-Fi-gated by default. No analytics or crash SDK appears in the dependency catalog.

---

## 1. Manifest(s)

Only one manifest exists: `app/src/main/AndroidManifest.xml`. There are no debug or staging manifest overrides [C: `find` over repo].

**Declared in source** (`app/src/main/AndroidManifest.xml`):
- `:4` `INTERNET` — the only `<uses-permission>`.
- `:12` `android:allowBackup="false"` — disables Auto Backup and device transfer. Drive is the backup path (comment at `:6-9`).
- `:15` `localeConfig` (en/es/de, `res/xml/locales_config.xml`).
- `:19-21` `com.google.mlkit.vision.DEPENDENCIES = subject_segment` — ML Kit subject-segmentation module is delivered by Play services, not bundled.
- `:23-31` FileProvider `exported="false"`, `grantUriPermissions="true"`. Paths restricted in `res/xml/file_paths.xml:3-13` (Pictures/robia_clothing_images, files/robia_clothing_images, files/robia_thumbnails, cache/robia_shares).
- `:33-41` `MainActivity` `exported="true"` with only LAUNCHER intent filter. Expected.
- No `<service>` declared by the app. No `usesCleartextTraffic`, no `networkSecurityConfig`, no `dataExtractionRules`, no `debuggable` attribute [C]. Release is non-debuggable by default; staging sets `isDebuggable = false` (`build.gradle.kts:107`).

**Merged manifest of the staging APK** (parsed from binary AXML) [C]:
- `uses-permission`: `INTERNET`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `com.gusanitolabs.robia.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` and the DYNAMIC permission come from libraries (WorkManager, play-services, etc.). None are Play-declaration permissions.
- Not present [C]: `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `CAMERA`, `POST_NOTIFICATIONS`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `MANAGE_EXTERNAL_STORAGE`, all location, contacts and SMS permissions.
- No `uses-feature` entries. The camera is used only through the system camera intent (`AddEditClothingScreen.kt:1385`, `ActivityResultContracts.TakePicture`), so no `CAMERA` permission or camera feature is needed.
- Exported components [C]:
  - `MainActivity` (exported=true, LAUNCHER).
  - `com.google.android.gms.auth.api.signin.RevocationBoundService` (exported=true, protected by `…signin.permission.REVOCATION_NOTIFICATION`).
  - WorkManager `SystemJobService` (exported=true, `BIND_JOB_SERVICE`).
  - WorkManager `DiagnosticsReceiver` and profileinstaller `ProfileInstallReceiver` (exported=true, `DUMP` permission).
  - All others `exported=false`.
- Foreground services: the merged `SystemForegroundService` has **no `foregroundServiceType`**. The app never calls `setForeground`, `ForegroundInfo`, or `setExpedited` (grep of `app/src/main/java` returned nothing) [C]. No Play FGS declaration is needed as things stand. If foreground work is added later, a type must be declared.
- Telemetry-related providers present in merged manifest [C]: `ai.onnxruntime.TelemetryInitializer` (provider, `initOrder=100`) and a `datatransport` CCT backend (`TransportBackendDiscovery`, `backend:…CctBackendFactory`). See §6.

**Photo intake:** SAF `ActivityResultContracts.OpenMultipleDocuments()` (`ui/AddEditClothingScreen.kt:1366`) for gallery, and `TakePicture()` (`:1385`) for the camera. Neither needs a media-read permission. This is Play-compliant. Export uses `CreateDocument("application/zip")` (`ui/DriveSettingsScreen.kt:89`) so the user picks the destination. **OK.**

Other manifest-adjacent checks [C]: no `http://` URLs in the Kotlin sources (grep returned only `https://`), so cleartext is not used.

---

## 2. app/build.gradle.kts

| Item | Value | Evidence |
|---|---|---|
| namespace / applicationId | `com.gusanitolabs.robia` (same for all build types) | `:47`, `:51` |
| compileSdk | 35 | `:48` |
| targetSdk | **35** | `:53` — see blocker B1 |
| minSdk | 26 | `:52` |
| versionCode / versionName | `1` / `0.1.0` (hard-coded, CI reads them only) | `:54-55`; README:58 says increment before each upload |
| signingConfigs | `robiaCiDebug` (only if `ROBIA_DEBUG_KEYSTORE_*` env set), `release` (only if `ANDROID_KEYSTORE_*` env set) | `:62-79` |
| debug build type | `robiaCiDebug` signing if env set; `abiFilters arm64-v8a` | `:82-89` |
| release build type | signing if env set; `isMinifyEnabled = true`, `isShrinkResources = true`; proguard-android-optimize + `proguard-rules.pro`; **no `abiFilters`** | `:91-101` |
| staging build type | `initWith(release)`, minify+shrink, `isDebuggable = false`, signing = `robiaCiDebug` else **debug**, `abiFilters arm64-v8a`, `matchingFallbacks release` | `:103-117` |
| applicationIdSuffix | none (staging has the same package as release) | grep `applicationIdSuffix` → none |
| Guard | release/bundle/publish/install tasks throw if any release env var is missing | `:12-45` |
| bundle {} / language split | not configured (defaults) | grep → none |
| androidResources | `noCompress += "tflite"` (MediaPipe reads the model from the APK) | `:130-133` |
| Room schema | `room.schemaLocation` set | `:142-144` |

Debug-only code paths. All are gated by `BuildConfig.DEBUG`, which is false in release and staging [C]:
- `MainActivity.kt:107-111` — cutout stage dump (`EXTRA_CUTOUT_DEBUG`).
- `MainActivity.kt:116-117` — performance fixture URIs.
- `ui/RobiaApp.kt:393` — `performanceFixtureMode`.
- `media/cutout/InteractiveSelector.kt:40`, `PreciseCutoutEngine.kt:97,395` — debug logs.
- `media/ImagePipelineContracts.kt:167` — telemetry sink only in DEBUG.

Code is compiled into release, but it is unreachable. **OK.**

Reachable in release, not debug-gated: **developer mode**. Ten taps within 5 seconds on Settings unlock it (`ui/RobiaApp.kt:277-278`, `handleSettingsClick` at `:1618-1631`). That exposes:
- `DevBenchmark` (`ui/SettingsScreen.kt:287`).
- Developer sync log and cloud-sync details (`RobiaApp.kt:335`, `:3343`).
- Per-garment diagnostics card with "Export input image", which writes a PNG to `Pictures/Robia` through MediaStore (`ui/SuggestionDiagnosticsCard.kt:97-120`; `ui/AddEditClothingScreen.kt:740-745`, `:1914-1934`).

Not a Play policy violation as far as I can see, but it is hidden developer UI shipped to users. See S4.

CI workflow (`.github/workflows/android-apk.yml`):
- Triggers `:3-12`: push to main, PR, `v*` tags, manual dispatch with `release` boolean.
- `debug-apk` job `:17-…`: builds debug and staging APKs, uploads staging R8 mapping (`:115-120`). Runs unit tests (`testDebugUnitTest`).
- `release-aab` job `:130`: runs on `v*` tags or manual `release=true`. Decodes keystore from secrets, runs `bundleRelease` (`:188`), `jarsigner -verify`, writes sha256 and metadata. **It does not upload a release R8 mapping**, only the staging one.
- The debug CI key is `CN=Robia CI Debug` (apksigner on the staging APK). It must not be used for Play. The release build does not fall back to it, since `signingConfig` for release is only `release` (`:92-94`).

Other doc note [C]: `docs/product/brief.md:35` says "AAB and Google Play release automation are future work", but the AAB job exists. The doc is out of date.

---

## 3. Runtime model/asset downloads

Every URL in the code (grep `https?://` over `app/src/main/java`):

| # | URL (host) | Where | Purpose |
|---|---|---|---|
| U1 | `huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/e301f74…/embeddinggemma-2-text-vision-440m.litertlm` | `suggestions/EmbeddingRecipe.kt:12` | Gemma embedding model (LiteRT-LM) |
| U2 | `github.com/danielgatis/rembg/releases/download/v0.0.0/u2netp.onnx` | `media/cutout/CutoutPack.kt:25` | cutout pack |
| U3 | `huggingface.co/onnx-community/dfine_s_obj365-ONNX/resolve/a61e4cd…/onnx/model.onnx` | `CutoutPack.kt:31` | cutout pack (D-FINE detector) |
| U4 | `huggingface.co/Acly/MobileSAM/resolve/0d3b403…/mobile_sam_image_encoder.onnx` | `CutoutPack.kt:37` | cutout pack |
| U5 | `huggingface.co/Acly/MobileSAM/resolve/0d3b403…/sam_mask_decoder_multi.onnx` | `CutoutPack.kt:43` | cutout pack |
| U6 | `one.google.com/storage` | `ui/RobiaApp.kt:3684` | opened as a link (storage info), not downloaded |
| U7 | Google Drive / upload / about endpoints | `sync/GoogleDriveWardrobeRepository.kt:959-1219, 1863` | user's Drive appdata |

Only U1–U5 are downloaded at runtime. All are model weights. **No executable code is downloaded**: no dex, jar, .so, or script. The only `.so` files are in the APK [C].

Live checks (`curl -sIL --max-time 30`), no auth headers:

| File | Final status | Size (content-length) | App-pinned size | Pinned sha256 vs LFS etag |
|---|---|---|---|---|
| embeddinggemma-2-text-vision-440m.litertlm (U1) | 302 → 200 | 387,710,976 | `MODEL_APPROX_MB = 388` (`EmbeddingRecipe.kt:14`); size check skipped (null) | **match**: etag `92dcbea1…62eb0` = `EmbeddingRecipe.kt:13` |
| u2netp.onnx (U2) | 302 → 200 | 4,574,861 | 4,574,861 (`CutoutPack.kt:27`) | GitHub release asset, no etag → **not verified** (size matches) |
| dfine model.onnx (U3) | 302 → 200 | 42,123,225 | 42,123,225 | **match** `372feaa3…1120` |
| mobile_sam_image_encoder.onnx (U4) | 302 → 200 | 28,157,093 | 28,157,093 | **match** `580f5fb6…c749` |
| sam_mask_decoder_multi.onnx (U5) | 302 → 200 | 16,496,559 | 16,496,559 | **match** `8976b90a…2ffe` |

All five are public and unauthenticated (no 401/403). I did not download the bytes; the etag comparison is from the LFS metadata. The U2 hash is unverified without a download.

**Licenses** (HF model-card front matter, fetched over curl) [C]:
- U1 `litert-community/embeddinggemma-2-text-vision-440m-litert-lm`: `license: apache-2.0`, base `google/embeddinggemma-2` also `apache-2.0` (main and pinned commit). [H] Google's Gemma terms may still apply to the Gemma family; check `google/embeddinggemma-2` terms before shipping.
- U3 `onnx-community/dfine_s_obj365-ONNX`: **no license field** in the card. base_model `ustc-community/dfine_s_obj365`. [H] upstream D-FINE is Apache-2.0; unverified here.
- U4/U5 `Acly/MobileSAM`: `license: mit` (card). [H] upstream MobileSAM is Apache-2.0; unverified.
- U2 `danielgatis/rembg`: MIT (repo LICENSE.txt). The u2netp weights' own license is [H] unverified.

**Consent, Wi-Fi, resume, storage, deletion** [C]:
- **Consent:** `ModelPromptDialog` (`ui/ModelDownloadUi.kt:55`) with "Download now" / "Later" and a size line. Shown from first-run (`ui/RobiaApp.kt:1950`), from `ui/RobiaApp.kt:1217` (gated by `shouldShowModelPrompt`, `ModelDownloadUi.kt:34`), and from `ui/BrowseFilterBar.kt:451`.
- **Caveat:** `ui/SettingsScreen.kt:339` calls `app.cutoutPack.download()` directly. I did not confirm that this button sits behind developer mode.
- **Wi-Fi:** default is unmetered only (`suggestions/model/ModelStore.kt:115`, `if (!allowMetered && !isUnmetered()) return WaitingForWifi`). Mobile data requires `MobileDataConfirmDialog` (`ModelDownloadUi.kt:88`) and `allowMobileData` on the worker (`ModelDownloadWorker.kt:65,77`).
- **Integrity:** Gemma verified against `MODEL_SHA256` (`ModelStore.kt:163`) with a sidecar (`:85,169,177`). Cutout files each verified against their pinned sha (`CutoutPack.kt:26-44`, via `ModelStore` with `expectedSha256`).
- **Resume:** HTTP `Range` (`ModelStore.kt:47-58`); partial file kept until verified.
- **Storage:** `filesDir/models` (`RobiaApplication.kt:103`), `filesDir/cutout_pack` (`RobiaApplication.kt:110`, `CutoutPackManifest.DIRECTORY`). App-private.
- **Deletion:** `SettingsScreen.kt:686` (`modelStore.delete()`), `PreciseCutoutSettings.kt:326` (`cutoutPack.delete()`), and `ModelStore.kt:187-189`.
- **Bundled, not downloaded:** `assets/models/magic_touch.tflite` (6.2 MB, `noCompress`). Its origin and license are **not documented** in the repo. `assets/licenses/` only has `inter-OFL.txt`.

---

## 4. Native libraries and 16 KB page size

**Staging APK, arm64 only** [C] (`abiFilters` in `debug`/`staging`):

| Library | Size (bytes) | Stored? | Zip data offset % 16384 | PT_LOAD p_align | Owner |
|---|---|---|---|---|---|
| lib/arm64-v8a/libonnxruntime.so | 34,208,680 | STORED | 0 | 16384 ×3 | ONNX Runtime 1.31.0 |
| lib/arm64-v8a/liblitertlm_jni.so | 22,155,680 | STORED | 0 | 16384 ×3 | LiteRT-LM 0.18.0 |
| lib/arm64-v8a/libmediapipe_tasks_jni.so | 14,367,776 | STORED | 0 | 16384 ×3 | MediaPipe tasks-core 1.1.1 |
| lib/arm64-v8a/libonnxruntime4j_jni.so | 122,576 | STORED | 0 | 16384 ×2 | ONNX Runtime Java bindings |
| lib/arm64-v8a/libandroidx.graphics.path.so | 10,096 | STORED | 0 | 16384 ×3 | androidx.graphics |
| lib/arm64-v8a/libdatastore_shared_counter.so | 7,112 | STORED | 0 | 16384 ×3 | androidx.datastore |

**Result: all six arm64 `.so` pass the 16 KB check on the staging APK** [C]. `extractNativeLibs` is false in the merged manifest, so the libraries stay uncompressed in the zip.

**Release AAB is not verified.** Release has no `abiFilters`, so the AAB will contain more ABIs. Dependency AARs in the Gradle cache [C] contain:
- `litertlm-android-0.18.0.aar`: arm64-v8a, x86_64.
- `tasks-core-1.1.1.aar`: arm64-v8a, armeabi-v7a, x86, x86_64.
- `onnxruntime-android-1.31.0.aar`: arm64-v8a, armeabi-v7a, x86, x86_64.

All 14 `.so` entries in these AARs have PT_LOAD `p_align` ≥ 16384 (ELF32 and ELF64 parsed) [C]. The AAB still needs its own check after `bundleRelease`, since packaging can change offsets.

Other size facts [C]: the APK has 451 entries. `classes.dex` is 3.8 MB compressed. The `.so` files total about 71 MB compressed.

---

## 5. Google Sign-In / Drive

- **Scopes:** one scope only, `https://www.googleapis.com/auth/drive.appdata` (`RobiaApplication.kt:68`, requested at `MainActivity.kt:146`). No `drive`, `drive.file`, `drive.readonly`, `drive.metadata`, or `openid`/`email`/`profile` scopes in source [C].
- **API:** `Identity.getAuthorizationClient` / `AuthorizationRequest` / `AuthorizationClient` (`MainActivity.kt:61,80,141-146`; `RobiaApplication.kt:177`), resolved via `StartIntentSenderForResult` (`MainActivity.kt:61`). This is the current Authorization API from `play-services-auth` 21.3.0 (`gradle/libs.versions.toml`; `build.gradle.kts:159`). No legacy `GoogleSignIn` and no Credential Manager, so no deprecated sign-in path [C].
- **Restricted-scope / CASA:** the app does not request a restricted scope [C]. Whether `drive.appdata` is classified "sensitive" (requires Google review) or "non-sensitive" could not be confirmed. The web-fetch of the scopes page did not reach the Drive table. [H] My understanding is that `drive.appdata` is non-sensitive and does not trigger CASA. Confirm in Cloud Console's OAuth consent screen before submission.
- **Repo plan** [C]: `docs/google_drive_sync_setup_plan.md:32-35` recommends `drive.appdata` and says to avoid `drive` (broad). Line 52 says Play builds need a separate Android OAuth client using the **Play App Signing SHA-1**. Line 240 mentions publishing the OAuth consent screen to production if verification is required.
- **Drive calls** (`GoogleDriveWardrobeRepository.kt`): `drive/v3/files` (`:959,999,1025,1092,1113,1120,1131,1149,1219`), upload multipart (`:971,973,1072,1074`), and `drive/v3/about?fields=user(emailAddress)` (`:1863`). The `about` call reads the user's email address on device.
- **Data deletion paths** [C]: disconnect Drive (`ui/RobiaApp.kt:495`), delete cloud backup (`ui/RobiaApp.kt:521` → `WardrobeSyncOperation.DeleteCloudBackup`), and per-model/pack deletion (§3).

---

## 6. Privacy and data flows

- **Analytics / crash / ads SDKs:** none in `gradle/libs.versions.toml` or `build.gradle.kts` [C]. Grep for firebase/crashlytics/analytics/sentry/bugsnag/amplitude/mixpanel/segment/datadog/newrelic/admob found no SDK (only UI text matches).
- **Transitive telemetry** [H]: the merged manifest includes ML Kit (Firebase component registrars, `MlKitInitProvider`) and the `datatransport` CCT backend (`TransportBackendDiscovery`), which are Google logging back-ends. Whether ML Kit or Play services sends usage data off device needs checking. The ORT `ai/onnxruntime/TelemetryInitializer` is present in the ORT AAR's `classes.jar` [C]; whether it transmits anything is **unverified**.
- **Network destinations** [C]: Hugging Face and GitHub (model downloads, no user data per NFR-05 in `docs/specs/robia-embeddings-sync-manage/requirements.md:182-185`), Google Drive/APIs (user's own appdata), and Play services. No developer server.
- **Logs** [C]: `Log.d` calls (`ui/RobiaApp.kt:596-600,1798-1802`, `ui/TagReviewScreen.kt:694`) log counts, timings and enum state, not photo content. The spec's NFR-05 (no PII in logs outside the developer sync log) is consistent with this. Not verified exhaustively.
- **Privacy policy:** **none** in code, strings, or repo [C]. The only mention is the spec text. Play Console requires a policy URL.
- **Account creation:** the app creates no account of its own. Sign-in is only a Google Drive authorization, and Robia offers Disconnect and Delete cloud backup (above). [H] Play's in-app account-deletion rule probably does not apply, since no account is created in the app. Confirm.
- **Data safety draft** (from code, to be checked):
  - Photos: local to the device, and in the user's own Drive appdata. Not sent to the developer. [H] Play may still treat this as "data shared" because it goes to Google; confirm.
  - Email address: read from Drive `about` (`GoogleDriveWardrobeRepository.kt:1863`). Declare if it is stored or shown, or state it stays local.
  - Possibly: ML Kit / Play services diagnostics [H].
  - Encryption in transit: HTTPS everywhere [C].
  - Deletion: in-app paths exist (§5).

---

## 7. App size

- Staging APK: **82,798,398 bytes (82.8 MB)** [C]. Well under the 200 MB limit you cited.
- Largest entries (compressed bytes) [C]:
  1. `lib/arm64-v8a/libonnxruntime.so` — 34,208,680
  2. `lib/arm64-v8a/liblitertlm_jni.so` — 22,155,680
  3. `lib/arm64-v8a/libmediapipe_tasks_jni.so` — 14,367,776
  4. `assets/models/magic_touch.tflite` — 6,227,884
  5. `classes.dex` — 3,808,720 (8,994,584 uncompressed)
  6. `resources.arsc` — 844,188
  7. `res/H8.ttf` — 201,657
  8. `res/XS.ttf` — 201,310
  9. `res/dW.ttf` — 196,951
  10. `lib/arm64-v8a/libonnxruntime4j_jni.so` — 122,576
- Runtime downloads are not in the APK: Gemma ≈ 388 MB, cutout pack ≈ 91.4 MB (4,574,861 + 42,123,225 + 28,157,093 + 16,496,559 = 91,351,738 bytes). Downloaded to app storage, not Play delivery.
- Release AAB on-device size will be arm64-only after Play splits; release has no `abiFilters`, so the bundle itself is larger.

---

## 8. Other review risks

- **targetSdk** — see §1/B1.
- **Hard-coded test/dev endpoints:** none found (grep for localhost, 127.0.0.1, 10.0.2.2, 192.168, example.com/org, `dev.`, `staging.`) [C].
- **Signing:** staging is signed with `CN=Robia CI Debug` (apksigner) [C]. Release signing uses the `release` config only. Staging has the same applicationId as release (no suffix), so a staging install would conflict with a Play-signed install on the same device. Do not upload staging to Play.
- **Launcher / name / icons:** `app_name = Robia` (`res/values/strings.xml:3`). Icons `@mipmap/ic_launcher` and `ic_launcher_round` (manifest `:13,16`), adaptive XML present under `mipmap-anydpi-v26/` [C]. Not visually inspected.
- **Ads, health/medical claims, children:** no ads SDK. Grep for medical/health/diagnosis/allergy/pregnancy/child/kids returned no product claims [C]. Not a health app as far as the code shows.
- **Third-party licence notices:** no in-app open-source licences screen. Apache-2.0 libraries (ML Kit, MediaPipe, ORT, LiteRT-LM, androidx) need notices distributed with the binary [H]. Only `inter-OFL.txt` is bundled.
- **Repo secrets:** no keystore, `google-services.json`, or private key is tracked [C] (`git ls-files` check). One script (`tools/embedding-precision/download_drive.py:31`) reads `client_secret` from a local token file; no literal secret [C].
- **Release mapping:** not uploaded by the release job (CI `:130-188`). Play Console will show unreadable crashes without it.
- **Developer mode in release:** see §2.
- **Native debug symbols:** not configured (`ndk.debugSymbolLevel` absent) [C]. Play may warn that native libraries lack symbols.
- **Doc drift:** `docs/product/brief.md:35` says AAB/Play automation is future work. The workflow already builds an AAB.

---

## 9. Final table

| # | Item | Evidence | Play impact | Fix |
|---|---|---|---|---|
| B1 | targetSdk 35; Play needs API 36 for new apps and updates since 2026-08-31 | `app/build.gradle.kts:48,53`; Google Play target-API help page (search result, not opened directly) | **BLOCKER** | Set `compileSdk = 36`, `targetSdk = 36`; retest on Android 16 (edge-to-edge, 16 KB, Drive auth) |
| B2 | No privacy policy in app or repo | no "privacy" in `strings.xml` or Kotlin; spec only `requirements.md:182` | **BLOCKER** (Console requirement) | Publish a policy URL; link it from Settings; enter it in Console |
| B3 | Release AAB never built or verified here; needs 4 CI secrets | `build.gradle.kts:19-45,91-101`; `android-apk.yml:130-188` | **MUST FIX** before first upload | Run `bundleRelease` in CI with secrets; check `jarsigner`, 16 KB zip alignment, and signing key matches the Play upload key |
| B4 | Drive OAuth Android client must use Play App Signing SHA-1; no evidence it exists | `docs/google_drive_sync_setup_plan.md:52`; `MainActivity.kt:141-146` | **MUST FIX** (sign-in fails on Play builds otherwise) | Add Android OAuth client with Play App Signing SHA-1 from Play Console |
| B5 | `versionCode = 1` hard-coded | `build.gradle.kts:54`; README:58 | **MUST FIX** before 2nd upload (OK for the first) | Bump per upload, e.g. CI-supplied code; keep versionName in step |
| B6 | Drive scope classification and OAuth consent production status unknown | `RobiaApplication.kt:68`; `docs/google_drive_sync_setup_plan.md:240` | **MUST FIX** (verify) | Check scope sensitivity in Cloud Console; publish consent screen to production |
| S1 | 16 KB: staging APK passes; release AAB not checked; AARs all aligned | §4 | SHOULD (verify after build) | Check the release AAB's `.so` zip offsets and PT_LOAD after `bundleRelease` |
| S2 | No `abiFilters` in release: x86/x86_64/armv7 libs enter the bundle | `build.gradle.kts:91-101`; AAR listing §4 | SHOULD | Add `ndk { abiFilters += "arm64-v8a" }` to release, or accept the larger bundle |
| S3 | No release R8 mapping or native symbols uploaded | `android-apk.yml:115-120` (staging only); no `debugSymbolLevel` | SHOULD | Upload `mapping/release/mapping.txt` to Play; set `ndk.debugSymbolLevel` if native symbols are needed |
| S4 | Developer mode reachable in release (10 taps); writes to `Pictures/Robia` | `RobiaApp.kt:277-278,1618-1631`; `SettingsScreen.kt:287`; `AddEditClothingScreen.kt:1914-1934` | SHOULD (not a policy block) | Gate the developer surface behind `BuildConfig.DEBUG` or a release-off flag |
| S5 | Staging shares applicationId with release and is CI-debug-signed | `build.gradle.kts:103-117`; apksigner `CN=Robia CI Debug` | SHOULD | Add `applicationIdSuffix = ".staging"` to staging; never upload it |
| S6 | Model licence unclear: D-FINE card has no licence; u2netp weights unverified; magic_touch provenance undocumented | §3; `assets/licenses` has only `inter-OFL.txt` | SHOULD | Confirm upstream licences; document `magic_touch` source; add notices |
| S7 | No in-app open-source licences screen for Apache-2.0 SDKs | no licence screen in code | SHOULD | Add an "Open-source licences" screen or notices file |
| S8 | Data-safety facts unclear: ML Kit/Play services CCT telemetry; ORT TelemetryInitializer; email read via Drive `about` | `AndroidManifest` merged providers; `GoogleDriveWardrobeRepository.kt:1863` | SHOULD (verify before filling the Data safety form) | Check what each SDK transmits; declare accordingly |
| S9 | Gemma terms: card says apache-2.0, but Gemma-family terms may apply | HF card front matter (curl) | SHOULD (verify) | Read `google/embeddinggemma-2` terms; decide what the app must show |
| S10 | Staging `SystemForegroundService` lacks `foregroundServiceType`; app never starts a foreground service | merged manifest; no `setForeground` in code | OK for now | If foreground work is added, declare the type |
| S11 | `SettingsScreen.kt:339` calls `cutoutPack.download()` directly; not confirmed behind developer mode | `ui/SettingsScreen.kt:339` | SHOULD (verify) | Confirm the button is developer-only, or route it through `ModelPromptDialog` |
| OK | No `READ_MEDIA_*`, `READ_EXTERNAL_STORAGE`, `CAMERA`, `POST_NOTIFICATIONS`, `QUERY_ALL_PACKAGES`, `REQUEST_INSTALL_PACKAGES`, `MANAGE_EXTERNAL_STORAGE`, or location/SMS/contacts permissions | merged manifest §1 | OK | — |
| OK | Photo picking via SAF and system camera intent | `AddEditClothingScreen.kt:1366,1385` | OK | — |
| OK | `allowBackup=false`; no cleartext; no `networkSecurityConfig` needed; FileProvider not exported with narrow paths | manifest `:12`; `file_paths.xml` | OK | — |
| OK | Model downloads: 5 unauthenticated URLs (HTTP 200); sizes match pinned; 4/5 etags match pinned sha256; prompt, Wi-Fi default, delete paths | §3 | OK | — |
| OK | No executable code downloaded (only weights) | §3 | OK | — |
| OK | No analytics/crash/ad SDK in catalog; no hard-coded dev endpoints; no tracked secrets | §6, §8 | OK | — |
| OK | APK 82.8 MB; models downloaded, not bundled | §7 | OK | — |

Temporary files written during the audit: `/tmp/r4-apk-audit-script.py`, `/tmp/r4-axml.py`, `/tmp/r4-aar-align.py` (read-only parsers). The APK copy was deleted after use; the original is still at `r3-exec/apk-bf25366/app-staging.apk`.
