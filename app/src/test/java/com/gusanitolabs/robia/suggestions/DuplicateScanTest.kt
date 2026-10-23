package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.GarmentVectors
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class DuplicateScanTest {
    private fun garment(id: String, category: String? = "category-dresses", archived: Boolean = false) = ClothingItem(
        id = id,
        name = id,
        tags = listOfNotNull(category?.let { GarmentTag(id = it, categoryId = "category", name = it, sortOrder = 0) }),
        isArchived = archived,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    )

    private fun entry(id: String, image: FloatArray, hash: String?) =
        id to GarmentVectors(garmentId = id, image = image, text = null, imageInputHash = hash)

    @Test
    fun sameImageHashMakesOneGroup_andArchivedAndStaleGarmentsAreLeftOut() {
        val items = listOf(
            garment("a"), garment("b"), garment("c"),
            garment("archivedSame", archived = true),
            garment("stale"), garment("lone"),
        )
        val vectors = mapOf(
            entry("a", floatArrayOf(1f, 0f), "h1"),
            entry("b", floatArrayOf(1f, 0f), "h1"),
            entry("c", floatArrayOf(1f, 0f), "h1"),
            entry("archivedSame", floatArrayOf(1f, 0f), "h1"),
            entry("stale", floatArrayOf(0f, 1f), "h2"),
            entry("lone", floatArrayOf(0f, 1f), "h2"),
        )
        val currentHashes = mapOf("a" to "h1", "b" to "h1", "c" to "h1", "archivedSame" to "h1", "stale" to "h9", "lone" to "h2")

        val groups = scan(items, vectors, currentHashes)

        assertEquals(listOf(DuplicateGroup(listOf("a", "b", "c"), DuplicateTier.SamePhoto)), groups)
    }

    @Test
    fun garmentWithoutStoredHashOrPhotoHashIsNotPaired() {
        val items = listOf(garment("a"), garment("b"), garment("c"))
        val vectors = mapOf(
            entry("a", floatArrayOf(1f, 0f), hash = null),
            entry("b", floatArrayOf(1f, 0f), "h1"),
            entry("c", floatArrayOf(1f, 0f), "h1"),
        )

        val groups = scan(items, vectors, currentPhotoHashes = mapOf("a" to "h1", "b" to "h1", "c" to "h1"))

        assertEquals(listOf(listOf("b", "c")), groups.map { it.garmentIds })
    }

    @Test
    fun groupsHoldAtMostSixGarments() {
        val ids = (1..8).map { "g%02d".format(it) }
        val items = ids.map { garment(it) }
        val vectors = ids.associateWith { id -> GarmentVectors(id, floatArrayOf(1f, 0f), null, imageInputHash = "h") }
        val currentHashes = ids.associateWith { "h" }

        val groups = scan(items, vectors, currentHashes)

        assertEquals(listOf(6, 2), groups.map { it.garmentIds.size }.sortedDescending())
        assertEquals(ids.toSet(), groups.flatMap { it.garmentIds }.toSet())
    }

    @Test
    fun dismissedPairsAreIgnored_andADismissedGroupDisappears() {
        val items = listOf(garment("a"), garment("b"), garment("c"))
        val vectors = mapOf(
            entry("a", floatArrayOf(1f, 0f), "h1"),
            entry("b", floatArrayOf(1f, 0f), "h1"),
            entry("c", floatArrayOf(1f, 0f), "h1"),
        )
        val currentHashes = mapOf("a" to "h1", "b" to "h1", "c" to "h1")
        val group = scan(items, vectors, currentHashes).single()

        assertTrue(scan(items, vectors, currentHashes, dismissed = group.pairKeys()).isEmpty())
        val onePairDismissed = scan(items, vectors, currentHashes, dismissed = setOf(DuplicatePairKey.of("b", "a")))
        assertEquals(listOf(listOf("a", "c")), onePairDismissed.map { it.garmentIds })
    }

    @Test
    fun dismissedPairNeverJoinsOneGroupThroughALaterGarment() {
        val ids = listOf("a", "b", "c", "d")
        val items = ids.map { garment(it) }
        val vectors = ids.associateWith { id -> GarmentVectors(id, floatArrayOf(1f, 0f), null, imageInputHash = "h1") }
        val currentHashes = ids.associateWith { "h1" }
        val dismissed = DuplicateGroup(listOf("a", "b", "c"), DuplicateTier.SamePhoto).pairKeys()

        val groups = scan(items, vectors, currentHashes, dismissed = dismissed)

        assertEquals(listOf(listOf("a", "d")), groups.map { it.garmentIds })
        assertTrue(groups.none { group -> group.pairKeys().any { it in dismissed } })
    }

    @Test
    fun calibrationPairsAreSameCategoryAndSortedByCosine() {
        val items = listOf(garment("a1"), garment("b2"), garment("c3"), garment("s4", category = "category-shirts"))
        val vectors = mapOf(
            entry("a1", floatArrayOf(1f, 0f), "h1"),
            entry("b2", floatArrayOf(1f, 1f), "h2"),
            entry("c3", floatArrayOf(0f, 1f), "h3"),
            entry("s4", floatArrayOf(1f, 0f), "h4"),
        )
        val currentHashes = mapOf("a1" to "h1", "b2" to "h2", "c3" to "h3", "s4" to "h4")

        val pairs = calibrationPairs(items, vectors, currentHashes, top = 30)

        assertEquals(listOf("a1" to "b2", "b2" to "c3", "a1" to "c3"), pairs.map { it.a to it.b })
        assertEquals(0.70710678f, pairs[0].cosine, 1e-5f)
        assertEquals(0.70710678f, pairs[1].cosine, 1e-5f)
        assertEquals(0f, pairs[2].cosine, 1e-5f)
    }

    @Test
    fun calibrationPairsRespectTopAndScanOf500GarmentsStaysFast() {
        val random = Random(20261023)
        val items = (1..500).map { garment("g%03d".format(it)) }
        val vectors = items.associate { item ->
            val image = FloatArray(EmbeddingRecipe.DIMENSION) { random.nextFloat() - 0.5f }
            item.id to GarmentVectors(item.id, image, null, imageInputHash = "h-${item.id}")
        }
        val currentHashes = items.associate { it.id to "h-${it.id}" }

        val startedAt = System.nanoTime()
        scan(items, vectors, currentHashes)
        val pairs = calibrationPairs(items, vectors, currentHashes, top = 30)
        val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L

        assertEquals(30, pairs.size)
        assertTrue(pairs.zipWithNext().all { (x, y) -> x.cosine >= y.cosine })
        assertTrue("scan took $elapsedMillis ms", elapsedMillis < 2_000L)
    }

    @Test
    fun rescanIsDueBeforeFirstRunAfterFiveNewGarmentsOrADayWithAChange() {
        val day = 24L * 60 * 60 * 1000
        val last = DuplicateScanRecord(atEpochMillis = 1_000L, garmentCount = 100)

        assertTrue(duplicateScanDue(null, garmentCount = 100, nowEpochMillis = 1_000L))
        assertFalse(duplicateScanDue(last, garmentCount = 104, nowEpochMillis = 1_000L + 1_000L))
        assertTrue(duplicateScanDue(last, garmentCount = 105, nowEpochMillis = 1_000L + 1_000L))
        assertTrue(duplicateScanDue(last, garmentCount = 103, nowEpochMillis = 1_000L + day))
        assertFalse(duplicateScanDue(last, garmentCount = 100, nowEpochMillis = 1_000L + day))
    }

    @Test
    fun keepingActiveDropsArchivedGarmentsAndGroupsLeftBelowTwo() {
        val groups = listOf(
            DuplicateGroup(listOf("a", "b"), DuplicateTier.SamePhoto),
            DuplicateGroup(listOf("c", "d", "e"), DuplicateTier.SamePhoto),
            DuplicateGroup(listOf("f", "g", "h"), DuplicateTier.SamePhoto),
        )

        // a archived: {a, b} is left with one garment and disappears. e archived: {c, d} stays.
        // g and h archived: {f, g, h} is left with one garment and disappears.
        val remaining = groups.keepingActive(setOf("b", "c", "d", "f"))

        assertEquals(listOf(listOf("c", "d")), remaining.map { it.garmentIds })
    }

    @Test
    fun onlyGarmentsSharingAStoredHashAreCandidatesForReEncoding() {
        val items = listOf(garment("a"), garment("b"), garment("c"), garment("archivedSame", archived = true), garment("d"))
        val vectors = mapOf(
            entry("a", floatArrayOf(1f, 0f), "h1"),
            entry("b", floatArrayOf(1f, 0f), "h1"),
            entry("c", floatArrayOf(0f, 1f), "h2"),
            entry("archivedSame", floatArrayOf(1f, 0f), "h1"),
            entry("d", floatArrayOf(1f, 0f), hash = null),
        )

        assertEquals(setOf("a", "b"), sharedHashCandidates(items, vectors))
    }

    @Test
    fun calibrationMarksExportHasOneEntryPerPairWithNullForUnmarked() {
        val pairs = listOf(CalibrationPair("a", "b", 0.9123456f), CalibrationPair("c", "d", 0.5f))

        val json = JSONArray(calibrationMarksJson(pairs, marks = mapOf(0 to true)))

        assertEquals(2, json.length())
        val first = json.getJSONObject(0)
        assertEquals("a", first.getString("a"))
        assertEquals("b", first.getString("b"))
        assertEquals(0.912346, first.getDouble("cos"), 1e-9)
        assertTrue(first.getBoolean("same"))
        assertTrue(json.getJSONObject(1).isNull("same"))
    }

    @Test
    fun sessionDismissalRemovesTheGroupAndKeepsItsPairsOutForTheProcess() {
        DuplicateSession.clear()
        val dismissed = DuplicateGroup(listOf("a", "b", "c"), DuplicateTier.SamePhoto)
        val kept = DuplicateGroup(listOf("d", "e"), DuplicateTier.SamePhoto)
        DuplicateSession.replaceGroups(listOf(dismissed, kept))

        DuplicateSession.dismiss(dismissed)

        assertEquals(listOf(kept), DuplicateSession.groups.value)
        assertEquals(dismissed.pairKeys(), DuplicateSession.dismissals.value)
    }

    @Test
    fun garmentsLeavingTheWardrobeLeaveTheirGroupsAndGroupsBelowTwoDisappear() {
        DuplicateSession.clear()
        DuplicateSession.replaceGroups(listOf(DuplicateGroup(listOf("a", "b", "c"), DuplicateTier.SamePhoto), DuplicateGroup(listOf("d", "e"), DuplicateTier.SamePhoto)))

        DuplicateSession.keepActive(setOf("a", "c", "x"))

        assertEquals(listOf(DuplicateGroup(listOf("a", "c"), DuplicateTier.SamePhoto)), DuplicateSession.groups.value)
    }
}
