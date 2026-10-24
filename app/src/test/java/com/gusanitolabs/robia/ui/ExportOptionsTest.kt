package com.gusanitolabs.robia.ui

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.gusanitolabs.robia.core.model.DEFAULT_EXPORT_FIELDS
import com.gusanitolabs.robia.core.model.ExportField
import com.gusanitolabs.robia.core.model.ExportFormat
import com.gusanitolabs.robia.core.model.ExportOptions
import com.gusanitolabs.robia.core.model.ExportPhoto
import com.gusanitolabs.robia.data.exportOptions
import com.gusanitolabs.robia.data.writeExportOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportOptionsTest {
    private val photoKey = stringPreferencesKey("export_photo")
    private val fieldsKey = stringSetPreferencesKey("export_fields")
    private val frameKey = booleanPreferencesKey("export_frame")

    /** V-EX1-1: defaults are Robia photo, Image and Name, Category, Colors, Occasions on. */
    @Test
    fun defaults_areRobiaImageAndTheFourFieldsOn() {
        val defaults = ExportOptions()

        assertEquals(ExportPhoto.Robia, defaults.photo)
        assertEquals(ExportFormat.Image, defaults.format)
        assertEquals(setOf(ExportField.Name, ExportField.Category, ExportField.Colors, ExportField.Occasions), defaults.fields)
        assertEquals(DEFAULT_EXPORT_FIELDS, defaults.fields)
        assertEquals(defaults, mutablePreferencesOf().exportOptions())
    }

    /** V-EX1-1: the Original photo option is disabled when the item has no original, so the export falls back to Robia. */
    @Test
    fun originalPhoto_isUsedOnlyWhenTheItemHasAnOriginal() {
        val original = ExportOptions(photo = ExportPhoto.Original)

        assertEquals(ExportPhoto.Robia, original.photoFor(hasOriginal = false))
        assertEquals(ExportPhoto.Original, original.photoFor(hasOriginal = true))
        assertEquals(ExportPhoto.Robia, ExportOptions().photoFor(hasOriginal = true))
    }

    /** V-EX1-1: choices written to DataStore come back unchanged on the next read. */
    @Test
    fun persistedChoices_roundTrip() {
        val chosen = ExportOptions(
            photo = ExportPhoto.Original,
            format = ExportFormat.Pdf,
            fields = setOf(ExportField.Fit, ExportField.Location, ExportField.Notes),
        )
        val preferences = mutablePreferencesOf().apply { writeExportOptions(chosen) }

        assertEquals(chosen, preferences.exportOptions())
    }

    @Test
    fun emptyFieldSet_isKeptAndNotReplacedByTheDefaults() {
        val preferences = mutablePreferencesOf().apply { writeExportOptions(ExportOptions(fields = emptySet())) }

        assertEquals(emptySet<ExportField>(), preferences.exportOptions().fields)
    }

    @Test
    fun unknownStoredValues_fallBackToDefaultsOrAreDropped() {
        val preferences: Preferences = mutablePreferencesOf(
            photoKey to "Gallery",
            fieldsKey to setOf("Fit", "Shoe size"),
        )

        val read = preferences.exportOptions()
        assertEquals(ExportPhoto.Robia, read.photo)
        assertEquals(ExportFormat.Image, read.format)
        assertEquals(setOf(ExportField.Fit), read.fields)
    }

    /** V-EX2-1: the frame flag defaults to on, and an absent stored key reads as on. */
    @Test
    fun frameFlag_defaultsToOnAndAbsentKeyReadsAsOn() {
        assertTrue(ExportOptions().showFrame)
        assertTrue(mutablePreferencesOf().exportOptions().showFrame)
    }

    /** V-EX2-1: the frame flag round-trips through persistence, next to the other export keys. */
    @Test
    fun frameFlag_roundTripsThroughPersistence() {
        for (frame in listOf(true, false)) {
            val chosen = ExportOptions(fields = emptySet(), showFrame = frame)
            val preferences = mutablePreferencesOf().apply { writeExportOptions(chosen) }

            assertEquals(chosen, preferences.exportOptions())
            assertEquals(frame, preferences[frameKey])
        }
    }

    /** V-EX2-1: the frame may be off only when every Show field is off; with any field on it resolves to on and disabled. */
    @Test
    fun resolveFrame_isOffOnlyWhenEveryShowFieldIsOff() {
        assertEquals(true to true, resolveFrame(ExportOptions(fields = emptySet(), showFrame = true)))
        assertEquals(false to true, resolveFrame(ExportOptions(fields = emptySet(), showFrame = false)))
        ExportField.entries.forEach { field ->
            assertEquals(true to false, resolveFrame(ExportOptions(fields = setOf(field), showFrame = false)))
            assertEquals(true to false, resolveFrame(ExportOptions(fields = setOf(field), showFrame = true)))
        }
    }
}
