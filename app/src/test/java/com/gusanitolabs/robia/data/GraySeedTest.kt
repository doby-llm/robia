package com.gusanitolabs.robia.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gray is added to installs seeded before it existed. The decision is [shouldSeedGray]; the repository only runs it and
 * inserts the row. Room does not run in JVM tests, so the database write is covered by reading `seedDefaultsIfNeeded`.
 */
class GraySeedTest {
    private val palettePresentBeforeGray = listOf("black", "white", "gray-charcoal", "brown", "navy-blue")

    @Test
    fun missingGrayIsSeeded() {
        assertTrue(shouldSeedGray(palettePresentBeforeGray, tombstonedColorIds = emptyList()))
    }

    @Test
    fun grayIsNotSeededAgainOnceItExists() {
        assertFalse(shouldSeedGray(palettePresentBeforeGray + GRAY_COLOR_ID, tombstonedColorIds = emptyList()))
    }

    @Test
    fun deletedGrayIsNotReAdded() {
        assertFalse(shouldSeedGray(palettePresentBeforeGray, tombstonedColorIds = listOf(GRAY_COLOR_ID)))
    }

    @Test
    fun otherColorTombstonesDoNotBlockGray() {
        assertTrue(shouldSeedGray(palettePresentBeforeGray, tombstonedColorIds = listOf("red", "pink")))
    }
}
