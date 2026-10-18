# Requirements — robia-ux-rework

Source: `feedback/2026-10-16-manu-email.md` (items R1–R15) and revision-1 feedback `feedback/2026-10-17-manu-rev1-feedback.md` (revision 2). Copy: English in the app, all three locales (values, values-es, values-de).
Priority: must unless marked. Standing Robia UI preferences from previous rounds apply (alphabetical lists with fixed rows first, no blinking indicators, discreet transitions, stacked full-width buttons for 3+ actions, neutral offline states).

## Browse (R3, R9, R10, R13)
- **REQ-BR-01** Every Browse grid card in a row has the same height regardless of name, notes or tags. Photo area aspect ratio 4:5, then a fixed two-line footer.
- **REQ-BR-02** Footer line 1 = garment name; if blank, the localized category label; if no category, `browse_similar_untitled` ("Untitled garment"). Line 2 = occasions then season labels joined by " · ", 1 line, ellipsis; empty line reserved when none. Max 2 colour dots (primary, secondary) at the end of line 1. No chips in the grid.
- **REQ-BR-03** The card shape is `shapes.large` (16 dp) and the photo is clipped by the card itself (top corners equal); selection border uses the same shape. No visible gap between the photo background and the card at any corner.
- **REQ-BR-04** The subtitle "Synced" (sync label used as a notes fallback) is removed. The per-card cloud badge is hidden when the garment is Synced or LocalOnly (Drive not set up); it shows icon + short label for Waiting / Uploading / Needs attention / Sign in, and once shown stays ≥ 1200 ms (no blinking). Photo-restore attention keeps its existing message (badge, not subtitle).
- **REQ-BR-05** When `hasActiveFilters`, a "Reset" text button appears right of the Filters chip and clears filters only (not search, not similar-to). Below the bar, a horizontally scrolling row shows one removable chip per active filter (tag, "Not set" category, colour with swatch, favourites); tapping ✕ removes only that filter.
- **REQ-BR-06** Grid end-of-list inset equals the bottom nav height + 16 dp (no oversized blank space).
- **REQ-BR-07** (rev 2) Browse has two layouts, chosen in Settings > General > "Browse layout": **Photos only** (default) = 2-column grid of equal 4:5 photo tiles with no text (favourite heart, cloud badge and selection check still shown) and **Photos with details** = the REQ-BR-01/02 card. Both use the same shape/clip rules (REQ-BR-03). The choice persists.

## Filters (R2)
- **REQ-FI-01** The advanced filter screen groups each dimension (Favourites, Category, Season, Occasion, Location, Colour) in a card with icon, title and selected-count badge. Long tag lists show the first 8 chips plus a "+N more" chip that expands in place. Lists are alphabetical (locale-aware), "Not set" last.
- **REQ-FI-02** Colours are a 6-column swatch grid with the colour name under each swatch; selected = ring.
- **REQ-FI-03** A fixed bottom bar shows "Show N garments" with the live count; the top bar has Close (✕) and "Reset" (enabled only when filters are active).
- **REQ-FI-04** (approved 2026-10-17) Multi-select in every section; OR within a section (Summer + Winter shows garments of either), AND across sections (… and Dresses). Favourites is a switch.

## Manage (R1)
- **REQ-MA-01** Horizontal swipe on the Manage content switches to the adjacent tab; the tab row follows; tapping a tab animates to that page; the selected tab id stays hoisted and survives rotation. Vertical scrolling inside a page is not affected.

## Garment form and detail (R5)
- **REQ-GF-01** Add/Edit screen: large photo card (current aspect, default 4:3) with Retake, Gallery and "Edit photo" actions, then ONE card with equal list rows: Category, Occasions, Season, Colours, Location, Fit, then the Notes field. No card-in-card, no 2-column grid. No Name field is added (the no-name add flow of commit 528c53d is kept; legacy names still display).
- **REQ-GF-02** Tapping Category/Location opens a modal bottom sheet with an alphabetical radio list; Occasions/Season open a checkbox list; Colours keeps the existing picker. AI-suggested values carry a ✦ marker until the user changes them.
- **REQ-GF-03** Item detail replaces `DetailMetadataGrid` (2-column cards) with the same read-only list rows; title = REQ-BR-02 line 1 rule.
- **REQ-GF-04** Batch edit opens the same `AddEditClothingScreen` (route `BatchEditClothing`), so it shows the same rows with no extra work.

## Photo processing feedback (R6)
- **REQ-PF-01** While segmentation runs: the photo is shown at full opacity with a soft diagonal light sweep (2.2 s loop) and a 3-step segmented progress bar (Finding your garment… / Cleaning the edges… / Reading colours and style…). No dark scrim, no spinner.
- **REQ-PF-02** As soon as the foreground is available: background fades to 18 % over 400 ms; a shimmer gradient runs only inside the garment alpha (BlendMode.SrcAtop) plus a soft warm outline glow pulsing at 1.8 s.
- **REQ-PF-03** When processing completes, the extracted colour swatches shown over the photo appear with a pop (scale 0.4→1.12→1, 300 ms); the AI ✦ markers on the rows come from U06. With system animations off (animator duration scale 0), all of the above are static and the stage label is still shown.
- **REQ-PF-04** Batch rows use the same sweep on their thumbnail (no labels) instead of spinner+dim.

## Batch add (rev 2)
- **REQ-BA-01** Faster batch: photos are processed as a pipeline instead of strictly one at a time. While garment N is in the suggestion step (EmbeddingGemma, single engine), the cutout of garment N+1 already runs; with ML Kit up to 2 cutouts run at once, with the precise pack 1. Garments start in screen order and become Ready in screen order (a later garment that finishes first waits invisibly until all earlier ones are terminal). Peak memory stays within the G-SEG-1 limit (≤ 1300 MB PSS). Cancel/interrupt/retry keep today's semantics.
- **REQ-BA-02** The batch screen is a 1-column list. Each row: 72 dp thumbnail (cutout on its colour gradient), status chip (Queued / Processing / Ready / Review / Failed) on the right, and under the position label the detected category and occasions as text ("Dresses · Dressed-up, Everyday"), 2 lines max with ellipsis; nothing when none. The status is NOT repeated as text; an error/fallback reason replaces the category line only for Failed / Review, with Retry and Discard buttons on that row.
- **REQ-BA-03** One header only: title, "8 of 12 processed", progress bar and an ETA right of the bar ("about 40 s left"; hidden until 2 garments are done; computed from the average time between completions of the last 4 garments × remaining, rounded to 10 s, never increasing by more than 20 % between updates; "Almost done" under 10 s). The bottom bar holds only the Save button and, when relevant, one short line ("2 need review"). The long helper sentence and the Accepted/Review/Failed/Interrupted counters are removed from both places.

## Segmentation (R4, R7, R8)
- **REQ-SG-01** ML Kit module availability: on app start (idle) and before segmenting, check `ModuleInstallClient.areModulesAvailable(segmenter)`; if missing, request an urgent install and wait up to 60 s showing "Preparing photo cleanup…"; then segment. A `MODEL_UNAVAILABLE` failure triggers one wait-and-retry cycle automatically. The user-visible fallback message names the cause in plain words.
- **REQ-SG-02** Batch items in NeedsReview because of a background fallback get a Retry action.
- **REQ-SG-03** ML Kit path builds alpha from the foreground confidence mask with a smoothstep and an edge-aware (guided) refinement; input is EXIF-oriented and capped at 2048 px long edge. Output PNG keeps soft alpha. Thumbnails use a sample size ≤ 2× target before bilinear scaling (no aliasing).
- **REQ-SG-04** Optional "Precise cutout" pack (Settings > Smart features), downloaded on demand with the same rules as the suggestions model (unmetered by default, resumable, sha256 pinned). When Ready and enabled, it replaces ML Kit for new photos; on any failure it falls back to ML Kit, then to the original photo.
- **REQ-SG-05** Precise cutout pipeline (reference: `tools/segmentation-bench/pipeline_v11.py`, variant A, rev 2): saliency (u2netp) → automatic interactive selection (MediaPipe Interactive Segmenter, 3 points, overlap ≥ 0.6) → hanger detection (D-FINE-S Objects365 class 101, score ≥ 0.25, plausibility rule) → hanger mask (MobileSAM box prompt, ≤ 15 % of garment area) → **hanger first**: the hanger pixels (dilated 4 px) are filled from the surrounding background and saliency + selection run again on the filled photo, so background enclosed by the hanger now touches the outside and is removed; if the new selection keeps < 85 % of the previous garment area, the previous result minus the hanger is used → island removal (< 10 % of largest) → guided-filter edges.
- **REQ-SG-06** (rev 2) The quick-edit dialog is replaced by a full-screen **Photo editor** with two tabs: **Cutout** (Keep / Remove mode; a tap or a short finger stroke selects the whole piece with the Interactive Segmenter and adds it back or removes it; a Brush toggle paints exactly under the finger with a 24 dp soft brush for small fixes; Undo ≥ 10, Reset to automatic result; checkerboard background, pinch to zoom) and **Light** (the existing Brightness slider and Temperature presets, same processing as today). Done saves one new processed PNG; colours and suggestions are re-detected as Quick edit does today; Cancel/back discards. Available for new and existing garments with a stored photo; works offline without the downloadable pack (model bundled, ~6 MB).
- **REQ-SG-08** (PLAN-CHANGE 1) Settings › Cutout lets the user choose: method Automatic/Standard, Remove hangers on/off, Hanger detection Normal/High, Edges Soft/Sharp, Batch speed Balanced/Memory saver. Defaults reproduce the approved behaviour. Options that need the pack are disabled with "Needs precise cutout" when it is absent. No model picker and no numeric thresholds.
- **REQ-SG-09** (PLAN-CHANGE 1) In the photo editor, for photos that still have their original (new photos, batch drafts), "Redo cutout" reruns one photo with Standard, Precise or Precise without hanger removal; the result is undoable and nothing is saved until Done.
- **REQ-SG-07** Performance gate G-SEG-1 (Poco F3, staging): precise pipeline median ≤ 4 s without hanger and ≤ 9 s with hanger (rev 2: includes the hanger-first stage) over the benchmark set; peak PSS ≤ 1300 MB. Refine tap ≤ 400 ms median (measured after SG3, row V-Z2-18).

## Export (R11)
- **REQ-PD-02** (rev 2) The PDF adapts to sparse garments (no name, notes optional): title = name, else the localized category, else "Garment"; with no notes the notes block is omitted and the photo box grows (up to 600 pt) so there is no blank band; with no colours the swatch row is omitted; the metadata list shows only rows with a value. Mockup shows the three cases (full, category only, nothing but the photo).
- **REQ-PD-01** Single-garment PDF is A4 portrait (595×842 pt), Inter (bundled, OFL) Regular/SemiBold/Bold, layout per `mockups/pdf.html`: header line, photo on a soft colour gradient (no tint over the garment, bitmap filtering on), title, notes, colour swatches, metadata list (Category, Season, Occasions, Fit — Location stays out as decided before), logo footer. Text wraps and never shows ellipses (earlier decision); a normal garment fits one page, extremely long notes continue on a second page.

- **REQ-BR-08** (rev 3) Pinch on Browse changes the number of columns: 2–4 in "Photos only", 2–3 in "Photos with details"; tiles glide to the new place, the scroll position is kept, the choice is remembered; one-finger scroll, tap and long-press are unchanged. No new library.

## Settings (R12)
- **REQ-ST-01** Settings root = grouped navigation rows: Smart features (Smart suggestions, Precise cutout), Backup (Google Drive), General (Language, Browse layout), About (version; 7 taps unlock developer as today). Developer section appears only when unlocked.
- **REQ-ST-02** Language is a sub-screen with a radio list driven by `LanguagePreference.entries`; each language shown in its own language plus the current-UI translation. The inline per-language rows and the read-only `LanguageSettingsScreen` list are removed.
- **REQ-ST-03** Google Drive is a sub-screen holding every existing Drive action (connect, back up again, restore, delete backup with its existing confirmation). No behaviour change.

## Feedback and motion (R14)
- **REQ-MO-01** Shared primitives in `core/designsystem`: press scale 0.97 spring, AI shimmer brush, pop-in, sparkle burst. No haptics (dropped in rev 2). System animator scale 0 disables motion.
- **REQ-MO-02** Review Accept: card slides right 40 dp + fades + collapses (250 ms). Discard: slides left. Garment cards and review buttons use press scale.
- **REQ-MO-04** (rev 2) When a tag or colour review is committed, a short completion screen replaces the review for 1.6 s (tap to skip): a check inside a soft circle, 6–8 small ✦ sparks drifting outward once (no loop), and the summary "Tags updated on 5 garments" / "Colours updated on 3 garments" (count = garments actually changed; 0 → no screen). Then it returns to Manage as today. Reduced motion: static check + text, 1.2 s.
- **REQ-MO-03** AI entry points (smart search toggle, Similar, review prompt dialogs, Edit photo button) use the AI style: warm gradient with a light pass every ~3 s, continuous while working.

## Taxonomy (R15)
- **REQ-TX-01** Two new default categories: `category-socks` ("Socks", description "socks or tights: hosiery worn on the feet and legs") and `category-underwear` ("Underwear", "underwear: bra, briefs, boxers or lingerie worn under clothes"), `suggestOccasions = false`, localized EN/ES/DE, seeded on existing installs (respecting tombstones), included in the scoring labels.
- **REQ-TX-02** The built-in Accessories description becomes "an accessory: a belt or waist belt, a scarf, a hat, sunglasses or a similar item" (rev 2, includes belts; measured on 142 photos at 70 vision tokens: accessories 11/12 vs 9/12 today, no other garment predicted as accessory). Garments whose Accessories tag has a user-edited description keep it. No other default category changes. Existing garments are not re-tagged automatically.
- **REQ-TX-03** Recipe id stays `eg2-r1-vt70`: label vectors are computed at runtime from the label text (cache key = text), so a changed description needs no migration; stored image vectors are unaffected.

## Colours (rev 3, R17)
- **REQ-CO-01** A default colour "Gray" (#8A8A8A) exists and is treated as neutral by colour detection; grey and denim garments are detected as Gray instead of brown/beige. Existing garments keep their colours until the colour review runs; a user who deleted Gray never gets it back.
- **REQ-CO-02** (optional, no behaviour change) Colour detection decodes a reduced bitmap (long edge ≥ 512 px) instead of the full photo.

## Google Drive (rev 3, R18, R20)
- **REQ-SY-01** Settings › Google Drive shows the space Robia uses in Drive (total size and photo count, last update time, refresh), computed from the file listing sync already does; no new permission.
- **REQ-SY-02** A photo restored from Drive is accepted only if its bytes match the hash/size recorded at upload; otherwise it stays guarded and the phone copy is never overwritten.
- **REQ-SY-03** An upload counts only if Drive reports the same size (and checksum when given); otherwise the previous photo record and blob are kept.
- **REQ-SY-04** Sync is bound to one Google account; with a different account nothing is uploaded, restored or purged until the user chooses "Use this account" (then the normal merge flow) or disconnects.
- **REQ-SY-05** "Check photos" (read-only) compares this phone, the backup snapshot and Drive and lists missing / damaged / not-yet-backed-up photos with Restore or Back up now; it never deletes anything.
- Out of scope (Manu, rev 3): backing up the original camera photo; selling on Ricardo (rejected).
