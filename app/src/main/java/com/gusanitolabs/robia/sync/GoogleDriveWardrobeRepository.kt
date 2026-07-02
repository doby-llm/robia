package com.gusanitolabs.robia.sync

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
import kotlinx.coroutines.suspendCancellableCoroutine
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
            is DriveApiResult.Success -> DriveSyncResult.Success(snapshotResult.value.sortedDeterministically())
            is DriveApiResult.NotFound -> DriveSyncResult.Success(WardrobeSyncSnapshot())
            is DriveApiResult.Unauthorized -> authBlocked()
            is DriveApiResult.Failure -> DriveSyncResult.Failure(snapshotResult.throwable)
        }
    }

    override suspend fun upsertSnapshot(snapshot: WardrobeSyncSnapshot): DriveSyncResult<DriveManifest> =
        withAccessToken { accessToken ->
            val deterministicSnapshot = snapshot.sortedDeterministically()
            when (val result = api.upsertSnapshot(accessToken, deterministicSnapshot)) {
                is DriveApiResult.Success -> DriveSyncResult.Success(DriveManifest.fromSnapshot(result.value))
                is DriveApiResult.Unauthorized -> authBlocked()
                is DriveApiResult.NotFound -> DriveSyncResult.Failure(
                    IllegalStateException("Drive snapshot upload completed without a readable file."),
                )
                is DriveApiResult.Failure -> DriveSyncResult.Failure(result.throwable)
            }
        }

    private suspend fun <T> withAccessToken(
        operation: suspend (accessToken: String) -> DriveSyncResult<T>,
    ): DriveSyncResult<T> {
        val authorizationResult = runCatching {
            authorizationClient.authorize(
                AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(driveScope))
                    .build(),
            ).await()
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

    private fun <T> authBlocked(): DriveSyncResult<T> = DriveSyncResult.Blocked(
        reason = DriveSyncDisabledReason.UserNotConnected,
        message = "Google Drive authorization is required before sync can continue.",
    )
}

interface DriveSnapshotApi {
    suspend fun fetchSnapshot(accessToken: String): DriveApiResult<WardrobeSyncSnapshot>
    suspend fun upsertSnapshot(accessToken: String, snapshot: WardrobeSyncSnapshot): DriveApiResult<WardrobeSyncSnapshot>
}

sealed interface DriveApiResult<out T> {
    data class Success<T>(val value: T) : DriveApiResult<T>
    data object NotFound : DriveApiResult<Nothing>
    data object Unauthorized : DriveApiResult<Nothing>
    data class Failure(val throwable: Throwable) : DriveApiResult<Nothing>
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

    private fun findSnapshotFileId(accessToken: String): String? {
        val query = "name='$SNAPSHOT_FILE_NAME' and trashed=false"
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder&fields=files(id,name,modifiedTime)&pageSize=1&q=" +
            URLEncoder.encode(query, "UTF-8")
        val result = request(
            method = "GET",
            url = url,
            accessToken = accessToken,
        ) { body ->
            val files = JSONObject(body).optJSONArray("files") ?: JSONArray()
            DriveApiResult.Success(files.optJSONObject(0)?.optString("id")?.takeIf(String::isNotBlank))
        }
        return (result as? DriveApiResult.Success)?.value
    }

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

    private fun <T> multipartRequest(
        method: String,
        url: String,
        accessToken: String,
        metadata: String,
        body: String,
        parse: (body: String) -> DriveApiResult<T>,
    ): DriveApiResult<T> = try {
        val boundary = "robia_drive_snapshot_${System.currentTimeMillis()}"
        val payload = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: $SNAPSHOT_MIME_TYPE; charset=UTF-8\r\n\r\n")
            append(body)
            append("\r\n--$boundary--\r\n")
        }.toByteArray(Charsets.UTF_8)

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

    private companion object {
        const val SNAPSHOT_FILE_NAME = "wardrobe_snapshot.json"
        const val SNAPSHOT_MIME_TYPE = "application/vnd.gusanitolabs.robia.wardrobe-snapshot+json"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 30_000
    }
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

    private fun jsonToPhoto(json: JSONObject): GarmentPhotoRecord = GarmentPhotoRecord(
        garmentId = json.getString("garmentId"),
        localUri = json.getString("localUri"),
        blobPath = json.optString("blobPath"),
        mimeType = json.optNullableString("mimeType"),
        contentHash = json.optNullableString("contentHash"),
        byteSize = json.optNullableLong("byteSize"),
        revision = json.optLong("revision"),
        updatedAtEpochMillis = json.optLong("updatedAtEpochMillis"),
    )

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
