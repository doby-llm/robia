package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class BackExitWiringContractTest {
    // B1: LocalContext is overridden with a localized ContextImpl, which is not a ContextWrapper, so
    // context.findActivity() returns null and the second Back press never closes the app.
    @Test
    fun exitBranchFinishesTheActivityFromTheViewNotTheLocalizedContext() {
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")

        assertEquals("expected exactly one Exit branch", 1, app.split("BackAction.Exit ->").size - 1)
        val exitBranch = app.substringAfter("BackAction.Exit ->").substringBefore("\n            }")

        assertFalse(
            "B1: BackAction.Exit must not call context.findActivity(); LocalContext is not a ContextWrapper, so the app never closes",
            exitBranch.contains("findActivity"),
        )
        assertTrue(
            "B1: BackAction.Exit must finish the activity resolved from LocalView in the composable scope",
            exitBranch.contains("activity?.finish()"),
        )
        assertTrue(
            "B1: the activity must be read from the view's context in the back-exit block",
            app.contains("val activity = LocalView.current.context.findActivity()"),
        )
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
