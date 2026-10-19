**If the app closes, tell the coordinator the time. Do NOT uninstall** (uninstalling deletes the local wardrobe).

# Z02 — Device checklist (robia-ux-rework round)

**APK:** staging build of `main` at a6b74c6. CI run https://github.com/doby-llm/robia/actions/runs/39247684143, artifact `robia-staging-apk` (id 12184451362): https://github.com/doby-llm/robia/actions/runs/39247684143/artifacts/12184451362

Install it over the current app. If Android reports a signature conflict, do NOT uninstall: that deletes local data. Tell the coordinator instead.

Phone charging, battery saver off, other apps closed. Mark each row PASS or FAIL and write what you saw in Notes.

Labels below are the English strings from `app/src/main/res/values/strings.xml`. If the phone is in Spanish or German, read the same string in `values-es` / `values-de`.

Unlock Developer first: tap the **Settings** tab 7 times, then switch **Developer Mode** on (needed for F-3 and the "Use precise cutout" check in F-4).

---

## A. No cloud needed

- [ ] **V-Z2-01** Browse cards. Steps: open Browse with at least 6 garments, some with long tag names. Expected: all cards in a row have the same height; no gap at the photo corners; no "Synced" text on any card; long tags end with "…". PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-02** Browse badges. Steps: look at the cards with Drive off. With Drive connected: turn Airplane mode on, edit one garment, turn Airplane mode off. Expected: synced cards show no badge; the edited card shows "Waiting" while offline, then "Uploading", then no badge; an upload that fails shows "Needs attention". PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-03** Filters reset. Steps: Browse → "Filters" → pick a Category and a Season → go back. Tap "Reset" next to Filters; then add two filters again and remove one with its ✕ ("Remove filter"). Expected: "Reset" clears all filters; the ✕ removes only that chip. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-04** Filters screen. Steps: Browse → "Filters". Check the grouped cards, the "+N more" line on a card with more tags than shown, and the colour grid ("Color Palette"). Select "Summer" and "Winter". Expected: grouped cards; "+N more" where needed; the button "Show N garments" has the right count; Summer + Winter returns garments tagged with either season. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-05** Manage swipe. Steps: Manage → swipe left and right across the tab content; then scroll one tab up and down. Expected: swipe changes tab; vertical scroll stays smooth. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-06** Settings. Steps: (a) Settings tab: groups "Smart features", "Backup", "General", "About". (b) "Language" → "Español", then back to "English" (leave it on English). (c) "Browse layout" → "Photos only", then "Photos with details", then back to "Photos only". (d) Tap the Settings tab 7 times. Expected: grouped rows; language changes the app language; the Browse layout setting changes the Browse grid as described; after the 7th tap "Developer mode unlocked" appears. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-08** Add garment form. Steps: Add clothing; tap each field row (opens a bottom sheet); fill a garment with a photo; "Save item"; reopen it. Expected: rows and bottom sheets; "✦ Suggested by Robia. Tap to change." on suggested values; after saving and reopening, every value is kept. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-09** Garment detail. Steps: Browse → tap a garment. Expected: details are list rows, not boxes; "Edit" opens the form; the photo card has "Edit photo". PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-11** Review cards. Steps: Manage → Category → open a tag and change its description → Save → "Review garments" (needs smart suggestions downloaded) → on one card tap "Accept", on another tap "Discard". Expected: the accepted card slides out; the discarded card leaves with the same discreet motion; the AI buttons shimmer subtly; no vibration anywhere. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-12** Share PDF. Steps: Browse → garment → share icon ("Share item") → "Share PDF" → open the PDF. Expected: one A4 page for a normal garment; matches `mockups/pdf.html`; no Location row, as before. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-13** Socks and Underwear. Steps: Manage → "Category" tab → scroll the list. Expected: "Socks" and "Underwear" are listed (Spanish: "Calcetines", "Ropa interior"), in alphabetical order. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-19** Browse default. Steps: Settings → "Browse layout" → "Photos only" → back to Browse. Expected: 2 columns of photos only; all tiles the same size; no text; the heart (favourite) and cloud badge still visible where they apply. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-20** Review completion. Steps: finish one tag review ("Accept all (N)", then "Save N accepted") and one colour review (finish with its save button). Expected: a short completion screen with sparks and "Tags updated on N garments" / "Colours updated on N garments"; it disappears by itself after about 1.6 s or on tap. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-23** Browse pinch. Steps: Browse; pinch out 2 → 3 → 4 columns, then pinch back. Scroll and long-press a card between the steps. Expected: tiles glide and the position is kept; scrolling and long-press still work; with "Photos with details" the maximum is 3 columns. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-24** Gray colour. Steps: Add clothing → photo of a grey garment → "Save item"; then a denim garment → "Save item". Manage → "Colors" tab. Expected: the grey garment is detected as "Gray"; "Gray" is listed under Manage › Colors; old garments keep their colours until a colour review. (The matrix said "Settings › colours"; the colour list is under Manage › Colors.) PASS / FAIL: ____ Notes: ____

---

## B. Photos, segmentation and cutout settings

- [ ] **V-Z2-10** Processing visuals. Steps: Add clothing → take or pick a photo; watch the stages ("Removing background", "Cropping picture", "Extracting color", "Preparing suggestions"). Expected: sweep → masked shimmer → pop; smooth, not excessive. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-14** Socks photo. Steps: Add clothing → photo of socks → check the Category field. Expected: suggested category "Socks" (or none), not "Trousers". PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-15** 10 photos in a row. Steps: Add clothing → save 10 single garments one after another. Expected: no "Photo cleanup is still downloading…" that stays; if it appears, it recovers alone or "Retry cleanup" works. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-16** Edges. Steps: compare 3 cutouts at card size in Browse and in the garment detail. Expected: smooth edges, no stair steps. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-17** Precise cutout in Settings. Steps: Settings → "Cutout" → top block "Precise cutout": "Download (≈ 91 MB)", wait for "Ready · 91 MB", then "Delete cutout pack" → "Delete". Expected: download, switch and delete all work (the switch state is covered by F-4). Fill the G-SEG-2 table below. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-18** Photo editor. Steps: Browse → garment → "Edit" → "Edit photo" → tab "Cutout". Tap "Remove", then tap or swipe over a leftover hanger. Tap "Brush" and paint back a small missing piece. Tap "Undo". Tab "Light": move "Brightness". Tap "Done". Expected: the hanger is gone; the brush restores the piece; Undo restores; the Light brightness works; Done saves and the colours update. If the tap timing (median ≤ 400 ms) is not visible on screen, write that in Notes. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-28** Cutout options. Steps: Settings → "Cutout". Change "Cutout method" (Automatic / Standard), "Remove hangers", "Hanger detection" (Normal / High), "Edges" (Soft / Sharp), "Batch speed" (Balanced / Memory saver). Take a new photo after each change. Expected: each option changes the next new photo as its hint describes. With "Standard", "Remove hangers" is off and disabled, and "Hanger detection" is disabled. With the pack not downloaded, "Remove hangers" shows "Needs precise cutout". PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-29** Hanger detection High. Steps: Settings → "Cutout" → "Hanger detection" → "High" (hint: "Finds more clips and wooden hangers. Check the result."). Photograph 5 garments on wooden or clip hangers. Expected: the hanger is removed; no part of the garment is lost. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-30** Redo cutout. Steps: Add clothing → new photo → "Edit photo" → tab "Cutout" → "Redo cutout" → "Standard", then "Precise", then "Precise without hanger removal" (check the result each time). Tap "Undo". Close with ✕ ("Cancel"). Then open a saved garment → "Edit photo". Expected: each option replaces the cutout; Undo returns the previous one; ✕ saves nothing (the garment is unchanged when reopened); "Redo cutout" is hidden on saved garments. PASS / FAIL: ____ Notes: ____

### G-SEG-2 table (≥ 10 garments; at least 3 on hangers, 2 white-on-white, socks if you have them)

Method: take each photo as a new garment; in the editor use "Redo cutout" → "Precise" (pack ON) and → "Standard" (ML Kit, pack OFF) on the same photo; rate each cutout OK or NOK; close with ✕ so nothing is saved.

Pass rule (SEG-DEC-006): the pack's NOK count is lower than the ML Kit NOK count, and no garment part is lost that ML Kit kept.

| # | Garment (type, on hanger?, white-on-white?) | Precise (pack ON) OK / NOK | Standard (ML Kit) OK / NOK | Part lost? (Y/N) | Notes |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |
| 4 | | | | | |
| 5 | | | | | |
| 6 | | | | | |
| 7 | | | | | |
| 8 | | | | | |
| 9 | | | | | |
| 10 | | | | | |

Precise NOK count: ____ · Standard NOK count: ____ · Pass (pack NOK < ML Kit NOK and no part lost): YES / NO

---

## C. Mockup comparison (no matrix row)

Open the file in `docs/specs/robia-ux-rework/mockups/` in a browser next to the same app screen. The mockup captions are Spanish, the UI copy is English. Expected for each: same order of blocks, same proportions and the same labels as in the app. Mark PASS / FAIL.

- [ ] **C-1** Browse ↔ `browse.html`. PASS / FAIL: ____
- [ ] **C-2** Filters ↔ `filters.html`. PASS / FAIL: ____
- [ ] **C-3** Settings ↔ `settings.html`. PASS / FAIL: ____
- [ ] **C-4** Add clothing and garment detail ↔ `garment-form.html`. PASS / FAIL: ____
- [ ] **C-5** Processing ↔ `processing.html`. PASS / FAIL: ____
- [ ] **C-6** Batch ↔ `batch.html`. PASS / FAIL: ____
- [ ] **C-7** Review motion ↔ `motion.html`. PASS / FAIL: ____
- [ ] **C-8** Share PDF ↔ `pdf.html`. PASS / FAIL: ____
- [ ] **C-9** Photo editor ↔ `photo-editor.html`. PASS / FAIL: ____

---

## D. Batch

- [ ] **V-Z2-21** Batch of 10 photos. Steps: Add clothing → "Gallery" → select 10 photos → the batch opens ("Add batch"). Start the stopwatch when the batch starts, stop when the last garment shows "Ready". Run it twice: (1) Settings → "Cutout" → "Cutout method" = "Standard" (pack OFF); (2) "Automatic" with the pack ready (pack ON). Expected: garments become "Ready" in screen order; no crash; record the time. The Logcat timing lines (`RobiaBatch`) need a USB cable, so they are not part of this row. Time OFF: ____ s · Time ON: ____ s. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-22** Batch screen. Steps: during the batch above, look at the list, the header and the bottom bar. Expected: one row per garment with category and occasions; no status text repeated under a row; one header with "N of M processed" and the time left ("About N s left", or "Almost done"); the bottom bar has only "Save N items" and a short attention line when something needs review. PASS / FAIL: ____ Notes: ____

---

## E. Google Drive (cloud; use the real wardrobe)

- [ ] **V-Z2-07** Google Drive screen. Steps: Settings → "Google Drive". Compare with the actions you used before. Expected: every action you used before is still there, e.g. "Backup again", "Delete backup from Google Drive", "Check photos". "Sign in" and "Disconnect" appear on the attention card when needed. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-25** Space used. Steps: Settings → "Google Drive" → "Space used in Google Drive" shows "… · N photos". Tap "Refresh". Compare the size with Google Drive → Manage apps → Robia. Expected: the value shows size and photo count; "Refresh" updates it; the value is close to the Google Drive app's number. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-26** Check photos. Steps: Settings → "Google Drive" → "Check photos". Expected: either "All N photos are safe in Google Drive" or "N photos need attention" with a list ("Restore photo", "Back up now" where offered). Nothing is deleted: the photo count in Google Drive is the same before and after. PASS / FAIL: ____ Notes: ____
- [ ] **V-Z2-27** Second Google account. Steps: Settings → "Google Drive" → sign in with a second Google account. Expected: "Different Google account" attention ("Nothing will be uploaded until you choose."); no upload happens. Do NOT tap "Use this account" (it binds the wardrobe to the second account). Tell the coordinator the result. PASS / FAIL: ____ Notes: ____

---

## F. Follow-up rows (no matrix row; from the coordinator's follow-ups)

Order: F-4 first (the benchmark in F-3 needs the pack ready), then F-3, F-1, F-2, F-5.

- [ ] **F-1** Real 12 MP camera photo with Precise ON. Steps: check Settings → "Cutout" → the row value reads "On" (pack ready, method "Automatic"). Add clothing → "Tap to open camera" → take a photo of one garment on a plain background with the phone's default camera setting. Start the stopwatch at the shutter and stop when the status reads "Photo cleaned up and colors extracted." Expected: no crash; the cutout appears. Time: ____ s. PASS / FAIL: ____ Notes: ____
- [ ] **F-2** Smart taps on a saved cutout (regression check for the bitmap ownership fix). Steps: Browse → a saved garment → "Edit" → "Edit photo" → tab "Cutout". Tap the garment 5 times (leave "Keep" or "Remove" as it is). Then close with ✕ ("Cancel"). Expected: no crash; each tap updates the preview. PASS / FAIL: ____ Notes: ____
- [ ] **F-3** Cutout benchmark, once. Steps: Settings → "Developer Mode" on → "Run cutout benchmark" → "Run". Wait until "Running…" ends. Expected: the result text contains `inputs_ok=8` and `total_ms median=` below 4000 ms. Tap "Copy" and send the text to the coordinator. PASS / FAIL: ____ inputs_ok: ____ median ms: ____ Notes: ____
- [ ] **F-4** Pack download over Wi-Fi, delete, re-download (switch state). Wi-Fi on. Steps: (1) Settings → "Cutout". If "Ready · 91 MB" shows, tap "Delete cutout pack" → "Delete" first. (2) Tap "Download (≈ 91 MB)" → "Downloading…" → "Ready · 91 MB". Settings › "Cutout" value reads "On". (3) Tap "Delete cutout pack" → "Delete": "Download (≈ 91 MB)" is back and the Settings value reads "Off". (4) Tap "Download (≈ 91 MB)" again. Expected: after the re-download, the row reads "On" again and the Developer "Use precise cutout" switch is still on (it keeps your choice). PASS / FAIL: ____ Notes: ____
- [ ] **F-5** Losing Wi-Fi mid-download. Steps: with the pack not ready, tap "Download (≈ 91 MB)". When some MB are shown, turn Wi-Fi off AND mobile data off (Airplane mode works). Expected: the download stops; the screen shows "Couldn’t download. Check your connection." and a "Retry" button; nothing crashes. Then turn the connection back on and tap "Retry" (the button label is "Retry", not "Download"). Note in Notes whether the progress resumes from where it stopped or starts at 0. PASS / FAIL: ____ Notes: ____

---

Send the coordinator: each ID with PASS / FAIL and Notes, the G-SEG-2 table, the batch times, the benchmark text, and the time of any app close.
