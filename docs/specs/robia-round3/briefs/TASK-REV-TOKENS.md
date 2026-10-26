# TASK-REV-TOKENS — Add and remove colour tokens for the review chips

## Goal
F21 (REQ-FB-21, R3-DEC-052, R3-DEC-054 item 10): the signed chips of the review need a green "added" set and a red "removed" set. Material has no success role, so they become `RobiaExtras` tokens, light and dark, copied from the approved mockup (`mockups/review-redesign-3/build.py`, `ADDREM`). REV-CARD uses them.

## Read first
`core/designsystem/Theme.kt` (`RobiaExtras` ~98, `RobiaLightExtras`/`RobiaDarkExtras` ~105, `MaterialTheme.extras`); `core/designsystem/Color.kt` (light tokens, dark tokens, `RobiaSurfaceContainerLowest` `#FFFFFF`, `RobiaDarkSurfaceContainerLowest` `#211C1A`, `RobiaOnSurfaceVariant`, `RobiaDarkOnSurfaceVariant`); `RobiaColorSchemeTest` (its WCAG helper and pins; do not edit it, copy the helper idea into the new test).

## Files
`core/designsystem/Color.kt`; `core/designsystem/Theme.kt` (`RobiaExtras`, the two extras instances and the new holder only); new `app/src/test/java/com/gusanitolabs/robia/core/designsystem/ReviewChangeColorsTest.kt`.

## Steps
1. `Color.kt`: add the tokens below as `internal val` (names `RobiaAddSign`, `RobiaOnAddSign`, `RobiaAddContainer`, `RobiaOnAddContainer`, `RobiaAddBorder`, the same with `Remove`, and `RobiaDark…` for dark).

   | Token | Light | Dark |
   |---|---|---|
   | add sign | `#2E6B47` | `#8FD3A5` |
   | on add sign (glyph) | `#FFFFFF` | `#0B2615` |
   | add container (chip fill) | `#DCEFE1` | `#1D4430` |
   | on add container (chip text) | `#17432A` | `#C6F2D3` |
   | add border | `#9CC8AA` | `#4F8F66` |
   | remove sign | `#BA1A1A` | `#FFB4AB` |
   | on remove sign (glyph) | `#FFFFFF` | `#5C0A05` |
   | remove container | `#FFDAD6` | `#5E1F19` |
   | on remove container | `#93000A` | `#FFDAD6` |
   | remove border | `#EAA59C` | `#B5554B` |

2. `Theme.kt`: `class RobiaChangeColors(val sign: Color, val onSign: Color, val container: Color, val onContainer: Color, val border: Color)`; `RobiaExtras` gains `addColors` and `removeColors`; fill both instances. Do not touch any `ColorScheme` role (the light scheme stays byte for byte; the remove light values equal the scheme's error roles on purpose, the dark ones differ from `errorContainer` and are the approved look).
3. `ReviewChangeColorsTest`: pin the twenty hex values; contrast ≥ 4.5:1 (light and dark) for sign vs `surfaceContainerLowest`, onContainer vs container, onSign vs sign, for add and remove (the mockup's table gives 6.3–9.9:1, so a failure means a typo); add sign ≠ remove sign. The border is decorative and is not asserted.

## Tests
V-REV-TOKENS-1, V-REV-TOKENS-2 (CI).

## Done when
Both rows pass; CI green; the existing `RobiaColorSchemeTest` untouched and green.

## Do not
Change a scheme role, convert a screen, add a colour scan, or use the tokens anywhere yet.
