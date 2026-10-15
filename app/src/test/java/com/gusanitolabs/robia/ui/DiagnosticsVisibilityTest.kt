package com.gusanitolabs.robia.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsVisibilityTest {
    @Test
    fun diagnostics_areVisibleOnlyWhenDeveloperModeIsUnlockedAndEnabled() {
        assertTrue(showSuggestionDiagnostics(devUnlocked = true, devEnabled = true))
        assertFalse(showSuggestionDiagnostics(devUnlocked = true, devEnabled = false))
        assertFalse(showSuggestionDiagnostics(devUnlocked = false, devEnabled = true))
        assertFalse(showSuggestionDiagnostics(devUnlocked = false, devEnabled = false))
    }
}
