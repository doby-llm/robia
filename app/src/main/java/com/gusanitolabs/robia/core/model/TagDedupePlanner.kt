package com.gusanitolabs.robia.core.model

/**
 * A stored tag as the dedupe planner sees it: only the fields that decide whether two rows are the same tag, and whether
 * a row is built-in ([isSystem] covers rows stored as system tags, not only the current default list).
 * [syncRevision] is the row's revision, compared with a tombstone to tell a restored built-in from a ghost.
 */
data class DedupeTag(
    val id: String,
    val categoryId: String,
    val name: String,
    val isSystem: Boolean = false,
    val syncRevision: Long = 0L,
)

/** Drop [dropId] and repoint its garment references to [keepId]. */
data class TagMerge(
    val dropId: String,
    val keepId: String,
)

/**
 * Finds rows that name the same tag in the same category. Within a group the built-in id wins, otherwise the lowest id,
 * so the result is the same on every device and every run. A built-in id is never dropped: when two built-ins share a
 * name, both stay, and custom duplicates merge into the first built-in.
 *
 * [tombstoneRevisionById] maps a deleted tag id to its tombstone revision. A keep row that is a ghost (tombstoned, and
 * not newer than the tombstone) is never merged into: another device deleted it, so moving garments onto it would strip
 * them from every device on the next sync. Both rows survive. A built-in restored after its deletion is newer and still
 * takes merges.
 */
object TagDedupePlanner {
    fun plan(tags: List<DedupeTag>, tombstoneRevisionById: Map<String, Long> = emptyMap()): List<TagMerge> {
        val builtInIds = DefaultTags.tags.mapTo(mutableSetOf(), GarmentTag::id)
        return tags
            .groupBy { tag -> tag.categoryId to TagNameRules.dedupeKey(tag.name) }
            .filterKeys { (_, key) -> key.isNotEmpty() }
            .toSortedMap(compareBy<Pair<String, String>>({ it.first }, { it.second }))
            .values
            .flatMap { group ->
                val ordered = group.sortedWith(compareBy<DedupeTag>({ !it.isBuiltIn(builtInIds) }, { it.id }))
                val keep = ordered.first()
                if (keep.isGhost(tombstoneRevisionById)) return@flatMap emptyList()
                ordered.drop(1)
                    .filter { dropped -> !dropped.isBuiltIn(builtInIds) }
                    .map { dropped -> TagMerge(dropId = dropped.id, keepId = keep.id) }
            }
    }

    private fun DedupeTag.isBuiltIn(builtInIds: Set<String>): Boolean = isSystem || id in builtInIds

    private fun DedupeTag.isGhost(tombstoneRevisionById: Map<String, Long>): Boolean {
        val tombstoneRevision = tombstoneRevisionById[id] ?: return false
        return syncRevision <= tombstoneRevision
    }
}

/**
 * Built-in tags to insert at launch. A built-in the user deleted has a `garment_tag` tombstone, so it is not inserted
 * again; otherwise it would come back every launch and absorb a custom tag of the same name.
 */
object DefaultTagSeeds {
    fun withoutDeleted(defaults: List<GarmentTag>, deletedTagIds: Set<String>): List<GarmentTag> =
        defaults.filter { tag -> tag.id !in deletedTagIds }

    fun forSeed(defaults: List<GarmentTag>, tombstoneRevisionById: Map<String, Long>): List<GarmentTag> =
        withoutDeleted(defaults, tombstoneRevisionById.keys)
}
