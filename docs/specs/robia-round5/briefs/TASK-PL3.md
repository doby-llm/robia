# TASK-PL3 — `tools/play`: Play Developer API CLI (status, bundle + track, listing)

## Goal
Give the coordinator one small, safe CLI that drives the Google Play Android Developer API for `com.gusanitolabs.robia`:
read track status, upload the signed AAB to a track with release notes, and push store listing text and images in
en-US, es-ES and de-DE. It is dry-run by default, commits only with `--commit`, and cannot touch production without two
explicit switches. The first AAB upload is manual (Play Console); this tool takes over after that (see PLAY-RUNBOOK
PB2/PL5). No Play data is changed by anyone until the coordinator runs `--commit` by hand.

## Read first
- `docs/specs/robia-round5/briefs/PLAY-RUNBOOK.md` (sections PB2, PB3, PL5, PL6): the calls this tool must serve.
- `.github/workflows/android-apk.yml`, job `release-aab`: artifact `robia-release-aab` contains `app-release.aab`,
  `app-release.aab.sha256` (format `<hash>  app/build/outputs/bundle/release/app-release.aab`, full path, so compare
  the hash token and the basename only) and `metadata/release-metadata.txt` (`ref`, `sha`, `versionName`,
  `versionCode`, `aab`, `sha256`). Do not change the workflow.
- Primary sources (checked 2026-11-04):
  - Setup: https://developers.google.com/android-publisher/getting_started
  - Edits and first upload: https://developers.google.com/android-publisher/edits
  - Bundle upload (endpoint, 2-minute timeout): https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles/upload
  - Track ids: https://developers.google.com/android-publisher/tracks
  - Track/Release/status: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.tracks
  - Testers (Google Groups only): https://developers.google.com/android-publisher/api-ref/rest/v3/edits.testers
  - Images endpoints: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.images
  - Listing language: https://developers.google.com/android-publisher/api-ref/rest/v3/edits.listings
  - Data safety CSV: https://developers.google.com/android-publisher/api-ref/rest/v3/applications/dataSafety
  - Validate: https://developers.google.com/android-publisher/api-ref/rest/v3/edits/validate
  - Quota (3000 queries/min per bucket): https://developers.google.com/android-publisher/quotas
  - Title 30 / short 80 / full 4000: https://support.google.com/googleplay/android-developer/answer/9859152
  - Screenshots, feature graphic, icon: https://support.google.com/googleplay/android-developer/answer/9866151
  - Permission "Release apps to testing tracks": https://support.google.com/googleplay/android-developer/answer/7159011
- Versions (PyPI JSON, `info.version`): `google-api-python-client` 2.201.0 (uploaded 2026-09-30), `google-auth` 2.62.1
  (uploaded 2026-11-03); both `requires_python >=3.10`. Re-check on the day you lock.
- Facts NOT confirmed from a primary source are listed at the end; do not build on them without a check.

## Steps
1. Create `tools/play/` with:
   - `pyproject.toml`: `requires-python = ">=3.10"`; dependencies `google-api-python-client>=2.201.0`,
     `google-auth>=2.62.1`; dev group `pytest`. No other runtime dependencies (no Pillow: parse PNG/JPEG headers with `struct`).
   - `uv.lock` from `UV_CACHE_DIR=/tmp/uv-cache ~/.local/bin/uv lock` inside `tools/play/`.
   - `README.md`: 15 lines max (setup, the dry-run rule, the production guard, exit codes).
   - `robia_play.py` (single module, argparse, stdlib `logging` to stderr) and `tests/test_robia_play.py`.
2. Constants: `PACKAGE = "com.gusanitolabs.robia"`; `LOCALES = ("en-US", "es-ES", "de-DE")`;
   `SCOPE = "https://www.googleapis.com/auth/androidpublisher"`; API track ids: `internal` → `qa`, `alpha` → `alpha`,
   `beta` → `beta`, `production` → `production`; any other value is passed through as a custom closed-track id.
3. Credentials: key path from env `ROBIA_PLAY_KEY`, default `~/.config/robia-play/service-account.json`. Refuse (exit 1)
   if the file is missing, not a regular file, or has any group/other permission bit (`st_mode & 0o077`). Load with
   `google.oauth2.service_account.Credentials.from_service_account_file(path, scopes=[SCOPE])`. Build the client with
   `googleapiclient.discovery.build("androidpublisher", "v3", http=AuthorizedHttp(creds, http=httplib2.Http(timeout=120)), cache_discovery=False)`.
   The service object is injected into every command function so tests never reach `build()`.
4. Subcommands (all accept `--commit`; without it the command is a dry run):
   - `status`: insert an edit, `tracks.list`, print each track id with its releases (name, status, versionCodes,
     userFraction), then delete the edit. Never commits. Exit 0.
   - `upload-bundle --aab PATH --metadata release-metadata.txt --track ID --notes-dir DIR [--status draft|completed]
     [--version-code N] [--production] [--commit]`:
     a. Before any network call: compute the AAB sha256 in 1 MiB chunks; compare with the hash token in
        `PATH.sha256`, the basename with the AAB name, and `sha256=` in the metadata if given. Mismatch → exit 2.
     b. Production guard: if the resolved track is `production`, require `--production` AND env
        `ROBIA_PLAY_ALLOW_PRODUCTION == "1"`; otherwise exit 3. Checked before any network call, in dry-run too.
     c. Notes: each of the three locales needs a non-empty `DIR/<locale>.txt` (UTF-8). Missing or empty → exit 2.
     d. `edits.insert`. Without `--version-code`: `edits.bundles.upload` with
        `MediaFileUpload(aab, mimetype="application/octet-stream", resumable=True)`; the response `versionCode` must equal
        the metadata `versionCode` when that is given (else exit 2). With `--version-code N`: skip the upload and reuse
        N (for the bundle already on Play from PB2). If the upload fails with a version-code "already exists" error, print
        the hint `rerun with --version-code N` and exit 4.
     e. `edits.tracks.update` with body `{"track": ID, "releases": [{"name": versionName or "1.0.0", "versionCodes": [N],
        "status": STATUS, "releaseNotes": [{"language": loc, "text": text} for each locale]}]}`.
     f. `edits.validate`. Errors → exit 5.
     g. `--commit`: `edits.commit`, print the result. Without it: print "DRY RUN: nothing committed", then delete the edit.
        The edit is deleted in a `finally` whenever it was not committed.
   - `listing --dir DIR [--commit]`: validate every local input first (see step 5), then per locale: `edits.listings.update`
     with `{title, shortDescription, fullDescription}`; `edits.images.deleteall` + `edits.images.upload` for
     `phoneScreenshots` (each `phone/*.png`), `featureGraphic` (`featureGraphic.png`), `icon` (`icon.png`, uploaded for every
     locale). Then validate; commit only with `--commit`. Same edit cleanup rule.
   - `release-notes --dir DIR`: offline. Print the character count per locale; exit 2 if any file is missing or empty.
5. Listing layout (the tool defines it; the creative lane must produce it):
   ```
   DIR/icon.png                    512x512, PNG 32-bit RGBA (colour type 6), <= 1024 KiB
   DIR/<locale>/title.txt          <= 30 characters (len() of the text, stripped)
   DIR/<locale>/short.txt          <= 80 characters
   DIR/<locale>/full.txt           <= 4000 characters
   DIR/<locale>/featureGraphic.png 1024x500, PNG 24-bit no alpha (colour type 2) or JPEG
   DIR/<locale>/phone/01.png ...   2 to 8 files; PNG 24-bit no alpha or JPEG; each side 320..3840 px;
                                   longer side <= 2 x shorter side
   ```
   The `icon.png` file at the top level is shared by all locales. Any violation → exit 2, no network call.
6. Errors and logs: log only the command, the track id, the versionCode, the edit id, and counts. For API errors print
   `HttpError` status, the Google `reason` if present, and at most 300 characters of the message. Redact anything that
   looks like `-----BEGIN` or a `"private_key"` field. Never print the key path's content, token, or request bodies.
7. Exit codes: 0 success (dry run or commit); 1 configuration or usage (key missing or permissive, unknown track, bad
   arguments); 2 local input check failed (sha256, notes, asset rule, versionCode mismatch); 3 production guard refused;
   4 Google API or network error; 5 `edits.validate` reported errors.
8. Method names and parameters (`edits().delete`, `edits().tracks().list`, `edits().commit`) are verified against the
   discovery document the client loads at runtime; if one is missing, fail with exit 4 and say which one.

## Tests
Unit tests only, no network. Use a `FakeService` that records calls on the same chain shape
(`edits().insert/bundles().upload/tracks().update/tracks().list/listings().update/images().deleteall/upload/validate/commit/delete`).
Build tiny PNG headers in the test (`struct`, IHDR chunk) and JPEG magic bytes; never ship real images or keys.
Run: `cd tools/play && UV_CACHE_DIR=/tmp/uv-cache ~/.local/bin/uv run pytest -q`.

## Done when
| ID | Check | Evidence |
|---|---|---|
| V-PL3-1 | `uv run pytest -q` passes, no network access during tests | pytest summary line |
| V-PL3-2 | AAB whose sha256 differs from `.sha256` → exit 2; FakeService receives zero calls | test + exit code |
| V-PL3-3 | `--track production` without both `--production` and `ROBIA_PLAY_ALLOW_PRODUCTION=1` → exit 3, zero calls; with both → dry run only | test |
| V-PL3-4 | Dry run: no `commit` call, exactly one `delete` for the edit, exit 0 | test |
| V-PL3-5 | `--commit` is the only way `commit` is called | test |
| V-PL3-6 | `--track internal` sends track id `qa`; `alpha` stays `alpha` | test |
| V-PL3-7 | Key file with mode 0644 → exit 1 before reading; key marker string never appears in stdout, stderr, or exception text | test (marker in a tmp key file) |
| V-PL3-8 | Listing: 31-char title refused; 4001-char full text refused; 1 phone screenshot refused; 1080x2400 screenshot refused (ratio 2.22); 2 valid screenshots accepted | test |
| V-PL3-9 | `validate` reports an error → exit 5, no commit, edit deleted | test |
| V-PL3-10 | Injected `HttpError` on `tracks.update` → exit 4, edit deleted in `finally` | test |
| V-PL3-11 | `--version-code N` skips `bundles.upload` and sends `versionCodes: [N]` | test |
| V-PL3-12 | Coordinator only, after PB3: `uv run robia_play.py status` prints the `alpha` and `qa` tracks with the real key; output pasted in the tracking issue | coordinator run, not the agent |

## Do not
- Do not run the tool against Play, and do not pass `--commit`, from the agent session. Tests are the only run.
- Do not commit, push, or create a credential. The key stays at `~/.config/robia-play/service-account.json`, mode 600.
- Do not manage testers: the API accepts Google Groups only (edits.testers); the email-list tester set stays in the Console.
- Do not set Data safety, content rating, target audience, ads, app access, or any production release from this tool.
- Do not use `changesNotSentForReview`, staged rollouts, or managed publishing flags in this version.
- Do not edit `android-apk.yml`, app code, other `tools/*`, or the briefs. Do not add dependencies beyond step 1.

## Manu / coordinator setup (exact clicks; labels from the Console UI may shift slightly)
A. Google Cloud (project used for Robia's Drive OAuth, see `docs/google_drive_sync_setup_plan.md`):
   1. https://console.cloud.google.com → project picker → select the Robia project.
   2. APIs & Services → Library → search "Google Play Android Developer API" → Enable.
   3. IAM & Admin → Service Accounts → Create service account → name `robia-play` → Done. (No IAM roles needed for Play
      access per third-party guides; the grant happens in Play Console.)
   4. Open `robia-play` → Keys → Add key → Create new key → JSON → Create. Save the file; the coordinator moves it to
      `~/.config/robia-play/service-account.json` and runs `chmod 600`. Never paste it into chat, a commit, or a log.
B. Play Console (https://play.google.com/console):
   1. Settings (gear) → Developer account → API access → link the Cloud project above (create one if asked).
   2. Users and permissions → Invite new users → paste the `robia-play@…iam.gserviceaccount.com` address.
   3. App permissions → Add app → Robia (`com.gusanitolabs.robia`) → grant "Release apps to testing tracks" (confirmed
      name) plus the store-presence permission for editing the listing (exact label to be read from the Console; not
      confirmed by a primary source). Do NOT grant production release. → Invite user → Send invitation.
   4. Wait before the first `status` call. Google gives no official figure; third-party guides say up to 24–48 h.
C. First AAB (manual; the API only works on an app that already has an upload):
   1. GitHub → Actions → "Android APK CI" → Run workflow → `release` = true → after it finishes, download artifact
      `robia-release-aab`. Check `metadata/release-metadata.txt`: versionName, versionCode, sha256.
   2. Play Console → Create app (name Robia, default language English (United States), App, Free) → complete the
      declarations the Console asks for (PLAY-RUNBOOK PB1).
   3. Testing → Internal testing → Create new release → accept Play App Signing → upload `app-release.aab` → name "1.0.0"
      → notes → Save → Review → Start rollout to internal testing (PB2). This is the only manual upload.
D. Then, as the coordinator: `status`; then `listing --dir <assets>` (dry run, read the plan) and `--commit`;
   then `upload-bundle --aab <aab> --metadata <metadata> --track alpha --notes-dir <notes> --status completed --version-code <N>`
   (dry run) and `--commit`, per PLAY-RUNBOOK PL5.

## Facts not confirmed from a primary source (verify before relying on them)
1. "Cloud IAM roles do not grant Play access": only third-party guides say so; Google's getting-started page is silent.
2. Propagation time after inviting the service account: no official figure (third-party: up to 24–48 h).
3. Exact Console labels for app-level permissions other than "Release apps to testing tracks".
4. What the API cannot do: create app, content rating, target audience, ads, app access, production application, legal
   consents. Not stated in the reference pages fetched; treat all as Console-only.
5. Custom closed-track id format in the API (only form-factor prefixes are documented, e.g. `wear:production`).
6. `edits.bundles.upload` size limit, resumable support, and MIME type (`application/octet-stream` appears only in
   third-party generated docs, which mention a 2 GB cap; not confirmed).
7. Edit lifetime and expiry: not stated.
8. `AppImageType` enum values (`phoneScreenshots`, `featureGraphic`, `icon`): the fetched page does not list them.
9. Release-notes character limit, and per-image file size limit for listing images.
10. `edits.validate` checks performed: the method is documented ("Validates an app edit.") but its checks are not listed.
11. Whether a Data safety CSV upload via `applications.dataSafety` has size or frequency limits (method exists; limits not stated).
