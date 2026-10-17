# TASK-U08 — Review and AI feel

## Goal
Accept/Discard and AI buttons feel flat (R14). Add haptics, a directional exit for review cards and the AI shimmer style to AI entry points, without touching review logic (mockup `motion.html`).

## Read first
TS§8; `ui/ReviewScaffold.kt` (`ReviewAcceptedCard` 107-130, `ReviewBottomBar` 266-295; comment at line 61: colour and tag reviews must look identical); `ui/TagReviewScreen.kt` 615-740; `ui/ColorReviewScreen.kt` 348-420; sparkle `IconButton` in `ui/BrowseFilterBar.kt`; Similar button in `ui/ItemDetailScreen.kt`; `core/designsystem/Motion.kt`, `Haptics.kt`.

## Steps
1. `ReviewAcceptedCard(…, direction: ExitDirection = Accept)`: exit = `slideOutHorizontally { ±40.dp px } + fadeOut + shrinkVertically`, 250 ms, keeping the existing 200 ms hold. Discarded cards (if they currently vanish) use the same component with `Discard` direction — only where the existing code already removes the card; do not add a new hold or state.
2. At the Accept click: `haptic(Confirm)` then the existing handler; Discard: `haptic(Reject)`. Same in both review screens.
3. `pressScale` on the per-card Accept/Discard buttons and `ReviewBottomBar` button.
4. AI style: sparkle button in Browse and Similar button in detail get `aiSurface(active = <existing busy flag if any, else false>)`; review prompt dialogs' primary AI action too (`ColorReviewPromptDialog`, `TagReviewPromptDialog` buttons).

## Tests
None new required (pure logic unchanged). Receipt must show `git diff` limited to modifiers/animation/haptic lines in review files (V-U08-1).

## Done when
V-U08-1, CI green.

## Do not
Change accept/discard/commit/undo logic, counters, dialogs' text or order.
