# Robia round 4 — SPEC (planning package, status: APPROVED 2026-11-01)

Planner: Claude Opus 5.5 (planning only). Execution: coordinator + workers per `PROMPT_COORDINATOR.md`.
Source request: `feedback/2026-10-30-manu-telegram.md` (verbatim). Base: `main` @ `0ceb916`.
Evidence (file:line, read-only investigations): `artifacts/inv-r4-collections-sites.md`, `artifacts/inv-r4-member-screen.md`,
`artifacts/inv-r4-mlkit-embeddings.md`, `artifacts/inv-r4-play-readiness.md`, `artifacts/inv-r4-cleanup.md`.
Mockup: `mockups/collections.html` (captions ES, UI copy EN, copy table EN/ES/DE at the end).

Scope of the round:
- **A. Collections** (feature) — §1–§6, tasks C01–C05.
- **B. Play Store readiness** — §7, tasks P01–P04 + POL now; Play Console upload (G-PLAY, P05) deferred (R4-DEC-014).
- **C. ML Kit Embeddings API** — §8, decision only, no task.
- **D. Workspace cleanup** (host, not app) — §9, task CLN (coordinator-run, closed allowlist).
- Device validation — task CHK.

---

## 1. Decided behaviour (Manu, 2026-10-30)
- A user creates and deletes arbitrary **collections** in Manage, in a new "Collections" tab that starts empty.
- A garment can belong to **0..N collections** (multi-select). There is no overlap/overwrite logic.
- The garment editor gets a Collections field, like Category or Occasion.
- A new **± (add/remove garments) icon** sits LEFT of the pencil on each collection row, **only for collections**
  (withdrawn for other categories: do not reintroduce). It opens a Browse-like members screen with filters, semantic
  search and drag selection.

## 2. Proposed defaults (approved with the packet unless Manu changes them)
| ID | Decision | Mechanism / reason |
|---|---|---|
| R4-D01 | Collections are a new **system TagCategory** `id="collection"`, `name="Collections"`, `sortOrder=60`, `isSystem=true`, seeded by `DefaultTags.categories`. Collections themselves are ordinary custom tags (`isSystem=false`). | Reuses tag storage, the `clothing_item_tags` join (already N:M), Drive sync, tombstones, filters. INSERT IGNORE seeding reaches existing installs at next launch (`LocalRepositories.kt:244`). **No Room migration** (DB stays v15), **sync schema stays 2** (`SyncModels.kt:243`; a bump would block older phones). `isSystem=true` keeps it out of the Analytics "custom" section (`WardrobeStats.kt:63-71`); custom tags must stay `isSystem=false` or the delete SQL silently refuses (`Dao.kt:459-466`). |
| R4-D02 | Collections are **never predicted, reviewed, embedded or used in semantic search.** | Already true: predictions/review only cover `category`/`occasion` (`SuggestionService.kt:115,121`, `TagReviewScreen.kt:101`); garment text vector = name + notes (`EmbeddingRecipe.kt:76-80`). No `RECIPE_ID` change → no re-embedding. To find garments of a collection the user uses the filter. |
| R4-D03 | **Snapshot equivalence ignores a category row that equals its built-in default and has no tags** in that snapshot (both sides, symmetric). | Without it, every existing multi-device user gets the merge proposal after the upgrade: the new empty row makes `equivalentForUpload` false (`SnapshotEquivalence.kt:18-19,52-63`) → `decideMerge` rule 6 `Propose` (`MergeDecision.kt:51-74`). Once a collection exists the row is non-empty and is compared as today. |
| R4-D04 | Membership writes go through ONE new `TagDao` `@Transaction setTagMembership(tagId, add, remove, now)`: insert/delete only that tag's links, and mark only the changed, non-archived garments `Queued` with `sync_revision = sync_dirty_at = now`, **without** changing `updated_at` and without touching photo-guard/retry fields. Caller enqueues `UpsertItem` per changed garment via `launchReviewWrite`. | Never `clearTags`/`clearTagsForItems` (wipe all tags of a garment, `Dao.kt:336,447`). Not bumping `updated_at` keeps Browse order (`ORDER BY is_favorite DESC, updated_at DESC`, `Dao.kt:18`) stable after a 30-garment save; the mapping revision already uses `sync_revision` (`LocalWardrobeSyncSnapshotRepository.kt:330,355`). **Gate in C01:** if any sync conflict path compares `updatedAtEpochMillis` of garments instead of `revision`, use the pre-approved fallback: also set `updated_at = now` (same as tag review, `ReviewChanges.kt:204-226`). |
| R4-D05 | Deleting a collection asks for confirmation (error colour, states what is lost: the collection and its memberships, never the garments). Other categories keep today's behaviour. | Today delete has no review/undo for non-reviewed kinds (`RobiaApp.kt:1494`, `TagReviewScreen.kt:393`). Delete path itself is safe: removes links + tag + tombstone, no garment write (`Dao.kt:519-524`). |
| R4-D06 | Editor/detail/filter surfaces: AddEdit gets a multi-select **Collections** row (shown only when ≥1 collection exists or the garment already has one); Item detail shows a Collections row only when the garment has ≥1; Advanced filters get a Collections card (only when ≥1 collection exists) with a "Not in any" option; filter chip title + icon branches. **Not in v1:** export field, share text, card text, analytics, creating a collection from the editor sheet, "add selected to collection" from Browse multi-select. | Standing rule: controls without meaning are hidden. Every listed site is hardcoded today (`AddEditClothingScreen.kt:1137-1196`, `ItemDetailScreen.kt:539-566`, `AdvancedFiltersScreen.kt:124-127,194-233`, `BrowseFilterBar.kt:579-585`, `CategoryIcons.kt:14-19`). The picker today would be titled "Seasons" and single-select for an unknown id (`AddEditClothingScreen.kt:1191,1196`) — C03 fixes it. |
| R4-D07 | Collection editor dialog: name only (no description field, no "suggest occasions" switch), titles "New collection"/"Edit collection", duplicate check as for tags. | Description exists only to feed label embeddings (`labelTextFor`), irrelevant for collections. |
| R4-D08 | Two collections with the same name created on two phones are merged at launch (memberships united) by the existing dedupe. | `TagDedupePlanner.kt:36-47`; consistent with all other tags. |
| R4-D09 | Removed memberships use the same union merge as every garment-tag link today (no per-link tombstone). | Normal sync uploads the local snapshot (own lineage) so a removal sticks; only an explicit merge proposal can re-add a removed link (`WardrobeSyncOutboxProcessor.kt:1940-1944`). Same exposure as removing an occasion today → follow-up, not in scope. |

## 3. Members screen (R4-D10..D16) — see mockup frames 3–6
- **R4-D10 Entry/exit.** Route `CollectionMembers(tagId)` pushed from the ± icon. Own top bar: back arrow, title = collection name, subtitle "N in collection" (live: `initial → draft`). No bottom navigation bar on this route.
- **R4-D11 Content.** All **active** (non-archived) garments, Browse order, current Browse layout and column count (pinch disabled, setting untouched). Each tile has a check badge: filled = in the draft. A tile whose state differs from the saved state shows a signed badge "+ added" (green) or "removed" (error colour, strike-through) — colour is never the only signal.
- **R4-D12 View switch.** Segmented control `All / In collection / Not in` (default All), combined with filters and search.
- **R4-D13 Search & filters.** Search field identical to Browse (visible only when the embedding model is Ready; same ranking via its own `SearchController` instance; `EmbeddingEngineHolder.preload()` on open). Filter chip row + Advanced filters as an **overlay with its own `BrowseFilterState`** (never edits the Browse filters). The Collections filter card is hidden on this screen.
- **R4-D14 Selection.** Tap toggles one garment. Long-press + drag **paints**: the anchor's new state (= opposite of its draft state) is applied to every garment in the dragged range; garments outside the range keep their draft state; autoscroll at edges, two fingers cancel (same gesture rules as Browse `DragSelect`). Light haptic tick per toggle.
- **R4-D15 Save.** Bottom bar: hint text while nothing changed (Save disabled); with changes: chips `+n` / `−m`, `Undo all`, `Save`. Save = one `setTagMembership` call, then the screen closes with the standard pop transition. No snackbar (routine action).
- **R4-D16 Leaving.** Back (system, top-bar arrow, predictive back) with no changes → leaves. With changes → stacked dialog "Save changes to “X”?" — **Save** / **Discard changes** (error colour) / **Keep editing**; outside tap = Keep editing; nothing is written except by Save. The draft survives rotation (`rememberSaveable` as a list) but not process death (nothing was written, acceptable). If the collection disappears while the screen is open (deleted on another phone and synced), the screen closes without writing. Garments that disappear (archived/deleted) are pruned from the draft. Empty wardrobe → empty state, Save hidden.

## 4. Requirements (stable IDs)
- **REQ-COL-01** Category row `collection` exists after first launch on fresh and upgraded installs; no migration; `DefaultTagsTest` and seeding tests pass.
- **REQ-COL-02** Creating, renaming, deleting a collection in Manage; alphabetical list (Collator); duplicate names refused (all locales); editor without description/switch.
- **REQ-COL-03** Delete confirmation for collections only; after delete no garment changed (row count, `updated_at`, sync status of garments untouched), links gone, tombstone written.
- **REQ-COL-04** `setTagMembership` changes exactly the listed links of one tag; other tags of the same garments untouched; archived garments never touched; unchanged garments not queued; changed garments `Queued` with new `sync_revision`; returns number of changed links; empty add/remove → no SQL write.
- **REQ-COL-05** Equivalence rule R4-D03 (both directions, empty-default ignored, non-empty compared); `decideMerge` returns `RecordBase`/`FastForward` (not `Propose`) for "remote from old app without the row, local new app with only the empty row".
- **REQ-COL-06** AddEdit Collections row: multi-select, correct title, hidden per R4-D06, saves membership; batch-edit route inherits it; existing memberships preserved by any edit.
- **REQ-COL-07** Item detail row, Advanced filters card + "Not in any", chip title, icon — per R4-D06.
- **REQ-COL-08** Members screen behaviour R4-D10..D16.
- **REQ-COL-09** EN/ES/DE copy exactly as the mockup table (workers may fix grammar only in ES/DE, never meaning); strings in tagged blocks.
- **REQ-COL-10** Old app reading a new backup stays lossless (no code change; covered by a JSON round-trip test that a `collection` category + tags + links survive `import → export`).

## 5. Non-goals (round 4)
Collections on other categories' ± screen (withdrawn); collection colours/icons/cover photos; export/share/analytics of collections; nested collections; per-link tombstones; changing embeddings or search.

## 6. Risks and how they are closed
| Risk | Closure |
|---|---|
| Upgrade merge prompt (multi-device) | R4-D03 + REQ-COL-05 tests, independent review of C01 |
| Membership write wipes other tags | dedicated SQL, sqlite-jdbc tests with a garment holding category+occasion+2 collections |
| Sync not woken / stale Synced mark | `UpsertItem` ops per changed garment; reviewer traces `markGarmentSynced(id, revision)` with the new revision |
| Photo-guard fields reset | touch SQL updates only status/revision/dirty/failure columns (test asserts guard columns unchanged) |
| Editor open while members saved (stale overwrite) | accepted narrow race, Follow-up F-R4-1 |


## 7. Play Store readiness (B) — audit `artifacts/inv-r4-play-readiness.md`
Policy sources (checked 2026-11-01): Play target-API help (answer 11926878), Device and Network Abuse policy
(answer 9888379), 16 KB page-size guide (developer.android.com/guide/practices/page-sizes), Drive scopes guide,
Play for On-device AI guide, Play size limits (answer 9859372).

### 7.1 Downloading models from Hugging Face/GitHub — allowed, with conditions
- Play forbids downloading **executable code** (dex, jar, .so) outside Play. Robia downloads only **model weights**
  (`.litertlm`, `.onnx`); the native runtimes (LiteRT-LM, ONNX Runtime, MediaPipe) ship in the APK. **Compliant.**
- All 5 URLs return 200 without login, are commit-pinned, size-pinned and SHA-256-checked (u2netp hash re-verified
  by the planner on 2026-11-01). Consent prompt, Wi-Fi default and delete paths exist.
- Residual risks (not policy blockers): a third-party host can disappear or rate-limit (HF commit URLs are stable
  but not under our control); the Data safety form must declare the downloads (no user data is sent, only HTTP GETs).
- Alternative, **not needed for the first release**: Play for On-device AI (AI packs, beta, up to 1.5 GB per pack,
  on-demand delivery, Wi-Fi consent built in). Recorded as follow-up F-R4-PLAY-1 (re-evaluate if HF availability
  becomes a problem or before leaving beta).
- Licences: EmbeddingGemma 2 LiteRT card = Apache-2.0; MobileSAM = MIT; u2netp (rembg) = MIT (the u2net weights are
  Apache-2.0 upstream); **D-FINE ONNX card has no licence field** (upstream D-FINE repo is Apache-2.0 — P03 must
  confirm and record the source); `magic_touch.tflite` (bundled, MediaPipe) — P03 records the source. The app needs
  an "Open-source licences" screen listing them (P03).

### 7.2 Blockers and must-fix items (decided: fix before the first Play upload)
| ID | Item | Mechanism | Task |
|---|---|---|---|
| PLAY-B1 | `targetSdk = 35`, `compileSdk = 35` (`app/build.gradle.kts:48,53`) | Since 2026-08-31 new apps and updates must target **API 36**; uploads are refused otherwise. Raising target changes Android 16 behaviour (edge-to-edge enforced, predictive back on by default, 16 KB pages, orientation/resizability ignored on large screens) | P01 |
| PLAY-B2 | No privacy-policy link in the app; the public policy repo `doby-llm/robia-privacy-policy` (Aug 2026) misses model downloads/collections and its deletion page promises a deletion we cannot perform | Play Console requires a policy URL for apps that handle personal data; the deletion page must be accurate | P02 (in-app link) + POL (policy repo edits) |
| PLAY-M1 | Release AAB never built; release signing secrets `ANDROID_KEYSTORE_*` not set in the repo (only `ROBIA_DEBUG_*` exist) | `release-aab` job (`android-apk.yml:130-188`) fails without them | P04 + Manu (create upload key, add 4 secrets) |
| PLAY-M2 | Drive sign-in on Play builds | Play re-signs with the **App Signing key**: an Android OAuth client with that SHA-1 must exist in Cloud Console, and the OAuth consent screen must be **In production** (testing mode = 100 test users, 7-day tokens). `drive.appdata` is a **non-sensitive** scope → no Google verification/CASA needed | Manu (Console steps in P04 brief) |
| PLAY-M3 | `versionCode = 1` hard-coded | Each upload needs a higher code | P04 (CI-supplied `versionCode`) |
| PLAY-S1 | Developer mode reachable in release (10 taps) | Not a policy issue; exposes benchmark/debug dumps to users | P02 (hide when `!BuildConfig.DEBUG` and build type is release) |
| PLAY-S2 | Release has no `abiFilters` (debug/staging are arm64-only) | Bundle carries x86/armv7 libs that were never tested; Play would offer the app to armv7 phones where the 388 MB model is not viable | P04 (release `abiFilters += "arm64-v8a"`) |
| PLAY-S3 | Staging shares the release applicationId | A staging APK could be mistaken for release; same id blocks side-by-side | **Not changed** (Manu's phone runs staging with that id and local data; a suffix = new app, data lost). Follow-up F-R4-PLAY-2 |
| PLAY-S4 | No R8 mapping uploaded for release | Crash stack traces unreadable in Play Console | P04 (upload `mapping.txt` as CI artifact) |
| OK | Permissions: only INTERNET (+ WorkManager); photos via SAF/system camera; no media permission; `allowBackup=false`; no cleartext; 16 KB alignment passes for all six arm64 `.so` in the staging APK; APK 82.8 MB; no analytics/ads SDK | | — |

### 7.2b Timing (Manu 2026-11-01, R4-DEC-014)
Code-side items (P01–P04, POL) are done in this round because they are independent of the visual refresh. The Play
Console work (G-PLAY, P05: app setup, store listing, Data safety, upload, testing tracks) waits until Manu has the new
screenshots/colours and says go; it may then be run by a browser agent on the GusanitoLabs account
(the.gusanito.lab@gmail.com) — that will be its own small plan (authenticated browser, vault for credentials, no
secrets in chat). If the GusanitoLabs account is a **personal** account created after 2023-11-13, production access
requires a **closed test with ≥12 testers opted in for 14 consecutive days**; that test is the longest lead time and can
start with provisional screenshots (they can be replaced later).

### 7.3 Data safety form (facts for Manu; no task)
Photos and garment data stay on the device; optional backup to the user's **own** Google Drive app-data folder
(`drive.appdata`); account e-mail read only to show which account is connected; model files downloaded from Hugging
Face/GitHub without sending user data; no analytics, ads or tracking; encryption in transit (HTTPS). P03 lists the
Google Play services/ML Kit telemetry the merged manifest declares so the form is accurate.

## 8. ML Kit Embeddings API (C) — decision, no task (`artifacts/inv-r4-mlkit-embeddings.md`)
The notification's premise does not match: the ~388 MB model is **not in the APK**; Robia downloads it on demand.
`com.google.mlkit:genai-embedding:1.0.0-alpha1` is an alpha preview, works only on AICore devices **whose Google
account is enrolled in the AICore Developer Preview**, applies quotas, and Google may swap the model (forced
re-index, thresholds re-calibrated, vectors no longer comparable across the user's phones). **Decision R4-D20:** do
not adopt now; follow-up F-R4-ML-1 = re-evaluate when it is GA without enrolment (optional spike: compare its vectors
on the precision dataset). Not a Play blocker either way.

## 9. Workspace cleanup (D) — host only, task CLN
Inventory (host paths, kept outside the public repo): `~/.local/share/robia-planning/r4/inventory/cleanup.md`
(+ `worktrees.tsv`, `workers.tsv`, `branches_local.tsv`). Classes: DELETE-SAFE (evidence: clean + merged/pushed, no
live process), ASK (Manu decides per item), KEEP. CLN runs only the categories Manu approved on 2026-11-01: **A** (clean/pushed worker clones, worktrees, merged branches), **B** (old APK copies and the stale adb process), **C** (benchmark/research data and venvs) — only where they do not interfere with this plan — and **E** (archive each unique-work worktree as a patch, then remove). **D (remote branches) is NOT approved**, re-verifying each target immediately before deletion (`git status`, `git cherry`, `pgrep`/`lsof`),
and never touches: personal photos (`precision-data/photos_raw`, `r3/feedback-assets`), state/feedback/decision
files, `r4/`, the main checkout, remote branches (unless approved), shared caches beyond the approved Gradle
version, anything outside Robia.
