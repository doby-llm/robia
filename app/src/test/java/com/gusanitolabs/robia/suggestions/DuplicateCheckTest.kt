package com.gusanitolabs.robia.suggestions

import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.data.GarmentVectors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** The forced check and the publish path shared with the automatic scan (DU1, R6-DEC-007). */
@OptIn(ExperimentalCoroutinesApi::class)
class DuplicateCheckTest {
    private fun garment(id: String, category: String? = "category-dresses") = ClothingItem(
        id = id,
        name = id,
        tags = listOfNotNull(category?.let { GarmentTag(id = it, categoryId = "category", name = it, sortOrder = 0) }),
        colorMetrics = ClothingColorMetrics(primaryPaletteColorId = null),
        isArchived = false,
        createdAtEpochMillis = 0L,
        updatedAtEpochMillis = 0L,
    ).copy(photoUri = "content://photos/$id")

    private fun entry(id: String, hash: String?) =
        id to GarmentVectors(garmentId = id, image = floatArrayOf(1f, 0f), text = null, imageInputHash = hash)

    private val pair = DuplicateGroup(listOf("a", "c"), DuplicateTier.SamePhoto)
    private val sameHash: suspend (String) -> String? = { "h1" }
    private val noDismissals: suspend () -> Set<DuplicatePairKey> = { emptySet() }

    private fun reset(previousSignature: String? = null) {
        DuplicateSession.clear()
        DuplicateScanHistory.lastSignature = previousSignature
    }

    private fun report(groups: List<DuplicateGroup> = emptyList(), notCompared: Int = 0) =
        DuplicateScanReport(DuplicateScanOutcome.Success(groups), notCompared)

    @Test
    fun aForcedScanRunsEvenWhenTheSignatureIsUnchanged() = runTest {
        reset(previousSignature = "same")
        val items = listOf(garment("a"), garment("c"))
        var runs = 0
        val scan: suspend (List<ClothingItem>, () -> Set<DuplicatePairKey>) -> DuplicateScanReport = { _, _ ->
            runs++
            report()
        }

        assertNull(publishDuplicateScan("same", items, force = false, isCurrent = { true }, loadStoredDismissals = noDismissals, scan = scan))
        assertEquals(0, runs)

        assertNotNull(publishDuplicateScan("same", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals, scan = scan))
        assertEquals(1, runs)
        assertEquals("same", DuplicateScanHistory.lastSignature)
    }

    @Test
    fun aForcedScanDuringAnAutomaticOneIsRefusedAndTheAutomaticOnePublishesOnce() = runTest {
        reset()
        val items = listOf(garment("a"), garment("c"))
        val gate = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()
        val automatic = async {
            publishDuplicateScan("auto", items, force = false, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ ->
                runs += "auto"
                gate.await()
                report(groups = listOf(pair))
            }
        }
        runCurrent()

        assertNull(
            publishDuplicateScan("forced", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ ->
                runs += "forced"
                report()
            },
        )
        gate.complete(Unit)

        assertNotNull(automatic.await())
        assertEquals(listOf("auto"), runs)
        assertEquals(listOf(pair), DuplicateSession.groups.value)
        assertEquals("auto", DuplicateScanHistory.lastSignature)
    }

    @Test
    fun anAutomaticScanWaitingForAForcedOneSkipsWhatTheForcedOneAlreadyRecorded() = runTest {
        reset()
        val items = listOf(garment("a"), garment("c"))
        val gate = CompletableDeferred<Unit>()
        val runs = mutableListOf<String>()
        val forced = async {
            publishDuplicateScan("same", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ ->
                runs += "forced"
                gate.await()
                report(groups = listOf(pair))
            }
        }
        runCurrent()
        val automatic = async {
            publishDuplicateScan("same", items, force = false, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ ->
                runs += "auto"
                report()
            }
        }
        runCurrent()
        gate.complete(Unit)

        assertNotNull(forced.await())
        // The automatic scan waited for the lock, then found the signature recorded by the forced one: it does not run again.
        assertNull(automatic.await())
        assertEquals(listOf("forced"), runs)
        assertEquals(listOf(pair), DuplicateSession.groups.value)
    }

    @Test
    fun aCancelledScanPublishesNothingAndReleasesTheLock() = runTest {
        reset(previousSignature = "old")
        val items = listOf(garment("a"), garment("c"))
        val gate = CompletableDeferred<Unit>()
        val automatic = async {
            publishDuplicateScan("new", items, force = false, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ ->
                gate.await()
                report(groups = listOf(pair))
            }
        }
        runCurrent()

        automatic.cancelAndJoin()

        assertEquals("old", DuplicateScanHistory.lastSignature)
        assertEquals(emptyList<DuplicateGroup>(), DuplicateSession.groups.value)
        assertNotNull(publishDuplicateScan("new", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ -> report() })
    }

    @Test
    fun aCancellationInsideTheScanIsRethrownAndReleasesTheLock() = runTest {
        reset()
        val items = listOf(garment("a"), garment("c"))

        try {
            publishDuplicateScan("new", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ ->
                throw CancellationException("cancelled")
            }
            fail("cancellation was swallowed")
        } catch (cancelled: CancellationException) {
            assertEquals("cancelled", cancelled.message)
        }

        assertNotNull(publishDuplicateScan("new", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ -> report() })
    }

    @Test
    fun aFailedScanKeepsTheSignatureAndTheGroupsAndReleasesTheLock() = runTest {
        reset(previousSignature = "old")
        DuplicateSession.replaceGroups(listOf(pair))
        val items = listOf(garment("a"), garment("c"))

        val failed = publishDuplicateScan("new", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ ->
            error("database closed")
        }

        assertEquals(DuplicateScanOutcome.Failed, failed?.outcome)
        assertEquals("old", DuplicateScanHistory.lastSignature)
        assertEquals(listOf(pair), DuplicateSession.groups.value)
        assertEquals(DuplicateCheckStatus.Failed, failed?.checkStatus())
        assertNotNull(publishDuplicateScan("new", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ -> report() })
    }

    @Test
    fun aForcedScanWhoseGarmentsChangedWhileItRanPublishesNothingAndRecordsNoSignature() = runTest {
        reset(previousSignature = "old")
        DuplicateSession.replaceGroups(listOf(pair))
        val items = listOf(garment("a"), garment("c"))
        val gate = CompletableDeferred<Unit>()
        var current = true
        val forced = async {
            publishDuplicateScan("new", items, force = true, isCurrent = { current }, loadStoredDismissals = noDismissals) { _, _ ->
                gate.await()
                report(groups = listOf(pair))
            }
        }
        runCurrent()

        // "c" is archived while the scan runs: the wardrobe no longer has the signature the scan started with.
        DuplicateSession.keepActive(setOf("a"))
        current = false
        gate.complete(Unit)

        val result = forced.await()
        assertEquals(DuplicateCheckStatus.Idle, result?.checkStatus())
        assertEquals(emptyList<DuplicateGroup>(), DuplicateSession.groups.value)
        assertEquals("old", DuplicateScanHistory.lastSignature)
    }

    @Test
    fun aForcedScanWhoseGarmentsStillMatchPublishesAndRecordsAsBefore() = runTest {
        reset(previousSignature = "old")
        val items = listOf(garment("a"), garment("c"))

        val result = publishDuplicateScan("new", items, force = true, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, _ ->
            report(groups = listOf(pair))
        }

        assertEquals(DuplicateScanOutcome.Success(listOf(pair)), result?.outcome)
        assertEquals(listOf(pair), DuplicateSession.groups.value)
        assertEquals("new", DuplicateScanHistory.lastSignature)
    }

    @Test
    fun aFailedReadOfTheDismissalsPublishesNothingAndRecordsNothing() = runTest {
        reset(previousSignature = "old")
        DuplicateSession.replaceGroups(listOf(pair))
        var scans = 0

        val result = publishDuplicateScan(
            "new",
            listOf(garment("a"), garment("c")),
            force = false, isCurrent = { true },
            loadStoredDismissals = { error("database closed") },
        ) { _, _ ->
            scans++
            report()
        }

        assertEquals(DuplicateScanOutcome.Failed, result?.outcome)
        assertEquals(0, scans)
        assertEquals("old", DuplicateScanHistory.lastSignature)
        assertEquals(listOf(pair), DuplicateSession.groups.value)
    }

    @Test
    fun aScanWithAMissingVectorPublishesItsGroupsButRecordsNoSignature() = runTest {
        reset(previousSignature = "old")
        // b has a photo and no IMAGE vector yet (the backfill has not reached it): a and c still pair.
        val items = listOf(garment("a"), garment("b"), garment("c"))
        val vectors = mapOf(entry("a", "h1"), entry("c", "h1"))
        val failures = mutableListOf<Exception>()
        suspend fun scanNow(dismissed: Set<DuplicatePairKey>) = collectDuplicateScanReport(
            items,
            snapshot = { vectors },
            hash = sameHash,
            dismissed = { dismissed },
            onFailure = { failures += it },
        )

        val result = publishDuplicateScan("new", items, force = false, isCurrent = { true }, loadStoredDismissals = noDismissals) { _, dismissed ->
            scanNow(dismissed())
        }

        assertEquals(DuplicateScanOutcome.Incomplete(listOf(pair)), result?.outcome)
        assertEquals(listOf(pair), DuplicateSession.groups.value)
        assertEquals("old", DuplicateScanHistory.lastSignature)
        assertEquals(1, result?.notCompared)
        assertEquals(emptyList<Exception>(), failures)
        assertEquals("old", nextDuplicateScanSignature("old", "new", result!!.outcome))
    }

    @Test
    fun aStaleVectorIsCountedAsNotComparedButDoesNotMakeTheScanIncomplete() = runTest {
        val items = listOf(garment("a"), garment("b"), garment("c"))
        val vectors = mapOf(entry("a", "h1"), entry("b", "old"), entry("c", "h1"))

        val report = collectDuplicateScanReport(
            items,
            snapshot = { vectors },
            hash = sameHash,
            dismissed = { emptySet() },
            onFailure = { throw AssertionError("unexpected failure", it) },
        )

        assertEquals(DuplicateScanOutcome.Success(listOf(pair)), report.outcome)
        assertEquals(1, report.notCompared)
        assertEquals(DuplicateCheckStatus.Done(groupCount = 1, notCompared = 1), report.checkStatus())
    }

    @Test
    fun aDismissedPairStaysOutOfTheForcedScan() = runTest {
        reset()
        val items = listOf(garment("a"), garment("c"))
        val vectors = mapOf(entry("a", "h1"), entry("c", "h1"))
        val dismissed = setOf(DuplicatePairKey.of("a", "c"))

        val result = publishDuplicateScan("new", items, force = true, isCurrent = { true }, loadStoredDismissals = { dismissed }) { scanItems, read ->
            collectDuplicateScanReport(
                scanItems,
                snapshot = { vectors },
                hash = sameHash,
                dismissed = read,
                onFailure = { throw AssertionError("unexpected failure", it) },
            )
        }

        assertEquals(DuplicateScanOutcome.Success(emptyList()), result?.outcome)
        assertEquals(emptyList<DuplicateGroup>(), DuplicateSession.groups.value)
        assertEquals(dismissed, DuplicateSession.dismissals.value)
    }

    @Test
    fun theRowStatusFollowsTheOutcome() {
        val groups = listOf(pair)

        assertEquals(DuplicateCheckStatus.Done(1, 0), DuplicateScanReport(DuplicateScanOutcome.Success(groups), 0).checkStatus())
        assertEquals(DuplicateCheckStatus.Done(1, 2), DuplicateScanReport(DuplicateScanOutcome.Incomplete(groups), 2).checkStatus())
        assertEquals(DuplicateCheckStatus.Done(0, 0), DuplicateScanReport(DuplicateScanOutcome.Success(emptyList()), 0).checkStatus())
        assertEquals(DuplicateCheckStatus.Failed, DuplicateScanReport(DuplicateScanOutcome.Failed, 0).checkStatus())
    }

    @Test
    fun anIncompleteOutcomeReplacesTheGroupsAndKeepsThePreviousSignature() {
        val previous = listOf(DuplicateGroup(listOf("x", "y"), DuplicateTier.SamePhoto))
        val incomplete = DuplicateScanOutcome.Incomplete(listOf(pair))

        assertEquals(listOf(pair), incomplete.groupsAfter(previous))
        assertEquals("old", nextDuplicateScanSignature("old", "new", incomplete))
        assertNull(nextDuplicateScanSignature(null, "new", incomplete))
    }
}
