# Robia

Robia is an Android digital wardrobe app built with Kotlin, Jetpack Compose, and Gradle.

## Android scaffold

- Package/application id: `com.gusanitolabs.robia`
- App label: `Robia`
- UI stack: Jetpack Compose + Material 3
- Initial locales: English, Spanish, German; the app follows the system language by default
- Current CI target: debug APK artifact via GitHub Actions

Android builds for this project are intentionally run in GitHub Actions, not on this host.

## CI artifacts

GitHub Actions builds Android artifacts on GitHub-hosted runners:

- Pull requests to `main` and pushes to `main` build a safe debug APK artifact named `robia-debug-apk`.
- Signed Play release bundles are produced only by the release job. The artifact is named `robia-release-aab` and contains:
  - the signed `.aab` file from `app/build/outputs/bundle/release/`
  - a `.sha256` checksum file
  - `release-metadata.txt` with the Git ref, commit SHA, `versionName`, `versionCode`, AAB filename, and SHA-256 digest

Download artifacts from the completed workflow run in the GitHub Actions UI.

## Release signing secrets

Configure these repository or environment secrets before running the release job:

- `ANDROID_KEYSTORE_BASE64`: base64-encoded Java/Android keystore file
- `ANDROID_KEYSTORE_PASSWORD`: keystore password
- `ANDROID_KEY_ALIAS`: signing key alias
- `ANDROID_KEY_PASSWORD`: signing key password

The release workflow decodes the keystore only under `RUNNER_TEMP`, applies `chmod 600`, and passes only the temporary keystore path plus signing values to Gradle. The release job does not run on pull requests and does not upload to Google Play.

## Triggering a release AAB build

Use either release trigger:

1. Push a version tag whose name starts with `v`, for example `v0.1.1`.
2. Run `Android APK CI` manually from the GitHub Actions UI with the `release` input set to `true`.

Manual runs with `release=false` build the debug APK only.

## Manual Play internal-test upload

1. Download the `robia-release-aab` artifact from the successful release workflow run.
2. Confirm the downloaded AAB checksum matches the artifact `.sha256` file.
3. In Google Play Console, create or open an internal testing release.
4. Upload the signed `.aab` manually.
5. Review Play Console validation, release notes, testers, and rollout settings before publishing to internal testing.

## Versioning rule

Increment `versionCode` in `app/build.gradle.kts` before every AAB uploaded to Play. Google Play rejects uploads that reuse a previously uploaded `versionCode`. Update `versionName` when the user-visible release version changes.

## Planning docs

- Google Drive sync credential/setup and CI boundary plan: `docs/google_drive_sync_setup_plan.md`
