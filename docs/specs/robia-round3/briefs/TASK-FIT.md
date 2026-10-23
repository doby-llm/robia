# TASK-FIT — Fit filter

## Goal
R3-05.

## Read first
`artifacts/inv-r3-a.md` §A3; `ui/BrowseFilterBar.kt` `BrowseFilterState` l.108-160, chips l.471-520; `ui/AdvancedFiltersScreen.kt` l.120-296.

## Files
`ui/BrowseFilterBar.kt`; `ui/AdvancedFiltersScreen.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/BrowseFilterStateTest.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/PruneUnknownFiltersTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `BrowseFilterState.fit: Set<FitFilter>`; matches/hasActive/count/remove; chip text = option name.
2. Card "Fit" with two chips in Advanced filters (after Occasion).

## Tests
V-FIT-1.

## Done when
V-FIT-1, V-FIT-D pass; CI green.

## Do not
Change fit values or the editor.
