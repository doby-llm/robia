# Shot list — CAP session · CR1

Authoritative list of shots for `briefs/CAP-SHOTLIST.md` (V-CAP-1: every id below × {en, es, de} in `manifest.json`).
`CAP-SHOTLIST.md` names one shot id, **F** (airplane-mode proof, with its fallback); it is kept. All other ids are new
and use single letters. Labels are the app's real strings per locale (`app/src/main/res/values*/strings.xml`).

## Conventions
- Build `com.gusanitolabs.robia.capture`, palette **Tobacco & Bordeaux**, theme **Light**, Settings › Browse layout
  **Photos only** ("Solo fotos" / "Nur Fotos"), except shot K (Dark). Demo mode status bar (10:00, full battery, full Wi-Fi, no notifications).
- One `screenrecord` file per shot: `<locale>/clips/<id>.mp4`; stills `<locale>/stills/<id>.png`. Shots marked
  "60 s" need `--time-limit 60` instead of the default 30.
- Taps via `adb shell input tap/swipe`, coordinates measured once per screen with `uiautomator dump`, identical in
  every locale. Text via `adb shell input text`, **one call per word** (`%s` for the space) with ~150 ms between
  calls, so the field fills word by word. `input text` cannot type non-ASCII characters: every scripted text below is
  ASCII on purpose.
- **Never record:** the system photo picker (it shows Manu's own photos), the Android share sheet, the notification
  shade, the keyboard's suggestion strip (start/trim around it; the videos punch in above the keyboard).
- **Tap log:** for every shot, the coordinator writes the tap/typing times (s from clip start) and x/y into
  `manifest.json` (`taps: [{t, x, y}]`), so the videos can draw their own touch rings.
- Each shot ends in the same wardrobe state it started in (discard / undo / delete steps below), so EN, ES and DE show
  the same wardrobe.

## Demo wardrobe needs (input to CAP step 2)
20–24 garments, ≥ 7 categories, varied colours (include a bordeaux/red, navy, beige, brown, black, white, green),
no logos/faces/brands. So that the queries below return believable results it must contain:
- Q1 "warm, wedding": ≥ 3 of wool coat, fine-knit dress or cardigan, blazer, smart trousers, dress shoes.
- Q2 "rainy day": ≥ 2 of rain jacket / trench coat, boots, dark trousers.
- Q3 "beach" (spare): ≥ 2 of linen shirt, shorts, sandals, summer dress.
Extra files in `/sdcard/Pictures/RobiaDemo/` (not loaded into the wardrobe):
- `A_hero.jpg`: one garment **not** in the wardrobe, strong single colour (e.g. a mustard knit sweater), photographed
  flat on a visible textured surface (wooden floor) so the cutout is obvious. The same file is the "Before" card
  (A-raw) of the horizontal video.
- `batch/B01..B06.jpg`: six garments not in the wardrobe.
- `dup_copy.jpg`: a byte-identical copy of one wardrobe photo (gives the "Same photo" tier).
Collections (create once, rename before each locale pass via Edit collection; ASCII names):
| EN | ES | DE |
|---|---|---|
| Weekend trip (5 garments) | Escapada (5) | Wochenendtrip (5) |
| Work week (6) | Oficina (6) | Arbeitswoche (6) |
| Winter (4) | Invierno (4) | Winter (4) |

## Search queries per locale (ASCII, natural)
| Id | EN | ES | DE | Used by |
|---|---|---|---|---|
| Q1 | `something warm for a wedding` | `algo abrigado para una boda` | `etwas Warmes zur Hochzeit` | D, F-gate, P1, V06, H03 |
| Q2 | `something for a rainy day` | `algo para cuando llueve` | `Outfit bei Regenwetter` | F (clip), V07, H04 |
| Q3 (spare) | `something light for the beach` | `algo ligero para la playa` | `leichte Kleidung zum Strand` | replaces Q1 or Q2 if its top results look wrong |
Acceptance for a query: the line "Sorted by similarity · N garments" / "Ordenado por parecido · N prendas" /
"Nach Ähnlichkeit sortiert · N Kleidungsstücke" shows and the first 3 results fit the query to a stranger's eye. If
"Smart search unavailable, showing name matches." appears, the model is not ready: stop and fix before recording.

## Shots
Order within each locale pass: M, B, D, F, L, A, C, G, H, J, I, E, K (K last because it switches the theme).

| Id | Screen | Scripted actions | Record | Still | Used in | Locale-specific input |
|---|---|---|---|---|---|---|
| **A** | Add clothing | Start on Browse. Tap bottom-nav "Add clothing" / "Añadir prenda" / "Kleidungsstück hinzufügen". Tap "Gallery · pick several" / "Galería · elige varias" / "Galerie · mehrere" → **pause recording** → Manu (or coordinator) picks `A_hero.jpg` in the system picker → **resume** (second file `A2.mp4` is fine; the creative lane joins them). Do nothing while the stages run ("Removing background", "Cropping picture", "Extracting color", "Preparing suggestions") until the cutout and the "✦ Suggested by Robia" fields settle; hold 3 s; slow swipe up to show Category / Occasions / colours; hold 2 s. **Cleanup:** back → "Discard changes?" / "¿Descartar cambios?" / "Änderungen verwerfen?" → discard (nothing saved). | 15–25 s | yes (settled: cutout + suggested fields) | V02–V04, H02, P2 | — |
| **A-raw** | (no capture) | `A_hero.jpg` itself, copied to `captures/<locale>/stills/A-raw.jpg` (same file for every locale). | — | yes | H02 "Before" | — |
| **B** | Browse | Browse at top, search empty. Wait 1 s; `input swipe 540 1800 540 700 500` ×3 (fast); wait 1 s; one slow swipe (`… 1200`) ; fling back to top. | 8 s | yes (top of grid, settled) | V01, H01 | — |
| **C** | Add batch | Tap "Add clothing" → "Gallery · pick several" → **pause** → pick `batch/B01..B06` in the picker → **resume** on the "Add batch" / "Añadir lote" / "Batch hinzufügen" screen. Record until all six tiles are "Ready" and "Save 6 items" / "Guardar 6 prendas" / "6 Teile speichern" is enabled. **Cleanup:** back → "Discard this batch?" / "¿Descartar este lote?" / "Diesen Batch verwerfen?" → discard. | 60 s | yes (mid-way: some tiles Processing, some Ready; and the final all-Ready frame) | V05, H05, P3 | — |
| **D** | Browse + search | Browse at top. Tap the field "Search your wardrobe" / "Buscar en tu armario" / "Kleiderschrank durchsuchen". Type Q1 word by word. Hide the keyboard (`input keyevent 4`). Hold 3 s on the results; slow swipe up once; hold 1 s. **Cleanup:** tap "Clear search" / "Borrar búsqueda" / "Suche löschen". | 12 s | yes (results with "Sorted by similarity" line, keyboard hidden) | V06, H03, P1 | Q1 |
| **E** | Possible duplicates | **Setup (not recorded):** add `dup_copy.jpg` with the single-add flow and "Save item"; wait until Browse shows "Possible duplicates found · 1 group to review" / "Posibles duplicados encontrados · 1 grupo por revisar" / "Mögliche Duplikate gefunden · 1 Gruppe zu prüfen". **Record:** Browse with the banner (1 s) → tap "Review" / "Revisar" / "Prüfen" → "Possible duplicates" screen, group "Same photo" (1.5 s) → on the original tap "Keep" / "Conservar" / "Behalten", on the copy "Delete" / "Borrar" / "Löschen" → confirm "Delete the selected garments?" / "¿Borrar las prendas seleccionadas?" / "Ausgewählte Kleidungsstücke löschen?" → "No possible duplicates left." / "No quedan posibles duplicados." / "Keine möglichen Duplikate mehr." (1 s). This is also the cleanup. | 12 s | yes (group shown, before any tap) | V09a, H05, P4 | — |
| **F** | Browse + search, **airplane mode** | **Gate (CAP-SHOTLIST §Airplane):** `adb shell cmd connectivity airplane-mode enable`; check `settings get global airplane_mode_on` = 1 (log it in the manifest); demo-mode broadcast `network -e airplane show` so the plane icon is visible; run Q1 once (not recorded or recorded as `F-gate.mp4`). **Pass** = "Sorted by similarity" line + sensible top results, and no "Smart search unavailable" line. **Record (only if passed):** Browse at top (2 s still on the status bar plane icon) → tap search field → type Q2 word by word → hide keyboard → hold 3 s. **Cleanup:** Clear search; `airplane-mode disable`. Tell the coordinator pass/fail per locale. | 12 s | yes (results in airplane mode, plane icon visible) | V07–V08, H04 | Q1 (gate), Q2 (clip) |
| **F-FB** | Fallback set for F | If the gate fails in any locale, the fallback is used in **all** locales (one story per video): record **M** (below) — it already is in every pass — and reuse the tail of **D**. No airplane shot, no offline claim anywhere (videos, screenshots, listing). | — | — | V07-FB, H04-FB | — |
| **G** | Collections | Bottom-nav "Manage" / "Gestionar" / "Verwalten" → tab "Collections" / "Colecciones" / "Sammlungen" (list of the three collections, 1.5 s) → tap the first collection ("Escapada" / "Weekend trip" / "Wochenendtrip" — whichever sorts first) → "Add or remove garments" / "Añadir o quitar prendas" / "Kleidungsstücke hinzufügen oder entfernen" → view "Not in" / "Fuera" / "Nicht drin" → tap 2 tiles (badge "added" / "añadida" / "hinzugefügt") → 1 s → "Undo all" / "Deshacer todo" / "Alles rückgängig". Back without saving. | 12 s | no | V09b, H05 | collection names (table above) |
| **H** | Analytics | Browse → tap the bar-chart icon ("Wardrobe stats" / "Estadísticas del armario" / "Kleiderschrank-Statistik") → "Analytics" / "Estadísticas" / "Statistik" screen (Categories, 2 s) → slow swipe to "Colours" / "Colores" / "Farben" (2 s) → "Occasions" (1 s). | 8 s | yes (Categories + Colours visible) | V09c, H05, P5 | — |
| **I** | Tag review | Manage → occasions tab ("Occasions" / "Ocasiones" / "Anlässe") → "Add tag" / "Añadir etiqueta" / "Tag hinzufügen" → name (ASCII) EN `Wedding` · ES `Boda` · DE `Hochzeit` → "Add" / "Añadir" / "Hinzufügen" → prompt "Review garments?" / "¿Revisar prendas?" / "Kleidungsstücke prüfen?" → "Review garments" / "Revisar prendas" / "Kleidungsstücke prüfen" → scan, then review cards "New occasion: …" / "Nueva ocasión: …" / "Neu (Anlass): …" (2 s) → "Accept all (N)" (1 s). **Cleanup (not recorded):** delete the tag again; if Robia offers a review, accept it, and check in Filters › Occasion that the tag is gone. Record I after D and F so the new occasion never affects their results. | 25 s | no | H05 | tag name |
| **J** | Export / share card | Browse → tap a garment with a strong colour → "Garment detail" → share icon ("Share item") → "Export" screen with the "Robia frame" preview (2 s) → toggle one "Show" field off and on (1 s). **Stop before "Share"** (the share sheet is never recorded). Back. | 8 s | no | H05 | — |
| **K** | Browse, **Dark** | Setup (not recorded): Settings › Appearance › Theme → "Dark" / "Oscuro" / "Dunkel". Record Browse at top 2 s, one slow swipe, hold 1 s. Cleanup: Theme → "Light". | 6 s | yes (top of grid) | H06, P6 | — |
| **L** | Filters | Browse → chip "Filters" / "Filtros" / "Filter" → Advanced Filters → tick Category "Coats" + "Knitwear", Occasion "Formal", Season "Winter" (one tap per beat, ~0.5 s apart) → hold 1 s. Back without applying (or "Reset" / "Restablecer" / "Zurücksetzen"). | 8 s | no | H03 (left phone, "Tags") | — |
| **M** | Settings › Smart features | Browse → Settings → scroll to "Smart features" / "Funciones inteligentes" / "Intelligente Funktionen" → hold 3 s on "Smart suggestions · Ready · 388 MB" / "Listo · 388 MB" / "Bereit · 388 MB". No Backup/Drive rows in frame if avoidable (they read "Not connected" on the capture build). | 6 s | yes | V07-FB, H04-FB | — |

Shot count per locale: A, A-raw, B, C, D, E, F (or F-FB), G, H, I, J, K, L, M → **14** ids (F-FB = M + D, no new file).
If the F gate fails, the manifest still gets an F entry per locale (`kind: still`, the `F-gate` frame, `gate: fail`) so
V-CAP-1 stays checkable; F-FB itself is not a manifest entry.

## Six Play screenshot moments (framed 1080×1920 in CR2)
Captions: Inter Bold 88 px, max 2 lines, ≤ 22 characters per line (DE governs); optional 44 px sub-line.

| # | Still | Screen | Caption EN / ES / DE | Sub-line |
|---|---|---|---|---|
| 01 | D | Search results for Q1 | "Search by meaning" · "Busca por significado" · "Suche nach Bedeutung" | the Q1 query in quotes (locale) |
| 02 | A | Cutout + suggested fields | "Background gone, / tags suggested" · "Fondo fuera, / etiquetas sugeridas" · "Hintergrund weg, / Tags vorgeschlagen" | — |
| 03 | C | Batch, tiles processing/ready | "Add many photos / at once" · "Añade muchas fotos / a la vez" · "Viele Fotos / auf einmal" | — |
| 04 | E | Possible duplicates, Same photo | "Spot duplicates" · "Detecta duplicados" · "Duplikate finden" | — |
| 05 | H | Analytics | "See your / wardrobe stats" · "Las estadísticas / de tu armario" · "Statistik deines / Kleiderschranks" | — |
| 06 | K | Browse, Dark theme | "All AI on your phone. / No account. No ads." · "Toda la IA en tu móvil. / Sin cuenta. Sin anuncios." · "Alle KI auf dem Handy. / Kein Konto. Keine Werbung." | — |
None of the six depends on shot F, so the screenshots are the same whether the airplane test passes or not.

## Manifest additions requested from CAP
Per entry, besides `{locale, shot, file, kind, seconds, width, height}`: `taps` (see Tap log), `query` (D, F),
`airplane_mode_on` before/after (F), `gate: pass|fail` (F).
