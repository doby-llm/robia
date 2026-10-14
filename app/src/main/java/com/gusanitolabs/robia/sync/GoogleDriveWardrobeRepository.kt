package com.gusanitolabs.robia.sync

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationClient
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.gusanitolabs.robia.core.model.DefaultTags
import com.gusanitolabs.robia.core.model.DriveSyncDisabledReason
import com.gusanitolabs.robia.core.model.DriveSyncTarget
import com.gusanitolabs.robia.core.model.EmbeddingSyncRecord
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
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
            is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> DriveSyncResult.Failure(snapshotResult.failureCause())
        }
    }

    override suspend fun fetchSnapshot(progress: DriveRestoreProgressListener?): DriveSyncResult<WardrobeSyncSnapshot> = withAccessToken { accessToken ->
        when (val snapshotResult = api.fetchSnapshot(accessToken)) {
            is DriveApiResult.Success -> hydratePhotoBlobs(accessToken, snapshotResult.value.sortedDeterministically(), progress)
            is DriveApiResult.NotFound -> DriveSyncResult.Success(WardrobeSyncSnapshot())
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> DriveSyncResult.Failure(snapshotResult.failureCause())
        }
    }

    override suspend fun fetchSnapshotRecords(): DriveSyncResult<WardrobeSyncSnapshot> = withAccessToken { accessToken ->
        when (val snapshotResult = api.fetchSnapshot(accessToken)) {
            is DriveApiResult.Success -> DriveSyncResult.Success(snapshotResult.value.sortedDeterministically())
            is DriveApiResult.NotFound -> DriveSyncResult.Success(WardrobeSyncSnapshot())
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> DriveSyncResult.Failure(snapshotResult.failureCause())
        }
    }

    override suspend fun retryRestoredPhoto(
        garmentId: String,
        progress: DriveRestoreProgressListener?,
    ): DriveSyncResult<GarmentPhotoRecord> = withAccessToken { accessToken ->
        when (val snapshotResult = api.fetchSnapshot(accessToken)) {
            is DriveApiResult.Success -> {
                val snapshot = snapshotResult.value.sortedDeterministically()
                val photo = snapshot.photos.singleOrNull { it.garmentId == garmentId }
                    ?: return@withAccessToken DriveSyncResult.Failure(
                        IllegalStateException("No remote photo exists for garment $garmentId."),
                    )
                when (val hydrated = hydratePhotoBlobs(accessToken, snapshot.copy(photos = listOf(photo)), progress)) {
                    is DriveSyncResult.Success -> DriveSyncResult.Success(hydrated.value.photos.single())
                    is DriveSyncResult.Blocked -> hydrated
                    is DriveSyncResult.Failure -> hydrated
                }
            }
            is DriveApiResult.NotFound -> DriveSyncResult.Failure(
                IllegalStateException("No remote snapshot exists for garment $garmentId."),
            )
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> DriveSyncResult.Failure(snapshotResult.failureCause())
        }
    }

    private val lastUploadedPhotos by lazy {
        LastUploadedPhotosStore(File(context.filesDir, "sync/last_uploaded_photos.json"))
    }

    override suspend fun upsertSnapshot(
        snapshot: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot?,
        onPhotoProgress: ((completedPhotos: Int, totalPhotos: Int) -> Unit)?,
    ): DriveSyncResult<DriveManifest> = withAccessToken { accessToken ->
        // Previous photo records: the local cache first, the remote snapshot covers a fresh install.
        val previous = remote?.photos.orEmpty().associate { photo -> photo.garmentId to photo.toLastUploadedPhoto() } +
            lastUploadedPhotos.read()
        val photoProgress: (completedPhotos: Int, totalPhotos: Int) -> Unit = onPhotoProgress ?: { _, _ -> }
        when (val result = photoUploadPipeline().upload(accessToken, snapshot.sortedDeterministically(), remote, previous, photoProgress)) {
            is DriveSyncResult.Success -> {
                // The cache only saves re-uploads in the next cycle, so a failed write must not fail this sync.
                runCatching {
                    lastUploadedPhotos.write(result.value.uploadedPhotos.map(GarmentPhotoRecord::toLastUploadedPhoto))
                }
                DriveSyncResult.Success(result.value.manifest)
            }
            is DriveSyncResult.Blocked -> result
            is DriveSyncResult.Failure -> result
        }
    }

    private fun photoUploadPipeline(): PhotoUploadPipeline = PhotoUploadPipeline(
        api = api,
        sourceHashOf = { photo -> photo.localSourceUri().sourceHash16(context) },
        encode = { photo -> ClothingImageStore.encodeDriveWebp(context, photo.localSourceUri()) },
        onPurgeDeferred = { message -> Log.w(PHOTO_LOG_TAG, message) },
    )

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
                        is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> return@withAccessToken DriveSyncResult.Failure(deleted.failureCause())
                    }
                }
                // Relist after idempotent DELETEs; a backup is deleted only once inventory is empty.
                when (val relist = api.listBackupFiles(accessToken)) {
                    is DriveApiResult.Success -> DriveSyncResult.Success(
                        DriveBackupDeletionResult(listed.value.size, relist.value.size),
                    )
                    is DriveApiResult.NotFound -> DriveSyncResult.Success(DriveBackupDeletionResult(listed.value.size))
                    is DriveApiResult.Unauthorized -> authBlocked()
                    is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> DriveSyncResult.Failure(relist.failureCause())
                }
            }
            is DriveApiResult.NotFound -> DriveSyncResult.Success(DriveBackupDeletionResult())
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> DriveSyncResult.Failure(listed.failureCause())
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

        return operation(accessToken)
    }

    private companion object {
        const val AUTHORIZATION_TIMEOUT_MILLIS = 30_000L
    }

    private suspend fun hydratePhotoBlobs(
        accessToken: String,
        snapshot: WardrobeSyncSnapshot,
        progress: DriveRestoreProgressListener?,
    ): DriveSyncResult<WardrobeSyncSnapshot> {
        val garmentNameById = snapshot.garments.associate { it.id to it.name }
        return when (
            val restored = restorePhotoBlobs(
                api = api,
                accessToken = accessToken,
                photos = snapshot.photos,
                garmentNameById = garmentNameById,
                workDir = File(context.cacheDir, RESTORE_WORK_DIRECTORY),
                decodeBounds = ::imageBoundsOf,
                moveToFinal = { garmentId, hash16, source ->
                    ClothingImageStore.writeRestoredImageFile(context, garmentId, hash16, source).toString()
                },
                progress = progress,
            )
        ) {
            is DriveApiResult.Success -> DriveSyncResult.Success(snapshot.copy(photos = restored.value).sortedDeterministically())
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.NotFound -> DriveSyncResult.Failure(IllegalStateException("Drive photo restore returned no photo list."))
            is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> DriveSyncResult.Failure(restored.failureCause())
        }
    }
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

// Transient keeps the Failure behaviour; StorageFull is kept distinct so the processor can park items as drive_full.
private fun DriveApiResult<*>.failureCause(): Throwable = when (this) {
    is DriveApiResult.Failure -> throwable
    is DriveApiResult.Transient -> cause ?: IOException("Drive request failed temporarily.")
    DriveApiResult.StorageFull -> DriveStorageFullException()
    else -> IllegalStateException("Unexpected Drive result: $this")
}

private fun <T> authBlocked(): DriveSyncResult<T> = DriveSyncResult.Blocked(
        reason = DriveSyncDisabledReason.UserNotConnected,
        message = "Google Drive authorization is required before sync can continue.",
    )

/** What the local photo of one garment looks like to the upload planner. */
internal sealed interface LocalPhotoState {
    val garmentId: String

    /** Local bytes are readable; [sourceHash] is the first 16 hex digits of their SHA-256. */
    data class Readable(override val garmentId: String, val sourceHash: String) : LocalPhotoState

    /** Local bytes are missing (guarded restore): the remote record is kept and its blobs are never purged. */
    data class Guarded(override val garmentId: String) : LocalPhotoState

    /** Local bytes exist but cannot be read: a per-photo failure, not a cycle failure. */
    data class Unreadable(override val garmentId: String) : LocalPhotoState
}

internal sealed interface PhotoUploadAction {
    val garmentId: String

    /** Local bytes unchanged and the previous blob is still in Drive: reuse it without encode or upload. */
    data class Reuse(override val garmentId: String, val previous: LastUploadedPhoto) : PhotoUploadAction

    /** Encode to WebP and upload under the key derived from the WebP bytes. */
    data class Upload(override val garmentId: String, val sourceHash: String) : PhotoUploadAction

    /** Keep the remote record unchanged and protect its blobs. */
    data class CopyRemote(override val garmentId: String) : PhotoUploadAction

    /** Permanent per-photo failure: keep the previous record and protect its blobs. */
    data class Fail(override val garmentId: String) : PhotoUploadAction
}

/**
 * Per-photo decision of TS§4.2 steps 2–3. Pure: no Drive or Android calls.
 * A legacy key is never reused, so every legacy blob is re-uploaded under a short key.
 */
internal fun planPhotoUploads(
    local: List<LocalPhotoState>,
    previous: Map<String, LastUploadedPhoto>,
    index: DriveFileIndex,
): List<PhotoUploadAction> = local.map { state ->
    val known = previous[state.garmentId]
    when (state) {
        is LocalPhotoState.Guarded -> PhotoUploadAction.CopyRemote(state.garmentId)
        is LocalPhotoState.Unreadable -> PhotoUploadAction.Fail(state.garmentId)
        is LocalPhotoState.Readable ->
            if (known != null && known.sourceHash == state.sourceHash && !PhotoBlobKeys.isLegacyKey(known.blobPath) &&
                index.idOf(known.blobPath) != null
            ) {
                PhotoUploadAction.Reuse(state.garmentId, known)
            } else {
                PhotoUploadAction.Upload(state.garmentId, state.sourceHash)
            }
    }
}

/** Result of one snapshot upload: the manifest (with per-photo failures) and the photo records it carries. */
internal data class PhotoUploadOutcome(
    val manifest: DriveManifest,
    val uploadedPhotos: List<GarmentPhotoRecord>,
)

/**
 * Upload order of TS§4.2 and §4.5: changed photos → snapshot JSON → purge of replaced blobs.
 * Android work (source hash, WebP encode) is injected so the decision and purge logic run on the JVM.
 */
internal class PhotoUploadPipeline(
    private val api: DriveSnapshotApi,
    private val sourceHashOf: (GarmentPhotoRecord) -> String,
    private val encode: (GarmentPhotoRecord) -> ImageBlob,
    private val onPurgeDeferred: (String) -> Unit = {},
) {
    suspend fun upload(
        accessToken: String,
        snapshot: WardrobeSyncSnapshot,
        remote: WardrobeSyncSnapshot?,
        previous: Map<String, LastUploadedPhoto>,
        onPhotoProgress: (completedPhotos: Int, totalPhotos: Int) -> Unit = { _, _ -> },
    ): DriveSyncResult<PhotoUploadOutcome> {
        val index = when (val listing = api.listAll(accessToken)) {
            is DriveApiResult.Success -> DriveFileIndex(listing.value)
            is DriveApiResult.NotFound -> DriveFileIndex(emptyList())
            is DriveApiResult.Unauthorized -> return authBlocked()
            is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull ->
                return DriveSyncResult.Failure(listing.failureCause())
        }
        val remoteByGarmentId = remote?.photos.orEmpty().associateBy(GarmentPhotoRecord::garmentId)
        val states: List<LocalPhotoState> = snapshot.photos.map { photo ->
            when {
                photo.isGuardedLocalPhoto() -> LocalPhotoState.Guarded(photo.garmentId)
                else -> runCatching { sourceHashOf(photo) }.fold(
                    onSuccess = { hash -> LocalPhotoState.Readable(photo.garmentId, hash) },
                    onFailure = { LocalPhotoState.Unreadable(photo.garmentId) },
                )
            }
        }
        val photosByGarmentId = snapshot.photos.associateBy(GarmentPhotoRecord::garmentId)
        val uploaded = mutableListOf<GarmentPhotoRecord>()
        val failures = mutableListOf<PhotoUploadFailure>()
        val protectedGarmentIds = mutableSetOf<String>()

        fun keepPreviousRecord(photo: GarmentPhotoRecord) {
            protectedGarmentIds += photo.garmentId
            failures += PhotoUploadFailure(photo.garmentId)
            val kept = remoteByGarmentId[photo.garmentId]
                ?: previous[photo.garmentId]?.let { known -> photo.withPreviousBlob(known) }
            kept?.let { record -> uploaded += record }
        }

        val actions = planPhotoUploads(states, previous, index)
        onPhotoProgress(0, actions.size)
        for ((actionIndex, action) in actions.withIndex()) {
            val photo = photosByGarmentId.getValue(action.garmentId)
            when (action) {
                is PhotoUploadAction.Reuse -> uploaded += photo.withPreviousBlob(action.previous)
                is PhotoUploadAction.CopyRemote -> {
                    protectedGarmentIds += action.garmentId
                    val kept = remoteByGarmentId[action.garmentId]
                        ?: previous[action.garmentId]?.let { known -> photo.withPreviousBlob(known) }
                    kept?.let { record -> uploaded += record }
                }
                is PhotoUploadAction.Fail -> keepPreviousRecord(photo)
                is PhotoUploadAction.Upload -> when (val upload = uploadChanged(accessToken, photo, action.sourceHash, index)) {
                    is DriveApiResult.Success -> uploaded += upload.value
                    is DriveApiResult.Failure, is DriveApiResult.NotFound -> keepPreviousRecord(photo)
                    is DriveApiResult.Unauthorized -> return authBlocked()
                    is DriveApiResult.Transient, DriveApiResult.StorageFull -> return DriveSyncResult.Failure(upload.failureCause())
                }
            }
            onPhotoProgress(actionIndex + 1, actions.size)
        }

        val uploadSnapshot = snapshot.copy(photos = uploaded).sortedDeterministically()
        return when (val result = api.upsertSnapshot(accessToken, uploadSnapshot)) {
            is DriveApiResult.Success -> {
                purgeReplacedPhotoBlobs(accessToken, uploadSnapshot, protectedGarmentIds, index)
                DriveSyncResult.Success(
                    PhotoUploadOutcome(
                        manifest = DriveManifest.fromSnapshot(result.value).copy(photoUploadFailures = failures),
                        uploadedPhotos = uploaded,
                    ),
                )
            }
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.NotFound -> DriveSyncResult.Failure(
                IllegalStateException("Drive snapshot upload completed without a readable file."),
            )
            is DriveApiResult.Failure, is DriveApiResult.Transient, DriveApiResult.StorageFull -> DriveSyncResult.Failure(result.failureCause())
        }
    }

    /** Encodes and uploads one changed photo. A [DriveApiResult.Failure] is per-photo; Transient and StorageFull stop the cycle. */
    private suspend fun uploadChanged(
        accessToken: String,
        photo: GarmentPhotoRecord,
        sourceHash: String,
        index: DriveFileIndex,
    ): DriveApiResult<GarmentPhotoRecord> {
        val blob = try {
            encode(photo).requireDriveReadable(photo.garmentId)
        } catch (error: Exception) {
            return DriveApiResult.Failure(error)
        }
        val blobPath = PhotoBlobKeys.driveKey(photo.garmentId, PhotoBlobKeys.hash16(blob.bytes))
        return when (val upload = api.upsertBlob(accessToken, blobPath, blob.bytes, blob.mimeType, index.idOf(blobPath))) {
            is DriveApiResult.Success -> DriveApiResult.Success(
                photo.copy(
                    blobPath = blobPath,
                    mimeType = blob.mimeType,
                    contentHash = blob.contentHash,
                    byteSize = blob.byteSize,
                    byteMagic = blob.byteMagic,
                    decodedWidth = blob.decodedWidth,
                    decodedHeight = blob.decodedHeight,
                    sourceHash = sourceHash,
                    restoredLocalUri = null,
                    restoreFailureCategory = null,
                    restoreFailureMessage = null,
                ),
            )
            is DriveApiResult.NotFound -> DriveApiResult.Failure(
                IllegalStateException("Drive photo blob upload completed without a readable file."),
            )
            is DriveApiResult.Unauthorized -> DriveApiResult.Unauthorized
            is DriveApiResult.Failure -> DriveApiResult.Failure(upload.throwable)
            is DriveApiResult.Transient -> DriveApiResult.Transient(upload.cause)
            DriveApiResult.StorageFull -> DriveApiResult.StorageFull
        }
    }

    /**
     * Deletes blobs that the uploaded snapshot no longer references, for swept garments only.
     * Guarded and failed garments are protected. Best effort: a failed delete is retried next cycle.
     */
    private suspend fun purgeReplacedPhotoBlobs(
        accessToken: String,
        snapshot: WardrobeSyncSnapshot,
        protectedGarmentIds: Set<String>,
        index: DriveFileIndex,
    ) {
        val activeBlobPaths = snapshot.photos.mapNotNull { photo -> photo.blobPath.takeIf(String::isNotBlank) }.toSet()
        val sweptGarmentIds = (
            snapshot.photos.map(GarmentPhotoRecord::garmentId) +
                snapshot.tombstones.filter { tombstone -> tombstone.entityType in PHOTO_TOMBSTONE_ENTITY_TYPES }
                    .map(SyncTombstoneRecord::entityId)
            ).distinct()
        val candidates = (
            snapshot.deletedPhotoBlobPurgeCandidates() +
                sweptGarmentIds.flatMap { garmentId -> index.namesWithPrefix(DriveFolderNaming.photoBlobPrefix(garmentId)) }
            ).distinct()
            .filterNot(activeBlobPaths::contains)
            .filterNot { blobPath ->
                protectedGarmentIds.any { garmentId -> blobPath.startsWith(DriveFolderNaming.photoBlobPrefix(garmentId)) }
            }
        candidates.forEach { blobPath ->
            val fileId = index.idOf(blobPath) ?: return@forEach
            when (api.deleteBlob(accessToken, blobPath, fileId)) {
                is DriveApiResult.Success, DriveApiResult.NotFound -> Unit
                else -> onPurgeDeferred("An old photo blob could not be deleted; the next sync retries it.")
            }
        }
    }
}

internal fun GarmentPhotoRecord.isGuardedLocalPhoto(): Boolean =
    restoreFailureCategory != null && restoredLocalUri.isNullOrBlank()

private fun GarmentPhotoRecord.localSourceUri(): Uri = Uri.parse(restoredLocalUri?.takeIf(String::isNotBlank) ?: localUri)

private fun GarmentPhotoRecord.withPreviousBlob(previous: LastUploadedPhoto): GarmentPhotoRecord = copy(
    blobPath = previous.blobPath,
    mimeType = previous.mimeType,
    contentHash = previous.contentHash,
    byteSize = previous.byteSize,
    sourceHash = previous.sourceHash,
    restoredLocalUri = null,
    restoreFailureCategory = null,
    restoreFailureMessage = null,
)

/** First 16 hex digits of the SHA-256 of the bytes behind this URI, streamed without decoding the photo. */
private fun Uri.sourceHash16(context: Context): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    val input = context.contentResolver.openInputStream(this) ?: throw IOException("Unable to open photo for hashing.")
    input.use { stream ->
        val buffer = ByteArray(DRIVE_DOWNLOAD_BUFFER_BYTES)
        while (true) {
            val read = stream.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }.take(16)
}

private const val PHOTO_LOG_TAG = "DriveWardrobeRepository"

interface DriveSnapshotApi {
    suspend fun fetchSnapshot(accessToken: String): DriveApiResult<WardrobeSyncSnapshot>
    suspend fun fetchBlob(
        accessToken: String,
        blobPath: String,
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        fileId: String? = null,
    ): DriveApiResult<DriveBlob>
    suspend fun fetchBlobToFile(accessToken: String, fileId: String, target: File): DriveApiResult<Long>
    suspend fun listAll(accessToken: String): DriveApiResult<List<DriveFileMetadata>>
    suspend fun listBlobPathsWithPrefix(accessToken: String, blobPathPrefix: String): DriveApiResult<List<String>>
    suspend fun upsertBlob(
        accessToken: String,
        blobPath: String,
        bytes: ByteArray,
        mimeType: String?,
        fileId: String? = null,
    ): DriveApiResult<Unit>
    suspend fun deleteBlob(accessToken: String, blobPath: String, fileId: String? = null): DriveApiResult<Unit>
    suspend fun listBackupFiles(accessToken: String): DriveApiResult<List<DriveFileMetadata>>
    suspend fun deleteFile(accessToken: String, fileId: String): DriveApiResult<Unit>
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
    val name: String = "",
)

sealed interface DriveApiResult<out T> {
    data class Success<T>(val value: T) : DriveApiResult<T>
    data object NotFound : DriveApiResult<Nothing>
    data object Unauthorized : DriveApiResult<Nothing>
    data class Failure(val throwable: Throwable) : DriveApiResult<Nothing>
    data class Transient(val cause: Throwable?) : DriveApiResult<Nothing>
    data object StorageFull : DriveApiResult<Nothing>
}

// A full 1,000-item page is treated as overflow, so deletion never traverses unbounded inventory.
private const val MAX_BACKUP_DELETE_FILES = 1_000
private const val DRIVE_PROGRESS_CHUNK_BYTES = 32 * 1024
private const val DRIVE_PROGRESS_THROTTLE_MILLIS = 250L
private const val DRIVE_DOWNLOAD_BUFFER_BYTES = 64 * 1024

private data class DriveFilesPage(
    val files: List<DriveFileMetadata>,
    val nextPageToken: String?,
)

private fun InputStream.readProgressBytes(
    contentLength: Long?,
    onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit,
): ByteArray {
    val output = ByteArrayOutputStream(
        contentLength?.takeIf { it > 0L && it <= Int.MAX_VALUE.toLong() }?.toInt()
            ?: DRIVE_PROGRESS_CHUNK_BYTES,
    )
    val buffer = ByteArray(DRIVE_PROGRESS_CHUNK_BYTES)
    var total = 0L
    var lastEmitAt = 0L
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        output.write(buffer, 0, read)
        total += read
        val now = System.currentTimeMillis()
        if (now - lastEmitAt >= DRIVE_PROGRESS_THROTTLE_MILLIS) {
            onProgress(total, contentLength)
            lastEmitAt = now
        }
    }
    onProgress(total, contentLength)
    return output.toByteArray()
}

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

    override suspend fun fetchBlob(
        accessToken: String,
        blobPath: String,
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit,
        fileId: String?,
    ): DriveApiResult<DriveBlob> {
        val file = fileId?.let { id -> DriveFileMetadata(id = id, name = blobPath) }
            ?: findFileByName(accessToken, blobPath)
            ?: return DriveApiResult.NotFound
        return byteRequest(
            method = "GET",
            url = "https://www.googleapis.com/drive/v3/files/${file.id}?alt=media",
            accessToken = accessToken,
            onProgress = onProgress,
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

    override suspend fun fetchBlobToFile(accessToken: String, fileId: String, target: File): DriveApiResult<Long> {
        val result = withRetry { streamBlobToFile(accessToken, fileId, target) }
        if (result !is DriveApiResult.Success) target.delete()
        return result
    }

    override suspend fun listAll(accessToken: String): DriveApiResult<List<DriveFileMetadata>> {
        val files = mutableListOf<DriveFileMetadata>()
        var pageToken: String? = null
        do {
            val pageUrl = "https://www.googleapis.com/drive/v3/files" +
                "?spaces=appDataFolder&fields=nextPageToken,files(id,name,modifiedTime,size)&pageSize=1000" +
                pageToken?.let { token -> "&pageToken=" + URLEncoder.encode(token, "UTF-8") }.orEmpty()
            val page = request(method = "GET", url = pageUrl, accessToken = accessToken) { body ->
                DriveApiResult.Success(parseFilesPage(body))
            }
            if (page !is DriveApiResult.Success) return page.failedAs()
            files += page.value.files
            pageToken = page.value.nextPageToken
        } while (pageToken != null)
        return DriveApiResult.Success(files)
    }

    override suspend fun upsertBlob(
        accessToken: String,
        blobPath: String,
        bytes: ByteArray,
        mimeType: String?,
        fileId: String?,
    ): DriveApiResult<Unit> {
        val existingFileId = fileId ?: findFileIdByName(accessToken, blobPath)
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

    override suspend fun listBlobPathsWithPrefix(accessToken: String, blobPathPrefix: String): DriveApiResult<List<String>> {
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

    override suspend fun deleteBlob(accessToken: String, blobPath: String, fileId: String?): DriveApiResult<Unit> {
        val resolvedFileId = fileId ?: findFileIdByName(accessToken, blobPath) ?: return DriveApiResult.NotFound
        return request(
            method = "DELETE",
            url = "https://www.googleapis.com/drive/v3/files/$resolvedFileId",
            accessToken = accessToken,
        ) { DriveApiResult.Success(Unit) }
    }

    override suspend fun listBackupFiles(accessToken: String): DriveApiResult<List<DriveFileMetadata>> = request(
        method = "GET",
        url = "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name,modifiedTime,mimeType,size)&pageSize=$MAX_BACKUP_DELETE_FILES",
        accessToken = accessToken,
    ) { body ->
        val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
        DriveApiResult.Success((0 until files.length()).mapNotNull { index ->
            files.optJSONObject(index)?.optString("id")?.takeIf(String::isNotBlank)?.let { id -> DriveFileMetadata(id = id) }
        })
    }

    override suspend fun deleteFile(accessToken: String, fileId: String): DriveApiResult<Unit> = request(
        method = "DELETE",
        url = "https://www.googleapis.com/drive/v3/files/$fileId",
        accessToken = accessToken,
    ) { DriveApiResult.Success(Unit) }

    private suspend fun findSnapshotFileId(accessToken: String): String? = findFileIdByName(accessToken, SNAPSHOT_FILE_NAME)

    private suspend fun findFileIdByName(accessToken: String, name: String): String? = findFileByName(accessToken, name)?.id

    private suspend fun findFileByName(accessToken: String, name: String): DriveFileMetadata? {
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
                        name = file.optString("name"),
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

    private suspend fun <T> request(
        method: String,
        url: String,
        accessToken: String,
        parse: (body: String) -> DriveApiResult<T>,
    ): DriveApiResult<T> = withRetry {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
            }
            connection.toDriveResult(parse)
        } catch (throwable: Throwable) {
            throwable.toFailedDriveResult()
        }
    }

    private suspend fun <T> byteRequest(
        method: String,
        url: String,
        accessToken: String,
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit = { _, _ -> },
        parse: (bytes: ByteArray, connection: HttpURLConnection) -> DriveApiResult<T>,
    ): DriveApiResult<T> = withRetry {
        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                setRequestProperty("Authorization", "Bearer $accessToken")
                connectTimeout = CONNECT_TIMEOUT_MILLIS
                readTimeout = READ_TIMEOUT_MILLIS
            }
            connection.toDriveByteResult(onProgress, parse)
        } catch (throwable: Throwable) {
            throwable.toFailedDriveResult()
        }
    }

    /** One streamed GET into [target]; the caller deletes [target] on any non-success result. */
    private suspend fun streamBlobToFile(accessToken: String, fileId: String, target: File): DriveApiResult<Long> = try {
        val connection = (URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
        }
        try {
            if (connection.responseCode in 200..299) {
                val copiedBytes = connection.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output, DRIVE_DOWNLOAD_BUFFER_BYTES) }
                }
                DriveApiResult.Success(copiedBytes)
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
                driveHttpError(connection.responseCode, errorBody)
            }
        } finally {
            connection.disconnect()
        }
    } catch (throwable: Throwable) {
        throwable.toFailedDriveResult()
    }

    private suspend fun <T> multipartRequest(
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

    private suspend fun <T> multipartRequest(
        method: String,
        url: String,
        accessToken: String,
        metadata: String,
        body: ByteArray,
        bodyMimeType: String,
        parse: (body: String) -> DriveApiResult<T>,
    ): DriveApiResult<T> = withRetry {
        try {
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
            throwable.toFailedDriveResult()
        }
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
                else -> driveHttpError(responseCode, body)
            }
        } finally {
            disconnect()
        }

    private fun <T> HttpURLConnection.toDriveByteResult(
        onProgress: (bytesCopied: Long, totalBytes: Long?) -> Unit,
        parse: (bytes: ByteArray, connection: HttpURLConnection) -> DriveApiResult<T>,
    ): DriveApiResult<T> =
        try {
            val bytes = if (responseCode in 200..299) {
                inputStream.use { input ->
                    input.readProgressBytes(contentLengthLong.takeIf { it >= 0L }, onProgress)
                }
            } else {
                errorStream?.use { input -> input.readProgressBytes(null, onProgress) } ?: ByteArray(0)
            }
            when (responseCode) {
                in 200..299 -> parse(bytes, this)
                else -> driveHttpError(responseCode, bytes.toString(Charsets.UTF_8))
            }
        } finally {
            disconnect()
        }

    private fun driveHttpError(code: Int, body: String): DriveApiResult<Nothing> {
        val failure = IOException("Drive API returned HTTP $code: $body")
        return when (classify(code, body)) {
            DriveErrorClass.AuthRequired -> DriveApiResult.Unauthorized
            DriveErrorClass.NotFound -> DriveApiResult.NotFound
            DriveErrorClass.StorageFull -> DriveApiResult.StorageFull
            DriveErrorClass.Transient -> DriveApiResult.Transient(failure)
            is DriveErrorClass.Permanent -> DriveApiResult.Failure(failure)
        }
    }

    private fun Throwable.toFailedDriveResult(): DriveApiResult<Nothing> = when (classify(this)) {
        DriveErrorClass.Transient -> DriveApiResult.Transient(this)
        else -> DriveApiResult.Failure(this)
    }

    private fun <R> DriveApiResult<*>.failedAs(): DriveApiResult<R> = when (this) {
        is DriveApiResult.Failure -> this
        is DriveApiResult.Transient -> this
        DriveApiResult.StorageFull -> DriveApiResult.StorageFull
        DriveApiResult.NotFound -> DriveApiResult.NotFound
        DriveApiResult.Unauthorized -> DriveApiResult.Unauthorized
        is DriveApiResult.Success -> error("A successful Drive result is not a failure.")
    }

    private fun parseFilesPage(body: String): DriveFilesPage {
        val json = JSONObject(body)
        val files = json.optJSONArray("files") ?: JSONArray()
        return DriveFilesPage(
            files = (0 until files.length()).mapNotNull { index ->
                files.optJSONObject(index)?.let { file ->
                    file.optString("id").takeIf(String::isNotBlank)?.let { id ->
                        DriveFileMetadata(
                            id = id,
                            name = file.optString("name"),
                            modifiedTime = file.optString("modifiedTime").takeIf(String::isNotBlank),
                            size = file.optLong("size").takeIf { it > 0L },
                        )
                    }
                }
            },
            nextPageToken = json.optString("nextPageToken").takeIf(String::isNotBlank),
        )
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

internal const val REMOTE_PHOTO_TRANSIENT = "remote_photo_transient"
internal const val RESTORE_PHOTO_CONCURRENCY = 4
internal const val RESTORE_PHOTO_TIMEOUT_MILLIS = 60_000L
private const val RESTORE_WORK_DIRECTORY = "restore"
private const val MAGIC_HEADER_BYTES = 12

/** Ends the restore early when Drive refuses the account or storage; cancels the sibling downloads. */
private class RestorePhotoAbort(val result: DriveApiResult<Nothing>) : RuntimeException("Drive restore aborted.")

/**
 * Restores photos after one Drive listing, downloading at most [RESTORE_PHOTO_CONCURRENCY] at a time.
 * A failing photo becomes a guarded record; only authorization or storage errors end the whole restore.
 */
internal suspend fun restorePhotoBlobs(
    api: DriveSnapshotApi,
    accessToken: String,
    photos: List<GarmentPhotoRecord>,
    garmentNameById: Map<String, String> = emptyMap(),
    workDir: File,
    decodeBounds: (File) -> Pair<Int, Int>?,
    moveToFinal: (garmentId: String, hash16: String, source: File) -> String,
    progress: DriveRestoreProgressListener? = null,
): DriveApiResult<List<GarmentPhotoRecord>> {
    val index = when (val listed = api.listAll(accessToken)) {
        is DriveApiResult.Success -> DriveFileIndex(listed.value)
        DriveApiResult.NotFound -> DriveFileIndex(emptyList())
        DriveApiResult.Unauthorized -> return DriveApiResult.Unauthorized
        DriveApiResult.StorageFull -> return DriveApiResult.StorageFull
        is DriveApiResult.Transient -> return listed
        is DriveApiResult.Failure -> return listed
    }
    workDir.mkdirs()
    val totalBytes = photos.mapNotNull { photo -> photo.byteSize }.takeIf { sizes -> sizes.size == photos.size }?.sum()
    val completedItems = AtomicInteger(0)
    val completedBytes = AtomicLong(0L)
    // The progress tracker is not thread-safe, so concurrent photos report one at a time.
    val progressLock = Any()
    val semaphore = Semaphore(RESTORE_PHOTO_CONCURRENCY)
    return try {
        val restored = coroutineScope {
            photos.map { photo ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val record = restoreOnePhoto(
                            api = api,
                            accessToken = accessToken,
                            photo = photo,
                            index = index,
                            workDir = workDir,
                            garmentNameById = garmentNameById,
                            decodeBounds = decodeBounds,
                            moveToFinal = moveToFinal,
                            completedBytes = completedBytes,
                        )
                        val done = completedItems.incrementAndGet()
                        synchronized(progressLock) {
                            progress?.onProgress(
                                DriveRestoreProgress(
                                    garmentId = photo.garmentId,
                                    safeCategory = record.restoreFailureCategory ?: "photo_restore_downloaded",
                                    completedItems = done,
                                    totalItems = photos.size,
                                    completedBytes = completedBytes.get(),
                                    totalBytes = totalBytes,
                                ),
                            )
                        }
                        record
                    }
                }
            }.awaitAll()
        }
        DriveApiResult.Success(restored)
    } catch (abort: RestorePhotoAbort) {
        abort.result
    }
}

private suspend fun restoreOnePhoto(
    api: DriveSnapshotApi,
    accessToken: String,
    photo: GarmentPhotoRecord,
    index: DriveFileIndex,
    workDir: File,
    garmentNameById: Map<String, String>,
    decodeBounds: (File) -> Pair<Int, Int>?,
    moveToFinal: (garmentId: String, hash16: String, source: File) -> String,
    completedBytes: AtomicLong,
): GarmentPhotoRecord {
    val blobPath = photo.blobPath.takeIf(String::isNotBlank) ?: return photo.guardedRestore(
        category = REMOTE_PHOTO_MISSING_BLOB_PATH,
        message = "Garment photo ${photo.garmentId} is missing a Drive blob path.",
        restoreDiagnosticEvents = listOf(
            photo.importDiagnosticEvent(persistedPhotoUriPresent = false, placeholderReason = REMOTE_PHOTO_MISSING_BLOB_PATH),
        ),
    )
    val fetchStartedEvents = photo.restoreFetchStartedEvents(description = garmentNameById[photo.garmentId])
    fun missingPhoto(): GarmentPhotoRecord = photo.guardedRestore(
        category = REMOTE_PHOTO_MISSING,
        message = "Drive photo blob $blobPath was not found for garment ${photo.garmentId}.",
        restoreDiagnosticEvents = fetchStartedEvents + listOf(
            photo.driveLookupMissingEvent(),
            photo.fetchMissingEvent(),
            photo.importDiagnosticEvent(persistedPhotoUriPresent = false, placeholderReason = REMOTE_PHOTO_MISSING),
        ),
    )
    val fileId = index.resolveFileId(blobPath, photo.garmentId) ?: return missingPhoto()
    val target = File(workDir, "${photo.garmentId.safeFileStem()}.part")
    try {
        val fetched = withTimeoutOrNull(RESTORE_PHOTO_TIMEOUT_MILLIS) {
            api.fetchBlobToFile(accessToken, fileId, target)
        } ?: return photo.guardedRestore(
            category = REMOTE_PHOTO_TRANSIENT,
            message = "Drive photo blob $blobPath for garment ${photo.garmentId} timed out.",
            restoreDiagnosticEvents = fetchStartedEvents + listOf(
                photo.importDiagnosticEvent(persistedPhotoUriPresent = false, placeholderReason = REMOTE_PHOTO_TRANSIENT),
            ),
        )
        val size = when (fetched) {
            is DriveApiResult.Success -> fetched.value
            DriveApiResult.Unauthorized -> throw RestorePhotoAbort(DriveApiResult.Unauthorized)
            DriveApiResult.StorageFull -> throw RestorePhotoAbort(DriveApiResult.StorageFull)
            DriveApiResult.NotFound -> return missingPhoto()
            is DriveApiResult.Transient -> return photo.guardedRestore(
                category = REMOTE_PHOTO_TRANSIENT,
                message = "Drive photo blob $blobPath for garment ${photo.garmentId} failed after retries.",
                cause = fetched.cause,
                restoreDiagnosticEvents = fetchStartedEvents + listOf(
                    photo.importDiagnosticEvent(persistedPhotoUriPresent = false, placeholderReason = REMOTE_PHOTO_TRANSIENT),
                ),
            )
            is DriveApiResult.Failure -> return photo.guardedRestore(
                category = REMOTE_PHOTO_UNREADABLE,
                message = "Drive photo blob $blobPath for garment ${photo.garmentId} could not be downloaded.",
                cause = fetched.throwable,
                restoreDiagnosticEvents = fetchStartedEvents + listOf(
                    photo.importDiagnosticEvent(persistedPhotoUriPresent = false, placeholderReason = REMOTE_PHOTO_UNREADABLE),
                ),
            )
        }
        completedBytes.addAndGet(size)
        val header = target.headerBytes()
        val contentHash = target.streamedSha256Hex()
        val fetchEvents = fetchStartedEvents + photo.fetchedFileEvent(
            byteCount = size,
            sha256Prefix = contentHash.take(12),
            magic = header.imageMagicLabel(),
        )
        val dimensions = decodeBounds(target)
        if (dimensions == null) {
            return photo.guardedRestore(
                category = REMOTE_PHOTO_UNREADABLE,
                message = "Remote Drive photo blob $blobPath for garment ${photo.garmentId} fetched but could not be decoded " +
                    "mime=${photo.mimeType ?: "unknown"} magic=${header.imageMagicLabel()} byteSize=$size detail=bounds_unavailable.",
                byteSize = size,
                contentHash = contentHash,
                byteMagic = header.magicHex(),
                restoreDiagnosticEvents = fetchEvents +
                    photo.decodeDiagnosticEvent(decoderPath = "BitmapFactory.bounds", failure = "bounds_unavailable") +
                    photo.importDiagnosticEvent(persistedPhotoUriPresent = false, placeholderReason = REMOTE_PHOTO_UNREADABLE),
            )
        }
        val uri = runCatching { moveToFinal(photo.garmentId, contentHash.take(16), target) }.getOrElse { throwable ->
            return photo.guardedRestore(
                category = REMOTE_PHOTO_UNREADABLE,
                message = "write_failed:${throwable.diagnosticSummary()}",
                cause = throwable,
                byteSize = size,
                contentHash = contentHash,
                byteMagic = header.magicHex(),
                restoreDiagnosticEvents = fetchEvents + photo.importDiagnosticEvent(
                    persistedPhotoUriPresent = false,
                    placeholderReason = REMOTE_PHOTO_UNREADABLE,
                ),
            )
        }
        return photo.copy(
            restoredLocalUri = uri,
            byteSize = size,
            contentHash = contentHash,
            byteMagic = header.magicHex(),
            decodedWidth = dimensions.first,
            decodedHeight = dimensions.second,
            restoreFailureCategory = null,
            restoreFailureMessage = null,
            restoreDiagnosticEvents = fetchEvents +
                photo.localWriteDiagnosticEvent(
                    targetExtension = photo.mimeType.toPhotoExtension() ?: blobPath.substringAfterLast('.', "jpg"),
                    fileLength = size,
                    readBackBytes = null,
                ) +
                photo.decodeDiagnosticEvent(decoderPath = "BitmapFactory.bounds", width = dimensions.first, height = dimensions.second) +
                photo.importDiagnosticEvent(persistedPhotoUriPresent = true, placeholderReason = null),
        )
    } finally {
        target.delete()
    }
}

// Exact paths are authoritative; a legacy path is only used when exactly one candidate exists.
// exact_blob_not_found: ambiguous candidates deliberately stay in terminal attention.
private fun DriveFileIndex.resolveFileId(blobPath: String, garmentId: String): String? =
    idOf(blobPath) ?: namesWithPrefix(DriveFolderNaming.photoBlobPrefix(garmentId))
        .singleOrNull()
        ?.let { candidate -> idOf(candidate) }

private fun String.safeFileStem(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")

private fun imageBoundsOf(file: File): Pair<Int, Int>? = runCatching {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, options)
    if (options.outWidth > 0 && options.outHeight > 0) options.outWidth to options.outHeight else null
}.getOrNull()

private fun File.headerBytes(): ByteArray = inputStream().use { input ->
    val buffer = ByteArray(MAGIC_HEADER_BYTES)
    val read = input.read(buffer)
    buffer.copyOf(maxOf(read, 0))
}

/** Streams the file through SHA-256 so a photo is never held in memory twice. */
private fun File.streamedSha256Hex(): String {
    val digest = java.security.MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(DRIVE_DOWNLOAD_BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
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

internal fun GarmentPhotoRecord.fetchedFileEvent(byteCount: Long, sha256Prefix: String, magic: String): String =
    "photo_restore_fetch_result garmentId=$garmentId status=success fetchedByteCount=$byteCount sha256Prefix=$sha256Prefix magic=$magic blobPath=$blobPath"

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
        byteSize?.let { append(" snapshotByteSize=$it") }
        contentHash?.takeIf(String::isNotBlank)?.let { append(" snapshotHash=${it.take(12)}") }
        mimeType?.takeIf(String::isNotBlank)?.let { append(" snapshotMime=$it") }
        append(" blobPath=$blobPath")
    },
)

internal fun DriveBlob.restoreFetchResultEvents(photo: GarmentPhotoRecord): List<String> = listOf(
    buildString {
        append("drive_file_lookup_result garmentId=${photo.garmentId} found=${file != null}")
        file?.let { metadata ->
            append(" fileIdHash=${metadata.id.sha256Prefix()}")
            metadata.modifiedTime?.let { append(" modifiedTime=$it") }
            metadata.mimeType?.let { append(" driveMimeType=$it") }
            metadata.size?.let { append(" driveSize=$it") }
        }
        append(" blobPath=${photo.blobPath}")
    },
    buildString {
        append("photo_restore_fetch_result garmentId=${photo.garmentId} status=success")
        httpStatusCode?.let { append(" httpStatus=$it category=${it.httpStatusCategoryLabel()}") }
        append(" fetchedByteCount=${bytes.size} bytesLength=${bytes.size}")
        contentType?.let { append(" contentType=$it") }
        contentLength?.let { append(" contentLength=$it") }
        append(" sha256Prefix=${bytes.sha256Hex().take(12)}")
        append(" magic=${bytes.imageMagicLabel()}")
        append(" png=${bytes.pngSanityLabel()}")
        append(" blobPath=${photo.blobPath}")
    },
)

internal fun GarmentPhotoRecord.driveLookupMissingEvent(): String =
    "drive_file_lookup_result garmentId=$garmentId found=false blobPath=$blobPath"

internal fun GarmentPhotoRecord.fetchMissingEvent(): String =
    "photo_restore_fetch_result garmentId=$garmentId status=not_found httpStatus=404 category=http_not_found blobPath=$blobPath"

internal fun GarmentPhotoRecord.localWriteDiagnosticEvent(
    targetExtension: String,
    fileLength: Long,
    readBackBytes: ByteArray?,
    writeFailure: Throwable? = null,
): String = buildString {
    append("photo_restore_local_write_result garmentId=$garmentId")
    append(" targetExtension=$targetExtension fileLength=$fileLength")
    append(" readbackByteCount=${readBackBytes?.size ?: 0}")
    readBackBytes?.let {
        append(" readbackHashPrefix=${it.sha256Hex().take(12)}")
        append(" readbackMagic=${it.imageMagicLabel()}")
        append(" readbackPng=${it.pngSanityLabel()}")
    }
    writeFailure?.let { append(" writeFailure=\"").append(it.diagnosticSummary()).append('"') }
    append(" blobPath=$blobPath")
}

internal fun GarmentPhotoRecord.decodeDiagnosticEvent(
    decoderPath: String,
    width: Int? = null,
    height: Int? = null,
    failure: String? = null,
): String = buildString {
    append("photo_restore_decode_result garmentId=$garmentId decoder=$decoderPath")
    if (width != null && height != null) {
        append(" status=success width=$width height=$height")
    } else {
        append(" status=failure reason=\"").append(failure ?: "unknown").append('"')
    }
    append(" blobPath=$blobPath")
}

internal fun GarmentPhotoRecord.importDiagnosticEvent(
    persistedPhotoUriPresent: Boolean,
    placeholderReason: String?,
): String = buildString {
    append("photo_restore_import_result garmentId=$garmentId")
    append(" persistedPhotoUriPresent=$persistedPhotoUriPresent")
    placeholderReason?.let { append(" placeholderReason=$it") }
    append(" blobPath=$blobPath")
}

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

private fun ByteArray.imageMagicLabel(): String = when {
    size >= 3 && this[0] == 0xff.toByte() && this[1] == 0xd8.toByte() && this[2] == 0xff.toByte() -> "jpeg_signature"
    size >= 8 &&
        this[0] == 0x89.toByte() && this[1] == 0x50.toByte() && this[2] == 0x4e.toByte() && this[3] == 0x47.toByte() &&
        this[4] == 0x0d.toByte() && this[5] == 0x0a.toByte() && this[6] == 0x1a.toByte() && this[7] == 0x0a.toByte() -> "png_signature"
    size >= 12 &&
        this[0] == 0x52.toByte() && this[1] == 0x49.toByte() && this[2] == 0x46.toByte() && this[3] == 0x46.toByte() &&
        this[8] == 0x57.toByte() && this[9] == 0x45.toByte() && this[10] == 0x42.toByte() && this[11] == 0x50.toByte() -> "webp_signature"
    size >= 6 &&
        this[0] == 0x47.toByte() && this[1] == 0x49.toByte() && this[2] == 0x46.toByte() &&
        this[3] == 0x38.toByte() && (this[4] == 0x37.toByte() || this[4] == 0x39.toByte()) && this[5] == 0x61.toByte() -> "gif_signature"
    else -> "unknown_signature"
}

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

internal object DriveSnapshotJson {
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
                    .put("revision", sorted.metadata.revision)
                    .also { json -> sorted.metadata.writerDeviceId?.let { writerDeviceId -> json.put("writerDeviceId", writerDeviceId) } },
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
                writerDeviceId = metadata?.optNullableString("writerDeviceId"),
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
        .put("suggestOccasions", record.suggestOccasions)
        .also { json -> record.description?.let { description -> json.put("description", description) } }

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
        .also { json ->
            if (record.embeddings.isNotEmpty()) json.put("embeddings", record.embeddings.toJsonArray(::embeddingToJson))
        }

    private fun embeddingToJson(record: EmbeddingSyncRecord): JSONObject = JSONObject()
        .put("kind", record.kind)
        .put("modelId", record.modelId)
        .put("recipeId", record.recipeId)
        .put("dim", record.dim)
        .put("inputHash", record.inputHash)
        .put("computedAtEpochMillis", record.computedAtEpochMillis)
        .put("b64", record.b64)

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
        .apply { record.sourceHash?.let { sourceHash -> put("sourceHash", sourceHash) } }

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

    private fun jsonToTag(json: JSONObject): TagSyncRecord {
        val id = json.getString("id")
        return TagSyncRecord(
            id = id,
            categoryId = json.getString("categoryId"),
            name = json.getString("name"),
            sortOrder = json.optInt("sortOrder"),
            isSystem = json.optBoolean("isSystem"),
            isArchived = json.optBoolean("isArchived"),
            revision = json.optLong("revision"),
            updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
            description = json.optNullableString("description"),
            // Older snapshots have no flag; only the default-off ids differ from the usual default.
            suggestOccasions = json.optBoolean("suggestOccasions", DefaultTags.defaultSuggestOccasions(id)),
        )
    }

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
        embeddings = json.optArray("embeddings", ::jsonToEmbedding),
    )

    private fun jsonToEmbedding(json: JSONObject): EmbeddingSyncRecord = EmbeddingSyncRecord(
        kind = json.optString("kind"),
        modelId = json.optString("modelId"),
        recipeId = json.optString("recipeId"),
        dim = json.optInt("dim"),
        inputHash = json.optString("inputHash"),
        computedAtEpochMillis = json.optLong("computedAtEpochMillis"),
        b64 = json.optString("b64"),
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
            sourceHash = json.optNullableString("sourceHash")?.takeIf(String::isNotBlank),
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
