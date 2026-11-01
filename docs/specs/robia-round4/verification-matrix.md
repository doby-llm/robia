# Verification matrix — robia-round4

Evidence levels: **CI** = JVM test in `testDebugUnitTest` (name it in the receipt) · **REV** = independent reviewer
checks by reading · **DEV** = Manu on the phone (CHK checklist, plain Spanish) · **REC** = coordinator re-runs a command.

| Row | Task | Level | Assertion |
|---|---|---|---|
| V-C01-1 | C01 | CI | `DefaultTags.categories` contains `collection` (name "Collections", sortOrder 60, isSystem true) and no default tag has `categoryId == "collection"`; existing DefaultTags/seeding tests stay green. |
| V-C01-2 | C01 | CI | `planTagMembership(currentMembers, add, remove)` (pure): adds only non-members, removes only members, an id in both sets is ignored, empty → empty plan. |
| V-C01-3 | C01 | CI (sqlite-jdbc) | Fixture: garment G1 has category + occasion + collections A and B; G2 archived member of A; G3 not a member. Running the SQL of `deleteTagRefsForItems(A,[G1])`, `insertItemTagRefs`, `touchItemsForSync([G1,G3],now)`: G1 keeps category/occasion/B, loses only A; G3 gains A; G2 untouched; G1/G3 `sync_status='Queued'`, `sync_revision=now`, `sync_dirty_at_epoch_millis=now`, `updated_at_epoch_millis` unchanged, photo-guard/retry columns unchanged; archived ids never updated. SQL is read from Dao.kt via the `daoStatement` helper pattern (`DefaultCategorySeedingTest`). |
| V-C01-4 | C01 | CI | Equivalence: local = remote + an unmodified default `collection` row with no tags → `equivalentForUpload` true in both directions; the same row with ≥1 collection tag → false; a renamed/sortOrder-changed default row → false; other categories unaffected. |
| V-C01-5 | C01 | CI | `decideMerge`: remote written by another device on the old app (no collection row), local = same data + empty collection row, no pending work → `RecordBase`; with an unrelated remote-only change → `FastForward`; with a real local collection → not silently overwritten (`Propose` or `UploadLocal` per existing rules). |
| V-C01-6 | C01 | CI | Round trip: snapshot JSON with a `collection` category, 2 collection tags and 3 links → import (`withoutTombstonedTaxonomy` + `mergeSnapshots`) → export keeps all of them. |
| V-C01-R | C01 | REV | Reviewer traces every caller of the new repository method and every writer of `clothing_item_tags`; confirms no path uses `clearTags*` for memberships, and the R4-D04 gate result (grep `updatedAtEpochMillis` in `sync/`) is stated in the receipt. |
| V-C02-1 | C02 | CI | `CollectionManageRules`: ± shown only for `categoryId=="collection"` and a non-null callback; restore-default never offered for collection; editor for collection hides description and suggest-occasions switch. |
| V-C02-2 | C02 | CI | Delete of a collection routes to the confirmation (pure decision function `needsDeleteConfirmation(tag)` true only for collection); the confirmed path calls the existing `onDeleteTag`. |
| V-C02-3 | C02 | CI | `ManagePerformanceContractTest` green; no `Card(` in ManageTagsScreen.kt. |
| V-C02-4 | C02 | CI | Strings: the new keys exist in values, values-es, values-de with the mockup text; three files keep equal entry counts. |
| V-C02-D | C02 | DEV | Manage shows the Collections tab (empty state with "New collection"); create "Viaje", rename, duplicate name refused, rows alphabetical with garment counts; delete asks and garments stay. |
| V-C03-1 | C03 | CI | `CollectionFieldRules`: picker for collection is multi-select and titled "Collections"; row hidden when there are no collections and the garment has none; shown otherwise. |
| V-C03-2 | C03 | CI | Saving a garment through the editor keeps every collection link it had (a pure helper that builds the saved tag set from selected ids is tested with a garment in 2 collections). |
| V-C03-3 | C03 | CI | `BrowseFilterState` with a collection tag and with "Not in any" (unset collection) matches correctly together with an occasion filter (AND across categories, OR within). |
| V-C03-4 | C03 | CI | `categoryTitleRes("collection")` and `categoryIconFor("collection")` return the new resources (no raw id, no Checkroom fallback). |
| V-C03-D | C03 | DEV | Edit a garment: Collections row, multi-select, saved; detail shows it; Filters › Collections incl. "Not in any". |
| V-C04-1 | C04 | CI | Session: toggle, diff (+n/−m), `Undo all`, view filter All/In/Not in, Save enabled only with changes, prune of garments that vanished, closes when the collection id disappears. |
| V-C04-2 | C04 | CI | Leave decision: no changes → Leave; changes → AskSaveDiscard; outside tap → KeepEditing (no write). |
| V-C04-3 | C04 | CI | Paint-drag: anchor in draft → range set to "not in"; anchor not in draft → range set to "in"; outside-range items keep draft; reversing direction restores items that leave the range to their pre-drag state; missing anchor/current → unchanged. |
| V-C04-4 | C04 | CI | Saveable form: session state round-trips through its `List<String>` save/restore representation. |
| V-C05-1 | C05 | CI (contract) | `RobiaApp.kt`: `needsWardrobeUiModels` uses `is RobiaRoute.CollectionMembers`; the route is excluded from the bottom bar; the ManageTags branch passes `onEditMembers` that pushes the route. |
| V-C05-2 | C05 | CI (contract) | Save path: the screen's save calls `tagRepository.setTagMembership` inside `launchReviewWrite` and returns `UpsertItem` for exactly the changed ids; no `clearTags` in the new file. |
| V-C05-3 | C05 | CI (contract) | The screen owns its own `BrowseFilterState`, `SearchController` and drag arm (never the shell's `browseFilters`/`selectedBrowseItemIds`); pinch does not call `onBrowseColumnsChange`. |
| V-C05-4 | C05 | CI | `DarkThemeBrowseScanTest` includes `CollectionMembersScreen.kt` and passes. |
| V-C05-5 | C05 | REV | Reviewer walks every exit (system back, predictive back, top-bar arrow, Save, collection deleted remotely, empty wardrobe, model absent) from the empty, unchanged and changed states, and confirms nothing is written except by Save, and a Discard writes nothing. |
| V-C05-6 | C05 | REV | Reviewer confirms Browse behaviour unchanged (diff of BrowseGrid.kt is only the two visibility words). |
| V-C05-D | C05 | DEV | Manage › Collections › ± opens the screen; tap and drag add/remove with badges; All/In/Not in; search (with model) and filters; Back with changes asks; Save closes and the count updates; Browse filter by that collection shows the same garments; after Drive sync on a second phone (if available) memberships appear. |
| V-P01-1 | P01 | CI | `compileSdk = 36`, `targetSdk = 36`; CI green (`testDebugUnitTest assembleDebug assembleStaging`). |
| V-P01-2 | P01 | REV | Receipt lists each Android 16 behaviour item of the brief with "checked in code: file:line" or "needs device". |
| V-P01-D | P01 | DEV | On Manu's phone (staging build): app opens, edge-to-edge OK on Browse/Manage/editor (no content under bars), predictive back works on Browse and on a pushed screen, Drive sync works, camera/gallery add works. |
| V-P02-1 | P02 | CI | `ReleaseGates.developerModeAllowed(buildType)` false for "release", true for debug/staging; `handleSettingsClick` consults it (contract test). |
| V-P02-2 | P02 | CI | Settings › General contains the Privacy policy row; URL constant equals the brief's value. |
| V-P02-D | P02 | DEV | Settings › Privacy policy opens the page in the browser. |
| V-P03-1 | P03 | CI | `assets/licenses/third_party.md` lists every downloaded file URL found in `EmbeddingRecipe.MODEL_URL` and `CutoutPack` (test parses both sources and the asset; fails on a missing entry). |
| V-P03-2 | P03 | REV | Licence of each entry has an upstream source link; D-FINE and magic_touch resolved or marked "unresolved — Manu" in `artifacts/data-safety.md`. |
| V-P03-D | P03 | DEV | Settings › Open-source licences shows the list and scrolls. |
| V-P04-1 | P04 | REC | `python3 scripts/check_aab_16kb.py --self-test` passes on the Pi. |
| V-P04-2 | P04 | REC | Without secrets the release job fails at "Configure release signing key" with the existing message (unchanged); with secrets (after G-PLAY) it produces `robia-release-aab` + `robia-release-mapping` artifacts, versionCode = run_number+100, arm64-only `.so`, and the 16 KB check passes. |
| V-P04-3 | P04 | CI | Debug/staging builds unchanged (same task list, still green). |
| V-CHK-1 | CHK | REC | Checklist file exists, uses current main UI labels, staging link + signer of the latest main run. |
| V-POL-1 | POL | REV | Coordinator diff-checks the policy PR against `artifacts/privacy-policy-changes.md` items 1–5 (and the data-safety cross-check of item 3); the URL in `ReleaseGates.PRIVACY_POLICY_URL` opens the updated page. |
