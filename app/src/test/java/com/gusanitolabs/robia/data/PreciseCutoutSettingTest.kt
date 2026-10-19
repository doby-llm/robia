package com.gusanitolabs.robia.data

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-SG2-6: the precise cutout setting is on when nothing is stored; a stored choice is kept. */
class PreciseCutoutSettingTest {
    private val preciseCutoutEnabledKey = booleanPreferencesKey("precise_cutout_enabled")

    @Test
    fun absentKey_meansOn() {
        assertTrue(mutablePreferencesOf().toRobiaSettings().preciseCutoutEnabled)
    }

    @Test
    fun storedFalse_isRead() {
        val preferences = mutablePreferencesOf(preciseCutoutEnabledKey to false)

        assertFalse(preferences.toRobiaSettings().preciseCutoutEnabled)
    }

    @Test
    fun storedTrue_isRead() {
        val preferences = mutablePreferencesOf(preciseCutoutEnabledKey to true)

        assertTrue(preferences.toRobiaSettings().preciseCutoutEnabled)
    }
}
