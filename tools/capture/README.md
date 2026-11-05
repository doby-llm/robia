# Store capture tooling

Scripts that record the Robia store clips and stills (CAP session, round 5) from a connected Android device, so the
captures can be re-taken or replayed against a later build. The shot descriptions live in
[`docs/specs/robia-round5/creative/shotlist.md`](../../docs/specs/robia-round5/creative/shotlist.md).

## Files

| File | Purpose |
|---|---|
| `cap.py` | `Session` runner: every `adb` call is appended to `<session>/commands.jsonl` with its delay. `replay` re-runs a log. |
| `ui.py` | uiautomator helpers: find a node by text or content-description, tap it, read visible texts. |
| `shots.py` | Per-shot scripts (`B`, `D`, `G`, `H`, `J`, `K`, `L`, `M`) for `en`, `es` or `de`. |
| `i18n.py` | Maps English UI strings to ES/DE using the app's own `strings.xml` (`app/src/main/res`). |
| `sessions/commands.jsonl` | Replay log of the first recording session (round 5). |
| `sessions/manifest.json` | Clip and still index for that session. Paths are relative to the session directory. |

## What it does

- **Device control.** Input is sent with `adb shell input`. Taps use either hard-coded coordinates or a label looked up in
  a `uiautomator dump`, so a label change shows up as a clear `not found` error rather than a mis-tap.
- **Localised labels.** Labels come from the app's strings, so EN, ES and DE captures use the real UI text.
- **Typing.** Gboard drops letters when a whole word is sent in one `input text` call, so text is typed one character
  at a time with a short pause, and spaces are sent as `KEYCODE_SPACE` (62). `shots.py` checks that the search field
  holds exactly the query and retakes the shot if it does not.
- **Recording.** Each clip is one `screenrecord` file on the device (`/sdcard/r5/<locale>/<shot>.mp4`), pulled into the
  session directory. Each shot also gets a still.
- **Replay log.** Every shell and adb command, sleep, marker and screen-recording start is appended to
  `commands.jsonl`. `cap.py replay` re-runs that log with the same timing (each wait capped at 10 s).

### Shots

| Shot | In `shots.py` | What it shows |
|---|---|---|
| B | yes | Browse grid: fast scroll, one slow pass, back to top |
| D | yes | Browse plus search for the query "something warm for a wedding" |
| G | yes | Collections: add garments to a collection (two tiles, then undo) |
| H | yes | Wardrobe statistics (analytics screen) |
| J | yes | Export / share card with the Robia frame; toggles one "Show" switch |
| K | yes | Browse in dark theme, then back to light |
| L | yes | Advanced filters, then reset |
| M | yes | Settings, smart suggestions |
| A, A2, C, E, F, I, I2 | **no** | Recorded in the session (see `commands.jsonl`), but their scripts are not in this copy |

The setup and explore sections at the top of `commands.jsonl` (demo status bar, collections setup) are logged there
but are not in `shots.py`. Shot F was recorded with airplane mode on: the log enables it before F and disables it
after.

## Prerequisites

- **adb** on `PATH`, one device connected and authorised. If more than one device is attached, set `ANDROID_SERIAL`.
- **Python 3**, standard library only. `ffprobe` is optional: without it, the manifest width and height stay `null`.
- **Capture build** `com.gusanitolabs.robia.capture` installed on the device.
- **Device setup.** The coordinates in `shots.py` assume a 1080×2400 display. Set the device language to the locale
  you record (`en`, `es` or `de`), and set the app to: palette Tobacco & Bordeaux, theme Light, Settings › Browse layout
  "Photos only" (Dark for shot K only). Gboard must be the active keyboard.
- **Demo photos** pushed to `/sdcard/Pictures/RobiaDemo`. The photos are **not** in the repo. Expected files:
  - `A_hero.jpg`
  - `dup_copy.jpg` (byte-identical copy of one wardrobe photo)
  - `batch/B01.jpg` … `batch/B06.jpg`

  Push them from a local folder that has the same layout:

  ```sh
  adb shell mkdir -p /sdcard/Pictures/RobiaDemo/batch
  adb push photos/. /sdcard/Pictures/RobiaDemo/
  ```
- **Demo wardrobe** created before recording: 12 garments and three collections, Weekend trip (5 garments), Winter
  (4) and Work week (6). Per locale, the collection names are changed to the ES and DE names listed in the shot list.

## Re-recording a shot

```sh
python3 -I tools/capture/shots.py en <session-dir> B D
```

This records shots `B` and `D` into `<session-dir>/en/clips/` and `<session-dir>/en/stills/`. It replaces the manifest
entries for those shots and appends to `<session-dir>/commands.jsonl`. Use a new session directory for each recording
run so the replay log stays one run long.

A relative `<session-dir>` is resolved against `$ROBIA_CAPTURE_ROOT` (default: the current directory). Pass an absolute
path to skip this.

## Replaying a session

```sh
python3 tools/capture/cap.py replay <session-dir>
```

Replay re-drives the device: taps, swipes, keys, airplane-mode toggles, setup broadcasts and screen recordings. It
does not retake stills, and it does not pull clips. Pull them afterwards:

```sh
mkdir -p <session-dir>/clips && adb pull /sdcard/r5/. <session-dir>/clips
```

Replay assumes the same device state as the original run: same build, language, wardrobe and coordinates.

## Known quirks

- **Status bar.** On Xiaomi MIUI, SystemUI demo mode is ignored, so the status bar (10:00, full battery, Wi-Fi, no
  notifications) is not clean. Cover the status bar in post.
- **Gboard.** Sending a whole word in one `input text` call drops letters. Keep the one-character-at-a-time typing in
  `cap.Session.text_words`.
- **Duplicates banner.** The "Possible duplicates" banner appears only after the embeddings have settled. After adding
  `dup_copy.jpg`, wait for the banner before recording shot E.
- **Tag review.** Shot I needs a category that still has untagged garments. Delete a category and recreate it with a
  description to get one.
- **System photo picker.** Multi-select in the picker is entered by long-press.
- **Airplane mode.** Shot F needs airplane mode on for the whole clip, then off again. The log enables it with
  `cmd connectivity airplane-mode enable` and disables it with `… disable`. `shots.py` does not toggle it, so do it
  by hand before recording F.
- **Typing check.** `D` retakes the shot with longer pauses if the field text does not match the query. It raises an
  error after three attempts.
