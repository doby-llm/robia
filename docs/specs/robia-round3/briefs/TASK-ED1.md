# TASK-ED1 — Editor smart tool: visible, clipped, safe

## Goal
Manu: "el borrado inteligente no funciona en absoluto" (drag shows nothing, tap shows a circle and then nothing or far-away parts vanish). Evidence (artifacts/inv-r3-cutout.md §2–3): the selection model returns the whole object under the finger, so a tap on the garment selects the whole garment and Remove erases it; a tap on transparent background changes nothing; drags over 600 ms are dropped silently and smart mode draws no trail. This task makes the tool behave predictably; a remaining Android wiring defect, if any, is caught by the dev tap dump in G-ADB1.

## Read first
`ui/PhotoEditorScreen.kt`: `runSmartSelection` l.274-308, `finishGesture` l.310-331 (600 ms rule l.326), `pointerInput` l.454-496 (trail only for brush l.484), `PulseMark` l.555-563, `screenToPhoto` l.846-852; `media/cutout/RefineSession.kt` `apply` l.53-64; `media/cutout/MaskOps.kt` `labelComponents` l.148; `artifacts/inv-r3-d.md` (whole file); merged PR #134 (grey selector background, `selectPixelOf`).

## Files
`ui/PhotoEditorScreen.kt`; new `media/cutout/SelectionClip.kt`; new `app/src/test/java/com/gusanitolabs/robia/media/cutout/SelectionClipTest.kt`; new `app/src/test/java/com/gusanitolabs/robia/ui/SmartGestureTest.kt` (plus imports, the three `strings.xml`, new tests).

## Steps
1. `SelectionClip.kt` (pure, JVM-testable): `fun clipToTouched(selection: FloatArray, w: Int, h: Int, touched: List<Pair<Int,Int>>, threshold: Float = 0.5f): FloatArray` keeps only 8-connected components (of `selection >= threshold`) that contain at least one touched pixel; soft values preserved inside kept components. `fun removesMostOfGarment(selection: FloatArray, alpha: FloatArray, fraction: Float = 0.60f): Boolean`.
2. Gesture: remove the `SMART_DRAG_MAX_MS` rule; draw the trail in smart mode with the same style as the brush trail but dashed; on release sample the path to ≤ 12 points evenly by path length (pure function `samplePath(points, max = 12)` in the screen file's companion or a small internal top-level function, tested by `SmartGestureTest`).
3. `runSmartSelection`: after the union, `clipToTouched(union, w, h, sampledPixels)`. In Remove mode, if `removesMostOfGarment(clipped, currentAlpha)` → do not apply, show a snackbar/inline hint `editor_smart_whole_garment` = "That selects the whole garment. Tap the background or hanger pieces, or use the brush." In Keep mode apply as today.
4. Developer mode (`RobiaSettings.developerModeEnabled`, read from `app.settingsRepository.settings`): overlay the raw union mask (terracotta at 40 %) for 1 s after each selection, and write `cutout-debug/editor/<timestamp>/{input.png, mask.png, clipped.png, points.json}` under `getExternalFilesDir(null)`.
5. Strings: change EN value of `redo_cutout` to "Cut out again" (ES "Volver a recortar", DE "Erneut freistellen"); keep the id.

## Tests
V-ED1-1..3 as listed.

## Done when
V-ED1-1, V-ED1-2, V-ED1-3, V-ED1-4, V-ED1-D pass; CI green.

## Do not
Change `InteractiveSelector`, the brush, Light, the undo stack (ED2) or Keep semantics.
