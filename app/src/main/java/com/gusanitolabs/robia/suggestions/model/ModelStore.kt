package com.gusanitolabs.robia.suggestions.model

import com.gusanitolabs.robia.suggestions.EmbeddingRecipe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

sealed interface ModelState {
    data object NotDownloaded : ModelState
    data object WaitingForWifi : ModelState
    data class Downloading(val bytes: Long, val total: Long) : ModelState
    data object Verifying : ModelState
    data class Ready(val path: String) : ModelState
    data class Failed(val reason: ModelFailure) : ModelState
}

sealed interface ModelFailure {
    data object NoNetwork : ModelFailure
    data class Http(val code: Int) : ModelFailure
    data object CorruptDownload : ModelFailure
    data object NoSpace : ModelFailure
}

/** [stream] starts at the requested offset when [code] is 206 and at byte 0 when it is 200. */
class ModelResponse(val code: Int, val contentLength: Long, val stream: InputStream)

fun interface ModelSource {
    fun open(rangeStart: Long): ModelResponse
}

class HttpModelSource(private val url: String) : ModelSource {
    override fun open(rangeStart: Long): ModelResponse {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        if (rangeStart > 0) connection.setRequestProperty("Range", "bytes=$rangeStart-")
        val code = connection.responseCode
        if (code != HttpURLConnection.HTTP_OK && code != HttpURLConnection.HTTP_PARTIAL) {
            connection.disconnect()
            return ModelResponse(code, 0L, InputStream.nullInputStream())
        }
        return ModelResponse(code, connection.contentLengthLong, connection.inputStream)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}

/**
 * Downloads the pinned model into `rootDir` (partial file, then verified final file). Resumes with a Range request;
 * a server that ignores Range (200) restarts the file from zero.
 */
class ModelStore(
    private val rootDir: File,
    private val source: ModelSource,
    private val isUnmetered: () -> Boolean,
    private val freeSpace: () -> Long = { rootDir.usableSpace },
    private val expectedSha256: String = EmbeddingRecipe.MODEL_SHA256,
) {
    private val lock = Mutex()
    private val finalFile = File(rootDir, EmbeddingRecipe.MODEL_FILE_NAME)
    private val partFile = File(rootDir, "${EmbeddingRecipe.MODEL_FILE_NAME}.part")
    private val sidecar = File(rootDir, "${EmbeddingRecipe.MODEL_FILE_NAME}.sha256")
    private val mutableState = MutableStateFlow<ModelState>(
        if (isReady()) ModelState.Ready(finalFile.path) else ModelState.NotDownloaded,
    )
    val state: StateFlow<ModelState> = mutableState.asStateFlow()

    /**
     * Downloads or resumes the model. Metered networks are refused unless [allowMetered] (the user confirmed mobile data).
     * Returns the final state; the same state is published to [state].
     */
    suspend fun download(allowMetered: Boolean = false): ModelState = lock.withLock {
        try {
            publish(downloadLocked(allowMetered))
        } catch (cancelled: CancellationException) {
            // Cancelled by the user or WorkManager; the partial file stays so the next download resumes.
            publish(ModelState.NotDownloaded)
            throw cancelled
        }
    }

    /** Removes the model, its checksum sidecar and any partial file. Callers release the engine first. */
    suspend fun delete() {
        lock.withLock {
            deleteAll()
            publish(ModelState.NotDownloaded)
        }
    }

    private suspend fun downloadLocked(allowMetered: Boolean): ModelState {
        if (isReady()) return ModelState.Ready(finalFile.path)
        if (!allowMetered && !isUnmetered()) return ModelState.WaitingForWifi
        rootDir.mkdirs()
        if (freeSpace() <= MIN_FREE_BYTES) return ModelState.Failed(ModelFailure.NoSpace)
        return withContext(Dispatchers.IO) { fetchAndVerify() }
    }

    private suspend fun fetchAndVerify(): ModelState {
        val offset = if (partFile.exists()) partFile.length() else 0L
        val response = try {
            source.open(offset)
        } catch (failure: IOException) {
            return ModelState.Failed(ModelFailure.NoNetwork)
        }
        if (response.code != HttpURLConnection.HTTP_OK && response.code != HttpURLConnection.HTTP_PARTIAL) {
            response.stream.close()
            return ModelState.Failed(ModelFailure.Http(response.code))
        }
        val resumed = response.code == HttpURLConnection.HTTP_PARTIAL
        val digest = MessageDigest.getInstance("SHA-256")
        var written = if (resumed) offset else 0L
        val total = if (response.contentLength >= 0) written + response.contentLength else -1L
        try {
            response.stream.use { input ->
                FileOutputStream(partFile, resumed).use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        // A resumed stream cannot be hashed incrementally; the whole file is hashed below.
                        if (!resumed) digest.update(buffer, 0, read)
                        written += read
                        mutableState.value = ModelState.Downloading(written, total)
                    }
                }
            }
        } catch (failure: IOException) {
            // The partial file stays on disk so the next run resumes with a Range request.
            return ModelState.Failed(ModelFailure.NoNetwork)
        }

        mutableState.value = ModelState.Verifying
        val actual = if (resumed) sha256Of(partFile) else digest.digest().toHex()
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            deleteAll()
            return ModelState.Failed(ModelFailure.CorruptDownload)
        }
        return try {
            Files.move(partFile.toPath(), finalFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            sidecar.writeText(expectedSha256)
            ModelState.Ready(finalFile.path)
        } catch (failure: IOException) {
            ModelState.Failed(ModelFailure.NoSpace)
        }
    }

    private fun isReady(): Boolean =
        finalFile.isFile && sidecar.isFile && sidecar.readText().contains(expectedSha256)

    private fun publish(state: ModelState): ModelState {
        mutableState.value = state
        return state
    }

    private fun deleteAll() {
        finalFile.delete()
        sidecar.delete()
        partFile.delete()
    }

    private fun sha256Of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_FREE_BYTES = 450L * 1024 * 1024
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}
