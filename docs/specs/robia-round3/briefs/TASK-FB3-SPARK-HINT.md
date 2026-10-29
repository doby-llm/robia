# TASK-FB3-SPARK-HINT — Visible spark icon in the review prompts; drop the skip hint

## Goal
ui3 F21 + F25 (REQ-FB-22, R3-DEC-057). (F21) In dark mode the sparkle on the "Review garments" and "Review colors" buttons cannot be seen. (F25) The line "Tap a change to skip it, then apply or reject. Reviewed garments leave the list." is redundant and goes.

## Read first
`artifacts/inv-f3-a.md` F21 and F25; `R3-DEC-057`; `core/designsystem/Theme.kt` (`RobiaExtras`, `RobiaLightExtras`, `RobiaDarkExtras`) and `Color.kt` (`RobiaOnTertiaryContainer`, `RobiaDarkTertiaryContainer`, `RobiaDarkPrimary`); `ui/TagReviewScreen.kt` (`TagReviewPromptDialog`, the pinned header `Column` of `TagReviewScreen`); `ui/ColorReviewScreen.kt` (`ColorReviewPromptDialog`); `RobiaColorSchemeTest`.

## Files
`core/designsystem/Color.kt`; `core/designsystem/Theme.kt` (`RobiaExtras`, `RobiaLightExtras`, `RobiaDarkExtras` only); `ui/TagReviewScreen.kt` (the icon tint of `TagReviewPromptDialog` and the `review_hint_changes` text only); `ui/ColorReviewScreen.kt` (the icon tint of `ColorReviewPromptDialog` only); the three `strings.xml` (delete one key); `app/src/test/java/com/gusanitolabs/robia/core/designsystem/RobiaColorSchemeTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/TagReviewCardModelTest.kt` (the key list only).

## Steps
1. **Cause, for the receipt.** The confirm button is a filled `Button`, so its container is `primary` (dark `#EAD7D1`). The icon is tinted `onTertiaryContainer`, dark `#FFDCC7`: about 1.1:1. In light it is `#C37F50` on `#1D100D` (about 5.7:1), so it was never noticed.
2. **Token.** Add `sparkOnPrimary: Color` to `RobiaExtras` (a field with no default; `RobiaExtras(` is built only in `Theme.kt`). Light = `RobiaOnTertiaryContainer` (the light value stays byte for byte). Dark = a new named constant in `Color.kt`, `Color(0xFF6D390F)` (the value of `RobiaDarkTertiaryContainer`, about 6.8:1 on `#EAD7D1`); name it for what it is (for example `RobiaDarkSparkOnPrimary`), do not alias the scheme role.
3. **Use it** as the `tint` of the `Icons.Outlined.AutoAwesome` `Icon` in `TagReviewPromptDialog` and in `ColorReviewPromptDialog` (`MaterialTheme.extras.sparkOnPrimary`). Do not touch the same tint in `ItemDetailScreen`, `AddEditClothingScreen` or `BrowseFilterBar`.
4. **Hint.** Delete the `Text(stringResource(R.string.review_hint_changes), ...)` of the pinned header in `TagReviewScreen`, the key `review_hint_changes` in the three `strings.xml`, and `"review_hint_changes"` in the key list of `TagReviewCardModelTest`. Keep `color_review_tap_hint` (the colour review's own line; a different string).
5. **Tests.** In `RobiaColorSchemeTest`: `RobiaLightExtras.sparkOnPrimary == RobiaOnTertiaryContainer`; `RobiaDarkExtras.sparkOnPrimary == Color(0xFF6D390F)`; the contrast of `sparkOnPrimary` over `primary` is at least 3.0 in both schemes (the file has a `contrast` helper). The light hex pins and the dark table tests stay untouched and green.
6. Grep before you finish: `review_hint_changes` has no hit anywhere under `app/src`.

## Tests
V-FB3-SPARK-HINT-1, -2 (CI); V-FB3-SPARK-HINT-3 (REV); V-FB3-SPARK-HINT-D (Manu).

## Done when
Rows pass; CI green; the receipt gives the grep result of step 6 and the two contrast numbers.

## Do not
Change any `ColorScheme` role, the other `onTertiaryContainer` users, `SparkleBurst`, `color_review_tap_hint`, or add a colour literal outside `Color.kt`.
