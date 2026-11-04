# TASK-CR — Promo videos, Play screenshots, feature graphic, listing copy (creative lane)

Model: Claude **Opus 5.5 --effort high** (R5-DEC-M07). Skill: brag (`latent-spaces/brag`, v0.5.0). On Opus 5.5 `/brag`
hands off to `/brag-slim` unless `--full`: Manu asked for Hyperframes, so use **`/brag --full`**; `/brag-slim` only as
the fallback of step 0.

## Goal
Make Robia understandable to a stranger in ~25 s: what it is (a wardrobe app for Android), the problem (you own more
than you remember and cannot find it), and what sets it apart (**the AI runs on the phone**: semantic search by
meaning, auto-tagging, background removal, duplicate detection — no cloud, no account, no ads). Deliver EN/ES/DE ×
vertical/horizontal, plus the Play store assets.

## Facts you may claim (anything else: ask the coordinator)
- Add a garment from a photo; the background is removed on the phone; category, occasion and colours are suggested.
- Batch add many photos at once.
- Search by meaning ("something warm for a wedding") with an on-device embedding model (EmbeddingGemma 2, downloaded
  once, ~388 MB). Works offline once downloaded *only if CAP's airplane test passed* (shot F).
- Duplicate detection; tag review; collections; statistics (Analytics screen); export/share cards.
- Optional backup to the user's own Google Drive (private app folder). No developer server, no analytics, no ads.
- Free. Languages EN/ES/DE. Android only. Palette Tobacco & Bordeaux (default) and Earth & Steel.
Banned: invented numbers, testimonials, "best/#1", competitor names, "AI-powered" without showing it.

## Phase 1 (CR1, before G-CODE, paper only)
Write in `docs/specs/robia-round5/creative/` (docs PR, coordinator merges):
1. `storyboard-vertical.md` and `storyboard-horizontal.md`: **two separate designs**, scene-by-scene with durations
   summing to 22–26 s, one tempo (BPM) with cuts on beats, on-screen copy per scene in EN/ES/DE (≈ 0.3 s per word on
   screen; DE runs ~30 % longer: lay out for DE). Native rules (R5-DEC-M05):
   - Vertical 1080×1920: one phone mock filling ~70 % height, captions in the safe zones (top 220 px and bottom 380 px
     reserved for platform UI), one idea per card, big type, fast.
   - Horizontal 1920×1080: phone left or right + copy column, side-by-side comparisons (before/after cutout, "tags vs
     meaning"), a multi-screen montage; wider pacing.
   - No frame of one may be a crop or scale of the other; shared assets are the clips, not the compositions.
2. Proposed narrative (adapt, don't copy blindly): Hook "You own more clothes than you remember" → snap a photo,
   background gone, tags filled → type "something warm for a wedding", results → airplane mode on, still works:
   "All AI runs on your phone" → montage duplicates/collections/stats → logo, "Your wardrobe, finally searchable",
   "Get it on Google Play" badge (official badge artwork and its usage rules).
3. `shotlist.md`: shot id, screen, exact scripted actions, duration, still yes/no, locale-specific inputs (search
   queries per language), the shot-F fallback. Include the 6 Play screenshot moments.
4. `listing-en-US.md`, `listing-es-ES.md`, `listing-de-DE.md`: title ≤ 30 ("Robia: Wardrobe & Outfits"-style, check
   length), short description ≤ 80, full description ≤ 4000, release notes 1.0.0 ≤ 500, character counts printed.
5. `credits.md`: music (brag's bundled "Happy Beats / Business Moves", ende.app, CC BY 4.0 — credit line text) or CC0
   alternative, SFX (Kenney, CC0), fonts (Inter, OFL).
Gate: coordinator sends Manu a Spanish 10-line summary of both storyboards; his reply (or silence = approved per his
rule) unlocks CAP.

## Phase 2 (CR2, after CAP)
0. Toolchain: Node 22 in user space (`~/.local/share/robia-planning/r5/node22`, official tarball for linux-arm64,
   verify SHA256 from nodejs.org SHASUMS256.txt; no system package changes, no `curl | sh`); `npx hyperframes doctor`.
   FFmpeg is at `/usr/bin/ffmpeg`, Chromium at `/usr/bin/chromium`. If doctor fails after 20 min → `/brag-slim`.
1. Work in `~/.local/share/robia-planning/r5/creative-out/` (outside git). Inputs: `r5/captures/` + `manifest.json`.
2. Build the vertical and horizontal compositions as separate projects; locale = text table swap only.
3. Renders one at a time: `systemd-run --user --wait -p MemoryMax=5G -p MemorySwapMax=0 <render cmd>` (the Pi has
   ~4–5 GB free; an uncapped render can kill the session). Check MemAvailable ≥ 5 GB first.
4. Before each full render: stills of every scene and mid-transition, inspect them, fix overflow (DE), contrast, collisions.
5. Audio: one mix, music −14 LUFS integrated (`ffmpeg loudnorm`), SFX under the music, cuts on beat.
6. Outputs per locale: `robia-<locale>-9x16.mp4`, `robia-<locale>-16x9.mp4` (H.264 high, yuv420p, 30 fps, AAC 192k,
   poster frame baked as frame 0), `poster-<locale>-{9x16,16x9}.jpg`, `share-copy-<locale>.txt`.
7. Play assets per locale in `play-assets/<locale>/`: `phone/01..06.png` 1080×1920 framed (device frame or rounded
   crop on brand background + caption), `featureGraphic.png` 1024×500 24-bit PNG no alpha (no text near edges; key
   message works without the logo too); `icon.png` 512×512 32-bit PNG once at `play-assets/` root (foreground
   `app/src/main/res/drawable-nodpi/ic_launcher_foreground_image.png` on #FCF8F1). Layout matches `tools/play`'s
   listing folder (`briefs/TASK-PL3.md`); copy the listing texts there too.
8. Upload small previews (EN both formats, contact sheet of screenshots) to Manu's Drive via the configured API
   (`~/.hermes/google-venv`, never browser) and send links. Gate G-CR.

## Do not
Use Manu's real wardrobe or account; put videos/captures in the git repo; claim offline search if shot F failed;
render two jobs at once; install system packages.
