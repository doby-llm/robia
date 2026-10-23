package com.gusanitolabs.robia.core.model

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
)

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
) {
    /** The Developer switch's view of [cutoutEngine]: on when the method is Automatic. */
    val preciseCutoutEnabled: Boolean
        get() = cutoutEngine == CutoutEngineChoice.Automatic
}
