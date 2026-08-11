package com.gusanitolabs.robia

import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RegressionSourceContractTest {
    @Test
    fun batchCancellation_terminalizesTheCurrentItemForManualRetry() {
        val coordinator = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchProcessingCoordinator.kt")
        val batchScreen = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt")

        assertTrue(coordinator.contains("onInterrupted(next)"))
        assertTrue(batchScreen.contains("BatchDraftStatus.Interrupted"))
        assertTrue(batchScreen.contains("catch (throwable: CancellationException)"))
        assertTrue(batchScreen.contains("status = BatchDraftStatus.Interrupted"))
    }

    @Test
    fun batchCoordinator_processesAll60QueuedItemsSeriallyToTerminalStates() {
        val coordinator = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchProcessingCoordinator.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val batchScreen = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt")

        assertTrue(coordinator.contains("processingJob?.isActive == true"))
        assertTrue(coordinator.contains("while (true)"))
        assertTrue(coordinator.contains("firstOrNull { it.status == BatchDraftStatus.Queued }"))
        assertTrue(app.contains("BatchProcessingCoordinator(batchProcessingScope)"))
        assertTrue(batchScreen.contains("private val BatchDraftStatus.isTerminal"))
    }

    @Test
    fun browseFilterBar_keepsDividerWithLeftFilterGroupBeforeSyncSpinner() {
        val filterBar = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
            .substringAfter("private fun FilterBar(")
            .substringBefore("@Composable\n@OptIn(ExperimentalFoundationApi::class)")

        val filterChipIndex = filterBar.indexOf("FilterChip(")
        val dividerIndex = filterBar.indexOf(".height(24.dp)")
        val flexibleSpacerIndex = filterBar.indexOf("Modifier.weight(1f)")
        val syncSpinnerIndex = filterBar.indexOf("CircularProgressIndicator(")

        assertTrue("FilterBar should expose one filter entry point", !filterBar.contains("R.string.all_filters"))
        assertTrue("FilterBar should not keep the duplicate All Filters AssistChip", !filterBar.contains("AssistChip("))
        assertIncreasing(
            filterChipIndex,
            dividerIndex,
            flexibleSpacerIndex,
            syncSpinnerIndex,
        )
    }

    @Test
    fun browseWardrobe_waitsForInitialLocalDataBeforeShowingEmptyState() {
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")

        assertTrue(app.contains("observeActiveItems().collectAsState(initial = null)"))
        assertTrue(app.contains("val wardrobeItemsLoading = loadedClothingItems == null"))
        assertTrue(app.contains("wardrobeItemsLoading = wardrobeItemsLoading"))

        val browseScreen = app
            .substringAfter("private fun BrowseWardrobeScreen(")
            .substringBefore("@Composable\nprivate fun FilterBar(")

        assertTrue(browseScreen.contains("isInitialLoading: Boolean"))
        assertTrue(browseScreen.contains("if (isInitialLoading)"))
        assertTrue(browseScreen.contains("BrowseWardrobeLoadingCard()"))
        assertTrue(browseScreen.contains("} else if (items.isEmpty())"))

        val loadingCard = app
            .substringAfter("private fun BrowseWardrobeLoadingCard()")
            .substringBefore("@Composable\n@OptIn(ExperimentalFoundationApi::class)")

        assertTrue(loadingCard.contains("R.string.browse_wardrobe_loading"))
        assertTrue(loadingCard.contains("R.string.content_browse_wardrobe_loading"))
        assertTrue(loadingCard.contains("CircularProgressIndicator("))
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
    fun metadataSync_hasDurablePendingWorkAndTombstoneWinsRestore() {
        val entities = source("app/src/main/java/com/gusanitolabs/robia/data/local/Entities.kt")
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val repository = source("app/src/main/java/com/gusanitolabs/robia/data/LocalRepositories.kt")
        val snapshotRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/LocalWardrobeSyncSnapshotRepository.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")

        assertTrue(entities.contains("data class PendingMetadataSyncWorkEntity"))
        assertTrue(dao.contains("observePendingMetadataSyncCount"))
        assertTrue(dao.contains("pendingMetadataSyncWork"))
        assertTrue(dao.contains("category_id IN ('category', 'season', 'occasion', 'location')"))

        assertTrue(repository.contains("queuedForSync()"))
        assertTrue(repository.contains("syncTombstone(entityType = \"main_color\""))
        assertTrue(repository.contains("syncTombstone(entityType = \"garment_tag\""))

        assertTrue(snapshotRepository.contains("revision = syncRevision"))
        assertTrue(snapshotRepository.contains(">= category.revision"))
        assertTrue(snapshotRepository.contains(">= tag.revision"))
        assertTrue(snapshotRepository.contains(">= color.revision"))

        assertTrue(processor.contains("observePendingMetadataSyncCount()"))
        assertTrue(processor.contains("pendingMetadataSyncWork()"))
        assertTrue(processor.contains("markMetadataSynced"))
        assertTrue(processor.contains(">= color.revision"))
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
            "drawBitmapFitContain",
            "ENABLE_PDF_IMAGE_GRADIENT_OVERLAY = true",
            "drawImageGradientOverlay",
            "drawColorRow",
            "drawMetadataGrid",
            "GarmentShareMetadataIcon",
            "Created with Robia",
        ).forEach { marker ->
            assertTrue("Expected garment PDF marker: $marker", exporter.contains(marker))
        }

        assertTrue("PDF fields should wrap/fit instead of drawing ellipses", !exporter.contains("…"))
        assertTrue("PDF fields should not draw ASCII ellipses", !exporter.contains("..."))
        assertTrue("Metadata grid values should be allowed to wrap to all lines", exporter.contains("maxLines = Int.MAX_VALUE"))
    }

    @Test
    fun garmentPdfExport_containsImageAndPromotesColorHierarchy() {
        val exporter = source("app/src/main/java/com/gusanitolabs/robia/media/GarmentShareExporter.kt")
        val bitmapFit = exporter
            .substringAfter("private fun drawBitmapFitContain(")
            .substringBefore("private fun drawImageGradientOverlay")
        val colorChip = exporter
            .substringAfter("private fun drawColorChip(")
            .substringBefore("private fun drawMetadataGrid")

        assertTrue("PDF image export should contain-fit instead of cover-crop", bitmapFit.contains("val scale = min("))
        assertTrue("PDF image export should not use cover-crop scaling", !bitmapFit.contains("val scale = maxOf("))
        assertTrue("PDF image frame should share its height calculation", exporter.contains("imageFrameHeight(image)"))

        assertTrue("Color cards should use a card-height constant", exporter.contains("COLOR_CARD_HEIGHT = 104f"))
        assertTrue("Color names should visually match metadata value hierarchy", colorChip.contains("textPaint(size = 24f"))
        assertTrue("Color swatches should read as large color samples", colorChip.contains("drawCircle(swatchCenterX, swatchCenterY, 29f"))
        assertTrue("Color card page-height measurement should match draw height", exporter.contains("+ COLOR_CARD_HEIGHT + 48f"))
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