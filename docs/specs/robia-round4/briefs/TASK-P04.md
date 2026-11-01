# TASK-P04 — Release build pipeline ready for Play

## Goal
A signed release AAB has never been built: the `release-aab` job (`.github/workflows/android-apk.yml:130-188`) needs
the four `ANDROID_KEYSTORE_*` secrets, which are not set in the repo (only `ROBIA_DEBUG_*` exist). `versionCode` is
hard-coded to 1 (`app/build.gradle.kts:54`); release has no `abiFilters` (debug/staging are arm64-only, so x86/armv7
native libs would ship untested and Play would offer the app to 32-bit phones where the 388 MB model is not viable);
no R8 `mapping.txt` is kept for Play crash de-obfuscation (PLAY-M1, M3, S2, S4).

## Read first
SPEC §7.2; `app/build.gradle.kts:19-120`; `.github/workflows/android-apk.yml` (whole `release-aab` job);
`artifacts/inv-r4-play-readiness.md` §2, §4.

## Steps
1. `app/build.gradle.kts`: `versionCode = (System.getenv("ROBIA_VERSION_CODE")?.toIntOrNull() ?: 1)`; in the `release`
   build type add `ndk { abiFilters += "arm64-v8a" }` (same as debug/staging). Nothing else.
2. Workflow `release-aab` job only: export `ROBIA_VERSION_CODE=$(( GITHUB_RUN_NUMBER + 100 ))` for the build step;
   after `bundleRelease` run `python3 scripts/check_aab_16kb.py app/build/outputs/bundle/release/app-release.aab`;
   upload artifacts `robia-release-aab` (the .aab) and `robia-release-mapping` (`app/build/outputs/mapping/release/mapping.txt`).
3. New `scripts/check_aab_16kb.py` (stdlib only): open the AAB as zip, for every `base/lib/arm64-v8a/*.so` parse the
   ELF64 program headers and fail (exit 1) if any `PT_LOAD` has `p_align < 16384`; fail if any non-arm64 `lib/` dir
   exists; print a table. `--self-test` builds two tiny synthetic ELF headers in memory (aligned / unaligned) and
   checks the verdicts.
4. The debug/staging job is unchanged.

## Done when
V-P04-1 (coordinator runs `python3 scripts/check_aab_16kb.py --self-test`), V-P04-3 green; V-P04-2 after G-PLAY.

## Manu steps (the coordinator sends these in Spanish at G-PLAY; ~30–45 min total)
1. Play Console: create the app "Robia" (package `com.gusanitolabs.robia`), enrol in **Play App Signing**.
2. Create an **upload key** on your computer (`keytool -genkeypair -v -keystore robia-upload.jks -keyalg RSA -keysize 4096 -validity 10000 -alias robia-upload`),
   keep it safe (backup outside the Pi), then add the four GitHub secrets in doby-llm/robia › Settings › Secrets ›
   Actions: `ANDROID_KEYSTORE_BASE64` (`base64 -w0 robia-upload.jks`), `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`.
   Never paste them in chat.
3. Google Cloud Console (project of the Drive OAuth client): add an **Android OAuth client** for
   `com.gusanitolabs.robia` with the **App signing key SHA-1** shown in Play Console › Setup › App integrity (and
   optionally the upload key SHA-1); set the OAuth consent screen to **In production** (scope `drive.appdata` is
   non-sensitive: no verification needed, only the app name/logo/privacy URL).
4. Publish the privacy policy at `https://doby-llm.github.io/robia/privacy.html` (draft provided by the coordinator).
5. Play Console app content: privacy policy URL, Data safety (from `artifacts/data-safety.md`), content rating
   questionnaire, target audience (adults), ads = none, app access (no login needed; Drive optional).
6. Internal testing: upload the `robia-release-aab` artifact, install from the Play link, test Drive sign-in.

## Do not
Store keys in the repo or logs; change staging/debug signing or applicationId; upload to Play from CI.
