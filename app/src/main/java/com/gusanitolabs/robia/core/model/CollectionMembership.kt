package com.gusanitolabs.robia.core.model

/** The system category that holds collections as ordinary custom tags (R4-D01). */
const val COLLECTION_CATEGORY_ID = "collection"

/** The links one membership change writes: [add] are new links, [remove] are links that existed. Both sorted. */
data class MembershipPlan(val add: List<String>, val remove: List<String>) {
    val changed: List<String> get() = (add + remove)
}

/**
 * Pure plan for one tag's membership change. [add] keeps only ids that are not members yet, [remove] keeps only members,
 * and an id listed in both inputs is ignored. Outputs are sorted so the plan is deterministic.
 */
fun planTagMembership(currentMembers: Set<String>, add: Set<String>, remove: Set<String>): MembershipPlan {
    val contradictory = add intersect remove
    return MembershipPlan(
        add = (add - currentMembers - contradictory).sorted(),
        remove = ((remove intersect currentMembers) - contradictory).sorted(),
    )
}
