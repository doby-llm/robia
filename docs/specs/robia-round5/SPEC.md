# Robia round 5 — SPEC (status: PROPOSED, waiting for APROBADO)

Planner: Claude Opus 5.5 (planning only). Base: `main` @ `651a305`. Request and answers verbatim:
`feedback/2026-11-04-manu.md`. Decisions: `decision-log.md`. Tasks and graph: `tasks.md`.

## 0. Goal and order (R5-DEC-M01..M06)
Three workstreams, in this order, because Manu wants the code closed before any marketing or Play work:
1. **TH: colour palettes.** Settings gets a palette picker. **Tobacco & Bordeaux · Light / Airy is the default**,
   and the current Earth & Steel stays selectable. The existing Light/Dark/Follow-system setting still applies to both.
2. **G-CODE.** Manu checks the staging build on his phone and says the code is closed. Nothing in §3–§4 starts before it.
3. **Marketing assets (CAP + CR):** ADB capture session with a demo wardrobe, then the promo videos (EN/ES/DE ×
   vertical/horizontal, each format designed natively), the Play screenshots, the feature graphic and the listing copy.
4. **Play (PB/PL):** personal account → closed test (≥ 12 testers × 14 consecutive days) → apply for production.
   Uploads, the listing and releases go through the **Play Developer API with a service account** (tool `tools/play/`).
   The forms that exist only in Play Console are filled by a **Hermes browser agent (Haiku 5.5)** with Manu present.

Coordinator Claude Sonnet 5.5 high; code workers Claude Haiku 5.5 high; creative lane (video and assets) Claude
Opus 5.5 high; Play Console browser agent = Hermes subagent on the delegation model (Haiku 5.5), launched from Hermes.

## 1. TH — colour palettes

### 1.1 Behaviour (REQ-TH-*)
- **REQ-TH-1** New device-local setting `ColorPalette { EarthSteel("earth_steel"), TobaccoBordeaux("tobacco_bordeaux") }`
  in DataStore key `color_palette`. Absent or unknown value → **TobaccoBordeaux** (so existing installs switch to the
  new default on update; Manu wants that). Not part of any Drive snapshot or export.
- **REQ-TH-2** Effective colours = palette × dark flag. `ThemeMode` (Light/Dark/System) and `resolveDarkTheme` are unchanged.
  `RobiaTheme(darkTheme, palette)` selects the `ColorScheme` and the `RobiaExtras` of that palette and mode.
- **REQ-TH-3** Tobacco & Bordeaux tokens = `artifacts/tobacco-bordeaux-airy.md` exactly, light and dark, including
  the one planner fix: light `onTertiaryContainer` **#FFD9DE** (the palette file's #8E4E58 has 1.61:1 on #6A303A; the
  only user is the "needs review" pill in batch add). Light `sparkOnPrimary` = the same #FFD9DE (today's rule: spark =
  light onTertiaryContainer), 7.74:1 on primary. Add/remove chip colours and photo wells are the same as Earth & Steel.
- **REQ-TH-4** Earth & Steel keeps every hex it has today (pinned by `RobiaColorSchemeTest`).
- **REQ-TH-5** Photo-well ink. Four sites draw on the always-light photo well with `RobiaLightColorScheme.*`
  (`AddEditClothingScreen.kt:1700-1714`, `BatchAddClothingScreen.kt:528`, `BrowseGrid.kt:821-836`,
  `DuplicateReviewScreen.kt:288`). They must use the **active palette's light scheme** through a new
  `RobiaExtras.wellScheme: ColorScheme` (light scheme of the active palette, in both modes). No other behaviour change.
- **REQ-TH-6** First frame. `MainActivity.onCreate` already reads `themeMode` early (bounded `runBlocking` +
  `withTimeoutOrNull`, `MainActivity.kt:70`) and sets the window background from `RobiaBackground`/`RobiaDarkBackground`
  (`MainActivity.kt:92`). Read `colorPalette` the same way (TobaccoBordeaux on timeout) and set the window background
  from the resolved palette scheme's `background` (no Earth-&-Steel flash). Styles/manifest unchanged.
- **REQ-TH-6b** `PhotoEditorScreen.kt` keeps its three fixed Earth-&-Steel imports (`RobiaPrimary`, `RobiaError` mask
  overlays over the photo at :626, `RobiaSurfaceContainerHigh` neutral temperature swatch at :1173): they draw on the
  photo, not on UI chrome. Add a `// theme-fixed:` comment at each, no colour change.
- **REQ-TH-7** Settings › Appearance gets a second row under "Theme": **"Colour palette"**, value = the selected
  palette's name, opening a radio dialog like `ThemeModeDialog`. Each option shows a 3-dot swatch (primary,
  secondaryContainer, tertiary of that palette in the current mode) before its name; options ordered alphabetically
  with the locale `Collator` (Manu's standing rule). Selecting applies immediately and closes the dialog (no snackbar).
- **REQ-TH-8** Unchanged with the palette: exports and share cards (`media/GarmentShareExporter.kt` fixed colours),
  garment colour swatches (`GarmentSwatchBackground.kt`), the 24 `// theme-fixed:` literals, the launcher icon.

### 1.2 Copy (exact; strings in one `<!-- TH2 -->` block per file)
| Key | EN | ES | DE |
|---|---|---|---|
| `settings_color_palette` | Colour palette | Paleta de colores | Farbpalette |
| `palette_earth_steel` | Earth & Steel | Tierra y acero | Erde & Stahl |
| `palette_tobacco_bordeaux` | Tobacco & Bordeaux | Tabaco y burdeos | Tabak & Bordeaux |
In XML write `&amp;`.

### 1.3 Release hygiene (REL1, same wave)
- `versionName` "0.1.0" → **"1.0.0"** (first public release). `versionCode` stays CI-driven (`run_number + 100`).
- New build type **`capture`**: `initWith(release)`, `applicationIdSuffix = ".capture"`, `versionNameSuffix = "-capture"`,
  signed with the CI debug key like `staging`, `isDebuggable = false`, `matchingFallbacks += "release"`. CI builds it in
  the existing debug job and uploads artifact `robia-capture-apk`. Purpose: the demo wardrobe for screenshots and video
  installs next to Manu's real app without touching his data (R5-DEC-D02). Never uploaded to Play. Drive sign-in does
  not work in it (no OAuth client for that package) and is not needed.

## 2. G-CODE
After TH1, TH2 and REL1 are merged: CHK5 (coordinator) gives Manu the staging APK (Drive link) and a short device
checklist (`tasks.md` CHK5). Manu's rule applies: anything he does not mention passed. When he says the code is
closed (or nothing fails), G-CODE passes and PL2 builds the release candidate AAB from that exact commit. Any later code
change needs a new G-CODE, a new AAB and a new capture of the screens it affects.

## 3. Marketing assets
### 3.1 CAP — ADB capture session (coordinator + Manu's phone)
Shot list, demo wardrobe and device setup: `briefs/CAP-SHOTLIST.md`. One session, three app languages (EN, ES, DE), on
the `capture` build of the G-CODE commit. Output (outside git, personal-data free, ~1–2 GB):
`~/.local/share/robia-planning/r5/captures/<locale>/{clips,stills}/` + `manifest.json` (file → shot id, duration, size).

### 3.2 CR — creative lane (Opus 5.5 high, brag `--full` / Hyperframes)
Brief: `briefs/TASK-CR.md`. Deliverables (EN/ES/DE each):
- 2 promo videos, **9:16 1080×1920** and **16:9 1920×1080**, ~25 s, music + soft SFX, no voice-over. Each format has
  **its own storyboard and layouts** (vertical: one device, captions in the safe zones, fast single-idea cards;
  horizontal: device + copy column, multi-device montage). A frame of one must never be a crop or scale of the other.
- Play phone screenshots: 6 per locale, framed **1080×1920** PNG with a caption (raw phone captures are ~1080×2400,
  above Play's 2:1 limit, so they are framed, never stretched).
- Feature graphic 1024×500 (PNG 24-bit, no alpha) per locale; app icon 512×512 (from `ic_launcher_foreground_image.png`
  432 px on #FCF8F1; one icon for all locales).
- Store listing copy per locale: title ≤ 30, short description ≤ 80, full description ≤ 4000 characters; release notes
  "1.0.0" ≤ 500 characters. Facts only from `briefs/TASK-CR.md` §Facts; no "best"/"#1"/ranking claims (Play metadata policy).
Gate **G-CR**: Manu approves the videos (one look per format in EN; ES/DE are text swaps) and the screenshots.

## 4. Play
Personal account (R5-DEC-M01) → per-app closed test: ≥ 12 testers opted in **continuously for 14 days**, then
"Apply for production" (three questionnaire sections; review usually ≤ 7 days). Packly's test does not count.
The Play Developer API cannot create the app, take the first upload, or fill Content rating / Target audience / Ads /
App access / the production application (primary source: developers.google.com/android-publisher/edits): those go
through Play Console. Runbook: `briefs/PLAY-RUNBOOK.md`; Console answers: `briefs/PLAY-CONSOLE-SHEET.md`; API tool:
`briefs/TASK-PL3.md`.

Who does what:
| Step | Actor | Manu needed for |
|---|---|---|
| Upload key + 4 GitHub secrets (PL1) | coordinator on the Pi | "go" only; key backup copy to his password manager |
| Release AAB (PL2) | coordinator (CI `release=true`) | — |
| `tools/play` (PL3) | Haiku worker | — |
| Create app + App content forms (PB1) | Hermes browser agent | sign-in/2FA; confirm the CONFIRM items |
| First AAB to Internal testing (PB2) | **Manu** in Console (API needs it; Packly's automated browser upload failed with 55318F15) | ~3 min |
| App-signing SHA-1 → Drive OAuth client; consent screen In production; service account + key + invite (PB3) | browser agent | sign-in; key download |
| Listing, screenshots, closed-track release via API (PL5) | coordinator with `tools/play` | — |
| Testers list, countries, send closed test for review (PB4) | browser agent | "Send for review" click; recruiting 12 testers |
| Apply for production after day 14 (PB5) | browser agent drafts | edits and sends |
| Production release (PL6) | coordinator, `--production` | explicit "go" |

### 4.1 Testers (G-TESTERS — the long pole)
Manu recruits ≥ 12 people with a Google account and an Android phone (15 recommended for drop-outs). The agent creates
an e-mail list in the closed track; each tester opens the opt-in link, joins, installs from Play and keeps it 14 days.
Google also asks whether testers used the features, so CHK-style instructions for testers are part of PB4.
Expected calendar from G-CODE: assets 2–4 days in parallel with Play setup → closed test 14 days → review ≤ 7 days.

## 5. Out of scope
New features; changes to the launcher icon; dynamic colour (Material You); a third palette; Play pre-registration;
iOS; paid ads; automatic production rollout without Manu.

## 6. Risks
| Risk | Mitigation |
|---|---|
| Semantic search fails in airplane mode | CAP gate: test it first; fallback beat without airplane mode (shot list §F) |
| Hyperframes needs Node 22 (Pi has 20.19) | CR step 0: user-space Node 22 (no system change), `npx hyperframes doctor`; failure → `/brag-slim` |
| Render memory on the Pi | renders as `systemd-run --user -p MemoryMax=5G -p MemorySwapMax=0`, one at a time |
| Fewer than 12 testers stay 14 days | recruit 15; agent checks opt-in count on day 7 and 12 |
| Drive sign-in fails on the Play build | PB3 adds the App-signing SHA-1 client before testers join; PB2 build tested by Manu |
| API 403 | service account must be invited in Play Console › Users and permissions, not only in Cloud IAM |
