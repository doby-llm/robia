# TASK-C02 — Manage › Collections tab

## Goal
After C01 the Manage screen shows a new tab automatically (tabs come from categories, `ui/ManageTagsScreen.kt:131`),
but it would be titled with the stored English name in ES/DE (`localizedName` falls to `else`, :973-979), use a grey
dot and a generic icon, show the tag editor's description field and "suggest occasions" switch (meaningless for
collections), and delete a collection with no confirmation (`RobiaApp.kt:1494`). This task makes the tab look like
mockup frames 1, 2 and 7 and prepares the ± entry point that C05 wires.

## Read first
SPEC R4-D05, R4-D07, REQ-COL-02/03/09; mockup frames 1, 2, 7 and the copy table; `ui/ManageTagsScreen.kt`
(`ManageTagsScreen` :107-120, `ManageTabPage` :281-361, `TagCategoryHeader` :364-409, `TagListRow` :412-469,
`TagEditorDialog` :592-716, `localizedName` :973-979, `dotColor` :982-988, `RestorableTagCategoryIds` :1008);
`ui/CategoryIcons.kt:14-19`; `ui/RobiaApp.kt` the `RobiaRoute.ManageTags ->` branch (~3158-3171);
`ManagePerformanceContractTest.kt` (forbids `Card(` in ManageTagsScreen.kt).

## Steps
1. New pure file `ui/CollectionManageRules.kt` (no Compose):
   `fun showsMembersAction(categoryId: String, hasCallback: Boolean): Boolean` (true only for `COLLECTION_CATEGORY_ID` and callback present);
   `fun needsDeleteConfirmation(tag: GarmentTag): Boolean` (true only for collections);
   `fun showsDescriptionField(categoryId: String): Boolean` (false for collection); reuse for the switch
   (it already shows only for `category`, keep that).
2. `CategoryIcons.kt`: `"collection" -> Icons.Outlined.CollectionsBookmark` (verified present in material-icons-extended).
3. `ManageTagsScreen`: add parameters `collectionMemberCounts: Map<String, Int> = emptyMap()` and
   `onEditMembers: ((GarmentTag) -> Unit)? = null`; pass both down to `ManageTabPage` and `TagListRow`.
   - `localizedName`: `"collection" -> stringResource(R.string.manage_tab_collections)`; `dotColor`: `"collection" -> MaterialTheme.colorScheme.tertiary`.
   - Collection tab empty state (frame 1): replace the generic "No tags in this category yet." for this category only
     with icon + title `collections_empty_title` + body `collections_empty_body` + filled button `collections_new`
     (opens the same editor as the header's add button).
   - `TagListRow` for collections: second line `collection_garment_count` (plural: "1 garment"/"%d garments") from
     `collectionMemberCounts[tag.id] ?: 0`; when `showsMembersAction(...)` an `IconButton` with
     `Icons.Rounded.PlaylistAdd`, contentDescription `content_edit_collection_members`, placed LEFT of the pencil,
     same 40 dp size and semantics pattern as the existing buttons, tinted `onSecondaryContainer` on a
     `secondaryContainer` circle (frame 2). Other categories' rows are unchanged.
   - `TagEditorDialog` for collections: titles `collections_new` / `collection_edit_title`; hide the description field
     (store `description = null`); the duplicate-name check stays.
   - Delete: in `ManageTabPage`, when `needsDeleteConfirmation(tag)`, show an `AlertDialog` (frame 7): title
     `collection_delete_title` (with name), body `collection_delete_body` (with member count), confirm
     `collection_delete_confirm` in `colorScheme.error`, dismiss `cancel` (existing key — grep it); outside tap = cancel.
     Only the confirm calls the existing `onDeleteTag(tag)`. No `Card(` anywhere in the file.
4. `RobiaApp.kt`, ONLY inside the `RobiaRoute.ManageTags ->` branch: pass
   `collectionMemberCounts = remember(domainItems) { domainItems.filterNot { it.isArchived }.flatMap { item -> item.tags.filter { it.categoryId == COLLECTION_CATEGORY_ID }.map { it.id } }.groupingBy { it }.eachCount() }`
   (adapt to the names available in `RobiaNavHost`; if `domainItems` holds only active items, drop the filter). Do NOT pass `onEditMembers` (C05 does).
5. `manage_tags_body` (EN/ES/DE): append collections to the list, e.g. EN "…occasions, seasons, collections, and the main color palette."
6. Tests: new `CollectionManageRulesTest` (V-C02-1, V-C02-2); strings parity check if not already covered (V-C02-4).

## Done when
V-C02-1 … V-C02-4 green in CI; device row V-C02-D listed in the receipt for the checklist.

## Do not
Add the ± to any other category; add the members route; write `Card(` in ManageTagsScreen.kt; change other categories' copy or behaviour.
