package com.gusanitolabs.robia.core.model

/** A stored tag as the dedupe planner sees it: only the fields that decide whether two rows are the same tag. */
data class DedupeTag(
    val id: String,
    val categoryId: String,
    val name: String,
)

/** Drop [dropId] and repoint its garment references to [keepId]. */
data class TagMerge(
    val dropId: String,
    val keepId: String,
)

/**
 * Finds rows that name the same tag in the same category. Within a group the built-in id wins, otherwise the lowest id,
 * so the result is the same on every device and every run.
 */
object TagDedupePlanner {
    fun plan(tags: List<DedupeTag>): List<TagMerge> {
        val builtInIds = DefaultTags.tags.mapTo(mutableSetOf(), GarmentTag::id)
        return tags
            .groupBy { tag -> tag.categoryId to TagNameRules.normalize(tag.name) }
            .filterKeys { (_, normalizedName) -> normalizedName.isNotEmpty() }
            .toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))
            .values
            .flatMap { group ->
                val ordered = group.sortedWith(compareBy<DedupeTag>({ it.id !in builtInIds }, { it.id }))
                val keep = ordered.first()
                ordered.drop(1).map { dropped -> TagMerge(dropId = dropped.id, keepId = keep.id) }
            }
    }
}
