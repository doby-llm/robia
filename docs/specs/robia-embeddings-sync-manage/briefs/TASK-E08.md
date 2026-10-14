# TASK-E08 — Tag description, suggest-occasions switch, review flow

## Goal
(1) In the tag editor the user can write a short description (improves suggestions) and, for categories, switch off "Suggest occasions for this category". (2) When the user adds, renames or deletes a category/occasion, or restores defaults, Robia opens a review screen with only the affected garments and proposed changes; nothing is applied without "Accept".

## Read first
Mockups `manage.html` (editor dialogs) and `tag-review.html`. DEC-010, TS§3.10. `ui/ColorReviewScreen.kt` (copy its structure) and in `RobiaApp.kt` how colour review is triggered and committed: `saveMainColorAndOfferReview`, `commitColorReview`, `RobiaRoute.ColorReview`. `ReviewPlanner` (E04). `TagEditorDialog` in `ManageTagsScreen.kt` (after M01).

## Steps
1. TagEditorDialog: description `OutlinedTextField` (max 200 chars, counter, placeholder + helper from tasks.md E08); switch row shown only when `categoryId == "category"`. Save passes both fields into the existing tag save path (D01 already persists/syncs them).
2. `TagChangeSet` built in `RobiaApp.kt` at the three handlers (`onSaveTag` add/rename, `onDeleteCustomTag`, `onRestoreDefaultTags`) only for categoryId `category`/`occasion`; description-only edits and switch toggles produce no change set. Persist the tag change first (unchanged behaviour), then plan.
3. Model not Ready → toast `tag_review_needs_model`; plan empty → toast `tag_review_nothing`; else set `activeTagReviewChangeSet` and `replaceRoute(RobiaRoute.TagReview)`.
4. `ui/TagReviewScreen.kt` per mockup: header card (operation text, progress, skipped count), 2-column grid of proposal cards (photo, current chip → proposed chip with ▾ picker, Accept/Accepted), bottom button "Save N changes" (or "Done" when nothing accepted). Picker: single choice for category (+ "No category" for deletion), multi-choice + "None" for occasion.
5. Commit: build updated items (only accepted) and call the existing `onSaveItems`. Back/discard → dialog like colour review; discarding keeps the tag change.
6. New route `TagReview` (bottom bar hidden).
7. Strings from mockup tables.

## Tests
`TagChangeSetTest` (V-E08a): builder returns the right change set per trigger; null for description-only/flag-only edits.

## Done when
CI green; V-E08a; device V-Z07 in Z01.
