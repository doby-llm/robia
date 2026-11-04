# TASK-REL1 — Version 1.0.0 and the `capture` build

## Goal
(1) The first public release is "1.0.0". (2) Screenshots and the promo video need a demo wardrobe on Manu's phone without
touching his real Robia data. A separate build with application id `com.gusanitolabs.robia.capture` installs next to
his app with its own empty storage (R5-DEC-D02). It is never uploaded to Play.

## Read first
SPEC §1.3. `app/build.gradle.kts:48-120` (defaultConfig, signingConfigs, buildTypes `release` and `staging`).
`.github/workflows/android-apk.yml` job `debug-apk` (step "Build debug and staging APKs" ~:81-113, artifact uploads).
`README.md` §CI artifacts. `ui/ReleaseGates.kt` (`developerModeAllowed(buildType) = buildType != "release"`).

## Steps
1. `versionName = "1.0.0"`.
2. Add after `staging`:
   ```kotlin
   create("capture") {
       initWith(getByName("release"))
       applicationIdSuffix = ".capture"
       versionNameSuffix = "-capture"
       isMinifyEnabled = true
       isShrinkResources = true
       isDebuggable = false
       signingConfig = if (hasRobiaCiDebugSigning) signingConfigs.getByName("robiaCiDebug") else signingConfigs.getByName("debug")
       ndk { abiFilters += "arm64-v8a" }
       matchingFallbacks += listOf("release")
   }
   ```
   The `fileprovider` authority already uses `${applicationId}`, so it does not collide.
3. Workflow: in the debug job build `assembleCapture` together with `assembleStaging` (same gradle call), include
   `app/build/outputs/apk/capture` in the existing per-directory loop if that loop checks the APKs, and upload artifact
   `robia-capture-apk` (path `app/build/outputs/apk/capture/*.apk`, same retention as staging). Do not touch `release-aab`.
4. README: add `robia-capture-apk` to the CI artifacts list with one sentence ("demo build with its own application id
   for screenshots and videos; never uploaded to Play").

## Tests
- V-REL1-1 CI green; the run has artifact `robia-capture-apk` (coordinator checks `gh run view`).
- V-REL1-2 add a CI step after the build: `"$ANDROID_HOME"/build-tools/*/aapt2 dump badging
  app/build/outputs/apk/capture/*.apk | head -1` (pick the newest build-tools dir with `ls -d ... | sort -V | tail -1`).
  The log line must read `package: name='com.gusanitolabs.robia.capture' ... versionName='1.0.0-capture'`; quote it in
  the receipt. The step fails the job if the name is wrong (`grep -q "name='com.gusanitolabs.robia.capture'"`).
- V-REL1-3 developer mode stays available in `capture` (buildType "capture" ≠ "release"): Manu may need the dev menu
  for nothing; acceptable. State it in the receipt.

## Done when
V-REL1-1 … V-REL1-3.

## Do not
Change `release` or `staging`, the release AAB job, signing secrets, the manifest, or the app id of other variants.
