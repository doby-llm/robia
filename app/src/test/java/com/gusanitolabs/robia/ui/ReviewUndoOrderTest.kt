package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.suggestions.TagChangeSet
import com.gusanitolabs.robia.suggestions.TagOperation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewUndoOrderTest {
    private val dress = tag("dress", name = "Dresses")
    private val shirt = tag("shirt", name = "Shirts")

    @Test
    fun cancelActsOnlyWhileItsRequestIsStillPending() {
        val request = TagReviewRequest(deleteChangeSet(), emptyList())

        assertTrue(isStillPending(request, request))
        // Taken by a first cancel or by Review: a repeat or a dismiss callback finds nothing pending.
        assertFalse(isStillPending(null, request))
        // An equal-looking request from a later change is a different object, so an old callback cannot act on it.
        assertFalse(isStillPending(request.copy(), request))
    }

    @Test
    fun undoPlanIsBuiltOnlyAfterThePreviousWriteFinishes() = runBlocking {
        val events = mutableListOf<String>()
        val write = launch {
            delay(10)
            events += "write"
        }

        val result = afterPreviousWrite(write) {
            events += "plan"
            "plan"
        }

        assertEquals("plan", result)
        assertEquals(listOf("write", "plan"), events)
    }

    @Test
    fun failedPreviousWriteDoesNotStopTheUndoPlan() = runBlocking {
        val failedWrite = CoroutineScope(SupervisorJob()).async<Unit> { error("save failed") }

        assertEquals("plan", afterPreviousWrite(failedWrite) { "plan" })
    }

    @Test
    fun undoPlanWithoutAPreviousWriteRunsAtOnce() = runBlocking {
        assertEquals("plan", afterPreviousWrite(null) { "plan" })
    }

    @Test
    fun deleteUndoReadsTheGarmentsOnlyAfterTheDeleteHasWritten() = runBlocking {
        val changeSet = TagChangeSet(
            id = "delete",
            operation = TagOperation.Deleted,
            categoryId = "category",
            touchedTagIds = setOf(dress.id),
            beforeTags = listOf(dress, shirt),
            afterTags = listOf(shirt),
        )
        val snapshot = garment("g1", listOf(dress, shirt))
        val request = TagReviewRequest(changeSet, listOf(snapshot))
        // What the database holds: the delete removes the garment's reference to the deleted tag.
        var storedTags = listOf(dress, shirt)
        val write = launch {
            delay(10)
            storedTags = listOf(shirt)
        }

        val plan = afterPreviousWrite(write) {
            tagReviewUndoPlan(request, listOf(garment("g1", storedTags)), NOW)
        }

        assertEquals(listOf(snapshot.copy(updatedAtEpochMillis = NOW)), plan.restoreGarments)
    }

    private fun deleteChangeSet() = TagChangeSet(
        id = "delete",
        operation = TagOperation.Deleted,
        categoryId = "category",
        touchedTagIds = setOf(dress.id),
        beforeTags = listOf(dress, shirt),
        afterTags = listOf(shirt),
    )

    private fun tag(id: String, name: String) = GarmentTag(id = id, categoryId = "category", name = name, sortOrder = 0)

    private fun garment(id: String, tags: List<GarmentTag>) =
        ClothingItem(id = id, name = id, tags = tags, createdAtEpochMillis = 0, updatedAtEpochMillis = 0)

    private companion object {
        const val NOW = 1_000L
    }
}
