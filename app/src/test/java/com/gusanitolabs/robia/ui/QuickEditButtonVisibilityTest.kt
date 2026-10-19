package com.gusanitolabs.robia.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEditButtonVisibilityTest {
    @Test
    fun editPhotoIsNotComposedWithoutAUsablePhoto() {
        assertFalse(quickEditButtonVisible(hasUsablePhoto = false))
    }

    @Test
    fun editPhotoIsComposedOnceAUsablePhotoExists() {
        assertTrue(quickEditButtonVisible(hasUsablePhoto = true))
    }
}
