# TASK-K03 — Crisp edges in the hanger band

## Goal
Manu's red shorts: sides and hem crisp, top edge (where the hanger was) blurred. Causes (inv-r3-f.md §3, certain in code): inside the band the selection comes from the filled photo but the guided filter uses the original photo as guide, the band boundary is a 0/1 step the filter then blurs, and hanger-coloured pixels just outside the kill keep alpha.

## Read first
`artifacts/inv-r3-f.md` §3; `MaskOps.refineAlpha` l.316-327 and `luminance` l.330; `PreciseCutoutEngine.alpha()` l.152-170 and `hangerFirst`; `HangerRemoval.fill/applyBand`; `tools/cutout-diag/reference_stages.py` (`finish` call) and `compare.py` (`transition_width`).

## Files
`media/cutout/PreciseCutoutEngine.kt`; `media/cutout/HangerRemoval.kt`; `media/cutout/MaskOps.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/HangerRemovalTest.kt`; `app/src/test/java/com/gusanitolabs/robia/media/cutout/MaskOpsTest.kt`; `tools/cutout-diag/reference_stages.py` (plus imports, the three `strings.xml`, new tests).

## Steps
1. Pass the band (rows < bandBottom) and the filled photo to S5. Inside the band the guided filter uses the luminance of the filled photo; outside, the original (unchanged).
2. Remove the artificial 0/1 step: apply the band's selection with a 3-px linear blend across the band bottom instead of a hard switch.
3. Decontaminate: for pixels with alpha > 0 within 2 px of the dilated kill support, replace RGB by the fill colour before compositing the final cutout.
4. Apply the same three changes in `tools/cutout-diag/reference_stages.py` (planner reference) so the parity check keeps comparing like with like; note the change in the script docstring.

## Tests
V-K03-1, V-K03-2 with synthetic arrays.

## Done when
V-K03-1, V-K03-2, V-K03-3, V-K03-D pass; CI green; independent review done (`PROMPT_REVIEW.md`).

## Do not
Change the edge filter outside the band, the S1–S4 stages, or `AlphaRefiner`.
