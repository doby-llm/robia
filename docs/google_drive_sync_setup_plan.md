# Robia Google Drive sync setup plan

This document is the credential and implementation-boundary plan for Robia's future two-way Google Drive sync. It intentionally does not implement Android sync code; Android validation and APK artifacts remain owned by GitHub Actions.

## 1. Manu's Google Cloud checklist

Do these steps once before a worker can connect Robia to Drive for real users.

### A. Create or choose the Google Cloud project

1. Open https://console.cloud.google.com/.
2. Create a project named something like `Robia Android` or select the existing Robia project.
3. In the project selector, make sure this Robia project is active before continuing.
4. Note the `Project ID`; keep it for the implementation handoff.

### B. Enable the Drive API

1. Go to `APIs & Services` -> `Library`.
2. Search for `Google Drive API`.
3. Open it and click `Enable`.
4. Do not create a service account for the Android app. The app must sync each user's own Drive through OAuth consent, not through a shared server credential.

### C. Configure OAuth consent

1. Go to `APIs & Services` -> `OAuth consent screen`.
2. Choose `External` unless Robia will only be used inside a Google Workspace organization.
3. Fill in:
   - App name: `Robia`
   - User support email: Manu's support email
   - Developer contact email: Manu's email
4. Add the Drive scope Robia needs:
   - Recommended MVP scope: `https://www.googleapis.com/auth/drive.appdata`
   - Why: Robia should keep the default wardrobe sync store in Drive `appDataFolder`, scoped to app-private data in the user-authorized Google account.
   - Use only if product requirements later need user-visible Robia files: `https://www.googleapis.com/auth/drive.file`, because it grants access to files the app creates or the user opens with the app.
   - Avoid unless absolutely required: `https://www.googleapis.com/auth/drive`, because it grants broad access to all Drive files and likely raises the review/verification bar.
5. Add test users while the app is in testing mode. Add Manu's Google account and any devices/accounts used for manual APK testing.
6. Save the consent screen.

### D. Create the Android OAuth client

1. Go to `APIs & Services` -> `Credentials`.
2. Click `Create credentials` -> `OAuth client ID`.
3. Application type: `Android`.
4. Name: `Robia Android debug` for local/debug builds.
5. Package name: `com.gusanitolabs.robia`.
6. SHA-1 certificate fingerprint:
   - For debug/dev builds, use the SHA-1 of the certificate that signed the APK being installed.
   - GitHub Actions prints the actual debug APK signing certificate after `assembleDebug` in the `Print APK signing certificate fingerprints` step. Copy the `Signer #1 certificate SHA-1 digest` from the run logs only for that exact debug APK/build.
   - Do not rely on `~/.android/debug.keystore` on GitHub-hosted runners as a stable long-term OAuth fingerprint: the default debug signing key can rotate between fresh runners/runs unless CI is configured with a stable signing key.
   - For repeatable CI debug testing, use Robia's stable CI debug keystore secrets: `ROBIA_DEBUG_KEYSTORE_BASE64`, `ROBIA_DEBUG_KEYSTORE_PASSWORD`, `ROBIA_DEBUG_KEY_ALIAS`, and `ROBIA_DEBUG_KEY_PASSWORD`. GitHub Actions decodes that keystore at runtime and signs debug APKs with the same certificate every run.
   - Stable Robia CI debug SHA-1 currently configured in GitHub Secrets: `FB:C8:C0:41:BD:52:B5:DD:D8:1C:DE:47:DD:44:EA:97:F4:CD:53:DB`.
   - For future Play releases, add a separate Android OAuth client using the Google Play App Signing SHA-1 from Play Console, not the upload-key SHA-1.
7. Save and copy the Android OAuth client ID.
8. Repeat with another OAuth client if you need separate debug, staging, and release signing fingerprints.

### E. What to send back to the repo workers

Send these values only through the agreed secret/config channel, not in chat comments that may be copied into commits:

- Google Cloud Project ID.
- Android OAuth client ID for debug/CI manual APK testing.
- Android OAuth client ID for release/Play, when available.
- The SHA-1 fingerprints that were registered and what build/signing key each belongs to.
- Whether OAuth consent is still `Testing` or has been published to `Production`.
- The support email shown on the consent screen.

## 2. Recommended Drive data model

Prefer the Drive `appDataFolder` for the default Robia sync store so wardrobe data is scoped to Robia's files in the user-authorized Google account. If product requirements later need user-visible files, keep the same schema under a user-selected `Robia/` folder and document the broader scope explicitly.

```text
appDataFolder:/robia/
  manifest.json
  wardrobe_snapshot.json
  photos/
    <item_uid>/
      original
  tombstones/
    <entity_type>_<entity_id>.json
```

The snapshot is the source of truth for the full local wardrobe graph: garments, photos/blob metadata, Manage-created categories/tags, occasion/season/location/category labels, main colors/palette, garment-tag mappings, garment-color mappings, default/system flags, archived state, revisions/timestamps, and deletion tombstones.

Recommended item folder id/name:

- Use a generated stable UUID as the primary `item_uid` and folder name, for example `garment_018f4c7e-...`.
- Do not use the garment name, because names can repeat and change.
- Do not use only an image hash as the authoritative ID, because duplicate photos, cropped versions, and edited metadata can create collisions or accidental merges.
- Store the original image SHA-256 in `item.json` as a dedupe hint, not as the primary ID.

Suggested `item.json` shape:

```json
{
  "schemaVersion": 1,
  "itemUid": "garment_uuid_here",
  "appPackage": "com.gusanitolabs.robia",
  "name": "Blue trousers",
  "notes": "",
  "tags": [
    { "id": "trousers", "categoryId": "category", "name": "Trousers", "isSystem": true }
  ],
  "fitValue": null,
  "colors": {
    "primaryRawValue": "#404044",
    "primaryPaletteColorId": "gray",
    "primaryPaletteColorName": "Gray",
    "primaryPaletteColorHex": "#5F6368",
    "secondaryRawValue": "#202124",
    "secondaryPaletteColorId": "black",
    "secondaryPaletteColorName": "Black",
    "secondaryPaletteColorHex": "#202124"
  },
  "imageFiles": {
    "original": "original.jpg",
    "processed": "processed.png",
    "originalSha256": "...",
    "processedSha256": "..."
  },
  "createdAtEpochMillis": 0,
  "updatedAtEpochMillis": 0,
  "deletedAtEpochMillis": null,
  "lastModifiedDeviceId": "device_uuid_here"
}
```

Suggested `manifest.json` shape:

```json
{
  "schemaVersion": 1,
  "wardrobeId": "wardrobe_uuid_here",
  "appPackage": "com.gusanitolabs.robia",
  "createdAtEpochMillis": 0,
  "updatedAtEpochMillis": 0,
  "itemsPath": "items/",
  "palettePath": "palettes/colors.json"
}
```

## 3. Sync policy decisions

Robia should be local-first, then sync when Google is connected.

### Two-way merge

On first account connection:

1. Locate or create the `Robia/` folder.
2. Read `manifest.json`, `items/*/item.json`, and `palettes/colors.json` if present.
3. If the phone has no local items, import Drive items into the local Room database and image store.
4. If the phone already has local items, merge both directions:
   - Local-only item -> upload item folder/files to Drive.
   - Drive-only item -> import locally.
   - Same `itemUid` changed locally and remotely -> compare `updatedAtEpochMillis`; keep latest for simple fields, and record conflict metadata if both sides changed since last sync.
5. Never delete a Drive item just because it is missing locally during first merge. Missing local state may mean a new install, cleared app data, or incomplete sync history.

### Deletes

Follow Manu's policy exactly:

- If the user deletes an item inside Robia, enqueue a local deletion event and delete that item's Drive folder during the next successful sync.
- If a Drive file/folder is missing or trashed outside the app, do not automatically delete the local item in the MVP. Mark it as `remote_missing` / `needs_attention` and show a recover/re-upload path later.
- Keep a local `sync_operations` queue so app-initiated deletes survive process death and offline periods.

### Palette/color changes

Color palette edits can change many items. Treat them as a sync-impacting domain event:

- palette color added/edited/deleted -> upload `palettes/colors.json` after the local transaction commits.
- approved per-item color re-extraction result -> update each affected item's `item.json` and processed image if it changed.
- rejected re-extraction result -> no Drive change for that item.

### UI status text

The current `Saved locally` text should evolve into derived sync state, with localized strings in English, Spanish, and German. Candidate states:

- `Saved locally`
- `Syncing...`
- `Synced to Drive`
- `Offline - will sync later`
- `Sync needs attention`
- `Google account disconnected`

No user-visible sync strings should be hardcoded in Compose.

## 4. What can be implemented before credentials exist

These tasks are safe before Manu provides OAuth/client setup:

- Expand `WardrobeSyncGateway` into a domain-facing interface that can enqueue item, tag, palette, image, and delete events.
- Add local Room tables for sync state, for example:
  - `sync_account_state`: selected Google account email hash/subject, connected/disconnected state, last full sync time.
  - `sync_item_state`: item UID, Drive folder/file IDs, local revision, remote revision, last synced timestamp, status.
  - `sync_operations`: durable pending operations (`UPSERT_ITEM`, `UPLOAD_IMAGE`, `DELETE_ITEM_FOLDER`, `UPSERT_PALETTE`).
- Add a `DriveWardrobeRepository` interface with a fake/local implementation for unit tests.
- Document and test the Drive file schema (`manifest.json`, `item.json`, image file names, palette file).
- Add fake provider tests for first-install import, local+remote merge, app-initiated delete propagation, external Drive deletion handling, and palette change fan-out.
- Add UI state models and localized string resources for sync statuses without wiring real OAuth.
- Add CI static checks that do not require secrets and do not run local Gradle on this host.

### Implemented foundation in this slice

This repository now includes credential-free Drive sync seams that intentionally do not
perform real OAuth or Drive API calls:

- `WardrobeSyncSnapshot` models the full graph: taxonomy categories/tags, main
  colors, garments, tag/color relationships, photo blob metadata, archived flags,
  revisions/timestamps, and tombstones.
- `LocalWardrobeSyncSnapshotRepository` exports Room state deterministically from
  `WardrobeDao`, `TagDao`, and `SyncTombstoneDao` without requiring Drive
  credentials or Android runtime validation on this ARM host.
- `sync_tombstones` records app-side deletion markers for Manage-created tags and
  main colors so future restore/merge logic can distinguish deletes from missing
  rows.
- `WardrobeSyncGateway` remains queue-oriented and now includes full snapshot and
  taxonomy/tombstone operations while preserving existing item/tag/palette queue
  events used by the UI.
- `DriveWardrobeRepository` is narrowed to manifest/snapshot exchange for the
  user-authorized Google account and defaults to Drive `appDataFolder` semantics.
  `NotConfiguredDriveWardrobeRepository` blocks all calls with setup-required
  state, and `InMemoryDriveWardrobeRepository` supports fake merge/import tests.
- Settings and wardrobe item status copy derive from sync state and are localized
  in English, Spanish, and German. The Google Drive menu item remains disabled
  while the app is not configured.

No OAuth client IDs, Drive folder IDs, access tokens, client secrets, service
accounts, or release-signing assumptions were added. Real Google account
connection remains blocked until Manu completes the Google Cloud/OAuth steps and
a follow-up worker wires the chosen credential/config channel.

## 5. What remains blocked until credentials/human action

These parts should wait until the Google Cloud checklist is complete:

- Real Google account sign-in/authorization on device.
- Real Drive API calls against a user's account.
- End-to-end manual APK testing of OAuth consent and Drive folder creation.
- Validating which registered SHA-1 fingerprint matches GitHub Actions APKs and later Play-signed APKs.
- Publishing OAuth consent to production if Google requires app verification.

## 6. Secret and config handling

Recommended names for future GitHub Actions secrets/variables if release signing or secret-backed config is introduced:

Secrets:

- `ANDROID_KEYSTORE_BASE64` - release/upload keystore encoded as base64.
- `ANDROID_KEYSTORE_PASSWORD` - keystore password.
- `ANDROID_KEY_ALIAS` - release/upload key alias.
- `ANDROID_KEY_PASSWORD` - key password.

Variables or non-secret config:

- `ROBIA_GOOGLE_CLOUD_PROJECT_ID`
- `ROBIA_ANDROID_OAUTH_CLIENT_ID_DEBUG`
- `ROBIA_ANDROID_OAUTH_CLIENT_ID_RELEASE`
- `ROBIA_DRIVE_SYNC_ENABLED=false` until implementation is ready.

Local developer config:

- Put machine-local values in `local.properties` or an untracked `.env.local`.
- Keep `local.properties` ignored by git.
- Do not commit keystores, refresh tokens, OAuth access tokens, service-account JSON files, or manually exported credential blobs.
- Android OAuth client IDs are not secrets in the same way as tokens, but keep environment-specific IDs out of source until the implementation chooses a stable config pattern.

Important Android note:

- An Android OAuth client normally has no client secret. Do not add a server-style OAuth client secret to the mobile app.
- Do not use a Google service account for per-user Drive sync.

## 7. CI-safe validation plan

Respect the project rule: do not run Android Gradle compile/lint/test/APK/AAB locally on this host. Use these validation boundaries:

Local worker checks allowed on this host:

- Documentation spelling/structure review.
- `git diff --check` for whitespace errors.
- Targeted Python/static scripts that explicitly avoid Gradle and Android compilation.
- Grep/static checks that verify no secrets were committed.

GitHub Actions checks:

- Existing `.github/workflows/android-apk.yml` builds the debug APK on `ubuntu-latest` with JDK 17, Android SDK setup, Gradle 8.9, TFLite drift check, and `gradle --no-daemon assembleDebug`.
- Future Drive implementation PRs should extend CI with unit tests/fake provider tests, but those tests should run in GitHub Actions rather than on this host.
- Do not require real Google credentials in CI. Use fake Drive repositories and mocked OAuth/account providers for automated validation.
- If a smoke test needs credential-like values, use placeholder values committed in test fixtures, never real tokens.

## 8. Dependency checklist for future implementation

Android app dependencies to evaluate when implementation begins:

- Google Identity / Authorization library for Android account authorization.
- Google Drive REST client or a thin HTTP client wrapper around Drive v3 endpoints.
- Kotlin serialization or Moshi for `manifest.json`, `item.json`, and `colors.json`.
- Room migrations for sync state tables.
- WorkManager for durable offline sync retries.

Local developer/CI tooling:

- JDK 17.
- Android SDK managed by GitHub Actions for builds.
- Gradle 8.9 in GitHub Actions.
- Python 3.11 only for existing static/TFLite helper scripts.
- `gh` CLI is useful for checking CI status and downloading APK artifacts, but not required by the app.
