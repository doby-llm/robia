package com.gusanitolabs.robia.sync

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.DriveSyncTarget
import com.gusanitolabs.robia.core.model.GarmentColorMappingRecord
import com.gusanitolabs.robia.core.model.GarmentPhotoRecord
import com.gusanitolabs.robia.core.model.GarmentSyncRecord
import com.gusanitolabs.robia.core.model.GarmentTagMappingRecord
import com.gusanitolabs.robia.core.model.MainColorSyncRecord
import com.gusanitolabs.robia.core.model.SyncTombstoneRecord
import com.gusanitolabs.robia.core.model.TagCategorySyncRecord
import com.gusanitolabs.robia.core.model.TagSyncRecord
import com.gusanitolabs.robia.core.model.WardrobeSyncSnapshot
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.ImageBlob
import com.gusanitolabs.robia.media.magicHex
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * Google Drive appDataFolder adapter for the already-authorized user account.
 *
 * Robia only stores deterministic wardrobe snapshots under the user's private Drive app data space.
 * OAuth is re-validated for every operation; if Play Services needs user interaction this adapter reports
 * an honest blocked state instead of pretending the snapshot was uploaded.
 */
class GoogleDriveWardrobeRepository(
    private val context: Context,
    private val authorizationClient: AuthorizationClient,
    private val driveScope: Scope,
    private val api: DriveSnapshotApi = HttpDriveSnapshotApi(),
    override val target: DriveSyncTarget = DriveSyncTarget(),
) : DriveWardrobeRepository {
    override suspend fun fetchManifest(): DriveSyncResult<DriveManifest> = withAccessToken { accessToken ->
        when (val snapshotResult = api.fetchSnapshot(accessToken)) {
            is DriveApiResult.Success -> DriveSyncResult.Success(
                DriveManifest.fromSnapshot(snapshotResult.value.sortedDeterministically()),
            )
            is DriveApiResult.NotFound -> DriveSyncResult.Success(DriveManifest(target = target))
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure -> DriveSyncResult.Failure(snapshotResult.throwable)
        }
    }

    override suspend fun fetchSnapshot(): DriveSyncResult<WardrobeSyncSnapshot> = withAccessToken { accessToken ->
        when (val snapshotResult = api.fetchSnapshot(accessToken)) {
            is DriveApiResult.Success -> hydratePhotoBlobs(accessToken, snapshotResult.value.sortedDeterministically())
            is DriveApiResult.NotFound -> DriveSyncResult.Success(WardrobeSyncSnapshot())
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure -> DriveSyncResult.Failure(snapshotResult.throwable)
        }
    }

    override suspend fun retryRestoredPhoto(garmentId: String): DriveSyncResult<GarmentPhotoRecord> = withAccessToken { accessToken ->
        when (val snapshotResult = api.fetchSnapshot(accessToken)) {
            is DriveApiResult.Success -> {
                val snapshot = snapshotResult.value.sortedDeterministically()
                val photo = snapshot.photos.singleOrNull { it.garmentId == garmentId }
                    ?: return@withAccessToken DriveSyncResult.Failure(
                        IllegalStateException("No remote photo exists for garment $garmentId."),
                    )
                when (val hydrated = hydratePhotoBlobs(accessToken, snapshot.copy(photos = listOf(photo)))) {
                    is DriveSyncResult.Success -> DriveSyncResult.Success(hydrated.value.photos.single())
                    is DriveSyncResult.Blocked -> hydrated
                    is DriveSyncResult.Failure -> hydrated
                }
            }
            is DriveApiResult.NotFound -> DriveSyncResult.Failure(
                IllegalStateException("No remote snapshot exists for garment $garmentId."),
            )
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure -> DriveSyncResult.Failure(snapshotResult.throwable)
        }
    }

    override suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot): DriveSyncResult<DriveManifest> =
        withAccessToken { accessToken ->
            val deterministicSnapshot = when (val result = uploadPhotoBlobs(accessToken, snapshot.sortedDeterministically())) {
                is DriveSyncResult.Success -> result.value
                is DriveSyncResult.Blocked -> return@withAccessToken result
                is DriveSyncResult.Failure -> return@withAccessToken result
            }
            when (val purgeResult = purgeDeletedPhotoBlobs(accessToken, deterministicSnapshot)) {
                is DriveSyncResult.Success -> Unit
                is DriveSyncResult.Blocked -> return@withAccessToken purgeResult
                is DriveSyncResult.Failure -> return@withAccessToken purgeResult
            }
            when (val result = api.upsertSnapshot(accessToken, deterministicSnapshot)) {
                is DriveApiResult.Success -> DriveSyncResult.Success(DriveManifest.fromSnapshot(result.value))
                is DriveApiResult.Unauthorized -> authBlocked()
                is DriveApiResult.NotFound -> DriveSyncResult.Failure(
                    IllegalStateException("Drive snapshot upload completed without a readable file."),
                )
                is DriveApiResult.Failure -> DriveSyncResult.Failure(result.throwable)
            }
        }

    override suspend fun deleteBackup(): DriveSyncResult<DriveBackupDeletionResult> = withAccessToken { accessToken ->
        when (val listed = api.listBackupFiles(accessToken)) {
            is DriveApiResult.Success -> {
                if (listed.value.size >= MAX_BACKUP_DELETE_FILES) {
                    return@withAccessToken DriveSyncResult.Failure(
                        IllegalStateException("Drive backup contains more files than the safe deletion limit."),
                    )
                }
                listed.value.forEach { file ->
                    when (val deleted = api.deleteFile(accessToken, file.id)) {
                        is DriveApiResult.Success, DriveApiResult.NotFound -> Unit
                        is DriveApiResult.Unauthorized -> return@withAccessToken authBlocked()
                        is DriveApiResult.Failure -> return@withAccessToken DriveSyncResult.Failure(deleted.throwable)
                    }
                }
                // Relist after idempotent DELETEs; a backup is deleted only once inventory is empty.
                when (val relist = api.listBackupFiles(accessToken)) {
                    is DriveApiResult.Success -> DriveSyncResult.Success(
                        DriveBackupDeletionResult(listed.value.size, relist.value.size),
                    )
                    is DriveApiResult.NotFound -> DriveSyncResult.Success(DriveBackupDeletionResult(listed.value.size))
                    is DriveApiResult.Unauthorized -> authBlocked()
                    is DriveApiResult.Failure -> DriveSyncResult.Failure(relist.throwable)
                }
            }
            is DriveApiResult.NotFound -> DriveSyncResult.Success(DriveBackupDeletionResult())
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure -> DriveSyncResult.Failure(listed.throwable)
        }
    }

    private suspend fun <T> withAccessToken(
        operation: suspend (accessToken: String) -> DriveSyncResult<T>,
    ): DriveSyncResult<T> {
        val authorizationResult = runCatching {
            withTimeout(AUTHORIZATION_TIMEOUT_MILLIS) {
                authorizationClient.authorize(
                    AuthorizationRequest.builder()
                        .setRequestedScopes(listOf(driveScope))
                        .build(),
                ).await()
            }
        }.getOrElse { throwable ->
            return DriveSyncResult.Failure(throwable)
        }

        if (authorizationResult.hasResolution()) {
            return authBlocked()
        }

        val grantedDriveScope = authorizationResult.grantedScopes.any { scope ->
            scope == driveScope.scopeUri
        }
        val accessToken = authorizationResult.accessToken
        if (!grantedDriveScope || accessToken.isNullOrBlank()) {
            return authBlocked()
        }

        return withDrivePhaseDeadline { operation(accessToken) }
    }

    private suspend fun <T> withDrivePhaseDeadline(
        operation: suspend () -> DriveSyncResult<T>,
    ): DriveSyncResult<T> = try {
        withTimeout(DRIVE_PHASE_TIMEOUT_MILLIS) { operation() }
    } catch (timeout: TimeoutCancellationException) {
        DriveSyncResult.Failure(java.net.SocketTimeoutException("Drive operation timed out."))
    }

    private companion object {
        const val AUTHORIZATION_TIMEOUT_MILLIS = 30_000L
        const val DRIVE_PHASE_TIMEOUT_MILLIS = 60_000L
    }

    private fun hydratePhotoBlobs(
        accessToken: String,
        snapshot: WardrobeSyncSnapshot,
    ): DriveSyncResult<WardrobeSyncSnapshot> {
        val garmentNameById = snapshot.garments.associate { it.id to it.name }
        val hydratedPhotos = snapshot.photos.map { photo ->
            val blobPath = photo.blobPath.takeIf(String::isNotBlank)
                ?: return@map photo.guardedRestore(
                    category = REMOTE_PHOTO_MISSING_BLOB_PATH,
                    message = "Garment photo ${photo.garmentId} is missing a Drive blob path.",
                    restoreDiagnosticEvents = listOf(
                        photo.importDiagnosticEvent(
                            persistedPhotoUriPresent = false,
                            placeholderReason = REMOTE_PHOTO_MISSING_BLOB_PATH,
                        ),
                    ),
                )
            val fetchStartedEvents = photo.restoreFetchStartedEvents(description = garmentNameById[photo.garmentId])
            when (val blobResult = fetchPhotoBlob(accessToken, photo, blobPath)) {
                is DriveApiResult.Success -> {
                    val bytes = blobResult.value.bytes
                    val fetchEvents = fetchStartedEvents + blobResult.value.restoreFetchResultEvents(photo)
                    val byteDecode = decodeImageBytes(bytes)
                    val restoredUriResult = runCatching {
                        ClothingImageStore.writeRestoredImageBlob(
                            context = context,
                            bytes = bytes,
                            blobPath = blobPath,
                            mimeType = photo.mimeType,
                        )
                    }
                    val restoredUri = restoredUriResult.getOrNull()
                    val readBackBytes = restoredUri?.let { uri ->
                        runCatching { context.contentResolver.openInputStream(uri)?.use { input -> input.readBytes() } }.getOrNull()
                    }
                    val readBackDecode = readBackBytes?.let(::decodeImageBytes)
                    val uriDimensions = restoredUri?.let { uri -> runCatching { ClothingImageStore.readImageDimensions(context, uri) }.getOrNull() }
                    val localWriteEvent = photo.localWriteDiagnosticEvent(
                        targetExtension = photo.mimeType.toPhotoExtension() ?: blobPath.substringAfterLast('.', "jpg"),
                        fileLength = bytes.size.toLong(),
                        readBackBytes = readBackBytes,
                        writeFailure = restoredUriResult.exceptionOrNull(),
                    )
                    val decodeEvents = listOf(
                        photo.decodeDiagnosticEvent(decoderPath = "BitmapFactory.byteArray", result = byteDecode),
                        photo.decodeDiagnosticEvent(decoderPath = "BitmapFactory.readbackBytes", result = readBackDecode),
                        photo.decodeDiagnosticEvent(
                            decoderPath = "BitmapFactory.contentUriBounds",
                            width = uriDimensions?.first,
                            height = uriDimensions?.second,
                            failure = if (uriDimensions == null) "contentUriBounds returned no dimensions" else null,
                        ),
                    )
                    val authoritativeDecode = readBackDecode?.takeIf { it.isSuccess }
                        ?: byteDecode.takeIf { it.isSuccess }
                    if (restoredUri != null && authoritativeDecode != null) {
                        photo.copy(
                            restoredLocalUri = restoredUri.toString(),
                            byteSize = bytes.size.toLong(),
                            contentHash = bytes.sha256Hex(),
                            byteMagic = bytes.magicHex(),
                            decodedWidth = authoritativeDecode.width,
                            decodedHeight = authoritativeDecode.height,
                            restoreFailureCategory = null,
                            restoreFailureMessage = null,
                            restoreDiagnosticEvents = fetchEvents + localWriteEvent + decodeEvents +
                                photo.importDiagnosticEvent(persistedPhotoUriPresent = true, placeholderReason = null),
                        )
                    } else {
                        val failure = restoredUriResult.exceptionOrNull()
                        val message = when {
                            restoredUri == null -> "write_failed:${failure?.diagnosticSummary() ?: "unknown"}"
                            byteDecode.failure != null -> byteDecode.failure
                            readBackDecode?.failure != null -> readBackDecode.failure
                            else -> "decode_failed: no decoder produced dimensions"
                        }
                        photo.guardedRestore(
                            category = REMOTE_PHOTO_UNREADABLE,
                            message = "Remote Drive photo blob $blobPath for garment ${photo.garmentId} fetched but could not be decoded/imported " +
                                "mime=${photo.mimeType ?: "unknown"} magic=${bytes.magicHex()} byteSize=${bytes.size} detail=$message.",
                            cause = failure,
                            byteSize = bytes.size.toLong(),
                            contentHash = bytes.sha256Hex(),
                            byteMagic = bytes.magicHex(),
                            restoreDiagnosticEvents = fetchEvents + localWriteEvent + decodeEvents + photo.importDiagnosticEvent(
                                persistedPhotoUriPresent = false,
                                placeholderReason = REMOTE_PHOTO_UNREADABLE,
                            ),
                        )
                    }
                }
                is DriveApiResult.NotFound -> photo.guardedRestore(
                    category = REMOTE_PHOTO_MISSING,
                    message = "Drive photo blob $blobPath was not found for garment ${photo.garmentId}.",
                    restoreDiagnosticEvents = fetchStartedEvents + listOf(
                        photo.driveLookupMissingEvent(),
                        photo.fetchMissingEvent(),
                        photo.importDiagnosticEvent(
                            persistedPhotoUriPresent = false,
                            placeholderReason = REMOTE_PHOTO_MISSING,
                        ),
                    ),
                )
                is DriveApiResult.Failure -> return DriveSyncResult.Failure(blobResult.throwable)
                is DriveApiResult.Unauthorized -> return authBlocked()
            }
        }
        return DriveSyncResult.Success(snapshot.copy(photos = hydratedPhotos).sortedDeterministically())
    }

    /** Exact paths are authoritative; legacy paths are only safe when one candidate exists. */
    private fun fetchPhotoBlob(
        accessToken: String,
        photo: GarmentPhotoRecord,
        exactBlobPath: String,
    ): DriveApiResult<DriveBlob> {
        val photoBlobPrefix = DriveFolderNaming.photoBlobPrefix(photo.garmentId)
        return when (val exactResult = api.fetchBlob(accessToken, exactBlobPath)) {
            is DriveApiResult.NotFound -> when (
                val candidates = api.listBlobPathsWithPrefix(accessToken, photoBlobPrefix)
            ) {
                is DriveApiResult.Success -> {
                    val legacyPhotoRestoreCandidate = candidates.value
                        .filter { path -> path.startsWith(photoBlobPrefix) }
                        .distinct()
                        .singleOrNull()
                    // exact_blob_not_found: ambiguous candidates deliberately stay in terminal attention.
                    legacyPhotoRestoreCandidate?.let { candidate -> api.fetchBlob(accessToken, candidate) } ?: exactResult
                }
                is DriveApiResult.NotFound -> exactResult
                is DriveApiResult.Unauthorized -> DriveApiResult.Unauthorized
                is DriveApiResult.Failure -> DriveApiResult.Failure(candidates.throwable)
            }
            else -> exactResult
        }
    }

    private fun GarmentPhotoRecord.guardedRestore(
        category: String,
        message: String,
        cause: Throwable? = null,
        byteSize: Long? = null,
        contentHash: String? = null,
        byteMagic: String? = null,
        restoreDiagnosticEvents: List<String> = emptyList(),
    ): GarmentPhotoRecord = copy(
        restoredLocalUri = null,
        byteSize = byteSize ?: this.byteSize,
        contentHash = contentHash ?: this.contentHash,
        byteMagic = byteMagic ?: this.byteMagic,
        decodedWidth = null,
        decodedHeight = null,
        restoreFailureCategory = category,
        restoreFailureMessage = sanitizePhotoRestoreMessage(
            if (cause?.message.isNullOrBlank()) message else "$message Cause: ${cause?.message}",
        ),
        restoreDiagnosticEvents = restoreDiagnosticEvents,
    )

    private fun uploadPhotoBlobs(
        accessToken: String,
        snapshot: WardrobeSyncSnapshot,
    ): DriveSyncResult<WardrobeSyncSnapshot> {
        val uploadedPhotos = snapshot.photos.map { photo ->
            val blobPath = photo.blobPath.takeIf(String::isNotBlank) ?: return DriveSyncResult.Failure(
                IllegalStateException("Garment photo ${photo.garmentId} is missing a Drive blob path."),
            )
            if (photo.restoreFailureCategory != null && photo.restoredLocalUri.isNullOrBlank()) {
                return@map photo
            }
            val sourceUri = photo.restoredLocalUri?.takeIf(String::isNotBlank) ?: photo.localUri
            val blob = runCatching { ClothingImageStore.readCanonicalDriveImageBlob(context, Uri.parse(sourceUri)) }
                .getOrElse { throwable ->
                    return DriveSyncResult.Failure(
                        IllegalStateException(
                            "Garment photo ${photo.garmentId} could not be canonicalized for Drive upload: " +
                                (throwable.message ?: throwable::class.java.simpleName),
                            throwable,
                        ),
                    )
                }
                ?: return DriveSyncResult.Failure(
                    IllegalStateException("Garment photo ${photo.garmentId} could not be read for Drive upload."),
                )
            val verifiedBlob = blob.requireDriveReadable(photo.garmentId)
            when (val uploadResult = api.upsertBlob(accessToken, blobPath, verifiedBlob.bytes, verifiedBlob.mimeType)) {
                is DriveApiResult.Success -> Unit
                is DriveApiResult.Unauthorized -> return authBlocked()
                is DriveApiResult.NotFound -> return DriveSyncResult.Failure(
                    IllegalStateException("Drive photo blob upload completed without a readable file."),
                )
                is DriveApiResult.Failure -> return DriveSyncResult.Failure(uploadResult.throwable)
            }
            photo.copy(
                blobPath = blobPath,
                mimeType = verifiedBlob.mimeType,
                contentHash = verifiedBlob.contentHash,
                byteSize = verifiedBlob.byteSize,
                byteMagic = verifiedBlob.byteMagic,
                decodedWidth = verifiedBlob.decodedWidth,
                decodedHeight = verifiedBlob.decodedHeight,
                restoredLocalUri = null,
                restoreFailureCategory = null,
                restoreFailureMessage = null,
            )
        }
        return DriveSyncResult.Success(snapshot.copy(photos = uploadedPhotos).sortedDeterministically())
    }

    private fun purgeDeletedPhotoBlobs(
        accessToken: String,
        snapshot: WardrobeSyncSnapshot,
    ): DriveSyncResult<Unit> {
        val deletedGarmentIds = snapshot.tombstones
            .filter { it.entityType in PHOTO_TOMBSTONE_ENTITY_TYPES }
            .map { it.entityId }
            .distinct()
        val prefixCandidates = mutableListOf<String>()
        deletedGarmentIds.forEach { garmentId ->
            when (val result = api.listBlobPathsWithPrefix(accessToken, DriveFolderNaming.photoBlobPrefix(garmentId))) {
                is DriveApiResult.Success -> prefixCandidates += result.value
                is DriveApiResult.NotFound -> Unit
                is DriveApiResult.Unauthorized -> return authBlocked()
                is DriveApiResult.Failure -> return DriveSyncResult.Failure(result.throwable)
            }
        }
        val activeBlobPaths = snapshot.photos.mapNotNull { it.blobPath.takeIf(String::isNotBlank) }.toSet()
        val purgeCandidates = (snapshot.deletedPhotoBlobPurgeCandidates() + prefixCandidates)
            .filterNot(activeBlobPaths::contains)
            .distinct()
        purgeCandidates.forEach { blobPath ->
            when (val result = api.deleteBlob(accessToken, blobPath)) {
                is DriveApiResult.Success, DriveApiResult.NotFound -> Unit
                is DriveApiResult.Unauthorized -> return authBlocked()
                is DriveApiResult.Failure -> return DriveSyncResult.Failure(result.throwable)
            }
        }
        return DriveSyncResult.Success(Unit)
    }

private fun ImageBlob.requireDriveReadable(garmentId: String): ImageBlob {
    val width = decodedWidth
    val height = decodedHeight
    require(width != null && width > 0 && height != null && height > 0) {
        "Garment photo $garmentId canonical Drive blob is not readable after encode " +
            "mime=${mimeType ?: "unknown"} magic=$byteMagic byteSize=$byteSize " +
            "sourceMime=${sourceMimeType ?: "unknown"} sourceMagic=${sourceByteMagic ?: "unknown"} " +
            "sourceByteSize=${sourceByteSize ?: 0}."
    }
    return this
}

private fun <T> authBlocked(): DriveSyncResult<T> = DriveSyncResult.Blocked(
        reason = DriveSyncDisabledReason.UserNotConnected,
        message = "Google Drive authorization is required before sync can continue.",
    )
}

interface DriveSnapshotApi {
    suspend fun fetchSnapshot(accessToken: String): DriveApiResult<WardrobeSyncSnapshot>
    fun fetchBlob(accessToken: String, blobPath: String): DriveApiResult<DriveBlob>
    fun listBlobPathsWithPrefix(accessToken: String, blobPathPrefix: String): DriveApiResult<List<String>>
    fun upsertBlob(accessToken: String, blobPath: String, bytes: ByteArray, mimeType: String?): DriveApiResult<Unit>
    fun deleteBlob(accessToken: String, blobPath: String): DriveApiResult<Unit>
    fun listBackupFiles(accessToken: String): DriveApiResult<List<DriveFileMetadata>>
    fun deleteFile(accessToken: String, fileId: String): DriveApiResult<Unit>
    suspend fun upsertSnapshot(accessToken: String, snapshot: WardrobeSyncSnapshot): DriveApiResult<WardrobeSyncSnapshot>
}

data class DriveBlob(
    val bytes: ByteArray,
    val file: DriveFileMetadata? = null,
    val httpStatusCode: Int? = null,
    val contentType: String? = null,
    val contentLength: Long? = null,
)

data class DriveFileMetadata(
    val id: String,
    val modifiedTime: String? = null,
    val mimeType: String? = null,
    val size: Long? = null,
)

sealed interface DriveApiResult<out T> {
    data class Success<T>(val value: T) : DriveApiResult<T>
    data object NotFound : DriveApiResult<Nothing>
    data object Unauthorized : DriveApiResult<Nothing>
    data class Failure(val throwable: Throwable) : DriveApiResult<Nothing>
}

// A full 1,000-item page is treated as overflow, so deletion never traverses unbounded inventory.
private const val MAX_BACKUP_DELETE_FILES = 1_000

private class HttpDriveSnapshotApi : DriveSnapshotApi {
    override suspend fun fetchSnapshot(accessToken: String): DriveApiResult<WardrobeSyncSnapshot> {
        val fileId = findSnapshotFileId(accessToken) ?: return DriveApiResult.NotFound
        return request(
            method = "GET",
            url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
            accessToken = accessToken,
        ) { body -> DriveApiResult.Success(DriveSnapshotJson.decode(body)) }
    }

    override suspend fun upsertSnapshot(
        accessToken: String,
        snapshot: WardrobeSyncSnapshot,
    ): DriveApiResult<WardrobeSyncSnapshot> {
        val existingFileId = findSnapshotFileId(accessToken)
        val method = if (existingFileId == null) "POST" else "PATCH"
        val url = if (existingFileId == null) {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart"
        }
        val metadata = JSONObject()
            .put("name", SNAPSHOT_FILE_NAME)
            .put("mimeType", SNAPSHOT_MIME_TYPE)
            .apply {
                if (existingFileId == null) put("parents", JSONArray().put("appDataFolder"))
            }
            .toString()
        val body = DriveSnapshotJson.encode(snapshot)
        return multipartRequest(method, url, accessToken, metadata, body) {
            DriveApiResult.Success(snapshot.sortedDeterministically())
        }
    }

    override fun fetchBlob(accessToken: String, blobPath: String): DriveApiResult<DriveBlob> {
        val file = findFileByName(accessToken, blobPath) ?: return DriveApiResult.NotFound
        return byteRequest(
            method = "GET",
            url = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media",
            accessToken = accessToken,
        ) { bytes, connection ->
            DriveApiResult.Success(
                DriveBlob(
                    bytes = bytes,
                    file = file,
                    httpStatusCode = connection.responseCode,
                    contentType = connection.contentType,
                    contentLength = connection.contentLengthLong.takeIf { it >= 0L },
                ),
            )
        }
    }

    override fun upsertBlob(
        accessToken: String,
        blobPath: String,
        bytes: ByteArray,
        mimeType: String?,
    ): DriveApiResult<Unit> {
        val existingFileId = findFileIdByName(accessToken, blobPath)
        val method = if (existingFileId == null) "POST" else "PATCH"
        val url = if (existingFileId == null) {
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        } else {
            "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart"
        }
        val metadata = JSONObject()
            .put("name", blobPath)
            .put("mimeType", mimeType ?: PHOTO_BLOB_MIME_TYPE)
            .apply {
                if (existingFileId == null) put("parents", JSONArray().put("appDataFolder"))
            }
            .toString()
        return multipartRequest(method, url, accessToken, metadata, bytes, mimeType ?: PHOTO_BLOB_MIME_TYPE) {
            DriveApiResult.Success(Unit)
        }
    }

    override fun listBlobPathsWithPrefix(accessToken: String, blobPathPrefix: String): DriveApiResult<List<String>> {
        val escapedPrefix = blobPathPrefix.toDriveQueryLiteral()
        val query = "name contains '$escapedPrefix' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder&fields=files(name)&pageSize=1000&q=" +
            URLEncoder.encode(query, "UTF-8")
        return request(
            method = "GET",
            url = url,
            accessToken = accessToken,
        ) { body ->
            val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
            DriveApiResult.Success(
                (0 until files.length()).mapNotNull { index ->
                    files.optJSONObject(index)?.optString("name")?.takeIf { name -> name.startsWith(blobPathPrefix) }
                },
            )
        }
    }

    override fun deleteBlob(accessToken: String, blobPath: String): DriveApiResult<Unit> {
        val fileId = findFileIdByName(accessToken, blobPath) ?: return DriveApiResult.NotFound
        return request(
            method = "DELETE",
            url = "https://www.googleapis.com/drive/v3/files/$fileId",
            accessToken = accessToken,
        ) { DriveApiResult.Success(Unit) }
    }

    override fun listBackupFiles(accessToken: String): DriveApiResult<List<DriveFileMetadata>> = request(
        method = "GET",
        url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,modifiedTime,mimeType,size)&pageSize=$MAX_BACKUP_DELETE_FILES",
        accessToken = accessToken,
    ) { body ->
        val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
        DriveApiResult.Success((0 until files.length()).mapNotNull { index ->
            files.optJSONObject(index)?.optString("id")?.takeIf(String::isNotBlank)?.let { id -> DriveFileMetadata(id = id) }
        })
    }

    override fun deleteFile(accessToken: String, fileId: String): DriveApiResult<Unit> = request(
        method = "DELETE",
        url = "https://www.googleapis.com/drive/v3/files/$fileId",
        accessToken = accessToken,
    ) { DriveApiResult.Success(Unit) }

    private fun findSnapshotFileId(accessToken: String): String? = findFileIdByName(accessToken, SNAPSHOT_FILE_NAME)

    private fun findFileIdByName(accessToken: String, name: String): String? = findFileByName(accessToken, name)?.id

    private fun findFileByName(accessToken: String, name: String): DriveFileMetadata? {
        val escapedName = name.toDriveQueryLiteral()
        val query = "name='$escapedName' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder&fields=files(id,name,modifiedTime,mimeType,size)&pageSize=1&orderBy=modifiedTime%20desc&q=" +
            URLEncoder.encode(query, "UTF-8")
        val result = request(
            method = "GET",
            url = url,
            accessToken = accessToken,
        ) { body ->
            val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
            val file = files.optJSONObject(0)
            DriveApiResult.Success(
                file?.optString("id")?.takeIf(String::isNotBlank)?.let { id ->
                    DriveFileMetadata(
                        id = id,
                        modifiedTime = file.optString("modifiedTime").takeIf(String::isNotBlank),
                        mimeType = file.optString("mimeType").takeIf(String::isNotBlank),
                        size = file.optLong("size").takeIf { it > 0L },
                    )
                },
            )
        }
        return (result as? DriveApiResult.Success)?.value
    }

    private fun String.toDriveQueryLiteral(): String = replace("\\", "\\\\").replace("'", "\\'")

    private fun <T> request(
        method: String,
        url: String,
        accessToken: String,
        parse: (body: String) -> DriveApiResult<T>,
    ): DriveApiResult<T> = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }
        connection.toDriveResult(parse)
    } catch (throwable: Throwable) {
        DriveApiResult.Failure(throwable)
    }

    private fun <T> byteRequest(
        method: String,
        url: String,
        accessToken: String,
        parse: (bytes: ByteArray, connection: HttpURLConnection) -> DriveApiResult<T>,
    ): DriveApiResult<T> = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }
        connection.toDriveByteResult(parse)
    } catch (throwable: Throwable) {
        DriveApiResult.Failure(throwable)
    }

    private fun <T> multipartRequest(
        method: String,
        url: String,
        accessToken: String,
        metadata: String,
        body: String,
        parse: (body: String) -> DriveApiResult<T>,
    ): DriveApiResult<T> = multipartRequest(
        method = method,
        url = url,
        accessToken = accessToken,
        metadata = metadata,
        body = body.toByteArray(Charsets.UTF_8),
        bodyMimeType = "$SNAPSHOT_MIME_TYPE; charset=UTF-8",
        parse = parse,
    )

    private fun <T> multipartRequest(
        method: String,
        url: String,
        accessToken: String,
        metadata: String,
        body: ByteArray,
        bodyMimeType: String,
        parse: (body: String) -> DriveApiResult<T>,
    ): DriveApiResult<T> = try {
        val boundary = "robia_drive_snapshot_${System.currentTimeMillis()}"
        val header = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: $bodyMimeType\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)
        val footer = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
        val payload = header + body + footer

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            setRequestProperty("Content-Length", payload.size.toString())
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }
        connection.outputStream.use { output -> output.write(payload) }
        connection.toDriveResult(parse)
    } catch (throwable: Throwable) {
        DriveApiResult.Failure(throwable)
    }

    private fun <T> HttpURLConnection.toDriveResult(parse: (body: String) -> DriveApiResult<T>): DriveApiResult<T> =
        try {
            val body = if (responseCode in 200..299) {
                inputStream.bufferedReader().use { reader -> reader.readText() }
            } else {
                errorStream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            }
            when (responseCode) {
                in 200..299 -> parse(body)
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> DriveApiResult.Unauthorized
                HttpURLConnection.HTTP_NOT_FOUND -> DriveApiResult.NotFound
                else -> DriveApiResult.Failure(IOException("Drive API returned HTTP $responseCode: $body"))
            }
        } finally {
            disconnect()
        }

    private fun <T> HttpURLConnection.toDriveByteResult(
        parse: (bytes: ByteArray, connection: HttpURLConnection) -> DriveApiResult<T>,
    ): DriveApiResult<T> =
        try {
            val bytes = if (responseCode in 200..299) {
                inputStream.use { input -> input.readBytes() }
            } else {
                errorStream?.use { input -> input.readBytes() } ?: ByteArray(0)
            }
            when (responseCode) {
                in 200..299 -> parse(bytes, this)
                HttpURLConnection.HTTP_UNAUTHORIZED,
                HttpURLConnection.HTTP_FORBIDDEN -> DriveApiResult.Unauthorized
                HttpURLConnection.HTTP_NOT_FOUND -> DriveApiResult.NotFound
                else -> DriveApiResult.Failure(IOException("Drive API returned HTTP $responseCode: ${bytes.toString(Charsets.UTF_8)}"))
            }
        } finally {
            disconnect()
        }


    private companion object {
        const val SNAPSHOT_FILE_NAME = "wardrobe_snapshot.json"
        const val SNAPSHOT_MIME_TYPE = "application/vnd.gusanitolabs.robia.wardrobe-snapshot+json"
        const val PHOTO_BLOB_MIME_TYPE = "application/octet-stream"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
}

internal const val REMOTE_PHOTO_MISSING_BLOB_PATH = "remote_photo_missing_blob_path"
internal const val REMOTE_PHOTO_MISSING = "remote_photo_missing"
internal const val REMOTE_PHOTO_UNREADABLE = "remote_photo_unreadable"
private val PHOTO_TOMBSTONE_ENTITY_TYPES = setOf("garment", "clothing_item", "item")

internal fun WardrobeSyncSnapshot.deletedPhotoBlobPurgeCandidates(): List<String> {
    val activeBlobPaths = photos.mapNotNull { it.blobPath.takeIf(String::isNotBlank) }.toSet()
    return tombstones
        .filter { it.entityType in PHOTO_TOMBSTONE_ENTITY_TYPES }
        .map { it.entityId }
        .distinct()
        .flatMap { garmentId ->
            listOf(
                DriveFolderNaming.photoBlobPath(garmentId),
                "photos/$garmentId/original",
            )
        }
        .filterNot(activeBlobPaths::contains)
        .distinct()
}

private fun sanitizePhotoRestoreMessage(message: String): String = message
    .replace(Regex("(^|\\s)(/[^\\s:]+(?:/[^\\s:]+)+)")) { match -> "${match.groupValues[1]}<path-redacted>" }
    .take(1_200)

internal fun GarmentPhotoRecord.restoreFetchStartedEvents(description: String?): List<String> = listOf(
    buildString {
        append("photo_restore_fetch_started garmentId=$garmentId")
        description?.takeIf(String::isNotBlank)?.let { append(" description=\"").append(it).append('"') }
        append(" blobPath=$blobPath")
        byteSize?.let { append(" snapshotByteSize=$it") }
        contentHash?.takeIf(String::isNotBlank)?.let { append(" snapshotHash=${it.take(12)}") }
        mimeType?.takeIf(String::isNotBlank)?.let { append(" snapshotMime=$it") }
    },
)

internal fun DriveBlob.restoreFetchResultEvents(photo: GarmentPhotoRecord): List<String> = listOf(
    buildString {
        append("drive_file_lookup_result garmentId=${photo.garmentId} blobPath=${photo.blobPath} found=${file != null}")
        file?.let { metadata ->
            append(" fileIdHash=${metadata.id.sha256Prefix()}")
            metadata.modifiedTime?.let { append(" modifiedTime=$it") }
            metadata.mimeType?.let { append(" driveMimeType=$it") }
            metadata.size?.let { append(" driveSize=$it") }
        }
    },
    buildString {
        append("photo_restore_fetch_result garmentId=${photo.garmentId} blobPath=${photo.blobPath} status=success")
        httpStatusCode?.let { append(" httpStatus=$it category=${it.httpStatusCategoryLabel()}") }
        append(" bytesLength=${bytes.size}")
        contentType?.let { append(" contentType=$it") }
        contentLength?.let { append(" contentLength=$it") }
        append(" sha256=${bytes.sha256Hex()}")
        append(" first32=${bytes.firstHex(32)}")
        append(" last32=${bytes.lastHex(32)}")
        append(" png=${bytes.pngSanityLabel()}")
    },
)

internal fun GarmentPhotoRecord.driveLookupMissingEvent(): String =
    "drive_file_lookup_result garmentId=$garmentId blobPath=$blobPath found=false"

internal fun GarmentPhotoRecord.fetchMissingEvent(): String =
    "photo_restore_fetch_result garmentId=$garmentId blobPath=$blobPath status=not_found httpStatus=404 category=http_not_found"

internal fun GarmentPhotoRecord.localWriteDiagnosticEvent(
    targetExtension: String,
    fileLength: Long,
    readBackBytes: ByteArray?,
    writeFailure: Throwable? = null,
): String = buildString {
    append("photo_restore_local_write_result garmentId=$garmentId blobPath=$blobPath")
    append(" targetExtension=$targetExtension fileLength=$fileLength")
    append(" readbackByteCount=${readBackBytes?.size ?: 0}")
    readBackBytes?.let {
        append(" readbackHash=${it.sha256Hex()}")
        append(" readbackFirst32=${it.firstHex(32)}")
        append(" readbackLast32=${it.lastHex(32)}")
    }
    writeFailure?.let { append(" writeFailure=\"").append(it.diagnosticSummary()).append('"') }
}

internal fun GarmentPhotoRecord.decodeDiagnosticEvent(
    decoderPath: String,
    width: Int? = null,
    height: Int? = null,
    failure: String? = null,
): String = buildString {
    append("photo_restore_decode_result garmentId=$garmentId blobPath=$blobPath decoder=$decoderPath")
    if (width != null && height != null) {
        append(" status=success width=$width height=$height")
    } else {
        append(" status=failure reason=\"").append(failure ?: "unknown").append('"')
    }
}

private fun GarmentPhotoRecord.decodeDiagnosticEvent(
    decoderPath: String,
    result: ImageDecodeResult?,
): String = if (result == null) {
    decodeDiagnosticEvent(decoderPath = decoderPath, failure = "not_attempted")
} else {
    decodeDiagnosticEvent(
        decoderPath = decoderPath,
        width = result.width,
        height = result.height,
        failure = result.failure,
    )
}

internal fun GarmentPhotoRecord.importDiagnosticEvent(
    persistedPhotoUriPresent: Boolean,
    placeholderReason: String?,
): String = buildString {
    append("photo_restore_import_result garmentId=$garmentId blobPath=$blobPath")
    append(" persistedPhotoUriPresent=$persistedPhotoUriPresent")
    placeholderReason?.let { append(" placeholderReason=$it") }
}

private data class ImageDecodeResult(
    val width: Int? = null,
    val height: Int? = null,
    val failure: String? = null,
) {
    val isSuccess: Boolean = width != null && width > 0 && height != null && height > 0
}

private fun decodeImageBytes(bytes: ByteArray): ImageDecodeResult = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    if (options.outWidth > 0 && options.outHeight > 0) {
        ImageDecodeResult(width = options.outWidth, height = options.outHeight)
    } else {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        if (bitmap != null) {
            try {
                ImageDecodeResult(width = bitmap.width, height = bitmap.height)
            } finally {
                bitmap.recycle()
            }
        } else {
            ImageDecodeResult(failure = "BitmapFactory returned null bounds=${options.outWidth}x${options.outHeight}")
        }
    }
}.getOrElse { throwable -> ImageDecodeResult(failure = throwable.diagnosticSummary()) }

private fun Throwable.diagnosticSummary(): String = buildString {
    append(this@diagnosticSummary::class.java.name)
    message?.takeIf(String::isNotBlank)?.let { append(": ").append(it) }
    stackTrace.firstOrNull()?.let { top ->
        append(" at ").append(top.className).append('.').append(top.methodName).append(':').append(top.lineNumber)
    }
}

private fun String?.toPhotoExtension(): String? = when (this?.lowercase()) {
    "image/jpeg", "image/jpg" -> "jpg"
    "image/png" -> "png"
    "image/webp" -> "webp"
    else -> null
}

private fun String.sha256Prefix(): String = toByteArray(Charsets.UTF_8).sha256Hex().take(12)

private fun Int.httpStatusCategoryLabel(): String = when (this) {
    401, 403 -> "http_auth_$this"
    404 -> "http_not_found"
    in 400..499 -> "http_client_$this"
    in 500..599 -> "http_server_$this"
    else -> "http_$this"
}

private fun ByteArray.sha256Hex(): String = java.security.MessageDigest.getInstance("SHA-256")
    .digest(this)
    .joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun ByteArray.firstHex(byteCount: Int): String = take(byteCount).joinToString(separator = "") { byte -> "%02x".format(byte) }

private fun ByteArray.lastHex(byteCount: Int): String = takeLast(byteCount).joinToString(separator = "") { byte -> "%02x".format(byte) }

internal fun ByteArray.pngSanityLabel(): String {
    val signature = byteArrayOf(
        0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
    )
    val hasSignature = size >= signature.size && signature.indices.all { index -> this[index] == signature[index] }
    if (!hasSignature) return "not_png_signature"
    if (size < 12) return "png_truncated_before_iend"
    val hasIend = this[size - 12] == 0.toByte() &&
        this[size - 11] == 0.toByte() &&
        this[size - 10] == 0.toByte() &&
        this[size - 9] == 0.toByte() &&
        this[size - 8] == 0x49.toByte() &&
        this[size - 7] == 0x45.toByte() &&
        this[size - 6] == 0x4e.toByte() &&
        this[size - 5] == 0x44.toByte()
    return if (hasIend) "png_signature_iend_present" else "png_signature_iend_missing"
}

private object DriveSnapshotJson {
    fun encode(snapshot: WardrobeSyncSnapshot): String {
        val sorted = snapshot.sortedDeterministically()
        return JSONObject()
            .put(
                "metadata",
                JSONObject()
                    .put("schemaVersion", sorted.metadata.schemaVersion)
                    .put("appPackage", sorted.metadata.appPackage)
                    .put("generatedAtEpochMillis", sorted.metadata.generatedAtEpochMillis)
                    .put("wardrobeId", sorted.metadata.wardrobeId)
                    .put("revision", sorted.metadata.revision),
            )
            .put(
                "taxonomies",
                JSONObject()
                    .put("categories", sorted.taxonomies.categories.toJsonArray(::tagCategoryToJson))
                    .put("tags", sorted.taxonomies.tags.toJsonArray(::tagToJson))
                    .put("mainColors", sorted.taxonomies.mainColors.toJsonArray(::mainColorToJson)),
            )
            .put("garments", sorted.garments.toJsonArray(::garmentToJson))
            .put("garmentTags", sorted.garmentTags.toJsonArray(::garmentTagToJson))
            .put("garmentColors", sorted.garmentColors.toJsonArray(::garmentColorToJson))
            .put("photos", sorted.photos.toJsonArray(::photoToJson))
            .put("tombstones", sorted.tombstones.toJsonArray(::tombstoneToJson))
            .toString()
    }

    fun decode(raw: String): WardrobeSyncSnapshot {
        val json = JSONObject(raw)
        val metadata = json.optJSONObject("metadata")
        val taxonomies = json.optJSONObject("taxonomies")
        return WardrobeSyncSnapshot(
            metadata = com.gusanitolabs.robia.core.model.WardrobeSnapshotMetadata(
                schemaVersion = metadata?.optInt("schemaVersion") ?: com.gusanitolabs.robia.core.model.WARDROBE_SYNC_SCHEMA_VERSION,
                appPackage = metadata?.optString("appPackage")?.takeIf(String::isNotBlank) ?: "com.gusanitolabs.robia",
                generatedAtEpochMillis = metadata?.optLong("generatedAtEpochMillis") ?: 0L,
                wardrobeId = metadata?.optString("wardrobeId")?.takeIf(String::isNotBlank),
                revision = metadata?.optLong("revision") ?: 0L,
            ),
            taxonomies = com.gusanitolabs.robia.core.model.WardrobeTaxonomySnapshot(
                categories = taxonomies.optArray("categories", ::jsonToTagCategory),
                tags = taxonomies.optArray("tags", ::jsonToTag),
                mainColors = taxonomies.optArray("mainColors", ::jsonToMainColor),
            ),
            garments = json.optArray("garments", ::jsonToGarment),
            garmentTags = json.optArray("garmentTags", ::jsonToGarmentTag),
            garmentColors = json.optArray("garmentColors", ::jsonToGarmentColor),
            photos = json.optArray("photos", ::jsonToPhoto),
            tombstones = json.optArray("tombstones", ::jsonToTombstone),
        ).sortedDeterministically()
    }

    private fun tagCategoryToJson(record: TagCategorySyncRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("name", record.name)
        .put("sortOrder", record.sortOrder)
        .put("isSystem", record.isSystem)
        .put("isArchived", record.isArchived)
        .put("revision", record.revision)
        .put("updatedAtEpochMillis", record.updatedAtEpochMillis)

    private fun tagToJson(record: TagSyncRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("categoryId", record.categoryId)
        .put("name", record.name)
        .put("sortOrder", record.sortOrder)
        .put("isSystem", record.isSystem)
        .put("isArchived", record.isArchived)
        .put("revision", record.revision)
        .put("updatedAtEpochMillis", record.updatedAtEpochMillis)

    private fun mainColorToJson(record: MainColorSyncRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("name", record.name)
        .put("hex", record.hex)
        .put("sortOrder", record.sortOrder)
        .put("isDefault", record.isDefault)
        .put("isArchived", record.isArchived)
        .put("revision", record.revision)
        .put("updatedAtEpochMillis", record.updatedAtEpochMillis)

    private fun garmentToJson(record: GarmentSyncRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("name", record.name)
        .put("notes", record.notes)
        .put("fitValue", record.fitValue)
        .put("isFavorite", record.isFavorite)
        .put("isArchived", record.isArchived)
        .put("createdAtEpochMillis", record.createdAtEpochMillis)
        .put("updatedAtEpochMillis", record.updatedAtEpochMillis)
        .put("revision", record.revision)

    private fun garmentTagToJson(record: GarmentTagMappingRecord): JSONObject = JSONObject()
        .put("garmentId", record.garmentId)
        .put("tagId", record.tagId)
        .put("revision", record.revision)
        .put("updatedAtEpochMillis", record.updatedAtEpochMillis)

    private fun garmentColorToJson(record: GarmentColorMappingRecord): JSONObject = JSONObject()
        .put("garmentId", record.garmentId)
        .put("role", record.role.name)
        .put("rawValue", record.rawValue)
        .put("displayLabel", record.displayLabel?.name)
        .put("paletteColorId", record.paletteColorId)
        .put("paletteColorName", record.paletteColorName)
        .put("paletteColorHex", record.paletteColorHex)
        .put("revision", record.revision)
        .put("updatedAtEpochMillis", record.updatedAtEpochMillis)

    private fun photoToJson(record: GarmentPhotoRecord): JSONObject = JSONObject()
        .put("garmentId", record.garmentId)
        .put("localUri", record.localUri)
        .put("blobPath", record.blobPath)
        .put("mimeType", record.mimeType)
        .put("contentHash", record.contentHash)
        .put("byteSize", record.byteSize)
        .put("revision", record.revision)
        .put("updatedAtEpochMillis", record.updatedAtEpochMillis)

    private fun tombstoneToJson(record: SyncTombstoneRecord): JSONObject = JSONObject()
        .put("entityType", record.entityType)
        .put("entityId", record.entityId)
        .put("deletedAtEpochMillis", record.deletedAtEpochMillis)
        .put("revision", record.revision)

    private fun jsonToTagCategory(json: JSONObject): TagCategorySyncRecord = TagCategorySyncRecord(
        id = json.getString("id"),
        name = json.getString("name"),
        sortOrder = json.optInt("sortOrder"),
        isSystem = json.optBoolean("isSystem"),
        isArchived = json.optBoolean("isArchived"),
        revision = json.optLong("revision"),
        updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
    )

    private fun jsonToTag(json: JSONObject): TagSyncRecord = TagSyncRecord(
        id = json.getString("id"),
        categoryId = json.getString("categoryId"),
        name = json.getString("name"),
        sortOrder = json.optInt("sortOrder"),
        isSystem = json.optBoolean("isSystem"),
        isArchived = json.optBoolean("isArchived"),
        revision = json.optLong("revision"),
        updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
    )

    private fun jsonToMainColor(json: JSONObject): MainColorSyncRecord = MainColorSyncRecord(
        id = json.getString("id"),
        name = json.getString("name"),
        hex = json.getString("hex"),
        sortOrder = json.optInt("sortOrder"),
        isDefault = json.optBoolean("isDefault"),
        isArchived = json.optBoolean("isArchived"),
        revision = json.optLong("revision"),
        updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
    )

    private fun jsonToGarment(json: JSONObject): GarmentSyncRecord = GarmentSyncRecord(
        id = json.getString("id"),
        name = json.getString("name"),
        notes = json.optString("notes"),
        fitValue = json.optNullableInt("fitValue"),
        isFavorite = json.optBoolean("isFavorite"),
        isArchived = json.optBoolean("isArchived"),
        createdAtEpochMillis = json.optLong("createdAtEpochMillis"),
        updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
        revision = json.optLong("revision"),
    )

    private fun jsonToGarmentTag(json: JSONObject): GarmentTagMappingRecord = GarmentTagMappingRecord(
        garmentId = json.getString("garmentId"),
        tagId = json.getString("tagId"),
        revision = json.optLong("revision"),
        updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
    )

    private fun jsonToGarmentColor(json: JSONObject): GarmentColorMappingRecord = GarmentColorMappingRecord(
        garmentId = json.getString("garmentId"),
        role = com.gusanitolabs.robia.core.model.GarmentColorRole.valueOf(json.getString("role")),
        rawValue = json.optNullableString("rawValue"),
        displayLabel = json.optNullableString("displayLabel")?.let {
            com.gusanitolabs.robia.core.model.DisplayColorLabel.valueOf(it)
        },
        paletteColorId = json.optNullableString("paletteColorId"),
        paletteColorName = json.optNullableString("paletteColorName"),
        paletteColorHex = json.optNullableString("paletteColorHex"),
        revision = json.optLong("revision"),
        updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
    )

    private fun jsonToPhoto(json: JSONObject): GarmentPhotoRecord {
        val garmentId = json.getString("garmentId")
        val localUri = json.getString("localUri")
        return GarmentPhotoRecord(
            garmentId = garmentId,
            localUri = localUri,
            blobPath = json.optNullableString("blobPath") ?: DriveFolderNaming.photoBlobPath(garmentId, localUri),
            mimeType = json.optNullableString("mimeType"),
            contentHash = json.optNullableString("contentHash"),
            byteSize = json.optNullableLong("byteSize"),
            revision = json.optLong("revision"),
            updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
        )
    }

    private fun jsonToTombstone(json: JSONObject): SyncTombstoneRecord = SyncTombstoneRecord(
        entityType = json.getString("entityType"),
        entityId = json.getString("entityId"),
        deletedAtEpochMillis = json.optLong("deletedAtEpochMillis"),
        revision = json.optLong("revision"),
    )
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result -> continuation.resume(result) }
    addOnFailureListener { throwable -> continuation.cancel(throwable) }
    addOnCanceledListener { continuation.cancel() }
}

private fun <T> List<T>.toJsonArray(mapper: (T) -> JSONObject): JSONArray =
    JSONArray().also { array -> forEach { item -> array.put(mapper(item)) } }

private fun <T> JSONObject?.optArray(name: String, mapper: (JSONObject) -> T): List<T> {
    val source = this?.optJSONArray(name) ?: return emptyList()
    return buildList {
        for (index in 0 until source.length()) {
            source.optJSONObject(index)?.let { item -> add(mapper(item)) }
        }
    }
}

private fun JSONObject.optNullableString(name: String): String? =
    if (has(name) && !isNull(name)) optString(name) else null

private fun JSONObject.optNullableInt(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

private fun JSONObject.optNullableLong(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null
