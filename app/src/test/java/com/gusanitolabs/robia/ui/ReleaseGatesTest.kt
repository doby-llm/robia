package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class ReleaseGatesTest {
    @Test
    fun developerModeIsAllowedInEveryBuildIncludingRelease() {
        assertTrue(developerModeAllowed("release"))
        assertTrue(developerModeAllowed("debug"))
        assertTrue(developerModeAllowed("staging"))
    }

    @Test
    fun privacyPolicyUrlIsThePublicPolicyPage() {
        assertEquals("https://github.com/doby-llm/robia-privacy-policy/blob/main/README.md", PRIVACY_POLICY_URL)
    }

    @Test
    fun handleSettingsClickChecksTheReleaseGateBeforeCountingTaps() {
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val handler = app.substringAfter("fun handleSettingsClick() {").substringBefore("\n    }\n")

        val gate = handler.indexOf("if (!developerModeAllowed(BuildConfig.BUILD_TYPE)) return")
        assertTrue(gate >= 0)
        assertTrue(gate < handler.indexOf("settingsTapTimestamps.add(now)"))
        assertTrue(
            app.contains("developerModeUnlocked = settings.developerModeUnlocked && developerModeAllowed(BuildConfig.BUILD_TYPE)"),
        )
    }

    @Test
    fun settingsGeneralGroupOpensThePrivacyPolicyUrlAndIgnoresMissingBrowsers() {
        val settings = source("app/src/main/java/com/gusanitolabs/robia/ui/SettingsScreen.kt")
        val general = settings
            .substringAfter("SettingsGroup(label = stringResource(R.string.settings_group_general))")
            .substringBefore("SettingsGroup(label = stringResource(R.string.settings_group_about))")

        assertTrue(general.contains("title = stringResource(R.string.settings_privacy_policy)"))
        assertTrue(general.contains("(view.context.findActivity() ?: context).startActivity(externalLinkIntent(PRIVACY_POLICY_URL))"))
        assertTrue(general.contains("catch (_: ActivityNotFoundException)"))
    }

    @Test
    fun privacyPolicyRowHasTheApprovedCopyInAllThreeLocales() {
        val en = source("app/src/main/res/values/strings.xml")
        val es = source("app/src/main/res/values-es/strings.xml")
        val de = source("app/src/main/res/values-de/strings.xml")

        assertTrue(en.contains("<string name=\"settings_privacy_policy\">Privacy policy</string>"))
        assertTrue(es.contains("<string name=\"settings_privacy_policy\">Política de privacidad</string>"))
        assertTrue(de.contains("<string name=\"settings_privacy_policy\">Datenschutzerklärung</string>"))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
