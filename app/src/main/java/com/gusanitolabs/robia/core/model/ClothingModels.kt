package com.gusanitolabs.robia.core.model

/** Immutable wardrobe item used by UI and sync seams. */
data class ClothingItem(
    val id: String,
    val name: String,
    val notes: String = "",
    val photoUri: String? = null,
    val tags: List<GarmentTag> = emptyList(),
    val colorMetrics: ClothingColorMetrics = ClothingColorMetrics(),
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

data class ClothingColorMetrics(
    val primaryRawValue: String? = null,
    val primaryDisplayLabel: DisplayColorLabel? = null,
    val secondaryRawValue: String? = null,
    val secondaryDisplayLabel: DisplayColorLabel? = null,
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

data class RobiaSettings(
    val languagePreference: LanguagePreference = LanguagePreference.System,
)
