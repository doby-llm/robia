package com.gusanitolabs.robia.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SubjectOutputCancellationTest {
    @Test
    fun activeCoroutine_keepsTheOutputAndDeletesNothing() = runBlocking {
        val deleted = mutableListOf<String>()

        deleteOutputIfCancelled("subject-1.png") { deleted.add(it) }

        assertTrue(deleted.isEmpty())
    }

    @Test
    fun cancelledAfterTheWrite_deletesTheOutputAndRethrows() {
        val deleted = mutableListOf<String>()
        val cancelledScope = CoroutineScope(Job().apply { cancel() })

        try {
            cancelledScope.deleteOutputIfCancelled("subject-1.png") { deleted.add(it) }
            fail("a cancelled engine must not hand its output on")
        } catch (expected: CancellationException) {
            // Expected: the cancellation reaches the caller.
        }
        assertEquals(listOf("subject-1.png"), deleted)
    }
}
