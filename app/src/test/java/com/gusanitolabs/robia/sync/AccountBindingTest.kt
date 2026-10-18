package com.gusanitolabs.robia.sync

import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.RobiaSettings
import com.gusanitolabs.robia.data.SettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeDriveWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeSettingsRepository
import com.gusanitolabs.robia.sync.fakes.FakeSyncWorkScheduler
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeRepository
import com.gusanitolabs.robia.sync.fakes.FakeWardrobeSnapshotStore
import com.gusanitolabs.robia.sync.fakes.snapshotWithGarments
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** V-D02-4: a different Google account blocks every Drive write; "Use this account" rebinds and merges, never overwrites. */
class AccountBindingTest {
    @Test
    fun aDifferentAccountBlocksUploadRestoreAndPurge() = runBlocking {
        val snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a", "b"))
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("c")))
        val processor = processorWith(accounts(bound = "owner@example.com", authorized = "other@example.com"), snapshots, drive)

        processor.enqueue(WardrobeSyncOperation.ExportFullSnapshot())
        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))
        processor.enqueue(WardrobeSyncOperation.DeleteCloudBackup())

        assertTrue(drive.upserts.isEmpty())
        assertTrue(snapshots.imports.isEmpty())
        val state = withTimeout(5_000) { processor.state.first { state -> state.hasConflictingAccountBinding } }
        assertTrue(state.attention.any { attention -> attention.kind == SyncAttentionKind.AccountMismatch })
    }

    @Test
    fun useThisAccountRebindsAndRunsMergeOnConnectWithoutOverwriting() = runBlocking {
        val snapshots = FakeWardrobeSnapshotStore(local = snapshotWithGarments("a", "b"))
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("b", "c", "d")))
        val settings = accounts(bound = "owner@example.com", authorized = "other@example.com")
        val processor = processorWith(settings, snapshots, drive)

        processor.useThisDriveAccount()

        val summary = withTimeout(5_000) { processor.state.first { state -> state.pendingMergeSummary != null }.pendingMergeSummary }
        assertEquals(3, summary?.remoteGarmentCount)
        assertTrue(snapshots.imports.isEmpty())
        assertTrue(drive.upserts.isEmpty())
        assertEquals("other@example.com", settings.settings.first().driveBoundAccountEmail)
        assertFalse(processor.state.first().hasConflictingAccountBinding)
    }

    @Test
    fun theSameAccountInAnotherLetterCaseIsNotAMismatch() = runBlocking {
        val snapshots = FakeWardrobeSnapshotStore()
        val drive = FakeDriveWardrobeRepository(snapshot = DriveSyncResult.Success(snapshotWithGarments("a", "b")))
        val processor = processorWith(accounts(bound = "Owner@Example.com", authorized = "owner@example.com"), snapshots, drive)

        processor.enqueue(WardrobeSyncOperation.ImportFullSnapshot(sourceRevision = 1L))

        assertEquals(listOf(listOf("a", "b")), snapshots.imports.map { snapshot -> snapshot.garments.map { it.id } })
        assertFalse(processor.state.first().attention.any { it.kind == SyncAttentionKind.AccountMismatch })
    }

    @Test
    fun anUnknownAccountIsNotBlocked() = runBlocking {
        assertNull(accounts(bound = null, authorized = "owner@example.com").settings.first().driveBoundAccountEmail)
        val processor = processorWith(accounts(bound = null, authorized = null), FakeWardrobeSnapshotStore(), FakeDriveWardrobeRepository())
        assertFalse(processor.state.first().hasConflictingAccountBinding)
    }

    private fun accounts(bound: String?, authorized: String?): AccountSettings =
        AccountSettings(FakeSettingsRepository(status = DriveSyncConnectionStatus.Connected), bound, authorized)

    /** The wiring of newTestProcessor, with account settings in place of the plain fake. */
    private fun processorWith(
        settings: SettingsRepository,
        snapshots: FakeWardrobeSnapshotStore,
        drive: FakeDriveWardrobeRepository,
    ): WardrobeSyncOutboxProcessor = WardrobeSyncOutboxProcessor(
        settingsRepository = settings,
        wardrobeRepository = FakeWardrobeRepository(),
        snapshotRepository = snapshots,
        driveRepository = drive,
        syncWorkScheduler = FakeSyncWorkScheduler(),
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        dispatcher = Dispatchers.Unconfined,
    )
}

/** Adds the two account fields to the fake settings; the setters update them, as the DataStore repository does. */
private class AccountSettings(
    private val base: FakeSettingsRepository,
    bound: String?,
    authorized: String?,
) : SettingsRepository by base {
    private val accounts = MutableStateFlow(bound to authorized)

    override val settings: Flow<RobiaSettings> = base.settings.combine(accounts) { current, (boundEmail, authorizedEmail) ->
        current.copy(driveBoundAccountEmail = boundEmail, driveAuthorizedAccountEmail = authorizedEmail)
    }

    override suspend fun setDriveBoundAccountEmail(email: String?) {
        accounts.value = email to accounts.value.second
    }

    override suspend fun setDriveAuthorizedAccountEmail(email: String?) {
        accounts.value = accounts.value.first to email
    }
}
