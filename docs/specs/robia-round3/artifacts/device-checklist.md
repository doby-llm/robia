# Robia round 3 — device checklist

**Build under test:** latest `main` staging APK.
APK link: <to be filled by the coordinator>
Main SHA: <to be filled>

An install over the existing app keeps your data only when the signer matches (same signer as the previous builds). If the signer differs, Android refuses the update or wipes the data; say so if that happens.

How to use this file: do each row on the build under test, mark **Pass** or **Fail** in the Pass/Fail column (`[x]`), and write what you saw in Notes. If a row fails, say which step failed.

## Counts (checked against verification-matrix.md)

| Round | `-D` rows listed |
|---|---|
| Round 3 | 20 |
| Round 3b | 8 |
| Round 3c | 6 |
| Round 3d | 6 |
| Round 3e | 1 |
| **Total** | **41** |

- Matrix count: `grep -E '^\| V-[A-Z0-9-]+-D \|' verification-matrix.md | wc -l` = **43**.
- Not listed: V-OUT-D (dropped, R3-DEC-042) and V-DUP1-D (closed, R3-DEC-047): 43 − 2 = **41**, which equals the table total above.
- V-ED1-D is listed as superseded by V-UI-SMART-D (R3-DEC-043); it is one of the 41.
- Gate rows (device): G-ADB1, G-ADB2, G-CUT, in the Round 3 table. Not part of the 41. G-DUP and G-OUT are closed and not listed.
- Extras: FIX-GRAY (#209) has no matrix row and is not part of the 41.

## Round 3

| Row | Task | What to do on the phone | Expected | Pass/Fail | Notes |
|---|---|---|---|---|---|
| V-K00-D | K00 | Update the app. Open Settings › Cutout. | Standard is selected. The precise option is labelled "Experimental". | [ ] | |
| V-K01-D | K01 | Coordinator via ADB only: run the debug dump on the phone for every image in `cutout-debug/in/`. | The dump runs and writes its files. | [ ] | |
| V-K03-D | K03 | Redo the red shorts (G-CUT photo set) with Experimental. | The top edge is as crisp as the sides. | [ ] | |
| V-ED1-D | ED1 | Superseded by V-UI-SMART-D (R3-DEC-043). Do not test it separately. | Not checked here. | [ ] | |
| V-ED2-D | ED2 | In the editor, add a new photo within the 64 MiB budget (not a 2048×2048 photo). Make 15 mixed edits and 2 "Cut out again", then undo all and redo all in order. | All can be undone and redone in order. On a saved garment, undo works only inside the open editor session, and "Cut out again" is not undoable there. A 2048×2048 photo is not covered: undo stops at the cut-out, by design. | [ ] | |
| V-GPU-D | GPU | Coordinator only: run the GPU row and record the numbers against CPU (`RobiaBenchmark` logcat line). | Numbers recorded. | [ ] | |
| V-OR1-D | OR1 | Add a new garment. Open its Export screen. | Export shows "Original photo". The original file is at most 2048 px on the long edge (1024 for Experimental). | [ ] | |
| V-OR2-D | OR2 | Reinstall the app and restore from Drive. Open a garment added after OR1, then Settings › Google Drive. | That garment has its original again (Export shows "Original photo"). The Drive settings show the originals line. | [ ] | |
| V-OR3-D | OR3 | Add a new garment, open Edit photo, and paint over the removed background with Keep brush. Then open a saved garment that has an original. | The brush brings the background back. "Cut out again" is offered for a saved garment with an original. | [ ] | |
| V-CLN-D | CLN | Open Settings › Storage usage after the first launch. Look at some garments. | Storage usage has dropped. All garments still show photos. | [ ] | |
| V-EX1-D | EX1 | Open an item's detail and tap share. Check the preview, then tap Share. | Export opens and the preview matches. Share opens the system sheet with an image or PDF. | [ ] | |
| V-ZIP-D | ZIP | Settings › Google Drive › Download all my data. Choose where to save, then open the file on a PC. | The file is saved where you chose. It opens on the PC with photos and JSON. | [ ] | |
| V-DUP2-D | DUP2 | (1) Look at the "Very similar" prompt for similar black dresses. If it shows a false pair, tap Not duplicates. (2) Kill the app from recents, reopen Browse. (3) Reinstall and restore. | (1) The false pair is dismissed. (2) and (3): the dismissed group does not come back. | [ ] | |
| V-BAT1-D | BAT1 | Batch-add 10 garments. Watch the ETA, scroll, then tap ✕ and Discard. | The ETA is visible after the first photo and while you scroll. Discard returns to Browse and nothing is saved. | [ ] | |
| V-BAT2-D | BAT2 | Start the pack download and tap Cancel. Start it again and turn Wi-Fi off and on during the download. | Cancel stops the download. After Wi-Fi returns, it continues by itself. | [ ] | |
| V-VIS-D | VIS | In Browse, look at the white top tile, then at 4 columns. Sign out of Drive and look at the banner. | The white top is visible on its tile. At 4 columns only the cloud icon shows. With Drive signed out, only the banner says Sign in. | [ ] | |
| V-SPK-D | SPK | Look at the buttons, the ✦ next to Filters, then apply suggestions. | No shiny buttons. ✦ is a plain icon. Applying suggestions shows one short sparkle. | [ ] | |
| V-FIT-D | FIT | Browse › Filters › Advanced filters › Fit. Set a value. | Fit works and shows a chip. | [ ] | |
| V-SEL-D | SEL | Long-press a photo in Browse and drag down. Delete the selection. | Every photo in between is selected. Delete asks once. | [ ] | |
| V-ANA-D | ANA | Open Analytics from its icon. Tap "Dresses". | Analytics opens. Tapping "Dresses" shows only dresses in Browse. | [ ] | |
| G-ADB1 | K00, K01, ED1, GPU | Gate, coordinator with Manu. No device step in this checklist. | Recorded in R3-DEC-021 (closed defect list) and the GPU numbers. | [ ] | Gate recorded; nothing to do here. |
| G-ADB2 | K03, ED2, PRF | Gate, coordinator with Manu. Run compare.py on the diagnostic set and check Browse janky % and p90 against G-ADB1. | compare.py exits 0. Browse janky % and p90 are lower than G-ADB1 (V-PRF-4). | [ ] | No result recorded in the planning docs at the time of writing. Open. |
| G-CUT | G-ADB2 | Manu: redo at least 10 garments (pink and red shorts, some on hangers, some white-on-white) with Experimental and with Standard. Rate each OK or NOK. | Experimental NOK count is lower than Standard NOK count on at least 10 garments, and no part is lost that Standard kept. Manu then decides the default. | [ ] | No result recorded in the planning docs at the time of writing. Open. |

## Round 3b

| Row | Task | What to do on the phone | Expected | Pass/Fail | Notes |
|---|---|---|---|---|---|
| V-SYNC-AUTH-D | SYNC-AUTH | Add 100+ garments and let them upload. Watch for a red Drive sign-in error. If one appears, open Developer › log and find the "sync ended" line. | No red Drive sign-in error appears. If one does, the "sync ended" line names the HTTP code and reason. Send that line. | [ ] | |
| V-SYNC-BANNER-D | SYNC-BANNER | Sign in with the Google account that has a backup. Tap Combine in the popup and wait until the upload finishes. | Browse shows no Combine/Replace card afterwards. | [ ] | |
| V-SYNC-ETA-D | SYNC-ETA | Combine or upload 20+ garments. Watch "Updating cloud backup". | "About N min/s left" shows after the first photos and does not jump wildly. | [ ] | |
| V-SYNC-SPEED-D | SYNC-SPEED | Upload 100 garments on this build. Note the total time in the Developer log. | Send the total time (before: about 160 s for 114 garments). No sign-in error. No duplicate files in Drive. | [ ] | |
| V-UI-CAL-D | UI-CAL | Open Developer › Duplicate calibration. | A progress indicator shows, then the pairs or an empty state with counts. The 30-pair re-marking is not asked. | [ ] | |
| V-UI-WHITE-D | UI-WHITE | In Browse, look at a white T-shirt. Open its garment detail. | A subtle grey gradient, like in the add flow, not flat beige. Same in the detail. | [ ] | |
| V-EX2-D | EX2 | Export: turn every field off, then switch the Robia frame off and open Preview. Turn one field on. Tap Preview again. | With all fields off, Preview shows the pure photo. With one field on, the frame switch turns on and is disabled. Tapping Preview scrolls the screen down to it. | [ ] | |
| V-BAT3-D | BAT3 | Batch-add 30+ garments and watch the list. Scroll up by hand, then tap Follow. | The list follows the garment being processed. It stops when you scroll by hand. Follow resumes it. | [ ] | |

## Round 3c

| Row | Task | What to do on the phone | Expected | Pass/Fail | Notes |
|---|---|---|---|---|---|
| V-OUT-DROP-D | OUT-DROP | Settings › turn Developer mode on and look at the Developer section. In Browse, type a word in search, then a descriptive phrase such as "relaxed weekend look". Check the duplicates banner. | The Developer section shows Restore sync log, Duplicate calibration and the benchmarks, and no Outfit lab. Search results still appear. The duplicates banner still works. | [ ] | |
| V-UI-SMART-D | UI-SMART | Open a garment photo that still has loose background or hanger pieces. Edit photo › Cutout tab › Remove, smart tool (Brush off). (1) Drag across a loose piece. (2) Tap another loose piece. (3) Tap the garment itself. (4) Undo, then Redo. (5) Switch to Keep and tap. (6) Done. | (1) The dashed trail shows and only that piece disappears. (2) It disappears. (3) The hint appears and nothing changes. (4) Undo brings each piece back and Redo removes it again. (5) A hint says to use Remove or the brush. (6) The garment is saved. If a step fails, say which one: the smart tool is then removed (R3-DEC-043). | [ ] | |
| V-DARK1-D | DARK1 | Settings › Appearance › Theme: the row shows Light. Choose Dark, then kill and reopen the app. Choose Follow system and switch the phone's dark mode on and off from quick settings. In Dark, open a garment › Share › Image and PDF. | The row shows Light and the app looks as before. Dark turns the whole app dark at once, with no restart; status bar icons turn light; Settings stays readable. It stays dark after a restart (a brief light splash is known and accepted). Follow system follows each switch. Exported Image and PDF look the same as in Light (white paper). | [ ] | |
| V-DARK2-D | DARK2 | In Dark, check: Browse at 2 and 4 columns, the filter pill and Filters sheet, search, long-press select and drag-select, the Browse banner. Open a white, a black and a red garment detail. Add and edit a garment (colour picker, tag chips, name field). Batch-add 3 photos while they process. Manage tags. | No unreadable text, no white or black boxes that do not belong, no invisible icons. All three garments are clearly visible on their backdrop. Say if the light photo tiles look wrong on a dark page (the alternative is dark tiles). | [ ] | |
| V-DARK3-D | DARK3 | In Dark, check: Settings (all groups, with Developer mode on), the Google Drive screen in each state you can reach, the Combine/Replace card if it shows, the language picker, Smart suggestions (download card), Cutout settings, Duplicate review and Duplicate calibration, a dialog and a snackbar. | No unreadable text and no mismatched cards. | [ ] | |
| V-DARK4-D | DARK4 | In Dark, open the photo editor (Cutout tab with checkerboard, Remove and Keep chips, brush, Light tab and slider, Undo/Redo, Done). Open Analytics and tap a bar. Open Export and share an image and a PDF. Also open Review colours and Review garments once. | Everything is readable and correct. The exported files are the same as in Light. The review screens are for looking only: REV rebuilds them. | [ ] | |

## Round 3d

| Row | Task | What to do on the phone | Expected | Pass/Fail | Notes |
|---|---|---|---|---|---|
| V-NAV-BACK-D | NAV-BACK | (1) In Browse, long-press a garment and press Back. (2) Type a search and set a filter, press Back once. (3) Open Manage and press Back. (4) In plain Browse, press Back, then press Back again within 2 s. (5) Press Back, wait 3 s, press again. (6) In Add, press Back with unsaved changes, then without them. | (1) Only the selection clears. (2) Search and filter both clear. (3) Browse. (4) A discreet toast "Press back again to exit" shows for about 2 s; a second press inside 2 s closes the app. (5) The toast shows again and the app stays open. (6) With unsaved changes Back asks to discard; without them it goes to Browse. | [ ] | |
| V-ADD-UX-D | ADD-UX | (1) In Add, check the gallery button and the hint under the photo buttons. (2) Pick one photo. (3) Scroll down by hand, then pick another photo. (4) Rotate the phone while it processes. (5) With "remove animations" on, pick a photo. (6) Pick several photos. (7) Edit an existing garment. | (1) The button reads "Gallery · pick several" and the hint line shows. (2) The screen scrolls smoothly until the photo's top edge is at the top of the area under the title, with the small buttons visible below. (3) No jump back. (4) No new scroll. (5) The move is instant. (6) The batch screen opens. (7) The button reads "Gallery" and there is no hint. Also say whether the photo should keep a small top margin. | [ ] | |
| V-PICKER-FIX-D | PICKER-FIX | (1) Add a garment and tap Category. Scroll the list to the bottom and back, slowly and with a fling. (2) Select an option. Reopen and select a suggested option and a non-suggested one. (3) Repeat with Occasion (tick several) and Location. (4) Open a picker from the colour and tag review dialogs and repeat. | The sheet opens fully expanded. "N options" stays in place. The sheet does not move or resize, and nothing jumps. Height is the same for suggested and non-suggested options. | [ ] | |
| V-REV-PROGRESS-D | REV-PROGRESS | Run Review garments (tags) on 100+ garments (Manu had 222). Watch the bar against the "N of M" text. Then do the same for Review colours. | The bar tracks the "N of M" text the whole time. At M−3 it is almost full. It does not wait and then jump to 100 % at the end. | [ ] | |
| V-DUP-FAST-D | DUP-FAST | Open Developer › Duplicate calibration on the large wardrobe (100+ garments). Note the time from tap to list. Open it a second time. | A progress bar with a count shows. Pairs appear in seconds. The list looks like before (same strongest pairs). The second opening is as fast or faster. Send the times. | [ ] | |
| V-DUP-TRIGGER-D | DUP-TRIGGER | Add two garments from the same gallery photo. Wait for Smart suggestions to finish, then open Browse. | The "Possible duplicates found" prompt appears without waiting a day or adding more garments. If it does not appear, send the two cosine values from Developer › Duplicate calibration. | [ ] | |

## Round 3e

| Row | Task | What to do on the phone | Expected | Pass/Fail | Notes |
|---|---|---|---|---|---|
| V-REV-D | REV-DONE | Run the end-to-end scenario in section 3, in light and then dark, and in ES and DE. | Each scenario step passes. Say which steps felt unclear or wrong. | [ ] | |

## FIX-GRAY (extra, no matrix row, #209)

| Row | Task | What to do on the phone | Expected | Pass/Fail | Notes |
|---|---|---|---|---|---|
| FIX-GRAY | FIX-GRAY | Open the item detail of a mid-grey garment and read its colour label. Also look at a very light warm neutral in the review card and in the add summary. | A grey garment reads "Gray" (ES "Gris", DE "Grau"), not "Beige", on the item detail. A very light warm neutral can read "Gray" in the review card and the add summary. That is known and pre-existing swatch naming, not a failure. | [ ] | |

## 3. Round 3e review: end-to-end scenario

Run this once in light and once in dark (Settings › Appearance), then once in Spanish and once in German. Steps marked "V-REV-D" are the matrix row's steps. Steps marked "coordinator addition" have no matrix row.

Setup: use a wardrobe with at least 10 garments that have categories and occasions.

1. **Delete an occasion that has garments** (V-REV-D step 8 setup). Accept the review prompt. The cards show "Category · Colour", with the green "+" chips, "Apply n changes" or "Apply 1 change", "Reject", and "N left" in the header.
2. **Decide cards with each button.** Apply one card. Tap a "+" chip on another card, which turns it into a dashed ghost; the button becomes "Keep as is" and the line "all changes skipped" shows; then tap the chip again to restore it. Reject one card. On a card with a red struck chip, tap "Got it" (V-REV-D step 8: no Reject there). (V-REV-D steps 2, 5, 6, 8)
3. **Undo from the snackbar.** After Apply, tap Undo: the garment returns to the same place with its tags as before (open the garment to check). After Reject, tap Undo: the card is back. (V-REV-D steps 5, 6)
4. **Apply all remaining.** Leave 3 or 4 cards pending. Read the count on "Apply all remaining (N)". Tap it, then tap **Cancel** in the dialog and check that nothing changed (coordinator addition). Tap it again, read that the dialog states how many tags are added and removed on how many garments, and confirm. The snackbar says "N garments updated". Tap Undo once: all of those cards return with their chips. (V-REV-D step 9 plus the coordinator's Cancel and 3–4 card detail)
5. **Finish the cards.** Decide the remaining cards. The completion screen shows the title, "+ N tags added · − N tags removed", "N kept as they were", "Edit changes" and "Done". (V-REV-D step 11)
6. **Edit changes and Review again.** Tap "Edit changes": you return to the list with every decided garment under "Reviewed · N". Open it, tap "Review again" on one garment, and decide it again (Apply). The completion screen returns. (V-REV-D steps 7, 11)
7. **Done.** Tap Done: you go to Manage tags. (V-REV-D step 11)
8. **Back, Leave, Discard.** Start a review, apply at least two garments, then press Back: the dialog asks "Leave the review?". Tap Leave: both garments stay updated and the tag change stays. Start another review with nothing applied and press Back: the dialog asks "Discard changes?". (V-REV-D step 10)
9. **Delete the LAST category and finish with unchosen cards** (coordinator addition, no matrix row). Delete the last category that has garments. Open the review, leave some cards as "Keep as is", and finish. The completion screen counts them under "N kept as they were".
10. **Dark mode.** Repeat steps 1 to 9 with Settings › Appearance set to Dark. Say whether red and green are clear without relying on colour: signs, strike-through and ghost chips. (V-REV-D, last sentence)
11. **Spanish and German** (coordinator addition, no matrix row). Repeat steps 1 to 9 in ES and DE. Check the plural forms of the snackbar and the "Leave the review?" and "Discard changes?" dialogs.
12. **Colour review.** Manage colors → change a colour and open the review. One line under the progress says the colors can be tapped to change the proposal. The rest of the screen is as before. (V-REV-D step 12)

Not covered by any row (device only): the Spanish and German wording in use, step 9's "last category" case, and the dark-mode pass beyond V-REV-D's own steps. CI covers only the pure parts (V-REV-*-1 to -4).

## 4. Watch

Performance and behaviour items to watch. Send numbers if anything looks wrong. These are not pass/fail rows.

- **First duplicate scan (memory and speed).** The first scan after this build re-hashes garments whose stored hash is not provably fresh, 3 in parallel (DUP-FAST). Note how long it takes and whether the app stalls or the memory use jumps. Send the time from V-DUP-FAST-D.
- **Sync banner.** During and after an upload, the Drive banner must match the sync state. The Combine/Replace card must not come back after the upload (V-SYNC-BANNER-D). A normal upload must not show a red Drive sign-in error (V-SYNC-AUTH-D).
- **Large wardrobes.** Repeat the 100+ garment checks: V-SYNC-SPEED-D (100 garments), V-REV-PROGRESS-D (222 garments in Manu's wardrobe), V-DUP-FAST-D. Note any stall, freeze or jump.

## 5. Known limits, not failures

- The "Reviewed · N" link shows while the scan is still running. Known; not a failure.
- German dative wording in the bulk-apply copy has a small nit. Known; not a failure. (Coordinator note; the exact string is not in the planning docs.)
- Small photos in the Robia-frame export can be upscaled (EX2). Known; not a failure. (Coordinator note; not in TASK-EX2.md.)
- DUP-N1: two phones dismissing the same duplicate pair at the same time is not handled. Known; not a failure.
- Deferred and recorded in the decision log: exact-tier edge strength is 1.0, so the strongest-pairs split falls back to id order (R3-DEC-029, R3-DEC-032); calibration marks are lost on rotation (developer-only screen; R3-DEC-032).
- Sync (SYNC-*) deferred items: none recorded in decision-log.md (R3-DEC-040 to R3-DEC-055 checked). Open question for the coordinator.
