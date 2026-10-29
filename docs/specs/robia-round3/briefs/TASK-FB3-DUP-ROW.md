# TASK-FB3-DUP-ROW — Duplicate rows without "Untitled garment"

## Goal
ui3 F27 (a) (REQ-FB-27, R3-DEC-062). Garments have no names, so every row of the duplicate review says "Untitled garment". Show something that tells garments apart, or nothing.

## Read first
`artifacts/inv-f3-b.md` F27 (a); `R3-DEC-062`; `ui/DuplicateReviewScreen.kt` (`DuplicateGarmentRow`, `DuplicatePhoto`); `ui/BrowseCardText.kt` (`cardTitle`, `cardMeta`, `CARD_META_SEPARATOR`, `browseCollator`, `UiTag` is in `ui/BrowseGrid.kt`); `ui/BrowseGrid.kt` where a `ClothingItem` becomes `UiTag(tag.id, tag.categoryId, tag.localizedTagLabel(context))`; `ui/TagReviewCard.kt` (`DisplayColorLabel.colorStringRes`, private: the colour words of the add flow); `BrowseCardTextTest` (style).

## Files
`ui/DuplicateReviewScreen.kt` (`DuplicateGarmentRow` and the inputs of its label only); `ui/BrowseCardText.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/DuplicateRowTextTest.kt`.

## Steps
1. In `BrowseCardText.kt` add `internal fun duplicateRowLine(name: String, tags: List<UiTag>, colourLabel: String?, collator: Collator): String?`: (1) the trimmed `name` when not blank; (2) else the known parts among the category label (the first tag whose `categoryId` is `"category"`) and `colourLabel` (when not blank), joined with the existing separator (one part alone shows alone); (3) else `cardMeta(tags, collator)` when not empty; (4) else `null`.
2. In `DuplicateReviewScreen.kt`, `DuplicateGarmentRow` builds its `UiTag` list from `item.tags` with `localizedTagLabel(context)` (as `BrowseGrid.kt` does), the colour label from `item.colorMetrics.primaryDisplayLabel` with a private `DisplayColorLabel` → `R.string.color_*` mapping next to the row (the same strings the add flow and the review card use; `Unknown` or null → no colour), and draws the `Text` only when `duplicateRowLine(...)` is not null. Remove the `browse_similar_untitled` fallback from this file (the string stays: `BrowseGrid.kt`, `ItemDetailScreen.kt` and `BrowseFilterBar.kt` use it). The 72 dp photo and the Keep/Delete chips stay as they are; a row with no line shows photo and chips only.
3. **Tests** (`DuplicateRowTextTest`, V-FB3-DUP-ROW-1): the name wins over everything; a whitespace name counts as blank; category and colour → "Dresses · Black"; category alone; colour alone; occasions then seasons in collator order when there is neither category nor colour; nothing at all → null.
4. Grep for the receipt: `browse_similar_untitled` in `DuplicateReviewScreen.kt` (zero) and in the three other files (still there).

## Tests
V-FB3-DUP-ROW-1 (CI); V-FB3-DUP-ROW-2 (REV); V-FB3-DUP-ROW-D (Manu).

## Done when
Rows pass; CI green.

## Do not
Touch the group card, the tier line, the buttons or the empty text (FB3-DUP-DONE owns the end of the review); remove `browse_similar_untitled`; edit `ui/TagReviewCard.kt` (it belongs to the review chain: copy the small colour mapping instead).
