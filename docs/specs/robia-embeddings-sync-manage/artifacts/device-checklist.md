# Z01 - Device checklist (Poco F3, Xiaomi M2012K11AG, API 33)

APK: latest `main` staging build (includes K01-K04). Run 38879790577, artifact robia-staging-apk (id 12036519904):
https://github.com/doby-llm/robia/actions/runs/38879790577/artifacts/12036519904
Install it over the current app (same CI signing key). If Android reports a signature conflict, do NOT uninstall: that deletes local data.

Phone charging, battery saver off, other apps closed. Mark each row PASS / FAIL and write what you saw.
ID collisions in verification-matrix.md are resolved here: V-Z05b = sync progress UX (S06), V-Z11b = Manage polish (M02).

## A. Safe to try first (no cloud needed)
- [ ] V-Z11b Manage: Colors tab uses the same list format as Season/Occasions (no boxed card); scroll to the very bottom of every tab: no blank space after the last item; no "Search tags" box; no height jump when switching tabs. Result:
- [ ] V-Z01 Manage scroll for ~20 s in a tab: smooth, no visible stutter (optional: `adb shell dumpsys gfxinfo com.gusanitolabs.robia`, janky frames <= 5 %). Result:
- [ ] V-Z02 Add 5 new photos: suggestions appear within about 2 s after the colours (developer diagnostics show duration). Result:
- [ ] V-Z05 Photo of a dress: "Dresses" is marked. Photo of an accessory: no occasion marked. Result:
- [ ] V-Z06 After the model download, the Browse banner counts down to 0 and disappears. Result:
- [ ] V-Z07 Manage: add category "Polos" with a description: the review lists only affected garments; Accept applies them; Discard keeps the tag. Result:
- [ ] V-Z08 Browse search "vestido rojo" with no filters: red dresses first. With a filter active: only filtered garments. Result:
- [ ] V-Z11 Item detail > "See similar garments": Browse shows chip "Similar to ...", the garment itself is absent, most similar first; clearing the chip restores Browse. Result:
- [ ] E10 (developer mode only) Item detail diagnostics card shows; "Recompute embedding" works; card is hidden with developer mode off. Result:

## B. Cloud (Google Drive)
- [ ] V-Z03 Edit a garment offline, close the app, reconnect: it shows Synced on next open, no error shown. Result:
- [ ] V-Z04 Fresh-install restore: all photos present, time < 47 s (target <= 20 s); restore twice: names unchanged. Result:
- [ ] V-Z05b Slow Wi-Fi restore: remaining-time line never disappears and changes at most every 5 s; the photo download bar moves; the upload stage shows "Uploading photos X of N"; nothing shifts in the layout. Result:
- [ ] V-Z12 Same restore: "Updating cloud backup" finishes by itself (no need to kill the app), much shorter than the previous ~3 minutes; step text matches real work. Result and duration:
- [ ] V-Z09 Revoke Drive access in your Google account: a "Sign in again" card appears and its button works. Result:
- [ ] Conflict (S04/S05, no matrix row): with Drive data from another phone, decline the merge: a conflict dialog offers "Keep this phone" and "Use backup"; "Use backup" asks for confirmation before replacing local data. Result (test with data you can afford to lose):

## C. Screens vs approved mockups
- [ ] V-Z10 Sync attention cards, merge/conflict dialogs, Manage and Review screens match the approved mockups (side-by-side screenshots). Result:

## D. Feedback fixes (email of 2026-10-15)
- [ ] V-Z13 (F01) Browse: search "dress" shows "Searching..." then "Sorted by similarity", grid jumps to the top and the most similar garments come first. Item detail > similar garments: the chip has a clothing icon (no empty quotes) and a similarity caption. Save a new item: you land on Browse with the new item first, search/filters cleared (no "No garments yet" screen). Result:
- [ ] V-Z14 (F02) Edit the default tag "Dresses": the Description box shows its default text (English); saving without changes does not mark it as modified; no big blank gap above "Suggest occasions for this category". After finishing or discarding any review you are back in Manage on the same tab. Result:
- [ ] V-Z15 (F03a/F03b) Colours: delete Yellow: a prompt offers the review; "Not now" keeps it deleted (also from garments that had it); accept: scan with progress, only real changes listed (no "Black, Brown" -> "Black, Brown"), "Accept all (N)" saves. Tags: add/edit/delete a category and delete the occasion "Dressed-up": prompt first, scan screen with progress, garments appear as found, "Accept all (N)", "From -> To" never breaks onto a second line, progress bar moves smoothly (the dot at the end of the bar may remain). Result:

## E. Feedback 2 fixes (Telegram of 2026-10-15)
- [ ] V-Z16 (G01) Browse search: type "dress": "Searching..." ends within ~5 s; results reorder (most similar first) or the notice "Smart search unavailable, showing name matches" appears; never spins for minutes. The search field shows the sparkle (AI) icon. Result:
- [ ] V-Z17 (G02) Review garments: tap "To" pill: title reads "Category"; the list scrolls, the last item (Shoes) is fully visible, every row has text (no empty bullet); lists/pickers/Manage/filters are alphabetical (None/Not set first); scan progress advances garment by garment; header count "N garments had it" is the real affected count (not 35); From and To are two stacked pills joined by a down arrow, To keeps the dropdown triangle; tag editor: the gap above "Suggest occasions for this category" is gone or clearly smaller. Result:
- [ ] V-Z18 (G03/G04) Browse shows a cloud icon (done / pulsing upload "Syncing..." / error), not a bare spinner. Edit/create/delete tags many times: the icon returns to done; Settings > Data sync lines never contradict each other. Developer mode: last-cycle card shows start/end/result/pending/error. If it spins for a long time, screenshot the developer card. Result:

## F. Feedback 3 fixes (Telegram of 2026-10-15, second batch)
- [ ] V-Z19 (H03) Tags: you cannot create a second 'Dresses' (also 'Vestidos' in Spanish) in any category/season/occasion; no duplicates appear after restart or 'Restore defaults'; a tag you deleted does not come back on restart. Tag editor: the gap above 'Suggest occasions for this category' is gone (if it is still there, send a screenshot). Result:
- [ ] V-Z20 (H04) Colours: edit Black to Grey; the review shows e.g. a (Black, Blue) trousers as (Blue, empty) or (Blue, other colour), never (Blue, Blue). Result:
- [ ] V-Z21 (H05) Reviews (tags and colours): accepting a card makes it fade away discreetly; 'Accept all (N)' counts accepted + remaining; category picker shows a bottom fade and 'N options' while it scrolls; back arrow (top left) asks 'Discard changes? This undoes the action you just made'; Discard undoes the change (try: delete a tag with garments, discard, the tag and its garments are back; add a tag, discard, it is gone) and returns to Manage. Result:
- [ ] V-Z22 (H06) Delete the embeddings model in Settings: the search bar disappears and only a sparkle icon remains right of Filters; tapping it explains Smart suggestions and offers download; after the download the search bar appears (with a gentle animation). Result:
- [ ] V-Z23 (H01/H02) Cloud icon shows ONLY while syncing ('Syncing...', no blinking between back-to-back cycles) or for attention/error; nothing when idle. Leave the app open 2 min after editing tags: no endless 'Syncing...' loop. If it loops, paste the sync log (it now has a reason= per cycle) and screenshot the developer card. Result:

## G. Feedback 4 fixes (J01-J04), after Manu's A-F results
- [ ] V-Z24 (J01/J02) Settings: tag/colour deletions end with 'All cloud changes synced', pending 0 and no icon; the old '5 pending' drains on the first cycle. Developer card shows a readable Result (e.g. Success), not 'c3'. Result:
- [ ] V-Z25 (J02) Airplane mode with a pending edit: Settings, Browse icon and developer card say 'Waiting for connection' (calm, no endless 'Syncing'); the garment sheet says 'Waiting for connection'; turning data on drains it. Result:
- [ ] V-Z26 (J03) Delete 'Dresses', Restore defaults: the review now proposes the dresses (if not, paste the log line of the scan). Edit a tag description: the review is offered. Accept the last card: the screen closes by itself. Tap outside the 'Review garments / Not now' box: the delete is undone (tag and its garments back); 'Not now' keeps it. Editor gap slightly bigger. Result:
- [ ] V-Z27 (J04) First launch (clear app data or fresh install): one prompt at a time, cloud setup first, Smart suggestions only after. Result:
- [ ] V-Z28 (J04) Merge dialog: buttons stacked, no overlap; Combine closes it and the Browse card shows 'Combining...' then done/failed (fails by itself after 90 s); Not now/outside tap keeps the proposal as a card; 'Keep this phone' asks to confirm overwrite. Result:
- [ ] V-Z29 (J04) Browse and Advanced filters: no blank space at the end of the scroll. Result:

## H. Feedback 5 fixes (K01-K04), after Manu's block G results
- [ ] V-Z30 (K01) Reinstall (or clear data) with an EMPTY phone, connect Drive: all garments restored and NO 'Combine with your Drive backup?' dialog, also a few seconds later. No second 'Syncing...' beyond the restore. Result:
- [ ] V-Z31 (K01) Real clash still asks: phone with different clothes + backup with other clothes -> the dialog appears. Edit during a restore does not get lost. Result:
- [ ] V-Z32 (K02) Smart suggestions prompt never appears while the Drive download/restore runs or on another screen: only on Browse at rest (about 3 s after). Result:
- [ ] V-Z33 (K02) Airplane mode with a pending edit: 'Waiting for connection' appears quickly; the developer card shows it neutral (not red UnknownHostException). Tap the cloud icon in Browse: small popup explains the state (auto-closes, 'Open Data sync' when it needs attention). Result:
- [ ] V-Z34 (K03) Review garments (tags and colours): each card has Discard next to Accept; a discarded card is NOT applied (add tag: garment unchanged; delete tag: garment ends without it); Accept all counts only open cards; after the last card the screen closes by itself. Result:
- [ ] V-Z35 (K03) With 0 garments in the app, no 'Review garments' prompt appears for any tag/colour change. Result:
- [ ] V-Z36 (K03) Manage > Default (restore defaults): the 'Review garments / Not now' prompt appears immediately (no 2-3 s wait). Result:
- [ ] V-Z37 (K04) Review cards: photo area has the same colour gradient as Browse (colour review: uses the edited primary colour). Browse looks unchanged. Result:
- [ ] V-Z38 (K04) Tag description editor: counter '52/200' no longer touches the border; the helper text wraps instead of ending in '...'; no new blank gap. Result:

## Return
Paste the results (a copy of this list with PASS/FAIL and notes is enough). Z01 is then closed by recording them in the tracking issue.
