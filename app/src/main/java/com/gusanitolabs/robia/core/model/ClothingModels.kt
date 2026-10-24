package com.gusanitolabs.robia.core.model

import org.json.JSONException
import org.json.JSONObject

/** Immutable wardrobe item used by UI and sync seams. */
data class ClothingItem(
    val id: String,
    val name: String,
    val notes: String = "",
    val photoUri: String? = null,
    val tags: List<GarmentTag> = emptyList(),
    val fitValue: Int? = null,
    val colorMetrics: ClothingColorMetrics = ClothingColorMetrics(),
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val syncStatus: GarmentSyncStatus = GarmentSyncStatus.LocalOnly,
    val syncRevision: Long = updatedAtEpochMillis,
    val syncDirtyAtEpochMillis: Long? = null,
    val lastSyncedAtEpochMillis: Long? = null,
    val syncFailureMessage: String? = null,
    val photoRestoreState: PhotoRestoreState = PhotoRestoreState.None,
    /** The frame the cutout engine processed (REQ-OR-01); null for garments saved before OR1 and for unprocessed photos. */
    val originalUri: String? = null,
    /** How to reproduce [photoUri] from [originalUri] (REQ-OR-02); null whenever [originalUri] is null. */
    val originalRecipe: CutoutRecipe? = null,
)

/** Version of the [CutoutRecipe] JSON. A recipe written with a later version is read as absent. */
const val CUTOUT_RECIPE_VERSION = 1

/** Where the top-left pixel of a stored cutout sits in its original frame (REQ-OR-05), in the original's pixels. */
data class CanvasOffset(val left: Int, val top: Int)

/**
 * The processing that turned an original photo into a cutout (REQ-OR-02). [engine] is `precise` or `mlkit`. The crop is in
 * the original's pixels, with [cropRight] and [cropBottom] exclusive. [rotationDegrees] is the EXIF rotation applied when the
 * frame was decoded; the saved original carries no EXIF, so reprocessing does not apply it again. Mirrors are not recorded.
 * ML Kit records the full frame, and [hangerThreshold] is 0 because it has no hanger step.
 *
 * [storedCanvasOffset] is where the stored cutout's top-left pixel sits in the work frame (the original): the engine crop plus
 * every trim made after it. It is null for recipes saved before OR3, which then have no Keep from the original and no Cut out
 * again. [version] is unchanged by it: the field is optional in the JSON.
 */
data class CutoutRecipe(
    val engine: String,
    val removeHanger: Boolean,
    val hangerThreshold: Float,
    val edges: CutoutEdges,
    val workW: Int,
    val workH: Int,
    val rotationDegrees: Int,
    val sampleSize: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropRight: Int,
    val cropBottom: Int,
    val version: Int = CUTOUT_RECIPE_VERSION,
    val storedCanvasOffset: CanvasOffset? = null,
) {
    fun toJson(): String = JSONObject()
        .put("engine", engine)
        .put("removeHanger", removeHanger)
        .put("hangerThreshold", hangerThreshold.toDouble())
        .put("edges", edges.name)
        .put("workW", workW)
        .put("workH", workH)
        .put("rotationDegrees", rotationDegrees)
        .put("sampleSize", sampleSize)
        .put("cropLeft", cropLeft)
        .put("cropTop", cropTop)
        .put("cropRight", cropRight)
        .put("cropBottom", cropBottom)
        .put("version", version)
        .apply {
            storedCanvasOffset?.let { offset ->
                put("storedCanvasOffset", JSONObject().put("left", offset.left).put("top", offset.top))
            }
        }
        .toString()

    companion object {
        /**
         * The recipe stored as [json], or null when it is missing, unreadable or from a later [CUTOUT_RECIPE_VERSION].
         * Unknown keys are ignored.
         */
        fun fromJson(json: String?): CutoutRecipe? {
            if (json.isNullOrBlank()) return null
            return try {
                val obj = JSONObject(json)
                val version = obj.getInt("version")
                if (version > CUTOUT_RECIPE_VERSION) return null
                CutoutRecipe(
                    engine = obj.getString("engine"),
                    removeHanger = obj.getBoolean("removeHanger"),
                    hangerThreshold = obj.getDouble("hangerThreshold").toFloat(),
                    edges = CutoutEdges.valueOf(obj.getString("edges")),
                    workW = obj.getInt("workW"),
                    workH = obj.getInt("workH"),
                    rotationDegrees = obj.getInt("rotationDegrees"),
                    sampleSize = obj.getInt("sampleSize"),
                    cropLeft = obj.getInt("cropLeft"),
                    cropTop = obj.getInt("cropTop"),
                    cropRight = obj.getInt("cropRight"),
                    cropBottom = obj.getInt("cropBottom"),
                    version = version,
                    storedCanvasOffset = obj.optJSONObject("storedCanvasOffset")?.let { offset ->
                        CanvasOffset(left = offset.getInt("left"), top = offset.getInt("top"))
                    },
                )
            } catch (failure: JSONException) {
                null
            } catch (failure: IllegalArgumentException) {
                null
            }
        }
    }
}

data class PhotoRestoreState(
    val guarded: Boolean = false,
    val retryAttemptCount: Int = 0,
    val retryAfterEpochMillis: Long? = null,
    val retryDeadlineEpochMillis: Long? = null,
) {
    val needsAttention: Boolean
        get() = guarded

    val hasRetryAttemptsRemaining: Boolean
        get() = retryAttemptCount < MAX_RETRY_ATTEMPTS

    val retryExhausted: Boolean
        get() = guarded && !hasRetryAttemptsRemaining

    companion object {
        const val MAX_RETRY_ATTEMPTS = 3
        val None = PhotoRestoreState()
    }
}

data class ClothingColorMetrics(
    val primaryRawValue: String? = null,
    val primaryDisplayLabel: DisplayColorLabel? = null,
    val primaryPaletteColorId: String? = null,
    val primaryPaletteColorName: String? = null,
    val primaryPaletteColorHex: String? = null,
    val secondaryRawValue: String? = null,
    val secondaryDisplayLabel: DisplayColorLabel? = null,
    val secondaryPaletteColorId: String? = null,
    val secondaryPaletteColorName: String? = null,
    val secondaryPaletteColorHex: String? = null,
)

enum class DisplayColorLabel {
    Black,
    Blue,
    Brown,
    Gray,
    Green,
    Orange,
    Pink,
    Purple,
    Red,
    White,
    Yellow,
    Multicolor,
    Unknown,
}

data class TagCategory(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val isSystem: Boolean = false,
)

data class GarmentTag(
    val id: String,
    val categoryId: String,
    val name: String,
    val sortOrder: Int,
    val isSystem: Boolean = false,
    val description: String? = null,
    val suggestOccasions: Boolean = true,
)

data class MainColor(
    val id: String,
    val name: String,
    val hex: String,
    val sortOrder: Int,
    val isDefault: Boolean = false,
)

enum class LanguagePreference(val storageValue: String?) {
    System(null),
    English("en"),
    Spanish("es"),
    German("de"),
}

enum class BrowseLayout { PhotosOnly, PhotosWithDetails }

/** Cutout method (SG4): Automatic uses the precise cutout when its pack is ready, Standard always uses ML Kit. */
enum class CutoutEngineChoice { Automatic, Standard }

enum class HangerSensitivity { Normal, High }

enum class CutoutEdges { Soft, Sharp }

enum class BatchCutoutMode { Balanced, MemorySaver }

data class RobiaSettings(
    val languagePreference: LanguagePreference = LanguagePreference.System,
    val developerModeUnlocked: Boolean = false,
    val developerModeEnabled: Boolean = false,
    val driveSyncConnectionStatus: DriveSyncConnectionStatus = DriveSyncConnectionStatus.NotConfigured,
    val cloudSetupPromptInteracted: Boolean = false,
    val driveFreshInstallRestoreAttempted: Boolean = false,
    val driveBackupDeletionState: DriveBackupDeletionState = DriveBackupDeletionState.None,
    val browseLayout: BrowseLayout = BrowseLayout.PhotosOnly,
    /** Pinch-chosen Browse column count; clamped to [browseColumnRange] when shown. */
    val browseColumns: Int = 2,
    /** Cutout method (Settings > Cutout); Standard is the default, Automatic (precise, Experimental) is opt-in. */
    val cutoutEngine: CutoutEngineChoice = CutoutEngineChoice.Standard,
    /** Hanger removal in the precise cutout; ignored by Standard. */
    val cutoutRemoveHanger: Boolean = true,
    val cutoutHangerSensitivity: HangerSensitivity = HangerSensitivity.Normal,
    val cutoutEdges: CutoutEdges = CutoutEdges.Soft,
    val batchCutoutMode: BatchCutoutMode = BatchCutoutMode.Balanced,
    /** Google account the Drive backup belongs to; set at the first authorization, changed only by "Use this account". */
    val driveBoundAccountEmail: String? = null,
    /** Google account of the latest authorization; a value other than [driveBoundAccountEmail] blocks every Drive write. */
    val driveAuthorizedAccountEmail: String? = null,
    /** Choices of the Export screen (EX1); reused for the next export. */
    val exportOptions: ExportOptions = ExportOptions(),
) {
    /** The Developer switch's view of [cutoutEngine]: on when the method is Automatic. */
    val preciseCutoutEnabled: Boolean
        get() = cutoutEngine == CutoutEngineChoice.Automatic
}

/** Export screen choices (REQ-EX-01). [photo] is the Robia photo unless the garment has an original. */
data class ExportOptions(
    val photo: ExportPhoto = ExportPhoto.Robia,
    val format: ExportFormat = ExportFormat.Image,
    val fields: Set<ExportField> = DEFAULT_EXPORT_FIELDS,
) {
    /** The photo an export of a garment uses: the original only when it has one, else the Robia photo. */
    fun photoFor(hasOriginal: Boolean): ExportPhoto =
        if (photo == ExportPhoto.Original && hasOriginal) ExportPhoto.Original else ExportPhoto.Robia
}

enum class ExportPhoto { Robia, Original }

enum class ExportFormat { Image, Pdf }

enum class ExportField { Name, Category, Colors, Occasions, Seasons, Fit, Location, Notes }

val DEFAULT_EXPORT_FIELDS: Set<ExportField> = setOf(
    ExportField.Name,
    ExportField.Category,
    ExportField.Colors,
    ExportField.Occasions,
)
