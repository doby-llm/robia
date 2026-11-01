package com.gusanitolabs.robia.ui

/** Which garments the members screen lists: everything, only draft members, or only garments outside the draft. */
enum class MembersView { All, InCollection, NotIn }

/**
 * What the screen does on back. "Outside tap = Keep editing" is a UI dismiss of the dialog, not a value here:
 * it never writes, so the session is left untouched.
 */
enum class LeaveDecision { Leave, AskSaveDiscard }

/**
 * Draft membership of ONE collection while its members screen is open. Nothing is written until the caller saves
 * [draft]; [saved] is what the screen opened with. Pure: no Android or Compose types.
 */
data class CollectionMembersSession(
    val collectionId: String,
    /** Members when the screen opened (active garments only). */
    val saved: Set<String>,
    val draft: Set<String>,
    val view: MembersView = MembersView.All,
) {
    /** Garments that the draft adds to the saved membership. */
    val added: Set<String> get() = draft - saved

    /** Garments that the draft removes from the saved membership. */
    val removed: Set<String> get() = saved - draft

    val hasChanges: Boolean get() = added.isNotEmpty() || removed.isNotEmpty()

    fun toggle(id: String): CollectionMembersSession =
        copy(draft = if (id in draft) draft - id else draft + id)

    fun withDraft(newDraft: Set<String>): CollectionMembersSession = copy(draft = newDraft)

    /** Resets the draft to [saved]. The view is kept: Undo all is not a navigation. */
    fun undoAll(): CollectionMembersSession = copy(draft = saved)

    fun withView(v: MembersView): CollectionMembersSession = copy(view = v)

    /** [orderedIds] filtered by [view], judged on DRAFT membership, in the order given. */
    fun visible(orderedIds: List<String>): List<String> = when (view) {
        MembersView.All -> orderedIds
        MembersView.InCollection -> orderedIds.filter { it in draft }
        MembersView.NotIn -> orderedIds.filter { it !in draft }
    }

    /**
     * Drops garments that are no longer active (archived or deleted) from [saved] and [draft], so they neither
     * count as removed nor stay in the draft.
     */
    fun pruned(activeIds: Set<String>): CollectionMembersSession =
        copy(saved = saved.intersect(activeIds), draft = draft.intersect(activeIds))

    /** Back with changes asks; without changes it leaves. Never mutates the session. */
    fun leaveDecision(): LeaveDecision = if (hasChanges) LeaveDecision.AskSaveDiscard else LeaveDecision.Leave

    /**
     * Flat form for `rememberSaveable`: `[collectionId, view, "s:<id>"…, "d:<id>"…]`, ids sorted so the output is
     * stable. Inverse of [fromSaveable].
     */
    fun toSaveable(): List<String> = buildList {
        add(collectionId)
        add(view.name)
        saved.sorted().forEach { add(SAVED_PREFIX + it) }
        draft.sorted().forEach { add(DRAFT_PREFIX + it) }
    }

    companion object {
        private const val SAVED_PREFIX = "s:"
        private const val DRAFT_PREFIX = "d:"

        /** A session at the start of editing: draft equals saved, view All. */
        fun open(collectionId: String, members: Set<String>): CollectionMembersSession =
            CollectionMembersSession(collectionId = collectionId, saved = members, draft = members)

        /** Restores a session from [toSaveable]; returns null for any list that does not match that shape. */
        fun fromSaveable(list: List<String>): CollectionMembersSession? {
            if (list.size < 2) return null
            val view = MembersView.entries.firstOrNull { it.name == list[1] } ?: return null
            val saved = mutableSetOf<String>()
            val draft = mutableSetOf<String>()
            for (entry in list.drop(2)) {
                when {
                    entry.startsWith(SAVED_PREFIX) -> saved += entry.removePrefix(SAVED_PREFIX)
                    entry.startsWith(DRAFT_PREFIX) -> draft += entry.removePrefix(DRAFT_PREFIX)
                    else -> return null
                }
            }
            return CollectionMembersSession(collectionId = list[0], saved = saved, draft = draft, view = view)
        }
    }
}

/** False once the collection has vanished (deleted here or on another phone via sync): the screen then closes. */
fun collectionStillExists(collectionId: String, availableTagIds: Set<String>): Boolean =
    collectionId in availableTagIds
