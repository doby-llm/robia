# Device checklist — robia-round5 (G-CODE, for Manu)

**Build under test:** main @ `1f91d79` (TH1 palette model + Tobacco & Bordeaux tokens, TH2 Settings row, REL1 version 1.0.0 + capture build).
**APK:** staging build `app-staging.apk` (82.9 MB), uploaded to Manu's Drive (folder Robia, file `apk-1f91d79-app-staging.apk`).
**Signature:** `0b0f81a5a287ab2c…`, identical to the previous staging build (`bf25366`): installing over your current app keeps your data. If Android says the signature conflicts, do NOT uninstall: stop and tell me.

Reply with the number and PASS or FAIL (and what you see on FAIL). Rule: anything you do not mention counts as PASS.

1. (V-TH-D1) Install over the current app and open it. The app starts in the new look **Tabaco y burdeos** (warm brown and bordeaux), without a flash of the old green/steel colours at launch. Your garments are all still there.
2. (V-TH-D2) Ajustes › Apariencia: below **Tema** there is a new row **Paleta de colores** showing "Tabaco y burdeos". Tap it: a dialog with two options in alphabetical order, **Tabaco y burdeos** and **Tierra y acero**, each with three small colour dots. Tap "Tierra y acero": the dialog closes at once, the whole app switches to the old look, no snackbar. Close and reopen the app: the choice is kept. Switch back to Tabaco y burdeos.
3. (V-TH-D3) With each palette and each theme (Claro, Oscuro, Seguir al sistema) look at: Browse, a garment's detail, Añadir por lotes (the pill **Requiere revisión** must be readable), a tag review screen (the + / − chips), and Ajustes. Text readable, no white-on-white or dark-on-dark, nothing unthemed.
4. (V-TH-D4) In Añadir/Editar prenda, the empty photo placeholder, and in the duplicate review the icon, are readable in both palettes (light and dark).
5. (V-TH-D5) Share/export a garment card: it looks the same as before in both palettes.

## Known limitations
- Dark look of the dots in the palette dialog and the divider under "Tema" was not seen by anyone yet (item 2/3).
- 15 old fixed-colour literals in `ui/` predate this round and were not touched.
- A launch can wait up to 250 ms for the stored palette before the first frame (by design).
