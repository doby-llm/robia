# Storyboard — vertical 9:16 (1080×1920) · CR1

Status: paper design for Manu's gate. Separate design from `storyboard-horizontal.md`. The two videos share the clips
(`shotlist.md`), the track and the copy *ideas*. They do not share compositions: no frame here is a crop or a scale of
a horizontal frame.

## Global

| Item | Value |
|---|---|
| Length | **24.0 s** = 48 beats = 12 bars |
| Tempo | **120 BPM** (beat 0.50 s, bar 2.00 s). Track: brag `happy-beats-business-moves-vol-1-by-ende-dot-app.mp3` (cue file: 120.19 BPM), **starts at track 3.02 s** so its beat grid lands on video 0.00 / 0.50 / 1.00 … Track strong cues then fall at video ≈ 13.0, 14.0, 15.0, 17.0, 18.0, 19.0, 20.0. Fade out 23.0–24.0 s. |
| Cuts | Every cut is on a beat. Major reveals (airplane at 13.0, end card at 20.0) on strong cues. |
| Safe zones | **y 0–220 and y 1540–1920 reserved** for platform UI: no copy and no key UI there. Copy also keeps out of the right 120 px (like/share rail on Reels/Shorts/TikTok). Copy area: x 60–960. |
| Phone mock | One phone, **1344 px tall (70 %)**, frame 630×1344, x 225–855, y 196–1540. Screen = capture scaled to fit (≈ 0.54 of 1080×2400). Thin frame #221814, 56 px corner radius, soft shadow. The status bar part of the phone can sit under the top reserved band (no key UI there). |
| Caption card | One idea per card. Rounded card (40 px radius), surface #FCFAF8 at 94 %, shadow. **BOTTOM band** y 1200–1500 (covers the phone's nav bar and lower grid) or **TOP band** y 240–520 (covers status bar and top bar). Inter Bold **72 px**, line height 1.1, **max 2 lines, max 22 characters per line** (DE governs). Ink #221814; emphasis word in tertiary #4E1B25. |
| Punch-in | The phone may scale 1.0 → 1.35 around a region of interest (screen content stays inside the phone frame, frame scales with it). This is a vertical-only move. |
| Background | Tobacco & Bordeaux light: #FCFAF8 with a soft #F3E4DA radial glow behind the phone. End card: #5A3B2D (primary) full bleed. |
| Touch indicator | CAP records with "Show taps" off; the video draws a 72 px ring (#221814 at 25 %) at the logged tap coordinates (`shotlist.md` §Tap log). |
| Reading rule | ≥ 0.3 s per **DE** word on screen (DE ≈ 30 % longer). The "DE w / min" column checks it. |
| Poster | Frame 0 = V01 fully composed (hook copy already visible, no fade-in). |
| Fonts / audio | Inter (OFL). Music + soft Kenney SFX (CC0) under it. See `credits.md`. |

## Scenes

Times in seconds. "Shot" = clip id in `shotlist.md`.

| # | In–out | Beats | Shot | Picture | Card | Copy EN / ES / DE | DE w / min |
|---|---|---|---|---|---|---|---|
| V01 Hook | 0.0–2.5 | 5 | B | Phone full size, Browse grid ("Photos only") already moving at frame 0, fast upward scroll with slight motion blur; it decelerates on 2.0. Small "Robia" wordmark (40 px, #4E1B25) above the card text. | BOTTOM | EN "You own more clothes / than you remember." · ES "Tienes más ropa / de la que recuerdas." · DE "Du hast mehr Kleidung, / als du denkst." | 7 / 2.1 ✓ |
| V02 Snap | 2.5–4.0 | 3 | A (start) | Hard cut on 2.5: Add clothing screen, ring on the photo area, the chosen demo photo (garment on a wooden floor) lands in the preview. | BOTTOM | EN "Snap a photo." · ES "Haz una foto." · DE "Mach ein Foto." | 3 / 0.9 ✓ |
| V03 Cutout | 4.0–5.5 | 3 | A (processing) | Punch-in 1.0 → 1.3 on the photo while the stage label reads "Removing background" / "Eliminando fondo" / "Hintergrund wird entfernt"; on 5.0 the floor disappears and the cutout sits on the photo well. SFX soft impact on 5.0. | BOTTOM | EN "Background? Gone." · ES "¿El fondo? Fuera." · DE "Hintergrund? Weg." | 2 / 0.6 ✓ |
| V04 Suggested | 5.5–7.5 | 4 | A (fields) | Punch moves down to the fields: Category, Occasions and colours filled, each with "✦ Suggested by Robia. Tap to change." / "✦ Sugerido por Robia…" / "✦ Von Robia vorgeschlagen…". Chips pop on 6.0, 6.5, 7.0. | TOP | EN "Category, occasion, / colors: suggested." · ES "Categoría, ocasión / y colores: sugeridos." · DE "Kategorie, Anlass, / Farben: vorgeschlagen." | 5 / 1.5 ✓ |
| V05 Batch | 7.5–9.0 | 3 | C | Phone back to 1.0. "Add batch" / "Añadir lote" / "Batch hinzufügen" grid: tiles flip Queued → Processing → Ready on beats; counter "x of 6 processed" visible. | BOTTOM | EN "Or add many at once." · ES "O muchas a la vez." · DE "Oder viele auf einmal." | 4 / 1.2 ✓ |
| V06a Query | 9.0–11.0 | 4 | D (typing) | Browse. Punch-in 1.35 on the top of the screen (search field "Search your wardrobe"); the query fills word by word. The keyboard area stays out of frame. The card shows the same query as a big search pill (magnifier icon), building word by word in sync. Soft click per word. | BOTTOM (pill) | EN "“something warm / for a wedding”" · ES "“algo abrigado / para una boda”" · DE "„etwas Warmes / zur Hochzeit“" | 4 / 1.2 ✓ |
| V06b Results | 11.0–13.0 | 4 | D (results) | Punch out to 1.0 on 11.0: grid re-sorted, caption line "Sorted by similarity · N garments" / "Ordenado por parecido · …" / "Nach Ähnlichkeit sortiert · …"; the top 3 results get a short highlight ring on 11.5, 12.0, 12.5. | BOTTOM | EN "Robia gets / what you mean." · ES "Robia entiende / lo que buscas." · DE "Robia versteht, / was du meinst." | 5 / 1.5 ✓ |
| V07 Airplane | 13.0–15.0 | 4 | F | Strong cue 13.0: a drawn airplane-mode toggle (our motion graphic, not the system shade) snaps ON next to the phone's status bar, then punch-in on the real status-bar airplane icon; a second search (Q2) returns results in airplane mode. | BOTTOM | EN "Airplane mode on. / Still finds it." · ES "Modo avión. / Y la encuentra igual." · DE "Flugmodus an. / Findet es trotzdem." | 5 / 1.5 ✓ |
| V08 On-device | 15.0–17.0 | 4 | F (tail) | Phone holds on the results; a thin #4E1B25 outline traces the phone frame on 15.0 ("everything happens inside this"). | TOP | EN "All AI runs / on your phone." · ES "Toda la IA, / en tu móvil." · DE "Alle KI-Funktionen / laufen auf dem Handy." | 6 / 1.8 ✓ |
| V09a Duplicates | 17.0–18.0 | 2 | E | Hard cut, strong cue: "Possible duplicates" screen, group "Same photo", the copy gets "Delete" on 17.5. | BOTTOM | EN "Spot duplicates." · ES "Detecta duplicados." · DE "Duplikate finden." | 2 / 0.6 ✓ |
| V09b Collections | 18.0–19.0 | 2 | G | Hard cut: Collections list → one collection's members, two tiles get the "added" badge. | BOTTOM | EN "Build collections." · ES "Crea colecciones." · DE "Sammlungen anlegen." | 2 / 0.6 ✓ |
| V09c Stats | 19.0–20.0 | 2 | H | Hard cut: Analytics, the Categories bars grow in (screen content as captured), quick scroll to Colours. | BOTTOM | EN "See your stats." · ES "Mira tus estadísticas." · DE "Statistik ansehen." | 2 / 0.6 ✓ |
| V10 End | 20.0–24.0 | 8 | — | Strong cue 20.0: phone drops out downwards, background wipes to #5A3B2D. Centre: app icon 240 px (from `ic_launcher_foreground_image.png` on #FCF8F1, rounded) + "Robia" wordmark #FBF7F1 at y ≈ 640–900. Tagline at 20.0 (y ≈ 960–1120, 72 px). Sub-line at 21.5 (40 px, #F3E4DA). Official localized Google Play badge at 22.0 (fade of the whole, unaltered artwork; height 150 px, clear space ≥ ¼ height, y ≈ 1300–1450; smaller than the tagline). Legal line 24 px at y ≈ 1490: "Google Play and the Google Play logo are trademarks of Google LLC." (localized). | — | Tagline EN "Your wardrobe, / finally searchable." · ES "Tu armario, por fin / fácil de buscar." · DE "Dein Kleiderschrank, / endlich durchsuchbar." — Sub EN "Free · No account · No ads" · ES "Gratis · Sin cuenta · Sin anuncios" · DE "Kostenlos · Kein Konto · Keine Werbung" | 4 + 6 = 10 / 3.0 ✓ (4.0 s) |

Sum: 2.5 + 1.5 + 1.5 + 2.0 + 1.5 + 2.0 + 2.0 + 2.0 + 2.0 + 1.0 + 1.0 + 1.0 + 4.0 = **24.0 s**.
Cuts: 2.5, 4.0, 5.5, 7.5, 9.0, 11.0, 13.0, 15.0, 17.0, 18.0, 19.0, 20.0 — all on beats.

## Shot-F fallback (airplane test failed at CAP)
Only V07 changes; nothing else claims offline.

| # | In–out | Shot | Picture | Copy EN / ES / DE | DE w / min |
|---|---|---|---|---|---|
| V07-FB | 13.0–15.0 | M | Settings › Smart features, punch-in on "Smart suggestions · Ready · 388 MB" / "Listo · 388 MB" / "Bereit · 388 MB". No airplane graphic. | EN "Downloaded once. / Lives on your phone." · ES "Descarga única. / Vive en tu móvil." · DE "Einmal geladen. / Bleibt auf dem Handy." | 6 / 1.8 ✓ |

V08 then plays over the tail of D (results) instead of F. Its copy ("All AI runs on your phone") does not claim offline
use and stays.

## SFX (Kenney via brag, CC0; always under the music)
- Taps: `ui/click2.ogg` at the drawn tap rings (V02, V05, V09a, V09b).
- Typing: `interface/click_003.ogg`, one per word (V06a, V07).
- Reveals: `impact/impactSoft_medium_001.ogg` on 5.0 (cutout), 13.0 (airplane), 20.0 (end card).
- No SFX from brag's `keyboard/` or `casino/` folders.

## Layout checks for CR2 (stills before render)
- DE line lengths ≤ 22 characters at 72 px fit the 960 px card with 48 px padding; check "Alle KI-Funktionen" and
  "Dein Kleiderschrank," on a real still.
- No card overlaps y < 220 or y > 1540; the badge and legal line end above 1540.
- The keyboard never appears in V06a/V07 (punch-in hides it; trim if it shows).
