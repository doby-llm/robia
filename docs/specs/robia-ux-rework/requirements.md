# Requirements — robia-ux-rework

Source: `feedback/2026-10-16-manu-email.md` (items R1–R15). Copy: English in the app, all three locales (values, values-es, values-de).
Priority: must unless marked. Standing Robia UI preferences from previous rounds apply (alphabetical lists with fixed rows first, no blinking indicators, discreet transitions, stacked full-width buttons for 3+ actions, neutral offline states).

## Browse (R3, R9, R10, R13)
- **REQ-BR-01** Every Browse grid card in a row has the same height regardless of name, notes or tags. Photo area aspect ratio 4:5, then a fixed two-line footer.
- **REQ-BR-02** Footer line 1 = garment name; if blank, the localized category label; if no category, `browse_similar_untitled` ("Untitled garment"). Line 2 = occasions then season labels joined by " · ", 1 line, ellipsis; empty line reserved when none. Max 2 colour dots (primary, secondary) at the end of line 1. No chips in the grid.
- **REQ-BR-03** The card shape is `shapes.large` (16 dp) and the photo is clipped by the card itself (top corners equal); selection border uses the same shape. No visible gap between the photo background and the card at any corner.
- **REQ-BR-04** The subtitle "Synced" (sync label used as a notes fallback) is removed. The per-card cloud badge is hidden when the garment is Synced or LocalOnly (Drive not set up); it shows icon + short label for Waiting / Uploading / Needs attention / Sign in, and once shown stays ≥ 1200 ms (no blinking). Photo-restore attention keeps its existing message (badge, not subtitle).
- **REQ-BR-05** When `hasActiveFilters`, a "Reset" text button appears right of the Filters chip and clears filters only (not search, not similar-to). Below the bar, a horizontally scrolling row shows one removable chip per active filter (tag, "Not set" category, colour with swatch, favourites); tapping ✕ removes only that filter.
- **REQ-BR-06** Grid end-of-list inset equals the bottom nav height + 16 dp (no oversized blank space).

## Filters (R2)
- **REQ-FI-01** The advanced filter screen groups each dimension (Favourites, Category, Season, Occasion, Location, Colour) in a card with icon, title and selected-count badge. Long tag lists show the first 8 chips plus a "+N more" chip that expands in place. Lists are alphabetical (locale-aware), "Not set" last.
- **REQ-FI-02** Colours are a 6-column swatch grid with the colour name under each swatch; selected = ring.
- **REQ-FI-03** A fixed bottom bar shows "Show N garments" with the live count; the top bar has Close (✕) and "Reset" (enabled only when filters are active).
- **REQ-FI-04** (pending Q4, default A) Multi-select in every section; OR within a section, AND across sections. Favourites is a switch.

## Manage (R1)
- **REQ-MA-01** Horizontal swipe on the Manage content switches to the adjacent tab; the tab row follows; tapping a tab animates to that page; the selected tab id stays hoisted and survives rotation. Vertical scrolling inside a page is not affected.

## Garment form and detail (R5)
- **REQ-GF-01** Add/Edit screen: large photo card (current aspect, default 4:3) with Retake, Gallery and "Refine" actions, then ONE card with equal list rows: Category, Occasions, Season, Colours, Location, Fit, then the Notes field. No card-in-card, no 2-column grid. No Name field is added (the no-name add flow of commit 528c53d is kept; legacy names still display).
- **REQ-GF-02** Tapping Category/Location opens a modal bottom sheet with an alphabetical radio list; Occasions/Season open a checkbox list; Colours keeps the existing picker. AI-suggested values carry a ✦ marker until the user changes them.
- **REQ-GF-03** Item detail replaces `DetailMetadataGrid` (2-column cards) with the same read-only list rows; title = REQ-BR-02 line 1 rule.
- **REQ-GF-04** Batch add tiles keep their grid; batch edit opens the same `AddEditClothingScreen` (route `BatchEditClothing`), so it shows the same rows with no extra work.

## Photo processing feedback (R6)
- **REQ-PF-01** While segmentation runs: the photo is shown at full opacity with a soft diagonal light sweep (2.2 s loop) and a 3-step segmented progress bar (Finding your garment… / Cleaning the edges… / Reading colours and style…). No dark scrim, no spinner.
- **REQ-PF-02** As soon as the foreground is available: background fades to 18 % over 400 ms; a shimmer gradient runs only inside the garment alpha (BlendMode.SrcAtop) plus a soft warm outline glow pulsing at 1.8 s.
- **REQ-PF-03** When processing completes, the extracted colour swatches shown over the photo appear with a pop (scale 0.4→1.12→1, 300 ms); the AI ✦ markers on the rows come from U06. With system animations off (animator duration scale 0), all of the above are static and the stage label is still shown.
- **REQ-PF-04** Batch tiles use the same sweep (no labels) instead of spinner+dim.

## Segmentation (R4, R7, R8)
- **REQ-SG-01** ML Kit module availability: on app start (idle) and before segmenting, check `ModuleInstallClient.areModulesAvailable(segmenter)`; if missing, request an urgent install and wait up to 60 s showing "Preparing photo cleanup…"; then segment. A `MODEL_UNAVAILABLE` failure triggers one wait-and-retry cycle automatically. The user-visible fallback message names the cause in plain words.
- **REQ-SG-02** Batch items in NeedsReview because of a background fallback get a Retry action.
- **REQ-SG-03** ML Kit path builds alpha from the foreground confidence mask with a smoothstep and an edge-aware (guided) refinement; input is EXIF-oriented and capped at 2048 px long edge. Output PNG keeps soft alpha. Thumbnails use a sample size ≤ 2× target before bilinear scaling (no aliasing).
- **REQ-SG-04** Optional "Precise cutout" pack (Settings > Smart features), downloaded on demand with the same rules as the suggestions model (unmetered by default, resumable, sha256 pinned). When Ready and enabled, it replaces ML Kit for new photos; on any failure it falls back to ML Kit, then to the original photo.
- **REQ-SG-05** Precise cutout pipeline (reference: `tools/segmentation-bench/pipeline_v10.py`): saliency (u2netp) → automatic interactive selection (MediaPipe Interactive Segmenter, 3 points, overlap ≥ 0.6) → hanger detection (D-FINE-S Objects365 class 101, score ≥ 0.25, plausibility rule) → hanger mask (MobileSAM box prompt) subtraction capped at 15 % of garment area → island removal (< 10 % of largest) → guided-filter edges.
- **REQ-SG-06** "Refine" editor: Keep/Remove mode, one tap = interactive segmentation at that point, merged into the alpha; undo stack (≥ 10), Reset to automatic result, Done saves a new processed PNG. Available for new and existing garments with a stored photo. Works without the downloadable pack (Interactive Segmenter model bundled, ~6 MB).
- **REQ-SG-07** Performance gate G-SEG-1 (Poco F3, staging): precise pipeline median ≤ 4 s without hanger and ≤ 8 s with hanger over the benchmark set; peak PSS ≤ 1300 MB. Refine tap ≤ 400 ms median (measured after SG3, row V-Z2-18).

## Export (R11)
- **REQ-PD-01** Single-garment PDF is A4 portrait (595×842 pt), Inter (bundled, OFL) Regular/SemiBold/Bold, layout per `mockups/pdf.html`: header line, photo on a soft colour gradient (no tint over the garment, bitmap filtering on), title, notes, colour swatches, metadata list (Category, Season, Occasions, Fit — Location stays out as decided before), logo footer. Text wraps and never shows ellipses (earlier decision); a normal garment fits one page, extremely long notes continue on a second page.

## Settings (R12)
- **REQ-ST-01** Settings root = grouped navigation rows: Smart features (Smart suggestions, Precise cutout), Backup (Google Drive), General (Language, Haptic feedback switch), About (version; 7 taps unlock developer as today). Developer section appears only when unlocked.
- **REQ-ST-02** Language is a sub-screen with a radio list driven by `LanguagePreference.entries`; each language shown in its own language plus the current-UI translation. The inline per-language rows and the read-only `LanguageSettingsScreen` list are removed.
- **REQ-ST-03** Google Drive is a sub-screen holding every existing Drive action (connect, back up again, restore, delete backup with its existing confirmation). No behaviour change.

## Feedback and motion (R14)
- **REQ-MO-01** Shared primitives in `core/designsystem`: press scale 0.97 spring, haptics helper (confirm/reject/tick with API < 30 fallbacks), AI shimmer brush, pop-in. A "Haptic feedback" setting (default on) gates haptics; system animator scale 0 disables motion.
- **REQ-MO-02** Review Accept: confirm haptic, card slides right 40 dp + fades + collapses (250 ms). Discard: reject haptic, slides left. Garment cards, primary buttons and chips use press scale.
- **REQ-MO-03** AI entry points (smart search toggle, Similar, Suggest, Review prompts, Refine) use the AI style: warm gradient with a light pass every ~3 s, continuous while working.

## Taxonomy (R15)
- **REQ-TX-01** Two new default categories: `category-socks` ("Socks", description "socks or tights: hosiery worn on the feet and legs") and `category-underwear` ("Underwear", "underwear: bra, briefs, boxers or lingerie worn under clothes"), `suggestOccasions = false`, localized EN/ES/DE, seeded on existing installs (respecting tombstones), included in the scoring labels.
- **REQ-TX-02** No other default category changes. Existing garments are not re-tagged automatically.
