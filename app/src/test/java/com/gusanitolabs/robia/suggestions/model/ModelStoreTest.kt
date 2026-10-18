package com.gusanitolabs.robia.suggestions.model

import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

class ModelStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val payload = ByteArray(10_000) { (it % 251).toByte() }
    private val payloadSha = sha256Hex(payload)

    private val modelsDir get() = File(temp.root, "models")
    private val finalFile get() = File(modelsDir, EmbeddingRecipe.MODEL_FILE_NAME)
    private val partFile get() = File(modelsDir, "${EmbeddingRecipe.MODEL_FILE_NAME}.part")
    private val sidecar get() = File(modelsDir, "${EmbeddingRecipe.MODEL_FILE_NAME}.sha256")

    private fun store(
        source: ModelSource,
        unmetered: Boolean = true,
        expectedSha256: String = payloadSha,
        expectedSizeBytes: Long? = null,
    ) = ModelStore(
        rootDir = modelsDir,
        source = source,
        isUnmetered = { unmetered },
        freeSpace = { Long.MAX_VALUE },
        expectedSha256 = expectedSha256,
        expectedSizeBytes = expectedSizeBytes,
    )

    @Test
    fun fullDownload_verifiesAndPublishesReady() {
        val source = ModelSource { ModelResponse(200, payload.size.toLong(), ByteArrayInputStream(payload)) }

        val result = runBlocking { store(source).download() }

        assertTrue(result is ModelState.Ready)
        assertArrayEquals(payload, finalFile.readBytes())
        assertTrue(sidecar.readText().contains(payloadSha))
        assertFalse(partFile.exists())
    }

    @Test
    fun resumedDownload_requestsRemainingBytesAndVerifiesWholeFile() {
        modelsDir.mkdirs()
        partFile.writeBytes(payload.copyOfRange(0, 4_000))
        var requestedOffset = -1L
        val source = ModelSource { offset ->
            requestedOffset = offset
            ModelResponse(
                206,
                (payload.size - 4_000).toLong(),
                ByteArrayInputStream(payload.copyOfRange(4_000, payload.size)),
            )
        }

        val result = runBlocking { store(source).download() }

        assertTrue(result is ModelState.Ready)
        assertEquals(4_000L, requestedOffset)
        assertArrayEquals(payload, finalFile.readBytes())
    }

    @Test
    fun serverIgnoringRange_restartsPartialFileFromZero() {
        modelsDir.mkdirs()
        partFile.writeBytes(ByteArray(3_000) { 9 })
        val source = ModelSource { ModelResponse(200, payload.size.toLong(), ByteArrayInputStream(payload)) }

        val result = runBlocking { store(source).download() }

        assertTrue(result is ModelState.Ready)
        assertArrayEquals(payload, finalFile.readBytes())
    }

    @Test
    fun hashMismatch_failsCorruptAndRemovesAllFiles() {
        val source = ModelSource { ModelResponse(200, payload.size.toLong(), ByteArrayInputStream(payload)) }

        val result = runBlocking { store(source, expectedSha256 = "0".repeat(64)).download() }

        assertEquals(ModelState.Failed(ModelFailure.CorruptDownload), result)
        assertFalse(finalFile.exists())
        assertFalse(partFile.exists())
        assertFalse(sidecar.exists())
    }

    @Test
    fun meteredNetwork_waitsForWifiWithoutOpeningSource() {
        var opened = false
        val source = ModelSource {
            opened = true
            error("source must not be opened on metered networks")
        }

        val result = runBlocking { store(source, unmetered = false).download() }

        assertEquals(ModelState.WaitingForWifi, result)
        assertFalse(opened)
    }

    @Test
    fun meteredNetwork_downloadsWhenMobileDataWasConfirmed() {
        val source = ModelSource { ModelResponse(200, payload.size.toLong(), ByteArrayInputStream(payload)) }

        val result = runBlocking { store(source, unmetered = false).download(allowMetered = true) }

        assertTrue(result is ModelState.Ready)
    }

    @Test
    fun packFile_usesItsOwnNameAndPinnedHash() {
        val packFile = File(temp.root, "cutout_pack").apply { mkdirs() }
        val store = ModelStore(
            rootDir = packFile,
            source = ModelSource { ModelResponse(200, payload.size.toLong(), ByteArrayInputStream(payload)) },
            isUnmetered = { true },
            freeSpace = { Long.MAX_VALUE },
            expectedSha256 = payloadSha,
            fileName = "u2netp.onnx",
        )

        val result = runBlocking { store.download() }

        assertTrue(result is ModelState.Ready)
        assertArrayEquals(payload, File(packFile, "u2netp.onnx").readBytes())
        assertTrue(File(packFile, "u2netp.onnx.sha256").readText().contains(payloadSha))
        assertFalse(File(packFile, EmbeddingRecipe.MODEL_FILE_NAME).exists())
    }

    @Test
    fun packFile_wrongHashIsRejectedAndNothingIsKept() {
        val packFile = File(temp.root, "cutout_pack").apply { mkdirs() }
        val store = ModelStore(
            rootDir = packFile,
            source = ModelSource { ModelResponse(200, payload.size.toLong(), ByteArrayInputStream(payload)) },
            isUnmetered = { true },
            freeSpace = { Long.MAX_VALUE },
            expectedSha256 = "0".repeat(64),
            fileName = "sam_mask_decoder_multi.onnx",
        )

        val result = runBlocking { store.download() }

        assertEquals(ModelState.Failed(ModelFailure.CorruptDownload), result)
        assertFalse(File(packFile, "sam_mask_decoder_multi.onnx").exists())
    }

    @Test
    fun packFile_truncatedFinalFileIsNotReady() {
        val packFile = File(temp.root, "cutout_pack").apply { mkdirs() }
        File(packFile, "u2netp.onnx").writeBytes(payload.copyOfRange(0, 4_000))
        File(packFile, "u2netp.onnx.sha256").writeText(payloadSha)

        val store = ModelStore(
            rootDir = packFile,
            source = ModelSource { error("source must not be opened when the file is present") },
            isUnmetered = { true },
            freeSpace = { Long.MAX_VALUE },
            expectedSha256 = payloadSha,
            fileName = "u2netp.onnx",
            expectedSizeBytes = payload.size.toLong(),
        )

        assertEquals(ModelState.NotDownloaded, store.state.value)
    }

    @Test
    fun packFile_correctSizeAndHashIsReady() {
        val packFile = File(temp.root, "cutout_pack").apply { mkdirs() }
        File(packFile, "u2netp.onnx").writeBytes(payload)
        File(packFile, "u2netp.onnx.sha256").writeText(payloadSha)

        val store = ModelStore(
            rootDir = packFile,
            source = ModelSource { error("source must not be opened when the file is present") },
            isUnmetered = { true },
            freeSpace = { Long.MAX_VALUE },
            expectedSha256 = payloadSha,
            fileName = "u2netp.onnx",
            expectedSizeBytes = payload.size.toLong(),
        )

        assertTrue(store.state.value is ModelState.Ready)
    }

    @Test
    fun packFile_wrongSizeDownloadIsRejectedAndNothingIsKept() {
        val packFile = File(temp.root, "cutout_pack").apply { mkdirs() }
        val store = ModelStore(
            rootDir = packFile,
            source = ModelSource { ModelResponse(200, 4_000L, ByteArrayInputStream(payload.copyOfRange(0, 4_000))) },
            isUnmetered = { true },
            freeSpace = { Long.MAX_VALUE },
            expectedSha256 = payloadSha,
            fileName = "u2netp.onnx",
            expectedSizeBytes = payload.size.toLong(),
        )

        val result = runBlocking { store.download() }

        assertEquals(ModelState.Failed(ModelFailure.CorruptDownload), result)
        assertFalse(File(packFile, "u2netp.onnx").exists())
        assertFalse(File(packFile, "u2netp.onnx.part").exists())
    }

    @Test
    fun gemmaDefaults_readyWithoutSizeCheck() {
        modelsDir.mkdirs()
        finalFile.writeBytes(payload.copyOfRange(0, 4_000))
        sidecar.writeText(payloadSha)

        val store = ModelStore(
            rootDir = modelsDir,
            source = ModelSource { error("source must not be opened when the file is present") },
            isUnmetered = { true },
            freeSpace = { Long.MAX_VALUE },
            expectedSha256 = payloadSha,
        )

        assertTrue(store.state.value is ModelState.Ready)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
