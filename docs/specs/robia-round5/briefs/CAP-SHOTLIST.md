# CAP — ADB capture session (coordinator + Manu's phone)

## Goal
Record the raw material for the promo videos and the Play screenshots on a real phone, on the `capture` build of the
G-CODE commit, with a demo wardrobe, in EN, ES and DE. `creative/shotlist.md` (CR1) is the authoritative list of shots;
this file fixes how the session runs.

## Before Manu plugs in (coordinator)
1. Download `robia-capture-apk` from the main CI run of the G-CODE commit; check `aapt2 dump badging` shows
   `com.gusanitolabs.robia.capture` and `1.0.0-capture`.
2. Demo wardrobe (R5-DEC-M04): 20–24 garments, varied categories and colours, no logos/faces/brand names readable,
   rights-free. Source, in order of preference: (a) photos Manu takes of a few neutral garments (no people), (b) CC0
   product photos (Unsplash/Pexels licence allows it; record each URL + licence in `r5/captures/demo-sources.md`).
   Store under `~/.local/share/robia-planning/r5/demo-wardrobe/` (never in git).
3. Message Manu (Spanish): enable USB debugging, plug in, accept the RSA prompt, ~60–90 min, phone stays unlocked;
   Do Not Disturb on; battery > 60 %.

## Device setup (coordinator via ADB)
- `adb install -r robia-capture.apk`; `adb push` the demo photos to `/sdcard/Pictures/RobiaDemo/`; media scan.
- Clean status bar: `adb shell settings put global sysui_demo_allowed 1`, then demo-mode broadcasts (clock 10:00,
  full battery, full wifi, no notifications). Restore at the end (`exit`).
- Language per pass: `adb shell cmd locale set-app-locales com.gusanitolabs.robia.capture --locales en-US|es-ES|de-DE`
  (per-app locale; Robia follows it) — fallback: the in-app language picker.
- Load the wardrobe once by driving the batch-add flow (Manu may tap if a system picker needs it). Let the embedding
  model download and the background backfill finish before recording (check Settings › Smart features).
- Colour palette Tobacco & Bordeaux, theme Light for most shots; one dark-mode shot per CR1's list.

## Recording
- Video: `adb shell screenrecord --bit-rate 16000000 --size <native> --time-limit 30 /sdcard/r5/<locale>/<shot>.mp4`
  (one file per shot; the creative lane trims). Taps are driven by `adb shell input tap/swipe/text` from coordinates the
  coordinator measures once per screen with `uiautomator dump` — scripted, so every locale repeats the same motion.
  Show taps OFF (the video draws its own touch indicator if wanted).
- Stills: `adb exec-out screencap -p > <locale>/stills/<shot>.png` at settled frames.
- **Airplane-mode proof (shot F):** first test once: airplane mode on (`adb shell cmd connectivity airplane-mode enable`),
  run a semantic search ("something warm for a wedding" / ES / DE equivalents from CR1). If results appear, record it;
  if not, record the fallback beat CR1 defines (search without the airplane shot) and tell the coordinator.
- After each locale: `adb pull /sdcard/r5/<locale>` → `~/.local/share/robia-planning/r5/captures/<locale>/clips/`.
- Write `captures/manifest.json`: `[{locale, shot, file, kind: clip|still, seconds, width, height}]` with `ffprobe`.

## Acceptance
- V-CAP-1 every shot id of `creative/shotlist.md` × {en, es, de} present in `manifest.json`.
- V-CAP-2 no real personal data visible: coordinator samples one frame per clip (ffmpeg) and checks with vision
  (no account e-mail, no real collection names, demo garments only); the Drive screen is never recorded.
- V-CAP-3 demo mode restored, `adb uninstall com.gusanitolabs.robia.capture` only if Manu asks (keeping it allows re-takes),
  demo photos removed from the phone; Manu's real app untouched (`adb shell pm list packages | grep robia` shows both).

## Do not
Install over or open `com.gusanitolabs.robia`; touch Manu's photos or accounts; record the notification shade.
