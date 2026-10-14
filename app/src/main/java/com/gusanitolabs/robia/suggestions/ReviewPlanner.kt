package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.GarmentVectors

enum class TagOperation { Added, Renamed, Deleted, RestoredDefault }

/** A category or occasion edit (TS§3.10). `touchedTagIds` are the added or renamed tags, or the deleted ones. */
data class TagChangeSet(
    val id: String,
    val operation: TagOperation,
    val categoryId: String,
    val touchedTagIds: Set<String>,
    val beforeTags: List<GarmentTag>,
    val afterTags: List<GarmentTag>,
)

enum class ProposalKind { Suggested, ChooseManually }

/** The tags one garment would have after the review is accepted. `proposed` is `before` plus the change. */
data class ReviewProposal(
    val garmentId: String,
    val before: Set<String>,
    val proposed: Set<String>,
    val kind: ProposalKind,
)

/**
 * DEC-010: which garments a tag change affects, and the tags each would get. Garments without an IMAGE vector cannot
 * be scored, so they are left out and counted by [skippedCount]; the exception is a deleted category, where they are
 * listed as [ProposalKind.ChooseManually]. Results keep the order of `garments`.
 */
object ReviewPlanner {
    fun plan(
        changeSet: TagChangeSet,
        garments: List<ClothingItem>,
        vectors: Map<String, GarmentVectors>,
        labelVectors: Map<String, FloatArray>,
        tags: List<GarmentTag>,
    ): List<ReviewProposal> {
        if (changeSet.operation == TagOperation.Deleted && changeSet.categoryId == OCCASION) return emptyList()
        val labels = tags
            .filter { it.categoryId == changeSet.categoryId }
            .mapNotNull { tag -> labelVectors[tag.id]?.let { tag.id to it } }
        val allImagesByTag = imagesByTag(garments, vectors)
        return garments.mapNotNull { garment ->
            val image = vectors[garment.id]?.image
            val examples = allImagesByTag.mapValues { (_, entries) -> entries.filter { it.first != garment.id }.map { it.second } }
            when {
                changeSet.operation == TagOperation.Deleted -> deletedCategory(changeSet, garment, image, labels, examples)
                changeSet.categoryId == CATEGORY -> addedCategory(changeSet, garment, image, labels, examples)
                else -> addedOccasion(changeSet, garment, image, labels, examples, tags)
            }
        }
    }

    /** Garments that a tag change could not be scored for because they have no IMAGE vector. */
    fun skippedCount(changeSet: TagChangeSet, garments: List<ClothingItem>, vectors: Map<String, GarmentVectors>): Int {
        if (changeSet.operation == TagOperation.Deleted) return 0
        return garments.count { vectors[it.id]?.image == null }
    }

    /** Added/renamed/restored category: affected when the top category is a touched tag other than the current one. */
    private fun addedCategory(
        changeSet: TagChangeSet,
        garment: ClothingItem,
        image: FloatArray?,
        labels: List<Pair<String, FloatArray>>,
        examples: Map<String, List<FloatArray>>,
    ): ReviewProposal? {
        if (image == null) return null
        val (scored, mode) = scoreLabels(image, labels, examples)
        val marked = selectCategory(scored, mode).marked ?: return null
        val before = garment.tagIds()
        val current = garment.categoryTagIds(changeSet.categoryId)
        if (marked !in changeSet.touchedTagIds || marked in current) return null
        return ReviewProposal(garment.id, before, before - current + marked, ProposalKind.Suggested)
    }

    /**
     * Deleted category: the garment loses the tag and gets the best remaining category even when the margin fails.
     * Without an IMAGE vector, or with no categories left, the user chooses.
     */
    private fun deletedCategory(
        changeSet: TagChangeSet,
        garment: ClothingItem,
        image: FloatArray?,
        labels: List<Pair<String, FloatArray>>,
        examples: Map<String, List<FloatArray>>,
    ): ReviewProposal? {
        val before = garment.tagIds()
        if (before.none { it in changeSet.touchedTagIds }) return null
        val remaining = before - changeSet.touchedTagIds
        if (image == null || labels.isEmpty()) {
            return ReviewProposal(garment.id, before, remaining, ProposalKind.ChooseManually)
        }
        val (scored, _) = scoreLabels(image, labels, examples)
        val winner = rankLabels(scored).first().tagId
        return ReviewProposal(garment.id, before, remaining + winner, ProposalKind.Suggested)
    }

    /**
     * Added/renamed/restored occasion: affected when DEC-009 selects a touched occasion the garment does not have.
     * The proposal only adds those occasions and never removes one.
     */
    private fun addedOccasion(
        changeSet: TagChangeSet,
        garment: ClothingItem,
        image: FloatArray?,
        labels: List<Pair<String, FloatArray>>,
        examples: Map<String, List<FloatArray>>,
        tags: List<GarmentTag>,
    ): ReviewProposal? {
        if (image == null) return null
        val categoryTagId = garment.categoryTagIds(CATEGORY).firstOrNull()
        val suggestOccasions = tags.firstOrNull { it.id == categoryTagId }?.suggestOccasions ?: true
        val (scored, mode) = scoreLabels(image, labels, examples)
        val before = garment.tagIds()
        val added = selectOccasions(scored, mode, suggestOccasions).selected.filter { it in changeSet.touchedTagIds && it !in before }
        if (added.isEmpty()) return null
        return ReviewProposal(garment.id, before, before + added, ProposalKind.Suggested)
    }

    private fun ClothingItem.tagIds(): Set<String> = tags.mapTo(mutableSetOf()) { it.id }

    private fun ClothingItem.categoryTagIds(categoryId: String): Set<String> =
        tags.filter { it.categoryId == categoryId }.mapTo(mutableSetOf()) { it.id }

    /** IMAGE vectors per tag, over every garment that has one. */
    private fun imagesByTag(
        garments: List<ClothingItem>,
        vectors: Map<String, GarmentVectors>,
    ): Map<String, List<Pair<String, FloatArray>>> {
        val result = mutableMapOf<String, MutableList<Pair<String, FloatArray>>>()
        for (garment in garments) {
            val image = vectors[garment.id]?.image ?: continue
            for (tag in garment.tags) result.getOrPut(tag.id) { mutableListOf() }.add(garment.id to image)
        }
        return result
    }

    private const val CATEGORY = "category"
    private const val OCCASION = "occasion"
}
