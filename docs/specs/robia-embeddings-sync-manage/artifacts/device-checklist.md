# Z01 - Device checklist (Poco F3, Xiaomi M2012K11AG, API 33)

APK: latest `main` staging build. Run 38710418285, artifact robia-staging-apk (id 11960139373):
https://github.com/doby-llm/robia/actions/runs/38710418285/artifacts/11960139373
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

## Return
Paste the results (a copy of this list with PASS/FAIL and notes is enough). Z01 is then closed by recording them in the tracking issue.
