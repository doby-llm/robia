package com.gusanitolabs.robia.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-BI1-1 to V-BI1-5 (robia-round6 BI1): the bounded, ordered, cancellable photo copy behind "Preparing photos". */
class PhotoImportTest {
    @Test
    fun keepsPickedOrderWhenCopiesFinishOutOfOrder() = runTest {
        val picked = (1..6).toList()
        val finished = mutableListOf<Int>()

        val copied = copyPickedPhotos(
            picked = picked,
            copy = { photo ->
                delay((7 - photo) * 10L)
                finished += photo
                "copy-$photo"
            },
            deleteCopies = {},
            onProgress = { _, _ -> },
        )

        assertTrue(finished != picked)
        assertEquals(picked.map { "copy-$it" }, copied)
    }

    @Test
    fun neverMoreThanFourCopiesAreInFlight() = runTest {
        var inFlight = 0
        var maxInFlight = 0

        copyPickedPhotos(
            picked = (1..20).toList(),
            copy = { photo ->
                inFlight += 1
                maxInFlight = maxOf(maxInFlight, inFlight)
                delay(10)
                inFlight -= 1
                "copy-$photo"
            },
            deleteCopies = {},
            onProgress = { _, _ -> },
        )

        assertEquals(MAX_PARALLEL_PHOTO_COPIES, maxInFlight)
    }

    @Test
    fun progressIsMonotonicAndEndsAtTotal() = runTest {
        val progress = mutableListOf<Pair<Int, Int>>()

        copyPickedPhotos(
            picked = (1..9).toList(),
            copy = { photo ->
                delay(photo * 3L)
                check(photo % 3 != 0) { "unreadable $photo" }
                "copy-$photo"
            },
            deleteCopies = {},
            onProgress = { done, total -> progress += (done to total) },
        )

        assertEquals((1..9).toList(), progress.map { it.first })
        assertTrue(progress.all { it.second == 9 })
        assertEquals(9 to 9, progress.last())
    }

    @Test
    fun failedCopyIsSkippedAndTheOthersAreKept() = runTest {
        val progress = mutableListOf<Pair<Int, Int>>()

        val copied = copyPickedPhotos(
            picked = listOf("a", "b", "c"),
            copy = { photo ->
                check(photo != "b") { "unreadable" }
                "copy-$photo"
            },
            deleteCopies = {},
            onProgress = { done, total -> progress += (done to total) },
        )

        assertEquals(listOf("copy-a", "copy-c"), copied)
        assertEquals(3 to 3, progress.last())
    }

    @Test
    fun cancellationDeletesEveryCopyAlreadyMadeAndRethrows() = runTest {
        val made = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val stuck = CompletableDeferred<Unit>()
        var thrown: Throwable? = null

        val importing = launch {
            try {
                copyPickedPhotos(
                    picked = listOf("a", "b", "c", "d", "e"),
                    copy = { photo ->
                        if (photo == "a" || photo == "b") {
                            "copy-$photo".also { made += it }
                        } else {
                            stuck.await()
                            "never-$photo"
                        }
                    },
                    deleteCopies = { copies -> deleted += copies },
                    onProgress = { _, _ -> },
                )
            } catch (cancelled: CancellationException) {
                thrown = cancelled
                throw cancelled
            }
        }
        runCurrent()
        importing.cancelAndJoin()

        assertTrue(thrown is CancellationException)
        assertEquals(listOf("copy-a", "copy-b"), made.sorted())
        assertEquals(made.sorted(), deleted.sorted())
    }
}
