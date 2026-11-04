# Verification matrix — robia-round5

Kinds: CI = unit/source test in GitHub Actions · REC = coordinator re-checks · DEV = Manu on the phone · EXT = Play/Cloud state.

| Row | Kind | Check |
|---|---|---|
| V-TH1-1 | CI | Tobacco light/dark schemes equal `artifacts/tobacco-bordeaux-airy.md` (incl. #FFD9DE fix) |
| V-TH1-2 | CI | Tobacco text pairs ≥ 4.5, spark ≥ 3, outline ≥ 3 |
| V-TH1-3 | CI | `ColorPalette.fromStorage` default TobaccoBordeaux, round-trip |
| V-TH1-4 | CI | `robiaColorScheme` 4 combinations; Earth & Steel hexes unchanged (existing test green, unmodified) |
| V-TH1-5 | CI | `wellScheme` = palette light scheme in both modes; chip colours equal |
| V-TH1-6 | CI | no `ui/` import of `RobiaLightColorScheme` |
| V-TH2-1..3 | CI | label mapping, source contract + equal string keys, Collator order EN/ES/DE |
| V-REL1-1..3 | CI/REC | `robia-capture-apk` artifact, badging line, dev menu note |
| V-PL1-1..2 | REC | 4 secrets present; dry-run release AAB signed |
| V-PL3-* | CI/REC | rows in `briefs/TASK-PL3.md`; Opus review SAFE |
| V-TH-D1 | DEV | Update over the current staging: app opens in Tobacco & Bordeaux, no colour flash at launch, data intact |
| V-TH-D2 | DEV | Settings › Apariencia › Paleta de colores: two options with dots; switching applies instantly; survives restart |
| V-TH-D3 | DEV | Both palettes × Claro/Oscuro/Seguir sistema: Browse, garment detail, batch add (pill "revisar" readable), tag review chips, Settings |
| V-TH-D4 | DEV | Empty photo placeholder in add/edit and the duplicate review icon readable in both palettes |
| V-TH-D5 | DEV | Share card / export look unchanged |
| V-CAP-1..3 | REC | `briefs/CAP-SHOTLIST.md` |
| V-CR-1 | REC | each video 22–26 s, 30 fps, correct size, −14 LUFS ±1, poster frame 0 |
| V-CR-2 | REC | vertical and horizontal storyboards differ scene by scene (no crop); DE text fits (stills checked) |
| V-CR-3 | REC | 6 screenshots/locale 1080×1920, feature graphic 1024×500 no alpha, icon 512×512; listing lengths within limits |
| V-CR-4 | DEV | G-CR: Manu approves EN videos and screenshots |
| V-PB-1 | EXT | Dashboard "Set up your app" all green; evidence screenshots |
| V-PB-2 | DEV | Play-installed internal build: Drive sign-in works after PB3 |
| V-PB-3 | EXT | `tools/play status` lists tracks (service account access) |
| V-PB-4 | EXT | closed test approved; ≥ 12 opted in on day 14 |
| V-PB-5 | EXT | production access granted |
