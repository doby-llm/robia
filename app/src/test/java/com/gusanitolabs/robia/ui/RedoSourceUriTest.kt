package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** SG4 Redo cutout visibility: only a photo picked in this editing session has a raw source (answer 3A). */
class RedoSourceUriTest {
    @Test
    fun aNewPhotoKeepsItsRawSource() {
        assertEquals("content://raw/1", redoSourceUri("content://raw/1", savedPhotoUri = null))
    }

    @Test
    fun aNewPhotoReplacingTheSavedOneKeepsItsRawSource() {
        assertEquals("content://raw/2", redoSourceUri("content://raw/2", savedPhotoUri = "stored.png"))
    }

    @Test
    fun aSavedGarmentWithItsStoredPhotoHidesRedo() {
        assertNull(redoSourceUri("stored.png", savedPhotoUri = "stored.png"))
    }

    @Test
    fun noRawSourceHidesRedo() {
        assertNull(redoSourceUri(null, savedPhotoUri = null))
        assertNull(redoSourceUri("", savedPhotoUri = null))
        assertNull(redoSourceUri("   ", savedPhotoUri = "stored.png"))
    }
}
