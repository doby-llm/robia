# TASK-UI-WHITE — Neutral grey gradient for white garments

## Goal
F5 (REQ-FB-05): in Browse a white T-shirt shows a flat beige/brown backdrop; Manu wants the subtle grey gradient he sees in the add flow. The near-white rule fires correctly (the beige proves it); the VIS stops `#E6E0D9`→`#CFC5BA` are the warm neutral REQ-BR3-02 specified, and the add flow uses `#F3F3F4` toward about `#D1CFCF`.

## Read first
`R3-DEC-041`; planner investigation `ui.md` §F5; `ui/GarmentSwatchBackground.kt` (`NEAR_WHITE_STOPS` ~17, rule ~44-45); `ui/BrowseGrid.kt` `GarmentPhotoPlaceholder` (~778-790, read-only here); `GarmentSwatchBackgroundTest` (`nearWhiteSwatchesGetTheWarmNeutralStops` ~53).

## Files
`ui/GarmentSwatchBackground.kt`; `app/src/test/java/com/gusanitolabs/robia/ui/GarmentSwatchBackgroundTest.kt` (plus imports).

## Steps
1. `NEAR_WHITE_STOPS` becomes top `#F3F3F4`, bottom about `#D6D5D6` (neutral: RGB channel spread < 10). Browse, detail, colour review and tag review follow because they share the helper. The near-white rule is unchanged.
2. Replace `nearWhiteSwatchesGetTheWarmNeutralStops` with equal strength for the new stops (White, Cream/Beige-light, luminance > 0.82 get them; Black/Navy/Burgundy keep their gradients) and add the channel-spread assertion (each stop's max − min channel < 10).

## Tests
V-UI-WHITE-1.

## Done when
V-UI-WHITE-1 passes, V-UI-WHITE-D is ready for Manu; CI green.

## Do not
Change the near-white rule, other colours' gradients, the add-flow backdrops, or theme tokens.
