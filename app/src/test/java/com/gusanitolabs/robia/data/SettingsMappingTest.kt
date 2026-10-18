package com.gusanitolabs.robia.data

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gusanitolabs.robia.core.model.BrowseLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsMappingTest {
    private val browseLayoutKey = stringPreferencesKey("browse_layout")

    @Test
    fun absentBrowseLayout_defaultsToPhotosOnly() {
        assertEquals(BrowseLayout.PhotosOnly, mutablePreferencesOf().toRobiaSettings().browseLayout)
    }

    @Test
    fun storedPhotosWithDetails_isRead() {
        val preferences = mutablePreferencesOf(browseLayoutKey to "PhotosWithDetails")

        assertEquals(BrowseLayout.PhotosWithDetails, preferences.toRobiaSettings().browseLayout)
    }

    @Test
    fun storedPhotosOnly_isRead() {
        val preferences = mutablePreferencesOf(browseLayoutKey to "PhotosOnly")

        assertEquals(BrowseLayout.PhotosOnly, preferences.toRobiaSettings().browseLayout)
    }

    @Test
    fun unknownBrowseLayout_fallsBackToPhotosOnly() {
        val preferences = mutablePreferencesOf(browseLayoutKey to "Garbage")

        assertEquals(BrowseLayout.PhotosOnly, preferences.toRobiaSettings().browseLayout)
    }
}
