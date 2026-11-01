package com.gusanitolabs.robia.ui

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ExternalLinkIntentTest {
    @Test
    fun externalLinkRequestIsAViewOfTheUrlWithNewTaskAlwaysSet() {
        val request = externalLinkRequest(PRIVACY_POLICY_URL)

        assertEquals(Intent.ACTION_VIEW, request.action)
        assertEquals(PRIVACY_POLICY_URL, request.url)
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, request.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    @Test
    fun everyExternalLinkInSettingsAndRobiaAppIsBuiltWithTheHelper() {
        val settings = source("app/src/main/java/com/gusanitolabs/robia/ui/SettingsScreen.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")

        // Only the helper may name ACTION_VIEW; any other start in these files would have to set the flag itself.
        assertEquals(1, Regex("Intent\\.ACTION_VIEW").findAll(settings).count())
        assertTrue(settings.contains("action = Intent.ACTION_VIEW"))
        assertTrue(!app.contains("Intent.ACTION_VIEW"))
        assertTrue(!settings.contains("startActivity(Intent(Intent.ACTION_VIEW"))
        assertTrue(!app.contains("startActivity(Intent(Intent.ACTION_VIEW"))
        assertTrue(app.contains("startActivity(externalLinkIntent(SYNC_STORAGE_URL))"))
        assertTrue(settings.contains("startActivity(externalLinkIntent(PRIVACY_POLICY_URL))"))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
