# Storyboard — horizontal 16:9 (1920×1080) · CR1

Status: paper design for Manu's gate. Separate design from `storyboard-vertical.md`: wider pacing (4–4.5 s phrases
instead of 1–2.5 s cards), a copy column next to the phone, side-by-side comparisons and a multi-phone montage. No
frame here is a crop or a scale of a vertical frame; only the clips (`shotlist.md`) are shared.

## Global

| Item | Value |
|---|---|
| Length | **25.0 s** = 50 beats |
| Tempo | **120 BPM**, same track and offset as the vertical (brag `happy-beats-business-moves-vol-1-by-ende-dot-app.mp3`, starts at track 3.02 s). Strong cues at video ≈ 13.0, 17.0, 19.0, 20.0 are used for the airplane reveal and the montage. Fade out 24.0–25.0 s. CR2 re-reads the full cue JSON for 22–25 s (the cue summary stops at track 25 s). |
| Cuts | Scene cuts at 4.5, 9.0, 13.0, 17.0, 21.0 (beats); entrances inside a scene on beats. |
| Safe area | 96 px margin on all sides; copy stays above y 960 (player controls). |
| Phone mock | Height **900 px** (83 %), frame ≈ 420×900, 44 px radius. "Hero" position right (x 1300–1720) or left (x 200–620). |
| Copy column | 900 px wide on the side opposite the phone (x 120–1020 or x 900–1800). Headline Inter Bold **72 px**, max 3 lines, max 22 characters per line (DE governs); body Inter SemiBold 40 px, max 2 lines; labels Inter SemiBold 36 px. Ink #221814, emphasis #4E1B25. |
| Background | #FCFAF8 with a large #F3E4DA rounded panel behind the phone; end card #5A3B2D. |
| Touch indicator | Same drawn ring as the vertical (72 px, 25 %), from the tap log. |
| Reading rule | ≥ 0.3 s per DE word visible; "DE w / min" column counts every word that appears in the scene. |
| Poster | Frame 0 = H01 composed (headline already visible). |

## Scenes

| # | In–out | Beats | Shot | Composition | Copy EN / ES / DE | DE w / min |
|---|---|---|---|---|---|---|
| H01 Hook | 0.0–4.5 | 9 | B | Phone **right**, Browse grid scrolling at a calm, steady speed (slower than the vertical). Copy column left: "Robia" wordmark 48 px, headline (frame 0), body appears on 2.0. | Headline EN "You own more clothes / than you remember." · ES "Tienes más ropa / de la que recuerdas." · DE "Du hast mehr Kleidung, / als du denkst." — Body EN "Meet Robia: your wardrobe, on Android." · ES "Esto es Robia: tu armario en Android." · DE "Das ist Robia: dein Kleiderschrank auf Android." | 7 + 7 = 14 / 4.2 ✓ |
| H02 Before / after | 4.5–9.0 | 9 | A + A-raw | **Side-by-side comparison.** Centre-left: the raw demo photo as a rounded card 420×560 labelled "Before" (garment on a wooden floor). Arrow draws on 5.0. Right: phone with clip A — processing stages, then the cutout and the "✦ Suggested by Robia" fields; phone label "After". Copy column top-left (x 120–760): headline on 4.5, body on 6.5. | Labels EN "Before" / "After" · ES "Antes" / "Después" · DE "Vorher" / "Nachher" — Headline EN "Snap it. / Background gone." · ES "Haz la foto. / Fondo fuera." · DE "Foto machen. / Hintergrund weg." — Body EN "Category, occasion and colors: suggested, on your phone." · ES "Categoría, ocasión y colores: sugeridos, en tu móvil." · DE "Kategorie, Anlass und Farben: vorgeschlagen – auf dem Handy." | 2 + 4 + 8 = 14 / 4.2 ✓ |
| H03 Tags vs meaning | 9.0–13.0 | 8 | L + D | **Side-by-side comparison.** Two phones at 800 px: left (dimmed to 70 %) Robia's Filters screen with several chips ticked, label "Tags"; right (full) the search field filling with the query, then the re-sorted results ("Sorted by similarity · N garments"), label "Meaning". A magnifier callout (2×) over the right phone's search field shows the query large. Headline top centre on 9.0. | Headline EN "Search by meaning, / not tags." · ES "Busca por significado, / no por etiquetas." · DE "Nach Bedeutung suchen, / nicht nach Tags." — Labels EN "Tags" / "Meaning" · ES "Etiquetas" / "Significado" · DE "Tags" / "Bedeutung" — Query (in app + callout) EN "something warm for a wedding" · ES "algo abrigado para una boda" · DE "etwas Warmes zur Hochzeit" | 6 + 2 + 4 = 12 / 3.6 ✓ |
| H04 Airplane | 13.0–17.0 | 8 | F | Phone moves to the **left** (layout rhythm). Strong cue 13.0: a large drawn airplane-mode toggle in the copy column snaps ON (our graphic, not the system shade) and a thin line connects it to the real airplane icon in the phone's status bar. 14.0: the Q2 search returns results in airplane mode. Line on 14.0. | Headline EN "Airplane mode on." · ES "Modo avión activado." · DE "Flugmodus an." — Line EN "Still finds it. All AI runs on your phone." · ES "Y la encuentra igual. Toda la IA, en tu móvil." · DE "Findet es trotzdem. Alle KI-Funktionen laufen auf dem Handy." | 2 + 9 = 11 / 3.3 ✓ |
| H05 Montage | 17.0–21.0 | 8 | C, I, E · G, H, J | **Multi-phone montage**, two sets of three phones (760 px tall, centres x 480 / 960 / 1440), label under each (y 900–950). Set 1 enters 17.0 / 17.5 / 18.0; set 2 slides in from the right on 19.0 and staggers 19.0 / 19.5 / 20.0. Each phone plays its clip. | Set 1: EN "Batch add" · "Tag review" · "Duplicates" / ES "Añadir en lote" · "Revisar etiquetas" · "Duplicados" / DE "Viele auf einmal" · "Tags prüfen" · "Duplikate" — Set 2: EN "Collections" · "Stats" · "Share cards" / ES "Colecciones" · "Estadísticas" · "Fichas para compartir" / DE "Sammlungen" · "Statistik" · "Karten zum Teilen" | set 1: 6 / 1.8 ✓ (2.0) · set 2: 5 / 1.5 ✓ (2.0) |
| H06 End | 21.0–25.0 | 8 | K | Background #5A3B2D. Right: one phone with the Browse grid in **Dark** theme (the only dark shot), slow 3 % zoom. Left column: app icon 200 px + "Robia" wordmark (#FBF7F1); tagline on 21.0; sub-line on 22.0; official localized Google Play badge on 22.5 (height 120 px, whole unaltered artwork fades in, clear space ≥ ¼ height, smaller than the tagline); legal line 22 px at y ≈ 930. | Tagline EN "Your wardrobe, / finally searchable." · ES "Tu armario, por fin / fácil de buscar." · DE "Dein Kleiderschrank, / endlich durchsuchbar." — Sub EN "Free · No account · No ads" · ES "Gratis · Sin cuenta · Sin anuncios" · DE "Kostenlos · Kein Konto · Keine Werbung" — Legal EN "Google Play and the Google Play logo are trademarks of Google LLC." (ES/DE translated) | 4 + 6 = 10 / 3.0 ✓ (4.0 s) |

Sum: 4.5 + 4.5 + 4.0 + 4.0 + 4.0 + 4.0 = **25.0 s**.

## Shot-F fallback (airplane test failed at CAP)
Only H04 changes.

| # | In–out | Shot | Composition | Copy EN / ES / DE | DE w / min |
|---|---|---|---|---|---|
| H04-FB | 13.0–17.0 | M + D | Phone left: Settings › Smart features, punch-in on "Smart suggestions · Ready · 388 MB"; on 15.0 cut inside the phone to the tail of D (results). Copy column right, no airplane graphic; a large "388 MB" chip with a download glyph (real UI value). | Headline EN "Downloaded once." · ES "Descarga única." · DE "Einmal geladen." — Line EN "All AI runs on your phone." · ES "Toda la IA, en tu móvil." · DE "Alle KI-Funktionen laufen auf dem Handy." | 2 + 6 = 8 / 2.4 ✓ |

## How this differs from the vertical (by design)
| | Vertical | Horizontal |
|---|---|---|
| Rhythm | 13 cards, 1.0–2.5 s, cuts every 2–4 beats | 6 scenes, 4–4.5 s phrases |
| Phone | one phone, 70 % height, centred, punch-ins | phone switches sides (right → left), 2-phone and 6-phone layouts |
| Copy | one idea per card, over the phone in the safe bands | copy column with headline + body next to the phone |
| Cutout | before → after inside the same phone | raw photo card next to the phone ("Before / After") |
| Search | big query pill, then results | "Tags vs meaning" with two phones |
| Montage | 3 single-feature cards, 1 s each | 6 phones in two sets of three, labelled |
| End | full-bleed brand card, centred | brand column + Dark-theme phone |

## SFX (Kenney via brag, CC0; under the music)
Taps `ui/click2.ogg`; typing `interface/click_003.ogg` per word; reveals `impact/impactSoft_medium_001.ogg` on 5.0,
13.0, 21.0; montage entrances `interface/bong_001.ogg` at low level on 17.0 and 19.0. Nothing from `keyboard/`.

## Layout checks for CR2 (stills before render)
- H02 body DE is the longest line ("Kategorie, Anlass und Farben: vorgeschlagen – auf dem Handy.") → 2 lines at 40 px in
  640 px; check it does not touch the "Before" card.
- H05 labels: "Fichas para compartir" and "Karten zum Teilen" must fit 360 px at 36 px (else 32 px for all labels).
- Badge ≥ 28 px tall (it is 120), legal line readable at 1080p.
