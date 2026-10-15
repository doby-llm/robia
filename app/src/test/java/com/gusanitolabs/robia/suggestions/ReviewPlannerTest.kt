package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.GarmentVectors
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPlannerTest {
    private val dresses = tag("category-dresses", "category")
    private val skirts = tag("category-skirts", "category")
    private val shorts = tag("category-shorts", "category")
    private val accessories = tag("category-accessories", "category", suggestOccasions = false)
    private val formal = tag("occasion-formal", "occasion")
    private val everyday = tag("occasion-everyday", "occasion")
    private val business = tag("occasion-business", "occasion")

    // Dim-4 label vectors. Garment images below are one-hot or hand-picked so every cosine is 0, 1 or 1/sqrt(2).
    private val labels = mapOf(
        "category-dresses" to floatArrayOf(1f, 0f, 0f, 0f),
        "category-skirts" to floatArrayOf(0f, 1f, 0f, 0f),
        "occasion-formal" to floatArrayOf(1f, 0f, 0f, 0f),
        "occasion-everyday" to floatArrayOf(0f, 1f, 0f, 0f),
        "occasion-business" to floatArrayOf(0f, 0f, 1f, 0f),
    )

    @Test
    fun addedCategoryAffectsOnlyGarmentsWhoseTopCategoryIsTheTouchedTagAndDiffers() {
        val garments = listOf(
            garment("gA", dresses), // image looks like a skirt: text 0 vs 1, margin 1 -> skirts
            garment("gB", skirts), // already the top category: unaffected
            garment("gC"), // top category is dresses (margin 2 in COMBINED): untouched
            garment("gD", dresses), // no IMAGE vector: skipped
        )
        val vectors = vectorsOf(
            "gA" to floatArrayOf(0f, 1f, 0f, 0f),
            "gB" to floatArrayOf(0f, 1f, 0f, 0f),
            "gC" to floatArrayOf(1f, 0f, 0f, 0f),
            "gD" to null,
        )
        val changeSet = changeSet(TagOperation.Added, "category", "category-skirts")

        val plan = ReviewPlanner.plan(changeSet, garments, vectors, labels, listOf(dresses, skirts))

        assertEquals(
            listOf(ReviewProposal("gA", setOf("category-dresses"), setOf("category-skirts"), ProposalKind.Suggested)),
            plan,
        )
        assertEquals(1, ReviewPlanner.skippedCount(changeSet, garments, vectors))
    }

    @Test
    fun renamedCategoryAffectsTheSameGarmentsAsAdded() {
        val garments = listOf(garment("gA", dresses), garment("gB", skirts), garment("gC"))
        val vectors = vectorsOf("gA" to floatArrayOf(0f, 1f, 0f, 0f), "gB" to floatArrayOf(0f, 1f, 0f, 0f), "gC" to floatArrayOf(1f, 0f, 0f, 0f))
        val tags = listOf(dresses, skirts)

        val added = ReviewPlanner.plan(changeSet(TagOperation.Added, "category", "category-skirts"), garments, vectors, labels, tags)
        val renamed = ReviewPlanner.plan(changeSet(TagOperation.Renamed, "category", "category-skirts"), garments, vectors, labels, tags)

        assertEquals(added, renamed)
    }

    @Test
    fun deletedCategorySuggestsBestRemainingCategoryEvenWhenMarginFailsAndChoosesManuallyWithoutImage() {
        // gE: image (1,1,0,0) ties dresses and skirts at 1/sqrt(2) (margin 0, below the TEXT margin). Ties sort by tagId.
        val garments = listOf(garment("gE", shorts), garment("gF", shorts), garment("gG", dresses))
        val vectors = vectorsOf(
            "gE" to floatArrayOf(1f, 1f, 0f, 0f),
            "gF" to null,
            "gG" to floatArrayOf(0f, 1f, 0f, 0f),
        )
        val changeSet = changeSet(TagOperation.Deleted, "category", "category-shorts")

        val plan = ReviewPlanner.plan(changeSet, garments, vectors, labels, listOf(dresses, skirts))

        assertEquals(
            listOf(
                ReviewProposal("gE", setOf("category-shorts"), setOf("category-dresses"), ProposalKind.Suggested),
                ReviewProposal("gF", setOf("category-shorts"), emptySet(), ProposalKind.ChooseManually),
            ),
            plan,
        )
        assertEquals(0, ReviewPlanner.skippedCount(changeSet, garments, vectors))
    }

    @Test
    fun addedOccasionAddsOnlyTouchedSelectedOccasionsAndRespectsCategoryFlag() {
        val garments = listOf(
            garment("hA", dresses), // business selected (1 - 0 > 0.010): added
            garment("hB", dresses, everyday), // everyday selected but not touched; business not selected: unchanged
            garment("hC", accessories), // category flag off: no occasion review
            garment("hD", dresses), // all occasion scores 0: nothing selected
            garment("hE", dresses), // no IMAGE vector: skipped
        )
        val vectors = vectorsOf(
            "hA" to floatArrayOf(0f, 0f, 1f, 0f),
            "hB" to floatArrayOf(0f, 1f, 0f, 0f),
            "hC" to floatArrayOf(0f, 0f, 1f, 0f),
            "hD" to floatArrayOf(0f, 0f, 0f, 1f),
            "hE" to null,
        )
        val changeSet = changeSet(TagOperation.Added, "occasion", "occasion-business")
        val tags = listOf(dresses, accessories, formal, everyday, business)

        val plan = ReviewPlanner.plan(changeSet, garments, vectors, labels, tags)

        assertEquals(
            listOf(ReviewProposal("hA", setOf("category-dresses"), setOf("category-dresses", "occasion-business"), ProposalKind.Suggested)),
            plan,
        )
        assertEquals(1, ReviewPlanner.skippedCount(changeSet, garments, vectors))
    }

    @Test
    fun deletedOccasionProposesTheCurrentOccasionsMinusTheDeletedOne() {
        val garments = listOf(
            garment("hA", dresses, business, formal), // loses business, keeps formal; nothing is forced in
            garment("hB", dresses, formal), // does not carry business: no proposal
        )
        val vectors = vectorsOf("hA" to null, "hB" to floatArrayOf(1f, 0f, 0f, 0f)) // deletion needs no IMAGE vector
        val changeSet = changeSet(TagOperation.Deleted, "occasion", "occasion-business")

        val plan = ReviewPlanner.plan(changeSet, garments, vectors, labels, listOf(dresses, formal, everyday))

        assertEquals(
            listOf(
                ReviewProposal(
                    "hA",
                    setOf("category-dresses", "occasion-business", "occasion-formal"),
                    setOf("category-dresses", "occasion-formal"),
                    ProposalKind.Suggested,
                ),
            ),
            plan,
        )
        assertEquals(0, ReviewPlanner.skippedCount(changeSet, garments, vectors))
    }

    @Test
    fun scanPlansEachGarmentTheSameWayAsThePlan() {
        val garments = listOf(garment("gA", shorts), garment("gB", dresses), garment("gC", shorts))
        val vectors = vectorsOf(
            "gA" to floatArrayOf(0f, 1f, 0f, 0f),
            "gB" to floatArrayOf(1f, 0f, 0f, 0f),
            "gC" to null,
        )
        val changeSet = changeSet(TagOperation.Deleted, "category", "category-shorts")
        val tags = listOf(dresses, skirts)

        val scan = ReviewPlanner.scan(changeSet, garments, vectors, labels, tags)

        assertEquals(
            ReviewPlanner.plan(changeSet, garments, vectors, labels, tags),
            garments.mapNotNull(scan::proposalFor),
        )
    }

    @Test
    fun scanEachStopsBeforeTheNextGarmentWhenCancelled() = runBlocking {
        val garments = listOf(garment("gA", shorts), garment("gB", shorts))
        val vectors = vectorsOf("gA" to floatArrayOf(1f, 1f, 0f, 0f), "gB" to floatArrayOf(1f, 1f, 0f, 0f))
        val scan = ReviewPlanner.scan(
            changeSet(TagOperation.Deleted, "category", "category-shorts"),
            garments,
            vectors,
            labels,
            listOf(dresses, skirts),
        )
        val seen = mutableListOf<String>()

        val job = launch {
            scan.scanEach(garments) { garment, _ ->
                seen += garment.id
                cancel() // navigation or discard: the next garment must not be planned
            }
        }
        job.join()

        assertEquals(listOf("gA"), seen)
    }

    @Test
    fun restoreDefaultsIsTheUnionOfAffectedSetsForEveryRestoredOccasion() {
        // iA selects formal, iB selects business, iC selects everyday (not restored, so not affected).
        val garments = listOf(garment("iA"), garment("iB"), garment("iC"))
        val vectors = vectorsOf(
            "iA" to floatArrayOf(1f, 0f, 0f, 0f),
            "iB" to floatArrayOf(0f, 0f, 1f, 0f),
            "iC" to floatArrayOf(0f, 1f, 0f, 0f),
        )
        val changeSet = changeSet(TagOperation.RestoredDefault, "occasion", "occasion-formal", "occasion-business")

        val plan = ReviewPlanner.plan(changeSet, garments, vectors, labels, listOf(formal, everyday, business))

        assertEquals(
            listOf(
                ReviewProposal("iA", emptySet(), setOf("occasion-formal"), ProposalKind.Suggested),
                ReviewProposal("iB", emptySet(), setOf("occasion-business"), ProposalKind.Suggested),
            ),
            plan,
        )
    }

    @Test
    fun scanEachPausesAfterEachGarmentSoTheProgressBarAdvancesOneByOne() = runBlocking {
        val garments = listOf(garment("gA"), garment("gB"), garment("gC"))
        val scan = ReviewPlanner.scan(
            changeSet(TagOperation.Added, "category", "category-skirts"),
            garments,
            emptyMap(),
            labels,
            listOf(dresses, skirts),
        )
        val visited = mutableListOf<String>()

        val startedAt = System.nanoTime()
        scan.scanEach(garments) { garment, _ -> visited += garment.id }
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(listOf("gA", "gB", "gC"), visited)
        assertTrue("three garments must take at least three steps, took $elapsedMillis ms", elapsedMillis >= 3 * 16L)
    }

    private fun tag(id: String, categoryId: String, suggestOccasions: Boolean = true) =
        GarmentTag(id = id, categoryId = categoryId, name = id, sortOrder = 0, suggestOccasions = suggestOccasions)

    private fun garment(id: String, vararg tags: GarmentTag) =
        ClothingItem(id = id, name = id, tags = tags.toList(), createdAtEpochMillis = 0, updatedAtEpochMillis = 0)

    /** Null image means the garment has no IMAGE vector. */
    private fun vectorsOf(vararg entries: Pair<String, FloatArray?>): Map<String, GarmentVectors> =
        entries.associate { (id, image) -> id to GarmentVectors(id, image, null) }

    private fun changeSet(operation: TagOperation, categoryId: String, vararg touched: String) = TagChangeSet(
        id = "change-${touched.joinToString()}",
        operation = operation,
        categoryId = categoryId,
        touchedTagIds = touched.toSet(),
        beforeTags = emptyList(),
        afterTags = emptyList(),
    )
}
