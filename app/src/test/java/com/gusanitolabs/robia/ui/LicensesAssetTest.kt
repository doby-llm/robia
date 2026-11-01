package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class LicensesAssetTest {
    @Test
    fun everyDownloadedModelUrlInTheSourceIsListedInTheLicenceAsset() {
        val asset = source("app/src/main/assets/licenses/third_party.md")
        val embedding = source("app/src/main/java/com/gusanitolabs/robia/suggestions/EmbeddingRecipe.kt")
        val cutout = source("app/src/main/java/com/gusanitolabs/robia/media/cutout/CutoutPack.kt")

        val modelUrls = Regex("""MODEL_URL = "([^"]+)"""").findAll(embedding).map { it.groupValues[1] }.toList()
        val cutoutUrls = Regex("""url = "([^"]+)"""").findAll(cutout).map { it.groupValues[1] }.toList()
        assertEquals(1, modelUrls.size)
        assertEquals(4, cutoutUrls.size)

        val missing = (modelUrls + cutoutUrls).filterNot { asset.contains(it) }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun bundledMagicTouchModelIsListedWithItsStatus() {
        val asset = source("app/src/main/assets/licenses/third_party.md")

        assertTrue(asset.contains("## magic_touch.tflite (interactive segmenter model)"))
        assertTrue(asset.contains("- Status: bundled"))
        assertTrue(asset.contains("unresolved — needs Manu"))
    }

    @Test
    fun settingsGeneralOpensTheLicenceDialogWithoutANewRoute() {
        val settings = source("app/src/main/java/com/gusanitolabs/robia/ui/SettingsScreen.kt")
        val general = settings
            .substringAfter("SettingsGroup(label = stringResource(R.string.settings_group_general))")
            .substringBefore("SettingsGroup(label = stringResource(R.string.settings_group_about))")

        assertTrue(general.contains("title = stringResource(R.string.settings_open_source_licenses)"))
        assertTrue(general.contains("onClick = { showOpenSourceLicenses = true }"))
        assertTrue(settings.contains("OpenSourceLicensesDialog(onDismiss = { showOpenSourceLicenses = false })"))
        assertTrue(!source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt").contains("OpenSourceLicenses"))
    }

    @Test
    fun licenceRowAndCloseButtonHaveTheApprovedCopyInAllThreeLocales() {
        val en = source("app/src/main/res/values/strings.xml")
        val es = source("app/src/main/res/values-es/strings.xml")
        val de = source("app/src/main/res/values-de/strings.xml")

        assertTrue(en.contains("<string name=\"settings_open_source_licenses\">Open-source licences</string>"))
        assertTrue(es.contains("<string name=\"settings_open_source_licenses\">Licencias de código abierto</string>"))
        assertTrue(de.contains("<string name=\"settings_open_source_licenses\">Open-Source-Lizenzen</string>"))
        assertTrue(en.contains("<string name=\"open_source_licenses_close\">Close</string>"))
        assertTrue(es.contains("<string name=\"open_source_licenses_close\">Cerrar</string>"))
        assertTrue(de.contains("<string name=\"open_source_licenses_close\">Schließen</string>"))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
