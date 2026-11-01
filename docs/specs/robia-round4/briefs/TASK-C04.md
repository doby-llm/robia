# TASK-C04 — Members session model and paint-drag (pure Kotlin)

## Goal
The members screen (C05) needs deterministic, testable logic for a draft of who is in ONE collection, the
All / In collection / Not in view, the +n/−m diff, Undo all, leaving with unsaved changes, and a drag that can both
add and remove. The Browse `DragSelect` only adds (`baseline + range`, `ui/DragSelect.kt:52-57`), so a paint mode is
new. Pure logic here, UI in C05, so CI can test it (no Compose UI tests exist).

## Read first
SPEC §3 (R4-D11 … R4-D16), REQ-COL-08; mockup frames 3–6; `ui/DragSelect.kt` (whole file, do not edit);
`ui/DragSelectTest` style in `app/src/test/java/com/gusanitolabs/robia/ui/DragSelectTest.kt`.

## Steps
1. `ui/CollectionMembersSession.kt` (no Android/Compose imports):
   ```kotlin
   enum class MembersView { All, InCollection, NotIn }
   enum class LeaveDecision { Leave, AskSaveDiscard }
   data class CollectionMembersSession(
       val collectionId: String,
       val saved: Set<String>,      // members when the screen opened (active garments only)
       val draft: Set<String>,
       val view: MembersView = MembersView.All,
   ) {
       val added: Set<String>; val removed: Set<String>; val hasChanges: Boolean
       fun toggle(id: String): CollectionMembersSession
       fun withDraft(newDraft: Set<String>): CollectionMembersSession
       fun undoAll(): CollectionMembersSession
       fun withView(v: MembersView): CollectionMembersSession
       fun visible(orderedIds: List<String>): List<String>     // applies view on DRAFT membership
       fun pruned(activeIds: Set<String>): CollectionMembersSession   // drops vanished garments from saved and draft
       fun leaveDecision(): LeaveDecision
       fun toSaveable(): List<String>                          // e.g. ["id", "view", "s:<id>"…, "d:<id>"…]
       companion object {
           fun open(collectionId: String, members: Set<String>): CollectionMembersSession
           fun fromSaveable(list: List<String>): CollectionMembersSession?
       }
   }
   fun collectionStillExists(collectionId: String, availableTagIds: Set<String>): Boolean
   ```
   "Outside tap = Keep editing" is UI behaviour (dialog dismiss) — no function needed; document it in KDoc.
2. `ui/CollectionDragPaint.kt` (no Compose):
   ```kotlin
   class DragPaint(private val anchorId: String, private val before: Set<String>) {
       val targetIn: Boolean = anchorId !in before
       var currentId: String = anchorId; private set
       fun moveTo(id: String)
       fun draft(orderedIds: List<String>): Set<String>
       // range = orderedIds between anchor and current (inclusive); range items set to targetIn;
       // all others = their state in `before` (so shrinking the range restores them); missing anchor/current -> before
   }
   ```
3. Tests: `CollectionMembersSessionTest` (V-C04-1, V-C04-2, V-C04-4) and `CollectionDragPaintTest` (V-C04-3), with
   the reversing-direction and missing-id cases.

## Done when
V-C04-1 … V-C04-4 green in CI.

## Do not
Edit `DragSelect.kt` or any existing file; add UI.
