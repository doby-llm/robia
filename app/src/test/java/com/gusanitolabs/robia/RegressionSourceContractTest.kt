package com.gusanitolabs.robia

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class RegressionSourceContractTest {
    @Test
    fun batchSave_requiresExplicitReviewAcceptanceAndRevalidatesAtSaveBoundary() {
        val batch = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val strings = source("app/src/main/res/values/strings.xml")

        assertTrue(batch.contains("val explicitlyAccepted: Boolean = false"))
        assertTrue(batch.contains("val acceptedCount = drafts.count(BatchDraftItem::isAcceptedForSave)"))
        assertTrue(batch.contains("val canSave = drafts.canSaveBatch()"))
        assertTrue(batch.contains("onSaveBatch(drafts.filter(BatchDraftItem::isAcceptedForSave).map { it.id }.toSet())"))
        assertTrue(batch.contains("R.string.batch_save_items, acceptedCount"))
        assertTrue(batch.contains("R.string.batch_save_disabled_state"))
        assertTrue(batch.contains("BatchDraftStatus.NeedsReview && explicitlyAccepted"))
        assertTrue(batch.contains("status != BatchDraftStatus.NeedsReview"))
        assertTrue(batch.contains("status = previous.status"))
        assertTrue(batch.contains("acceptOriginalPhoto: Boolean = false"))
        assertTrue(batch.contains("status = BatchDraftStatus.Queued"))
        assertTrue(batch.contains("explicitlyAccepted = false"))

        assertTrue(app.contains("onSaveBatch = { requestedDraftIds ->"))
        assertTrue(app.contains("val acceptedDrafts = batchDrafts.toList().acceptedForSave(requestedDraftIds)"))
        assertTrue(app.contains("val batchItems = acceptedDrafts.map { draft ->"))
        assertTrue(app.contains("batchDrafts.removeAll { it.id in savedIds }"))
        assertTrue(app.contains("R.string.batch_save_result"))
        assertTrue(app.contains("if (batchDrafts.isEmpty()) replaceRoute(RobiaRoute.Browse)"))
        assertTrue(app.contains("R.string.batch_keep_original_accept"))
        assertTrue(app.contains("onSaveBatchDraft(item, draft.status == BatchDraftStatus.NeedsReview)"))

        assertTrue(strings.contains("name=\"batch_save_items\">Save %1$d items"))
        assertTrue(strings.contains("name=\"batch_helper_summary\""))
        assertTrue(strings.contains("name=\"batch_save_result\""))
        assertTrue(strings.contains("name=\"batch_keep_original_accept\">Keep original and accept"))
    }

    @Test
    fun batchSave_keepsFailureActionsAndDoesNotMakeEditingFailedItemsSaveable() {
        val batch = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt")
        val coordinator = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchProcessingCoordinator.kt")

        assertTrue(batch.contains("get() = this == BatchDraftStatus.Failed || this == BatchDraftStatus.Interrupted"))
        assertTrue(batch.contains("TextButton(onClick = onRetry)"))
        assertTrue(batch.contains("TextButton(onClick = onDiscard)"))
        assertTrue(batch.contains("get() = this == BatchDraftStatus.Ready || this == BatchDraftStatus.NeedsReview"))
        assertTrue(batch.contains("internal fun BatchDraftItem.retryForBatch()"))
        assertTrue(batch.contains("status = BatchDraftStatus.Queued"))
        assertTrue(coordinator.contains("val ownedDraftIds = drafts().map(BatchDraftItem::id).toSet()"))
        assertTrue(coordinator.contains("draft.id in ownedDraftIds && draft.isProcessingActive()"))
        assertTrue(coordinator.contains("interruptActiveDrafts?.invoke()"))
    }

    @Test
    fun batchCancellation_terminalizesTheCurrentItemForManualRetry() {
        val coordinator = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchProcessingCoordinator.kt")
        val batchScreen = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")

        assertTrue(coordinator.contains("forEach(onInterrupted)"))
        assertTrue(batchScreen.contains("BatchDraftStatus.Interrupted"))
        assertTrue(batchScreen.contains("internal fun BatchDraftItem.interruptedForBatch(message: String): BatchDraftItem?"))
        assertTrue(batchScreen.contains("if (isProcessingActive())"))
        assertTrue(batchScreen.contains("catch (throwable: CancellationException)"))
        assertTrue(batchScreen.contains("status = BatchDraftStatus.Interrupted"))
        assertTrue(app.contains("?.interruptedForBatch(context.getString(R.string.batch_interrupted_message))"))
    }

    @Test
    fun batchStatusBadge_labelsAcceptedNeedsReviewWithVisualStatus() {
        val badge = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt")
            .substringAfter("private fun BatchStatusBadge(")
            .substringBefore("internal suspend fun processBatchDraft(")

        assertTrue(badge.contains("val visualStatus = if (isAccepted) BatchDraftStatus.Ready else status"))
        assertTrue(badge.contains("text = stringResource(visualStatus.labelRes)"))
        assertTrue(!badge.contains("text = stringResource(status.labelRes)"))
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
    fun performanceFixtureUris_acceptsAdbStringArrayAndArrayListExtrasSafely() {
        val mainActivity = source("app/src/main/java/com/gusanitolabs/robia/MainActivity.kt")
        val reader = mainActivity
            .substringAfter("private fun performanceFixtureUris(): List<String> =")
            .substringBefore("private fun requestGoogleDriveAuthorization()")

        assertTrue(reader.contains("if (BuildConfig.DEBUG)"))
        assertTrue(reader.contains("intent.performanceFixtureUriStrings(EXTRA_PERFORMANCE_FIXTURE_URIS)"))
        assertTrue(!reader.contains("getStringArrayListExtra(EXTRA_PERFORMANCE_FIXTURE_URIS).orEmpty()"))
        assertTrue(reader.contains("is Array<*> -> extra.toStringListOrEmpty()"))
        assertTrue(reader.contains("is ArrayList<*> -> extra.toStringListOrEmpty()"))
        assertTrue(reader.contains("else -> emptyList()"))
        assertTrue(reader.contains("value as? String ?: return emptyList()"))
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
        assertTrue(app.contains("val wardrobeItemsLoading = !performanceFixtureMode && loadedClothingItems == null"))
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
    fun driveSync_deadlinesAndCancellationAlwaysReleaseClaimedWorkForManualBoundedRetry() {
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")
        val driveRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/GoogleDriveWardrobeRepository.kt")
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val repository = source("app/src/main/java/com/gusanitolabs/robia/data/WardrobeRepository.kt")

        assertTrue(processor.contains("withTimeout(SYNC_OPERATION_TIMEOUT_MILLIS)"))
        assertTrue(processor.contains("withContext(NonCancellable)"))
        assertTrue(processor.contains("markFailedRetryable(lockedWork, lockedMetadataWork, timeoutMessage)"))
        assertTrue(processor.contains("scheduleRetryWakeUp"))
        assertTrue(processor.contains("delay((retryAt - System.currentTimeMillis()).coerceAtLeast(0L))"))
        assertTrue(processor.contains("hasPendingCloudDeletion()"))
        assertTrue(processor.contains("retry rejected reason=cloud_deletion_pending"))
        assertTrue(driveRepository.contains("withTimeout(AUTHORIZATION_TIMEOUT_MILLIS)"))
        assertTrue(driveRepository.contains("withDrivePhaseDeadline"))
        val repositoryBeforeHttpApi = driveRepository.substringBefore("private class HttpDriveSnapshotApi")
        assertTrue(repositoryBeforeHttpApi.contains("const val AUTHORIZATION_TIMEOUT_MILLIS = 30_000L"))
        assertTrue(repositoryBeforeHttpApi.contains("const val DRIVE_PHASE_TIMEOUT_MILLIS = 60_000L"))
        assertTrue(!repositoryBeforeHttpApi.contains("private class HttpDriveSnapshotApi"))
        assertTrue(repository.contains("suspend fun hasPendingCloudDeletion(): Boolean"))
        assertTrue(repository.contains("suspend fun nextRunnableSyncRetryEpochMillis(): Long?"))
        assertTrue(dao.contains("hasPendingCloudDeletion"))
        assertTrue(dao.contains("retry_attempt_count = MIN(retry_attempt_count + 1, 3)"))
        assertTrue(dao.contains("WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000"))

        val retryableFailureMarker = processor
            .substringAfter("private suspend fun markFailedRetryable(")
            .substringBefore("private fun DriveSyncConnectionStatus.toWardrobeSyncState")
        assertTrue(retryableFailureMarker.contains("message: String? = null"))
        assertTrue(
            retryableFailureMarker.contains(
                "wardrobeRepository.markGarmentSyncFailedRetryable(item.id, item.revision, message)",
            ),
        )
        assertTrue(
            retryableFailureMarker.contains(
                "wardrobeRepository.markMetadataSyncFailedRetryable(item, message)",
            ),
        )

        val deletionGuard = processor
            .substringAfter("if (wardrobeRepository.hasPendingCloudDeletion())")
            .substringBefore("val retryRevision")
        assertTrue(deletionGuard.contains("correlationId = UUID.randomUUID().toString()"))
        assertTrue(deletionGuard.contains("phase = null"))
        assertTrue(deletionGuard.contains("status = null"))
    }

    @Test
    fun metadataAndTombstoneFailuresPersistBoundedRetryAttempts() {
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val repository = source("app/src/main/java/com/gusanitolabs/robia/data/LocalRepositories.kt")

        assertTrue(repository.contains("markMetadataSyncFailedRetryable(work.toEntity(), message)"))
        assertTrue(dao.contains("val now = System.currentTimeMillis()"))
        assertTrue(dao.contains("updateMetadataSyncFailedRetryable(work, message, now) > 0"))
        assertTrue(dao.contains("claimMetadataSync(work, System.currentTimeMillis()) > 0"))

        listOf(
            "TagCategory" to "sync_revision = :revision",
            "GarmentTag" to "sync_revision = :revision",
            "MainColor" to "sync_revision = :revision",
            "Tombstone" to "revision = :revision",
        ).forEach { (entity, revisionPredicate) ->
            val claimQuery = queryBeforeMethod(dao, "suspend fun claim${entity}MetadataSync")
            assertTrue(claimQuery.contains("sync_status = 'Running'"))
            assertTrue(claimQuery.contains("sync_started_at_epoch_millis = :startedAtEpochMillis"))
            assertTrue(claimQuery.contains(revisionPredicate))

            val query = queryBeforeMethod(dao, "suspend fun update${entity}SyncFailedRetryable")
            assertTrue(query.contains("CASE WHEN retry_attempt_count + 1 >= 3 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END"))
            assertTrue(query.contains("retry_attempt_count = MIN(retry_attempt_count + 1, 3)"))
            assertTrue(query.contains("WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000"))
            assertTrue(query.contains("sync_started_at_epoch_millis = NULL"))
            assertTrue(query.contains("sync_failure_message = :message"))
        }
    }

    @Test
    fun metadataAndTombstoneStaleRunningRecoveryUsesDurableRetryPolicy() {
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val recovery = dao.substringAfter("suspend fun recoverStaleRunningSyncWork(staleBeforeEpochMillis: Long): Int")
            .substringBefore("@Query(\"UPDATE clothing_items")

        assertTrue(recovery.contains("val now = System.currentTimeMillis()"))
        assertTrue(recovery.contains("recoverStaleTagCategorySyncWork(staleBeforeEpochMillis, now)"))
        assertTrue(recovery.contains("recoverStaleGarmentTagSyncWork(staleBeforeEpochMillis, now)"))
        assertTrue(recovery.contains("recoverStaleMainColorSyncWork(staleBeforeEpochMillis, now)"))
        assertTrue(recovery.contains("recoverStaleTombstoneSyncWork(staleBeforeEpochMillis, now)"))
        assertTrue(!recovery.contains("retry_after_epoch_millis = 0"))

        listOf("TagCategory", "GarmentTag", "MainColor", "Tombstone").forEach { entity ->
            val query = queryBeforeMethod(dao, "suspend fun recoverStale${entity}SyncWork")
            assertTrue(query.contains("CASE WHEN retry_attempt_count + 1 >= 3 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END"))
            assertTrue(query.contains("retry_attempt_count = MIN(retry_attempt_count + 1, 3)"))
            assertTrue(query.contains("WHEN 0 THEN 60000 WHEN 1 THEN 300000 ELSE 900000"))
            assertTrue(query.contains("COALESCE(sync_started_at_epoch_millis, 0) <= :staleBeforeEpochMillis"))
            assertTrue(!query.contains("retry_after_epoch_millis = 0"))
        }
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
        assertTrue(retryHandler.contains("driveRepository.retryRestoredPhoto(garmentId"))
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
    fun restoredPhotoRetryApply_hasSingleVerifiedContractAndDecisiveDiagnostics() {
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val snapshotRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/LocalWardrobeSyncSnapshotRepository.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")
        val driveRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/GoogleDriveWardrobeRepository.kt")

        assertTrue(dao.contains("data class RestoredPhotoApplyStateEntity"))
        assertTrue(dao.contains("restoredPhotoApplyState"))
        assertTrue(dao.contains("photo_uri AS photoUri"))
        assertTrue(dao.contains("sync_dirty_at_epoch_millis AS syncDirtyAtEpochMillis"))

        val applyQuery = queryBeforeMethod(dao, "suspend fun applyRestoredPhoto")
        assertTrue(applyQuery.contains("photo_uri = :photoUri"))
        assertTrue(applyQuery.contains("sync_revision = :revision"))
        assertTrue(applyQuery.contains("photo_restore_guarded = 1"))
        assertTrue(applyQuery.contains("sync_status = 'Running'"))
        assertTrue(applyQuery.contains("CASE WHEN sync_dirty_at_epoch_millis IS NULL THEN 'Synced' ELSE 'Queued' END"))
        assertTrue(applyQuery.contains("photo_restore_guarded = 0"))
        assertTrue(applyQuery.contains("retry_attempt_count = 0"))

        assertTrue(snapshotRepository.contains("sealed interface RestoredPhotoApplyResult"))
        assertTrue(snapshotRepository.contains("restoredUri == null -> \"restored_uri_absent\""))
        assertTrue(snapshotRepository.contains("\"import_guarded:\$guardedReason\""))
        assertTrue(snapshotRepository.contains("rowsUpdated == 1 && persisted"))
        assertTrue(snapshotRepository.contains("rowAfter?.photoUri == restoredUri"))
        assertTrue(snapshotRepository.contains("!rowAfter.photoRestoreGuarded"))
        assertTrue(snapshotRepository.contains("rowBefore.zeroUpdateReason(expectedRevision, persisted)"))
        listOf(
            "garment_not_found",
            "revision_mismatch",
            "photo_not_guarded",
            "status_not_running",
            "post_update_verification_failed",
            "dao_update_zero_rows",
        ).forEach { reason -> assertTrue(snapshotRepository.contains(reason)) }

        assertTrue(processor.contains("applyResult.toDiagnosticEvent()"))
        assertTrue(processor.contains("daoRowsUpdated=\$daoRowsUpdated"))
        assertTrue(processor.contains("restoredUriPresent=\$restoredUriPresent restoredUriUsable=\$importedPhotoUsable"))
        assertTrue(processor.contains("localRevisionBefore="))
        assertTrue(processor.contains("statusBefore="))
        assertTrue(processor.contains("guardBefore="))
        assertTrue(processor.contains("photoUriPresentAfter="))
        assertTrue(processor.contains("blobPath=\$blobPath"))

        assertTrue(driveRepository.contains("fetchedByteCount=\${bytes.size}"))
        assertTrue(driveRepository.contains("readbackByteCount=\${readBackBytes?.size ?: 0}"))
        assertTrue(driveRepository.contains("photo_restore_decode_result garmentId=\$garmentId decoder=\$decoderPath"))
        assertTrue(driveRepository.contains("photo_restore_import_result garmentId=\$garmentId"))

        val fetchDiagnostics = driveRepository
            .substringAfter("internal fun DriveBlob.restoreFetchResultEvents")
            .substringBefore("internal fun GarmentPhotoRecord.driveLookupMissingEvent")
        val fetchResultDiagnostics = fetchDiagnostics.substringAfter("photo_restore_fetch_result")
        assertTrue(fetchResultDiagnostics.indexOf("fetchedByteCount=") < fetchResultDiagnostics.indexOf("blobPath="))

        val importDiagnostics = driveRepository
            .substringAfter("internal fun GarmentPhotoRecord.importDiagnosticEvent")
            .substringBefore("private data class ImageDecodeResult")
        assertTrue(importDiagnostics.indexOf("persistedPhotoUriPresent=") < importDiagnostics.indexOf("blobPath="))
    }

    @Test
    fun guardedPhotoRestoreRetry_persistsAcrossRestartUntilVerifiedOneRowApply() {
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val repository = source("app/src/main/java/com/gusanitolabs/robia/data/LocalRepositories.kt")
        val snapshotRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/LocalWardrobeSyncSnapshotRepository.kt")

        val claimQuery = queryBeforeMethod(dao, "suspend fun markGarmentPhotoRestoreRetrying")
        assertTrue(claimQuery.contains("photo_restore_guarded = 1"))
        assertTrue(claimQuery.contains("sync_status IN ('NeedsUserAction', 'Queued')"))
        assertTrue(claimQuery.contains("retry_after_epoch_millis IS NULL OR retry_after_epoch_millis <= :now"))
        assertTrue(claimQuery.contains("photo_restore_retry_deadline_epoch_millis >= :now"))

        val staleRecoveryQuery = queryBeforeMethod(dao, "suspend fun recoverStaleGarmentSyncWork")
        assertTrue(staleRecoveryQuery.contains("CASE WHEN photo_restore_guarded = 1 THEN 'NeedsUserAction' ELSE 'FailedRetryable' END"))
        assertTrue(staleRecoveryQuery.contains("retry_after_epoch_millis = 0"))
        assertTrue(staleRecoveryQuery.contains("sync_started_at_epoch_millis = NULL"))

        assertTrue(repository.contains("eligibleGarmentPhotoRestoreRetryRevision(id, now)"))
        assertTrue(repository.contains("markGarmentPhotoRestoreRetrying(id, revision, startedAtEpochMillis, now) > 0"))
        assertTrue(snapshotRepository.contains("Targeted guarded-photo retry uses [applyRestoredPhoto]"))
        assertTrue(snapshotRepository.contains("must resolve exactly one pre-existing guarded row"))
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
    fun settings_isDedicatedRouteAndDiagnosticsStayDeveloperGated() {
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val settingsScreen = app
            .substringAfter("private fun SettingsScreen(")
            .substringBefore("@StringRes\nprivate fun DriveSyncConnectionStatus.cloudSetupSummaryRes")

        assertTrue(app.contains("data object Settings : RobiaRoute"))
        assertTrue(app.contains("pushRoute(RobiaRoute.Settings)"))
        assertTrue(!app.contains("private fun SettingsMenu("))
        assertTrue(settingsScreen.contains("developerModeUnlocked"))
        assertTrue(settingsScreen.contains("if (developerModeEnabled)"))
        assertTrue(settingsScreen.contains("onRestoreSyncLogClick"))
        assertTrue(app.contains("RobiaRoute.Settings -> SettingsScreen("))
        assertTrue(app.contains("RobiaRoute.DeveloperSyncLog -> if (developerModeEnabled)"))
    }

    @Test
    fun developerDiagnosticsLog_isAsyncGlobalBoundedAndDeveloperModeGated() {
        val log = source("app/src/main/java/com/gusanitolabs/robia/sync/RestoreSyncLogRepository.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")

        assertTrue(log.contains("developer_restore_sync.log"))
        assertTrue(log.contains("DEFAULT_MAX_EVENTS = 500"))
        assertTrue(log.contains("DEFAULT_MAX_BYTES = 256 * 1024"))
        assertTrue(log.contains("scope.launch"))
        assertTrue(log.contains("runCatching"))
        assertTrue(log.contains("if (!enabled) return"))
        assertTrue(log.contains("category: String = \"restore\""))
        assertTrue(log.contains("level: String = \"info\""))
        assertTrue(log.contains("boundedDiagnosticLogLines"))
        assertTrue(processor.contains("restoreSyncLogRepository.setEnabled(settings.developerModeEnabled)"))
        assertTrue(processor.contains("recordSettingsChanges(settings)"))
    }

    @Test
    fun restoreProgress_reportsItemAndByteProgressWithoutInventingTotals() {
        val gateway = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncGateway.kt")
        val drive = source("app/src/main/java/com/gusanitolabs/robia/sync/GoogleDriveWardrobeRepository.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")

        assertTrue(gateway.contains("data class RestoreItemProgress"))
        assertTrue(gateway.contains("data class RestoreByteProgress"))
        assertTrue(gateway.contains("val totalBytes: Long?"))
        assertTrue(drive.contains("readProgressBytes"))
        assertTrue(drive.contains("contentLengthLong.takeIf { it >= 0L }"))
        assertTrue(drive.contains("DRIVE_PROGRESS_THROTTLE_MILLIS"))
        assertTrue(processor.contains("itemProgress = RestoreItemProgress"))
        assertTrue(processor.contains("byteProgress = RestoreByteProgress"))
        assertTrue(app.contains("cloud_restore_byte_progress_unknown"))
        assertTrue(app.contains("cloud_restore_byte_progress_known"))
    }

    @Test
    fun restoredPhotoRetry_hasVisibleStateBackoffAndNoDuplicateWork() {
        val models = source("app/src/main/java/com/gusanitolabs/robia/core/model/ClothingModels.kt")
        val repository = source("app/src/main/java/com/gusanitolabs/robia/data/LocalRepositories.kt")
        val dao = source("app/src/main/java/com/gusanitolabs/robia/data/local/Dao.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")

        assertTrue(models.contains("data class PhotoRestoreState"))
        assertTrue(models.contains("val retryAttemptCount: Int = 0"))
        assertTrue(models.contains("val hasRetryAttemptsRemaining"))
        assertTrue(repository.contains("photoRestoreState = PhotoRestoreState"))
        assertTrue(repository.contains("retryAttemptCount = item.retryAttemptCount"))
        assertTrue(repository.contains("retryAfterEpochMillis = item.retryAfterEpochMillis"))
        assertTrue(dao.contains("sync_status = 'Running'"))
        assertTrue(dao.contains("retry_attempt_count < 3"))
        assertTrue(dao.contains("retry_after_epoch_millis <= :now"))
        assertTrue(processor.contains("retry requested"))
        assertTrue(processor.contains("retry rejected reason="))
        assertTrue(processor.contains("retry_deadline_epoch_ms"))
        assertTrue(processor.contains("photo_restore_lookup"))
        assertTrue(processor.contains("photo_restore_write"))
        assertTrue(processor.contains("retry started"))
        assertTrue(app.contains("canRetryRestoredPhotoNow"))
        assertTrue(app.contains("photoRestoreState.hasRetryAttemptsRemaining"))
        assertTrue(app.contains("cloud_restore_photo_retry_exhausted"))
        assertTrue(app.contains("cloud_restore_retry_photo_expired"))
        assertTrue(app.contains("cloud_restore_photo_retry_backoff_until"))
        assertTrue(app.contains("syncStatus == GarmentSyncStatus.Running"))
    }

    @Test
    fun drivePhotoRestoreDiagnosticsDoNotEmitRawByteSamples() {
        val driveRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/GoogleDriveWardrobeRepository.kt")
        val restoreLog = source("app/src/main/java/com/gusanitolabs/robia/sync/RestoreSyncLogRepository.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")

        assertFalse(driveRepository.contains(" first32="))
        assertFalse(driveRepository.contains(" last32="))
        assertFalse(driveRepository.contains(" readbackFirst32="))
        assertFalse(driveRepository.contains(" readbackLast32="))
        assertFalse(driveRepository.contains(" sha256="))
        assertFalse(driveRepository.contains(" readbackHash="))
        assertTrue(driveRepository.contains("sha256Prefix="))
        assertTrue(driveRepository.contains("readbackHashPrefix="))
        assertTrue(driveRepository.contains("imageMagicLabel"))
        assertTrue(restoreLog.contains("content_hash_prefix="))
        assertTrue(restoreLog.contains("sanitizeMagicClassification"))
        assertTrue(processor.contains("imageMagicClassification"))
    }

    @Test
    fun affectedGarmentIdentityUsesDomainPhotoRestoreStateInsteadOfLocalizedFailureText() {
        val models = source("app/src/main/java/com/gusanitolabs/robia/core/model/ClothingModels.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val hasMissing = app
            .substringAfter("private val UiWardrobeItem.hasMissingRestoredPhoto")
            .substringBefore("@Composable\nprivate fun TonalTag")

        assertTrue(models.contains("val photoRestoreState: PhotoRestoreState"))
        assertTrue(app.contains("val photoRestoreState: PhotoRestoreState"))
        assertTrue(app.contains("photoRestoreState = item.photoRestoreState"))
        assertTrue(hasMissing.contains("photoRestoreState.needsAttention"))
        assertTrue(!hasMissing.contains("Foto no restaurada"))
        assertTrue(app.contains("text = item.name"))
        assertTrue(app.contains("text = item.subtitle"))
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
    fun driveBackupDeletion_isDurableScopedAndRequiresExplicitReenable() {
        val models = source("app/src/main/java/com/gusanitolabs/robia/core/model/SyncModels.kt")
        val settings = source("app/src/main/java/com/gusanitolabs/robia/data/SettingsRepository.kt")
        val driveRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/DriveWardrobeRepository.kt")
        val googleDriveRepository = source("app/src/main/java/com/gusanitolabs/robia/sync/GoogleDriveWardrobeRepository.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val strings = source("app/src/main/res/values/strings.xml")

        assertTrue(models.contains("enum class DriveBackupDeletionState"))
        assertTrue(settings.contains("pauseSyncForDriveBackupDeletion"))
        assertTrue(driveRepository.contains("suspend fun deleteBackup"))
        assertTrue(googleDriveRepository.contains("listBackupFiles"))
        assertTrue(googleDriveRepository.contains("spaces=appDataFolder"))
        assertTrue(googleDriveRepository.contains("MAX_BACKUP_DELETE_FILES"))
        assertTrue(googleDriveRepository.contains("relist"))
        assertTrue(processor.contains("remainingFileCount"))
        assertTrue(processor.contains("pauseSyncForDriveBackupDeletion()"))
        assertTrue(processor.contains("mutex.withLock"))
        assertTrue(processor.contains("DriveSyncConnectionStatus.Disabled"))
        assertTrue(processor.contains("DeleteCloudBackup"))
        assertTrue(app.contains("setDriveBackupDeletionState(com.gusanitolabs.robia.core.model.DriveBackupDeletionState.None)"))
        assertTrue(app.contains("R.string.drive_backup_delete_menu"))
        assertTrue(strings.contains("Delete backup from Google Drive"))
        assertTrue(strings.contains("The clothes on this device will not be deleted."))
    }

    @Test
    fun driveBackupDeletion_handlesTerminalOutcomesAndInterruptionsWithoutResumingSync() {
        val models = source("app/src/main/java/com/gusanitolabs/robia/core/model/SyncModels.kt")
        val processor = source("app/src/main/java/com/gusanitolabs/robia/sync/WardrobeSyncOutboxProcessor.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val strings = source("app/src/main/res/values/strings.xml")

        assertTrue(models.contains("Deleting"))
        assertTrue(models.contains("Complete"))
        assertTrue(models.contains("NeedsAttention"))
        assertTrue(processor.contains("remainingFileCount == 0"))
        assertTrue(processor.contains("catch (cancellation: CancellationException)"))
        assertTrue(processor.contains("withContext(NonCancellable)"))
        assertTrue(processor.contains("DriveBackupDeletionState.NeedsAttention"))
        assertTrue(processor.contains("throw cancellation"))
        assertTrue(processor.contains("mutex.withLock"))
        assertTrue(app.contains("settings.driveBackupDeletionState"))
        assertTrue(app.contains("drive_backup_deletion_status_deleting"))
        assertTrue(app.contains("drive_backup_deletion_status_complete"))
        assertTrue(app.contains("drive_backup_deletion_status_attention"))
        assertTrue(app.contains("drive_backup_again"))
        assertTrue(strings.contains("Deleting Google Drive backup"))
        assertTrue(strings.contains("Backup deleted"))
        assertTrue(strings.contains("Backup deletion needs attention"))
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

    @Test
    fun imagePreviewsUseTheSharedCoilAdapterWithoutChangingCanonicalPhotos() {
        val imageStore = source("app/src/main/java/com/gusanitolabs/robia/media/ClothingImageStore.kt")
        val boundedImage = source("app/src/main/java/com/gusanitolabs/robia/ui/BoundedGarmentImage.kt")
        val pipeline = source("app/src/main/java/com/gusanitolabs/robia/media/ImagePipeline.kt")
        val app = source("app/src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt")
        val batch = source("app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt")
        val addEdit = source("app/src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt")
        val colorReview = source("app/src/main/java/com/gusanitolabs/robia/ui/ColorReviewScreen.kt")
        val paths = source("app/src/main/res/xml/file_paths.xml")

        assertTrue(imageStore.contains("fun getOrCreateBoundedThumbnail("))
        assertTrue(imageStore.contains("inJustDecodeBounds = true"))
        assertTrue(imageStore.contains("inSampleSize = thumbnailDecodeSampleSize"))
        assertTrue(imageStore.contains("Bitmap.createScaledBitmap"))
        assertTrue(imageStore.contains("THUMBNAIL_DIRECTORY = \"robia_thumbnails\""))
        assertTrue(imageStore.contains("data class ImageMetrics"))
        assertTrue(paths.contains("name=\"private_thumbnails\""))

        assertTrue(pipeline.contains("ImageLoader.Builder"))
        assertTrue(pipeline.contains("MemoryCache.Builder"))
        assertTrue(pipeline.contains("DiskCache.Builder"))
        assertTrue(pipeline.contains("eventListenerFactory"))
        assertTrue(pipeline.contains("decoderCoroutineContext"))
        assertTrue(pipeline.contains("prefetchPermit"))
        assertTrue(pipeline.contains("ownedInFlightRequests"))
        assertTrue(pipeline.contains("CoroutineStart.LAZY"))
        assertTrue(pipeline.contains("completeInFlight"))
        assertTrue(pipeline.contains("diskCacheEntries"))
        assertTrue(pipeline.contains("errorRecorded"))
        assertTrue(pipeline.contains("Size.ORIGINAL"))
        assertTrue(boundedImage.contains("ImagePipeline.shared"))
        assertTrue(boundedImage.contains("thumbnailMaxEdgePx: Int?"))
        assertTrue(boundedImage.contains("allowOriginal = allowOriginal"))
        assertTrue(!boundedImage.contains("mutableStateOf(photoUri)"))
        assertTrue(!boundedImage.contains("setImageURI"))
        assertTrue(boundedImage.contains("ImageTargetBounds"))

        val gridCard = app.substringAfter("private fun GarmentGridCard(").substringBefore("@Composable\nprivate fun GarmentCloudStatusBadge")
        val detailMedia = app.substringAfter("private fun DetailMediaCard(").substringBefore("@Composable\nprivate fun ColorMetricsCard")
        assertTrue(gridCard.contains("GRID_THUMBNAIL_MAX_EDGE_PX"))
        assertTrue(app.contains("DETAIL_DISPLAY_MAX_EDGE_PX = 512"))
        assertTrue(detailMedia.contains("thumbnailMaxEdgePx = DETAIL_DISPLAY_MAX_EDGE_PX"))
        assertTrue(detailMedia.contains("onShareImageClick"))

        assertTrue(batch.contains("BATCH_THUMBNAIL_MAX_EDGE_PX"))
        assertTrue(addEdit.contains("EDITOR_PREVIEW_MAX_EDGE_PX"))
        assertTrue(colorReview.contains("COLOR_REVIEW_THUMBNAIL_MAX_EDGE_PX"))
        assertTrue(batch.contains("photoUri = photoUri"))
        assertTrue(addEdit.contains("photoUri = canonicalPhotoUri"))
    }

    @Test
    fun performanceReportCapturesThumbnailAndMemoryEvidenceForLikeForLikeComparison() {
        val workflow = source(".github/workflows/android-performance-baseline.yml")
        val summary = source("scripts/summarize_performance_baseline.py")
        val pipeline = source("app/src/main/java/com/gusanitolabs/robia/media/ImagePipeline.kt")

        assertTrue(workflow.contains("dumpsys meminfo com.gusanitolabs.robia > performance-artifacts/meminfo-before.txt"))
        assertTrue(workflow.contains("dumpsys meminfo com.gusanitolabs.robia > performance-artifacts/meminfo-after.txt"))
        assertTrue(workflow.contains("RobiaPerformance:I"))
        assertTrue(pipeline.contains("placeholder_visible"))
        assertTrue(pipeline.contains("memory_hit"))
        assertTrue(pipeline.contains("disk_hit"))
        assertTrue(pipeline.contains("in_flight_wait"))
        assertTrue(pipeline.contains("blankDurationMs"))
        assertTrue(pipeline.contains("activeDecodeCount"))
        assertTrue(pipeline.contains("first_draw"))
        assertTrue(pipeline.contains("TrackingMemoryCache"))
        assertTrue(pipeline.contains("recordEvictions"))
        assertTrue(pipeline.contains("onEvictions(keysBefore - delegate.keys)"))
        assertTrue(pipeline.contains("evictionCount.addAndGet"))
        assertTrue(!pipeline.contains("sourceUri="))

        assertTrue(summary.contains("REQUIRED_IMAGE_STAGES ="))
        assertTrue(summary.contains("IMAGE_STAGE_PATTERN"))
        assertTrue(summary.contains("image_stage_records ="))
        assertTrue(summary.contains("Image pipeline records captured"))
        assertTrue(summary.contains("read_meminfo"))
        assertTrue(summary.contains("meminfo-before.txt"))
        assertTrue(summary.contains("meminfo-after.txt"))
        assertTrue(workflow.contains("for image_stage in resolve decode bind first_draw in_flight_wait placeholder_visible eviction; do"))
        assertTrue(workflow.contains("stage=\${image_stage}([[:space:]]|$)"))
        assertTrue(summary.contains("stage=(?:resolve|decode|bind|first_draw|in_flight_wait|placeholder_visible|eviction)"))
        assertTrue(summary.contains("IMAGE_STAGE_NAME_PATTERN"))
        assertTrue(summary.contains("missing required image stages"))
        assertTrue(!workflow.contains("thumbnail_stage"))
        assertTrue(!summary.contains("thumbnail_stage"))
        assertTrue(workflow.contains("python3 scripts/check_image_thumbnail_pipeline_static.py"))
    }

    @Test
    fun androidPlatformBackup_isDisabledForDriveOnlyOptInPolicy() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val drivePlan = source("docs/google_drive_sync_setup_plan.md")

        assertTrue(manifest.contains("android:allowBackup=\"false\""))
        assertTrue(!manifest.contains("android:dataExtractionRules"))
        assertTrue(!manifest.contains("android:fullBackupContent"))
        assertTrue(manifest.contains("Google Drive-only and explicit opt-in inside the app"))
        assertTrue(manifest.contains("Disable Android Auto Backup and device transfer"))

        assertTrue(drivePlan.contains("Google Drive-only and opt-in"))
        assertTrue(drivePlan.contains("Android Auto Backup and Android device-to-device transfer must not copy"))
        assertTrue(drivePlan.contains("Do not re-enable Android platform backup rules"))
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

    private fun queryBeforeMethod(source: String, methodNeedle: String): String =
        source.substringBefore(methodNeedle).substringAfterLast("@Query")
}
