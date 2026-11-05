# PLAY-RUNBOOK — from G-CODE to production

Account: GusanitoLabs, **personal** (R5-DEC-M01). Answers for every Console form: `PLAY-CONSOLE-SHEET.md`.
API tool: `tools/play` (`TASK-PL3.md`). Data facts: `../robia-round4/artifacts/data-safety.md`.
Privacy policy: https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md.

## Actors
- **Coordinator** (Sonnet 5.5, `claude -p` on the Pi): PL1, PL2, PL5, PL6; never a browser.
- **Browser agent**: a Hermes `delegate_task` child (delegation model = Haiku 5.5) using `browser_exec` with
  `local=true` (Manu's Chromium profile), launched by Hermes from the Telegram chat when Manu says he is at the PC.
  Context = this runbook section + the sheet. It never types a password or code (`browser_vault` / Manu does it), stops
  before every "Send for review / Start rollout / Publish / Apply" and asks Manu, screenshots each saved form into
  `~/.local/share/robia-planning/r5/play-evidence/`.
- **Manu**: sign-in + 2FA, the first upload, the irreversible clicks, recruiting testers.

## PL1 — Upload key + secrets (coordinator, host; before G-CODE)
1. `keytool -genkeypair -v -keystore ~/.config/robia-release/robia-upload.jks -alias robia-upload -keyalg RSA
   -keysize 4096 -validity 10000 -dname "CN=GusanitoLabs, O=GusanitoLabs, C=CH"`; passwords generated with
   `openssl rand -base64 24`, stored only in `~/.config/robia-release/secrets.env` (chmod 600), never printed.
2. `gh secret set ANDROID_KEYSTORE_BASE64 -R doby-llm/robia < <(base64 -w0 robia-upload.jks)`; same for
   `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` from the env file via stdin.
3. Tell Manu (Spanish): copy `robia-upload.jks` + `secrets.env` to his password manager / offline backup (losing the
   upload key is recoverable through Play support, but slow).
- V-PL1-1 `gh secret list` shows the 4 names. V-PL1-2 a `release=true` dry run on the current main produces a signed AAB
  (`jarsigner -verify` OK); that AAB is discarded (never uploaded; its versionCode is burnt only if uploaded).

## PL2 — Release candidate (coordinator, after G-CODE)
`gh workflow run android-apk.yml --ref main -f release=true`; download `robia-release-aab`; verify sha256 file,
`release-metadata.txt` (versionName 1.0.0, versionCode, commit = G-CODE SHA). Upload the AAB to Manu's Drive (API) for PB2.

## PB1 — Create the app and App content (browser agent + Manu, after G-CODE)
Play Console › Create app (name Robia, default language English (United States), App, Free, declarations) → Dashboard
"Set up your app": privacy policy, app access, ads, content rating, target audience, news, data safety, government,
financial, health, advertising ID → Store settings (category Lifestyle, contact e-mail). Store listing text/images are
done later by API (PL5); the agent only fills what the API cannot do.

## PB2 — First AAB to Internal testing (Manu, ~3 min)
Test and release › Testing › Internal testing › Create new release → accept **Play App Signing** (Google-generated
key) → upload the PL2 AAB from Drive → release name "1.0.0" → notes from `creative/listing-*.md` → Save → Review →
Start rollout to internal testing. Testers list: Manu's own account. Manu installs from the opt-in link and checks that
Drive sign-in fails (expected until PB3) and the rest works.

## PB3 — Keys and access (browser agent + Manu)
1. Play Console › Test and release › App integrity › Play app signing › **App signing key certificate SHA-1** → copy.
2. Google Cloud (project used for Robia's Drive OAuth, see `docs/google_drive_sync_setup_plan.md`) › Google Auth
   Platform › Clients › Create client › Android: package `com.gusanitolabs.robia`, that SHA-1. Audience/consent screen
   **In production**, scope only `drive.appdata` (non-sensitive, no verification). Manu re-tests Drive on the internal build.
3. Same Cloud project › enable "Google Play Android Developer API" › service account `robia-play` › JSON key → Manu
   downloads it; coordinator moves it to `~/.config/robia-play/service-account.json` (chmod 600).
4. Play Console › Users and permissions › Invite new users › the service-account e-mail › App permissions › Robia:
   the permissions listed in the sheet (no production release). Wait up to 24 h for propagation; `tools/play status`
   proves access.

## PL5 — Listing and closed track by API (coordinator, after PB3, PL3, G-CR)
`uv run robia_play.py listing --dir r5/creative-out/play-assets` (dry run, read the plan) → `--commit`.
`upload-bundle --aab <PL2 aab> --track alpha --notes-dir ... --status completed` (dry run) → `--commit`
with `--version-code <N of PB2>` (the bundle is already on Play from PB2, so the tool skips the upload and only
assigns N to the closed track `alpha`; API id of Internal testing is `qa`; see TASK-PL3). Evidence: `status` output in the tracking issue.

## PB4 — Closed test (browser agent + Manu)
Closed testing › the track › Testers: e-mail list "Robia testers" with ≥ 15 addresses Manu provides; countries: all
(or Manu's list); feedback e-mail. Publishing overview › **Send for review** (Manu clicks). After approval, copy the
opt-in link → Manu sends it with a short Spanish/English/German tester note: install, add 5 garments, try search,
batch add, a collection, keep the app 14 days. Coordinator records the start date; reminders day 7 and day 12 to check
opted-in count ≥ 12 (Dashboard shows it).

## PB5 — Apply for production (day ≥ 14, browser agent drafts, Manu sends)
Dashboard › Apply for production: three sections (closed test, app, production readiness). Draft from the sheet plus
real tester feedback; Manu edits and clicks Apply. Review usually ≤ 7 days.

## PL6 — Production (after approval, Manu's explicit go)
`ROBIA_PLAY_ALLOW_PRODUCTION=1 uv run robia_play.py upload-bundle ... --track production --production --commit`
(staged rollout 20 % → 100 % if Manu wants), or promote in Console. Keep the closed track for future betas.
