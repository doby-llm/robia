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
    fun syncOutbox_distinguishesRunnableWorkFromAttentionAndRecoversStaleRunningWork() {
        val syncModels = source("app/src/main/java/com/gusanitolabs/robia/core/model/SyncModels.kt")
        val entities = source("app/src/main/java/com/gusanitolabs/robia/data/local/Entities.kt")
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val database = source("app/src/main/java/com/gusanitolabs/robia/data/local/RobiaDatabase.kt")
        val snapshotRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/LocalWardrobeSyncSnapshotRepository.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")

        assertTrue(syncModels.contains("Running,"))
        assertTrue(syncModels.contains("NeedsUserAction,"))
        assertTrue(entities.contains("retry_attempt_count"))
        assertTrue(entities.contains("retry_after_epoch_millis"))
        assertTrue(entities.contains("sync_started_at_epoch_millis"))
        assertTrue(database.contains("MIGRATION_10_11"))
        assertTrue(database.contains("SET sync_status = 'Running'"))
        assertTrue(dao.contains("retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now"))
        assertTrue(dao.contains("sync_status = 'NeedsUserAction'"))
        assertTrue(dao.contains("recoverStaleRunningSyncWork"))
        assertTrue(processor.contains("recoverStaleRunningSyncWork"))
        assertTrue(processor.contains("MAX_RETRY_ATTEMPTS"))
        assertTrue(processor.contains("CloudRestoreStatus.CompletedWithAttention"))
        assertTrue(snapshotRepository.contains("GarmentSyncStatus.NeedsUserAction"))

        val visibleActivity = app
            .substringAfter("private val WardrobeSyncState.hasVisibleSyncActivity: Boolean")
            .substringBefore("@Composable\nprivate fun LocalizedRobiaContent")
        assertTrue(visibleActivity.contains("connectionStatus == DriveSyncConnectionStatus.Syncing"))
        assertTrue(!visibleActivity.contains("pendingOperationCount > 0"))
    }

    @Test
    fun guardedDrivePhotos_areRecoverablePerGarmentWithoutRestartingRestore() {
        val gateway = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncGateway.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")
        val retryHandler = processor
            .substringAfter("private suspend fun retryRestoredPhoto(garmentId: String)")
            .substringBefore("private suspend fun restoreFreshInstallOnceIfNeeded()")
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val driveRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/GoogleDriveWardrobeRepository.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val strings = source("app/src/main/res/values/strings.xml")

        assertTrue(gateway.contains("data class RetryRestoredPhoto"))
        assertTrue(retryHandler.contains("claimGarmentPhotoRestoreRetry(garmentId)"))
        assertTrue(retryHandler.contains("driveRepository.retryRestoredPhoto(garmentId)"))
        assertTrue(!retryHandler.contains("processPendingGarments("))
        assertTrue(!retryHandler.contains("restoreProgress.value"))
        assertTrue(dao.contains("retry_attempt_count < 3"))
        assertTrue(dao.contains("retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now"))
        assertTrue(processor.contains("CloudRestoreStatus.CompletedWithAttention"))
        assertTrue(driveRepository.contains("listBlobPathsWithPrefix"))
        assertTrue(driveRepository.contains("filter { path -> path.startsWith(photoBlobPrefix) }"))
        assertTrue(driveRepository.contains("legacyPhotoRestoreCandidate"))
        assertTrue(driveRepository.contains("exact_blob_not_found"))
        assertTrue(app.contains("onRetryRestoredPhoto"))
        assertTrue(app.contains("R.string.cloud_restore_retry_photo"))
        assertTrue(app.contains("R.string.content_cloud_restore_retry_photo"))
        assertTrue(strings.contains("name=\"cloud_restore_retry_photo\""))
        assertTrue(strings.contains("name=\"content_cloud_restore_retry_photo\""))
    }

    @Test
    fun guardedPhotoRetry_staysOutOfNormalUploadAndCannotOverwriteNewerEdits() {
        val entities = source("app/src/main/java/com/gusanitolabs/robia/data/local/Entities.kt")
        val database = source("app/src/main/java/com/gusanitolabs/robia/data/local/RobiaDatabase.kt")
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val snapshotRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/LocalWardrobeSyncSnapshotRepository.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")

        assertTrue(entities.contains("photo_restore_guarded"))
        assertTrue(entities.contains("photo_restore_retry_deadline_epoch_millis"))
        assertTrue(database.contains("MIGRATION_11_12"))
        assertTrue(dao.contains("sync_status = 'NeedsUserAction'"))
        assertTrue(dao.contains("photo_restore_retry_deadline_epoch_millis >= :now"))
        assertTrue(dao.contains("sync_revision = :revision"))
        assertTrue(snapshotRepository.contains("hasGuardedPhotoRestoreIssues"))
        assertTrue(processor.contains("!hasGuardedPhotoRestoreIssues.value"))
        assertTrue(processor.contains("SyncCycleResult.Attention"))
    }

    @Test
    fun restoredPhotoRetry_compilesGenericSuccessAndUsesComposableAccessibilityText() {
        val driveRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/DriveWardrobeRepository.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val retryButton = app
            .substringAfter("if (item.hasMissingRestoredPhoto)")
            .substringBefore("item.notes.takeIf")
        val itemDetailScreen = app
            .substringAfter("private fun ItemDetailScreen(")
            .substringBefore("@Composable\nprivate fun DetailMediaCard(")

        assertTrue(driveRepository.contains("?.let { photo -> DriveSyncResult.Success(photo) }"))
        assertTrue(!driveRepository.contains("DriveSyncResult::Success"))
        assertTrue(app.contains("val retryPhotoContentDescription = stringResource(R.string.content_cloud_restore_retry_photo)"))
        assertTrue(
            itemDetailScreen.indexOf("val retryPhotoContentDescription") < itemDetailScreen.indexOf("LazyColumn("),
        )
        assertTrue(retryButton.contains("contentDescription = retryPhotoContentDescription"))
        assertTrue(retryButton.contains("Text(stringResource(R.string.cloud_restore_retry_photo))"))
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