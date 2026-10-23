package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.suggestions.model.ModelDownloadWorker
import com.gusanitolabs.robia.suggestions.model.ModelResponse
import com.gusanitolabs.robia.suggestions.model.ModelSource
import com.gusanitolabs.robia.suggestions.model.ModelState
import com.gusanitolabs.robia.suggestions.model.ModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CutoutPackDownloadTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val payload = ByteArray(200_000) { (it % 251).toByte() }
    private val payloadSha = MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }
    private val fileName = CutoutPackManifest.FILES.first().fileName

    private val packDir get() = File(temp.root, CutoutPackManifest.DIRECTORY)
    private val finalFile get() = File(packDir, fileName)
    private val partFile get() = File(packDir, "$fileName.part")

    private fun store(source: ModelSource) = ModelStore(
        rootDir = packDir,
        source = source,
        isUnmetered = { true },
        freeSpace = { Long.MAX_VALUE },
        expectedSha256 = payloadSha,
        fileName = fileName,
        expectedSizeBytes = payload.size.toLong(),
    )

    /** Starts a download, cancels it while the copy loop waits on its second read, and returns the store. */
    private fun cancelMidDownload(): ModelStore {
        val paused = CountDownLatch(1)
        val release = CountDownLatch(1)
        val store = store(ModelSource { ModelResponse(200, payload.size.toLong(), PausingStream(payload, paused, release)) })
        runBlocking {
            val download = async(Dispatchers.IO) { store.download() }
            assertTrue(paused.await(5, TimeUnit.SECONDS))
            download.cancel()
            release.countDown()
            download.join()
        }
        return store
    }

    @Test
    fun cancelKeepsPartialFileAndPublishesNotDownloaded() {
        val store = cancelMidDownload()

        assertEquals(ModelState.NotDownloaded, store.state.value)
        assertTrue(partFile.length() > 0L && partFile.length() < payload.size)
        assertFalse(finalFile.exists())
    }

    @Test
    fun nextDownloadResumesWithRangeFromTheKeptPartialFile() {
        cancelMidDownload()
        val kept = partFile.length()
        var requestedOffset = -1L
        val source = ModelSource { offset ->
            requestedOffset = offset
            ModelResponse(
                206,
                payload.size - offset,
                ByteArrayInputStream(payload.copyOfRange(offset.toInt(), payload.size)),
            )
        }

        val result = runBlocking { store(source).download() }

        assertTrue(result is ModelState.Ready)
        assertEquals(kept, requestedOffset)
        assertArrayEquals(payload, finalFile.readBytes())
    }

    @Test
    fun cutoutPackWorkIsUniqueAndSeparateFromTheEmbeddingModel() {
        assertEquals("cutout_pack_download", ModelDownloadWorker.CUTOUT_PACK_WORK_NAME)
        assertNotEquals(ModelDownloadWorker.WORK_NAME, ModelDownloadWorker.CUTOUT_PACK_WORK_NAME)
    }
}

/** Serves [bytes] in chunks; the second read signals [paused] and waits for [release], so a cancel lands mid-file. */
private class PausingStream(
    private val bytes: ByteArray,
    private val paused: CountDownLatch,
    private val release: CountDownLatch,
) : InputStream() {
    private var position = 0
    private var reads = 0

    override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= bytes.size) return -1
        reads++
        if (reads == 2) {
            paused.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val count = minOf(length, bytes.size - position)
        System.arraycopy(bytes, position, buffer, offset, count)
        position += count
        return count
    }
}
