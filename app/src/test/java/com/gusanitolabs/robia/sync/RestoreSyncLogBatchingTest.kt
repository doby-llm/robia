package com.gusanitolabs.robia.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class RestoreSyncLogBatchingTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun fiveHundredAppendsReachTheFileInAtMostFifteenWrites() = runBlocking {
        val logFile = File(temporaryFolder.root, "developer_restore_sync.log")
        val writes = AtomicInteger(0)
        val countingWriter = RestoreSyncLogFileWriter { file, text, append ->
            writes.incrementAndGet()
            DefaultRestoreSyncLogFileWriter.write(file, text, append)
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = FileRestoreSyncLogRepository(logFile, scope = scope, fileWriter = countingWriter)
            repository.setEnabled(true)
            repeat(500) { index -> repository.append(event(index)) }
            repository.flush()

            assertTrue("file writes=${writes.get()}", writes.get() <= 15)
            assertEquals(500, logFile.readLines().size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun boundedFileKeepsTheNewestEventsAfterTrimming() = runBlocking {
        val logFile = File(temporaryFolder.root, "bounded.log")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val repository = FileRestoreSyncLogRepository(logFile, maxEvents = 10, scope = scope, flushLines = 5)
            repository.setEnabled(true)
            repeat(25) { index -> repository.append(event(index)) }
            repository.flush()

            val lines = logFile.readLines()
            assertEquals(10, lines.size)
            assertTrue(lines.last().contains("message=\"event 24\""))
            assertTrue(lines.first().contains("message=\"event 15\""))
        } finally {
            scope.cancel()
        }
    }

    private fun event(index: Int): RestoreSyncLogEvent = RestoreSyncLogEvent(
        correlationId = "test",
        phase = null,
        status = null,
        message = "event $index",
    )
}
