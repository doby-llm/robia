# Device checklist — robia-round4 (for Manu)

**Build under test:** main @ `0345639` (all code tasks merged: Collections, SDK 36, Settings links, licences, release pipeline).
**APK:** https://github.com/doby-llm/robia/actions/runs/41329346176/artifacts/13091889740 (artifact `robia-staging-apk`, 82.8 MB, GitHub login needed).
**Signature:** `0b0f81a5a287ab2c…`, identical to the previous build (`bf25366`), so installing over your current app keeps your data. If Android says the signature conflicts, do NOT uninstall: stop and tell me.

Reply with the number and PASS or FAIL (and what you see on FAIL). What you do not mention I will record as "not exercised", not as PASS.

## A. Collections (no cloud)
1. Open Manage tags (Gestionar etiquetas). A new **Colecciones** tab exists after the other tabs. With no collections it shows an icon, "Aún no hay colecciones" and a **Nueva colección** button.
2. Tap **Nueva colección**, type "Viaje", save. The row "Viaje" appears with "0 prendas" and, on the right, a small circular button with a list-plus icon (**Añadir o quitar prendas**) next to the pencil. Other tabs (Categorías, Ocasiones…) have no such button.
3. Create "Capsule" and then try to create another "viaje" (any capitalisation). The duplicate is refused. The list is in alphabetical order (Capsule, Viaje).
4. Pencil on "Capsule": the dialog is titled **Editar colección**, has only a name field (no description, no "suggest occasions" switch). Rename it "Cápsula" and save.
5. Tap the list-plus button on "Viaje". A full-screen grid of all your garments opens: top bar with back arrow, title "Viaje" and "0 en la colección"; no bottom navigation bar; a hint "Toca para añadir o quitar…" and a disabled **Guardar**.
6. Tap 3 garments. Each shows a check badge and a green "+ añadida" mark; the subtitle changes to "0 → 3 en la colección" and the bottom bar shows **+3**, **Deshacer todo** and **Guardar**.
7. Long-press a garment and drag over a row of garments: they all get selected as a block (the same state as the one you started on). Drag back: the ones you leave return to how they were. Lift your finger.
8. Switch the segmented control: **Todas / En la colección / Fuera** shows the right subsets. Open the filters and the search (search only with the smart model installed): they work and your Browse filters are NOT changed afterwards. The Collections filter card is not offered here.
9. Tap **Deshacer todo**: everything returns to the start and Guardar is disabled again.
10. Select 2 garments and press back (arrow or system back): a dialog "¿Guardar los cambios en «Viaje»?" with **Guardar**, **Descartar cambios** (red) and **Seguir editando** stacked. Tap outside it: you stay editing, nothing saved. Back again → **Descartar cambios**: you leave and Manage still shows "0 prendas".
11. Open the screen again, select 3 garments, **Guardar**: the screen closes without a snackbar and Manage shows "Viaje — 3 prendas".
12. Open ± again: the 3 garments are checked and no "+/-" marks show. Untick one, Guardar: the count shows 2 and the garment is still in your wardrobe.
13. Put one garment in both "Viaje" and "Cápsula" (a garment can be in several). Both counts include it.

## B. Editor, detail and filters
14. Edit a garment: after Occasions there is a **Colecciones** row. Tap it: a multi-select sheet titled "Colecciones" lists your collections alphabetically. Pick two, confirm, save the garment. Reopen it: both are still there (also after editing something unrelated such as the name).
15. Open that garment's detail: a **Colecciones** row shows the two names. A garment in no collection shows no such row.
16. Filters (Filtros): a **Colecciones** card appears after Ocasión. Select "Viaje": only its garments show. Select "En ninguna": only garments in no collection show. Combine with an occasion: both conditions apply. The filter chip reads "Colecciones: …".
17. Delete a collection in Manage: a dialog "¿Eliminar «…»?" says the garments stay in your wardrobe; **Eliminar colección** is red; Cancelar / tapping outside change nothing. Confirm once: the collection disappears and its garments are still in Browse. With zero collections, the editor row and the filter card disappear again.

## C. Android 16 and general behaviour
18. The app opens. On Browse, Manage and the garment editor nothing is hidden under the status bar or the navigation bar.
19. On Browse, back exits the app. On a pushed screen (Settings, Manage) back returns to the previous screen and shows the back animation.
20. Add a garment with the camera and another from the gallery.

## D. Settings links
21. Settings › General › **Política de privacidad**: opens the policy page in the browser (text mentions optional model downloads, collections and how to delete your backup yourself).
22. Settings › General › **Licencias de código abierto**: a full-screen list opens, it scrolls, and **Cerrar** closes it.
23. The developer mode (10 taps on the Settings title) still works on this staging build. (It is disabled only in the Play release build.)

## E. Drive (only if you have a second phone with the same build)
24. With Drive connected on both phones, after saving a collection on phone A, force a sync on both. Phone B shows the same collections and memberships. Nothing is proposed as a merge.
25. Settings › Google Drive on a phone with older Robia data: after opening the new build nothing asks you to merge (the first sync after the upgrade must be silent).

## Known limitations (decided, not bugs)
- **Not in this version:** "add selected to collection" from Browse multi-select; creating a collection from the garment editor sheet; collections in export, share text, garment card text or analytics (REQ/R4-D06). Collections never feed the smart suggestions or the semantic search.
- If you rotate the phone on the members screen, the whole app goes back to Browse (this applies to every screen, existing behaviour) and the unsaved selection is lost. Nothing is written.
- Removing a garment from a collection on one phone and adding something on another can bring the removed garment back after an explicit merge (same as occasions today).
- Two phones that create a collection with the same name merge into one (memberships united).
- A phone still on the old app may show one merge proposal once after a new-app phone syncs.
- The inert favourite heart on member tiles does nothing there; the footer "N garments not in this collection" of the mockup is not implemented.
- The privacy page's claim about third-party telemetry (ONNX Runtime, ML Kit, Play services) is still to be verified in Play Console when the Play step happens.
- Licence of the bundled `magic_touch` model is unresolved (shown as such in the list).
- Play upload, signing key and Play Console steps are deferred until you say the visuals are ready.
