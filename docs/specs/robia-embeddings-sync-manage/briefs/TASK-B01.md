# TASK-B01 — Toolchain upgrade, staging APK, R8, CI

## Goal
The new on-device model library (LiteRT-LM 0.18.0) only compiles with Kotlin 2.4 and Java 21. We also need an optimized ("staging") APK because Manu measures lag and model speed on a release-like build. This task only changes the build; the app must behave exactly as before.

## Read first
`gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `.github/workflows/android-apk.yml`, `README.md` (CI section). Spec: TS§5, DEC-002, DEC-015.

## Steps
0. **CI is currently broken on every branch (found 2026-10-14):** step "Set up Android SDK" (`android-actions/setup-android@v3`) fails with `Warning: Failed to find package 'tools'` / `sdkmanager failed with exit code 1` (cmdline-tools 16.0 no longer has the legacy `tools` package that the action installs by default). Fix in both jobs of `android-apk.yml` (and in `android-performance-baseline.yml` if it uses the action):
   ```yaml
   - name: Set up Android SDK
     uses: android-actions/setup-android@v3
     with:
       packages: 'platform-tools'
   ```
   AGP downloads the platform/build-tools it needs. If it still fails, also add `cmdline-tools-version: 13114758` (a known working version) and say so in the receipt. Do this first and confirm the job gets past the SDK step before the other changes.
1. `gradle/libs.versions.toml`:
   - `agp = "8.13.2"`, `kotlin = "2.4.21"`, `ksp = "2.3.12"`, `room = "2.8.4"`.
   - add versions `litertlm = "0.18.0"`, `work = "2.11.2"`.
   - add libraries:
     `litertlm-android = { module = "com.google.ai.edge.litertlm:litertlm-android", version.ref = "litertlm" }`
     `androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }`
     `androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycleRuntimeKtx" }`
2. `app/build.gradle.kts`:
   - `compileOptions` → `JavaVersion.VERSION_21` (both).
   - replace the `kotlinOptions { jvmTarget = "17" }` block with, at top level after `android {}`:
     ```kotlin
     kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21) } }
     ```
   - `dependencies`: add `implementation(libs.litertlm.android)`.
   - `release { isMinifyEnabled = true; isShrinkResources = true; … }` (keep signing and proguardFiles).
   - add the `staging` build type exactly as TS§5.2 (signing: `robiaCiDebug` if `hasRobiaCiDebugSigning`, else `debug`).
   - check `isReleaseBuildRequested`: it tests `taskName.contains("release")`; `assembleStaging` does not contain "release", so no change is needed — confirm by reading it.
3. `additional-info-core/build.gradle.kts` has `jvmToolchain(17)`: leave it (the module is deleted in B02). If CI fails because of it, change it to 21.
4. `app/proguard-rules.pro`: append the rules of TS§5.3.
5. `.github/workflows/android-apk.yml` (debug job and release job):
   - `java-version: '21'` (rename step "Set up JDK 21"), `gradle-version: 8.14.3`.
   - debug job build step: `gradle --no-daemon testDebugUnitTest assembleDebug assembleStaging`.
   - fingerprint step: also print for `app/build/outputs/apk/staging/*.apk`.
   - add upload step `robia-staging-apk` (path `app/build/outputs/apk/staging/*.apk`), and upload step `room-schemas` (path `app/schemas`, `if-no-files-found: warn`).
   - keep the TFLite drift steps (B02 removes them).
   - `android-performance-baseline.yml`: change only JDK/gradle versions.
6. Push, open the PR, wait for CI. If R8 fails with "Missing class" errors, read `app/build/outputs/mapping/staging/missing_rules.txt` from the log; add `-dontwarn` lines only for classes that are clearly optional (e.g. annotation or desugaring classes), never `-keep` everything.
7. After CI is green: `gh run download <run-id> -n room-schemas -D app/schemas`, commit `app/schemas/com.gusanitolabs.robia.data.local.RobiaDatabase/12.json` (only `12.json`), push again.
8. `README.md` CI section: mention `robia-staging-apk` ("optimized, release-like, installs over the debug APK when signed with the CI debug key").

## Fallback (pre-approved, DEC-002)
- AGP 8.13.2 incompatible with Kotlin 2.4.21 → latest stable AGP 9.x + its minimum Gradle.
- Compose BOM 2024.12.01 fails with the Kotlin 2.4 Compose plugin → `composeBom = "2026.09.00"`.
Record any fallback in the PR receipt.

## Tests
No new tests. All existing tests must pass.

## Done when
CI green; artifacts `robia-debug-apk` and `robia-staging-apk` present; `12.json` committed; receipt contains `aapt2 dump badging` (package, versionCode) and `apksigner verify --print-certs` for both APKs (run them in a CI step or locally on the downloaded APKs — `aapt2`/`apksigner` from build-tools are available on the runner). Rows V-B01, V-B02.

## Do not
Change any Kotlin source; remove TFLite; add ABI filters.
