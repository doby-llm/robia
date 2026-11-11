package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-DU1-5: the "Check for duplicates" row sits in the Smart features group, behind the model-Ready gate, and its strings exist in each locale. */
class DuplicateCheckRowContractTest {
    @Test
    fun rowIsHiddenUnlessTheSuggestionModelIsReady() {
        val settings = resource("app/src/main/java/com/gusanitolabs/robia/ui/SettingsScreen.kt")
        val smartFeatures = settings
            .substringAfter("SettingsGroup(label = stringResource(R.string.settings_group_smart_features))")
            .substringBefore("SettingsGroup(label = stringResource(R.string.settings_group_backup))")

        val gate = smartFeatures.indexOf("if (modelState is ModelState.Ready)")
        val row = smartFeatures.indexOf("R.string.duplicate_check_title")
        assertTrue("the row must sit behind the Ready gate", gate >= 0 && row > gate)
        assertTrue("the row must use the forced-check tap", smartFeatures.indexOf("onClick = onDuplicateCheckClick", row) > row)
    }

    @Test
    fun theRowStringsExistInEveryLocale() {
        val keys = listOf("duplicate_check_title", "duplicate_check_summary", "duplicate_check_running", "duplicate_check_none", "duplicate_check_failed")
        listOf(
            resource("app/src/main/res/values/strings.xml"),
            resource("app/src/main/res/values-es/strings.xml"),
            resource("app/src/main/res/values-de/strings.xml"),
        ).forEach { xml ->
            keys.forEach { key -> assertTrue("$key missing", xml.contains("<string name=\"$key\">")) }
            assertTrue(xml.contains("<plurals name=\"duplicate_check_not_ready\">"))
        }
    }

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
