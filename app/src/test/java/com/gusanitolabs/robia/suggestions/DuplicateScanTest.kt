package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.GarmentVectors
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun calibrationCountsFunnelThroughNoVectorStaleHashAndNoCategory() {
        val items = listOf(
            garment("ok1"), garment("ok2"),
            garment("noVector"),
            garment("stale"),
            garment("noCategory", category = null),
            garment("archived", archived = true),
        )
        val vectors = mapOf(
            entry("ok1", floatArrayOf(1f, 0f), "h1"),
            entry("ok2", floatArrayOf(1f, 0f), "h1"),
            "noVector" to GarmentVectors(garmentId = "noVector", image = null, text = null, imageInputHash = null),
            entry("stale", floatArrayOf(0f, 1f), "h2"),
            entry("noCategory", floatArrayOf(1f, 0f), "h3"),
            entry("archived", floatArrayOf(1f, 0f), "h1"),
        )
        val currentHashes = mapOf("ok1" to "h1", "ok2" to "h1", "stale" to "h9", "noCategory" to "h3", "archived" to "h1")

        // Five active; four with an IMAGE vector; three match their current photo; two of those have a category.
        assertEquals(CalibrationCounts(active = 5, withVector = 4, hashMatched = 3, categorised = 2), calibrationCounts(items, vectors, currentHashes))
        assertEquals(listOf("ok1" to "ok2"), calibrationPairs(items, vectors, currentHashes).map { it.a to it.b })
    }

    @Test
    fun calibrationCountsAreZeroBelowTheFirstStepWhenNoVectorsExistYet() {
        val items = listOf(garment("a"), garment("b"), garment("archived", archived = true))

        assertEquals(CalibrationCounts(active = 2, withVector = 0, hashMatched = 0, categorised = 0), calibrationCounts(items, emptyMap(), emptyMap()))
    }

    /** The all-pairs algorithm as it was before DUP-FAST: the reference the blocked top-k pass must reproduce (V-DUP-FAST-3). */
    private fun referenceCalibrationPairs(
        items: List<ClothingItem>,
        vectors: Map<String, GarmentVectors>,
        currentHashes: Map<String, String>,
        top: Int,
    ): List<CalibrationPair> {
        val garments = eligibleGarments(items, vectors, currentHashes).filter { it.category != null }
        val unit = garments.map { it.vector.normalized() }
        return garments.indices
            .flatMap { i -> (i + 1 until garments.size).map { j -> i to j } }
            .filter { (i, j) -> garments[i].category == garments[j].category }
            .map { (i, j) -> CalibrationPair(garments[i].id, garments[j].id, dot(unit[i], unit[j])) }
            .sortedWith(compareByDescending<CalibrationPair> { it.cosine }.thenBy { it.a }.thenBy { it.b })
            .take(top)
    }

    /** Synthetic wardrobe over four categories (one without), with repeated and near-repeated vectors so cosines tie. */
    private fun syntheticWardrobe(seed: Int, count: Int): Triple<List<ClothingItem>, Map<String, GarmentVectors>, Map<String, String>> {
        val random = Random(seed)
        val pool = List(24) { FloatArray(EmbeddingRecipe.DIMENSION) { random.nextFloat() - 0.5f } }
        val categories = listOf("category-dresses", "category-shirts", "category-pants", null)
        val items = (1..count).map { n -> garment("g%03d".format(n), category = categories[random.nextInt(categories.size)]) }
        val vectors = items.associate { item ->
            val base = pool[random.nextInt(pool.size)]
            val image = if (random.nextInt(4) == 0) FloatArray(base.size) { k -> base[k] * 0.999f } else base.copyOf()
            item.id to GarmentVectors(item.id, image, null, imageInputHash = "h-${item.id}")
        }
        // About one garment in ten has a photo that changed since its vector: it is not eligible, like a stale vector in the scan.
        val hashes = items.associate { item -> item.id to if (random.nextInt(10) == 0) "stale-${item.id}" else "h-${item.id}" }
        return Triple(items, vectors, hashes)
    }

    @Test
    fun blockedTopPairsEqualTheOldAllPairsAlgorithm() {
        val (items, vectors, hashes) = syntheticWardrobe(seed = 7, count = 240)

        for (top in listOf(1, 30, 100_000)) {
            val blocked = calibrationPairs(items, vectors, hashes, top = top)
            val reference = referenceCalibrationPairs(items, vectors, hashes, top = top)

            assertEquals(reference.map { it.a to it.b }, blocked.map { it.a to it.b })
            blocked.zip(reference).forEach { (got, expected) -> assertEquals(expected.cosine, got.cosine, 1e-5f) }
        }
    }

    @Test
    fun pairPassVisitsOnlySameCategoryPairsAndTheTopHeapStaysBounded() {
        val (items, vectors, hashes) = syntheticWardrobe(seed = 11, count = 500)
        val garments = eligibleGarments(items, vectors, hashes)
        val perCategory = garments.filter { it.category != null }.groupingBy { it.category }.eachCount().values
        val expectedPairs = perCategory.sumOf { n -> n.toLong() * (n - 1) / 2 }
        val allPairs = garments.size.toLong() * (garments.size - 1) / 2
        var rows = 0

        val visited = scanBlocks(garments, onRow = { rows++ }) { _, _, _ -> }

        assertEquals(expectedPairs, visited)
        assertTrue("same-category pairs must be fewer than all pairs", visited < allPairs)
        assertEquals(perCategory.sum(), rows)
        val best = CalibrationTop(limit = 30)
        scanBlocks(garments, onRow = {}) { a, b, cosine -> best.offer(a, b, cosine) }
        assertEquals(30, best.size)
    }

    @Test
    fun resolverHashesEveryPhotoWithAtMostThreeInFlight() = runTest {
        val items = (1..10).map { n -> garment("g$n").copy(photoUri = "content://photos/g$n") }
        val calls = AtomicInteger(0)
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)

        val hashes = resolvePhotoHashes(items.map(ClothingItem::id).toSet(), items, hash = { uri ->
            calls.incrementAndGet()
            maxInFlight.accumulateAndGet(inFlight.incrementAndGet()) { a, b -> maxOf(a, b) }
            delay(10)
            inFlight.decrementAndGet()
            "h-$uri"
        })

        assertEquals(10, calls.get())
        assertEquals(3, maxInFlight.get())
        assertEquals(items.associate { it.id to "h-${it.photoUri}" }, hashes)
    }

    @Test
    fun aPhotoThatCannotBeHashedLeavesItsGarmentOutAndGarmentsWithoutAPhotoAreSkipped() = runTest {
        val items = listOf(
            garment("a").copy(photoUri = "content://photos/a"),
            garment("b").copy(photoUri = "content://photos/b"),
            garment("c").copy(photoUri = "content://photos/c"),
            garment("noPhoto"),
            garment("blankPhoto").copy(photoUri = " "),
        )

        val hashes = resolvePhotoHashes(items.map(ClothingItem::id).toSet(), items, hash = { uri ->
            when (uri.substringAfterLast('/')) {
                "b" -> null
                "c" -> error("unreadable")
                else -> "h"
            }
        })

        assertEquals(mapOf("a" to "h"), hashes)
    }

    @Test
    fun cancellationStopsThePhotosNotYetStarted() = runTest {
        val items = (1..10).map { n -> garment("g$n").copy(photoUri = "content://photos/g$n") }
        val calls = AtomicInteger(0)
        val job = launch {
            resolvePhotoHashes(items.map(ClothingItem::id).toSet(), items, hash = {
                calls.incrementAndGet()
                delay(100)
                "h"
            })
        }

        testScheduler.advanceTimeBy(50)
        job.cancelAndJoin()

        assertEquals(3, calls.get())
    }

    @Test
    fun progressNeverGoesBackwardsAndEndsAtTheNumberOfPhotosHashed() = runTest {
        val items = (1..7).map { n -> garment("g$n").copy(photoUri = "content://photos/g$n") } + garment("noPhoto")
        val calls = AtomicInteger(0)
        val reported = mutableListOf<Pair<Int, Int>>()

        resolvePhotoHashes(items.map(ClothingItem::id).toSet(), items, hash = { uri ->
            calls.incrementAndGet()
            delay(5)
            if (uri.endsWith("g3")) null else "h"
        }, onProgress = { done, total -> reported.add(done to total) })

        assertEquals(0 to 7, reported.first())
        assertTrue(reported.zipWithNext().all { (before, after) -> before.first <= after.first })
        assertEquals(calls.get() to calls.get(), reported.last())
    }
}
