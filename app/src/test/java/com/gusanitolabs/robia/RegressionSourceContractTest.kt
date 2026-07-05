package com.gusanitolabs.robia

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RegressionSourceContractTest {
    @Test
    fun browseFilterBar_keepsDividerWithLeftFilterGroupBeforeSyncSpinner() {
        val filterBar = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
            .substringAfter("private fun FilterBar(")
            .substringBefore("@Composable\n@OptIn(ExperimentalFoundationApi::class)")

        val filterChipIndex = filterBar.indexOf("FilterChip(")
        val allFiltersChipIndex = filterBar.indexOf("AssistChip(")
        val dividerIndex = filterBar.indexOf(".height(24.dp)")
        val flexibleSpacerIndex = filterBar.indexOf("Modifier.weight(1f)")
        val syncSpinnerIndex = filterBar.indexOf("CircularProgressIndicator(")

        assertIncreasing(
            filterChipIndex,
            allFiltersChipIndex,
            dividerIndex,
            flexibleSpacerIndex,
            syncSpinnerIndex,
        )
    }

    @Test
    fun restoreProgress_updatesCanReachShellWhileFreshInstallRestoreRuns() {
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")

        assertTrue(
            processor.contains("launchSyncWork { restoreFreshInstallOnceIfNeeded() }"),
        )
        assertTrue(
            processor.contains("launchSyncWork { processPendingGarments() }"),
        )
        assertTrue(processor.contains("scheduledWorkJob?.isActive == true"))
        assertTrue(
            processor.contains("restoreProgress.value = diagnostics.progress"),
        )
    }

    @Test
    fun restoreProgressOverlay_gatesDiagnosticsToDeveloperModeAndStaysCentered() {
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val overlay = app
            .substringAfter("private fun CloudRestoreProgressOverlay(")
            .substringBefore("@Composable\nprivate fun CloudRestoreDiagnosticsPanel(")

        assertTrue(overlay.contains("developerModeEnabled: Boolean"))
        assertTrue(overlay.contains("progress.diagnostics.takeIf { developerModeEnabled }"))
        assertTrue(overlay.contains("contentAlignment = Alignment.Center"))
        assertTrue(overlay.contains(".widthIn(max = 520.dp)"))
    }

    @Test
    fun cloudSetupRecommendation_waitsForDurableSettingsBeforeShowing() {
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val settingsRepository = source("app/src/main/java/com/gusanitolabs/robia/data/SettingsRepository.kt")

        assertTrue(app.contains("collectAsState(initial = null)"))
        assertTrue(app.contains("val settingsLoaded = persistedSettings != null"))
        assertTrue(app.contains("RobiaSettings(cloudSetupPromptInteracted = true)"))

        assertTrue(settingsRepository.contains("cloudSetupPromptInteracted = preferences[cloudSetupPromptInteractedKey] ?: false"))
        assertTrue(settingsRepository.contains("if (status != DriveSyncConnectionStatus.NotConfigured)"))
        assertTrue(settingsRepository.contains("preferences[cloudSetupPromptInteractedKey] = true"))
        assertTrue(settingsRepository.contains("override suspend fun markCloudSetupPromptInteracted()"))

        val promptEffect = app
            .substringAfter("LaunchedEffect(\n        settingsLoaded,")
            .substringBefore("LaunchedEffect(items)")

        assertTrue(promptEffect.contains("if (settingsLoaded &&"))
        assertTrue(promptEffect.contains("!settings.cloudSetupPromptInteracted"))
        assertTrue(promptEffect.contains("settings.driveSyncConnectionStatus == DriveSyncConnectionStatus.NotConfigured"))
        assertTrue(promptEffect.contains("cloudSetupGuard.isFirstRunRecommendation"))
        assertTrue(promptEffect.contains("onCloudSetupPromptInteracted()"))
        assertTrue(promptEffect.contains("CloudSetupDialogMode.RecommendedFirstRun"))
    }

    @Test
    fun garmentPdfExport_usesMobileReferenceLayoutAndBundledBrandAsset() {
        val exporter = source("app/src/main/java/com/gusanitolabs/robia/media/GarmentShareExporter.kt")

        listOf(
            "ROBIA_LOGO_ASSET = \"robia_logo.png\"",
            "MIN_PDF_PAGE_HEIGHT = 1280",
            "drawBitmapFitCover",
            "drawColorRow",
            "drawMetadataGrid",
            "Created with Robia",
        ).forEach { marker ->
            assertTrue("Expected garment PDF marker: $marker", exporter.contains(marker))
        }
    }

    @Test
    fun garmentPdfExport_keepsRequiredMetadataGridOrder() {
        val mapper = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
            .substringAfter("private fun UiWardrobeItem.toGarmentShareItem(): GarmentShareItem = GarmentShareItem(")
            .substringBefore("@Composable\nprivate fun shareColorName")

        val metadataBlock = mapper
            .substringAfter("metadata = listOf(")
            .substringBefore("    ),\n    colorSectionLabel")

        assertTrue("PDF metadata grid should not include location", !metadataBlock.contains("metadata_location"))
        assertIncreasing(
            metadataBlock.indexOf("metadata_category"),
            metadataBlock.indexOf("metadata_season"),
            metadataBlock.indexOf("metadata_occasions"),
            metadataBlock.indexOf("metadata_fit"),
        )
    }

    private fun assertIncreasing(vararg indices: Int) {
        assertTrue("Expected every source marker to be present", indices.all { it >= 0 })
        indices.zipWithNext().forEach { (left, right) ->
            assertTrue("Expected source markers to preserve UI order", left < right)
        }
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return Files.readString(candidates.first(Files::exists))
    }
}