package com.gusanitolabs.robia.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gusanitolabs.robia.core.model.BrowseLayout
import com.gusanitolabs.robia.core.model.DriveBackupDeletionState
import com.gusanitolabs.robia.core.model.DriveSyncConnectionStatus
import com.gusanitolabs.robia.core.model.LanguagePreference
import com.gusanitolabs.robia.core.model.RobiaSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface SettingsRepository {
    val settings: Flow<RobiaSettings>
    suspend fun setLanguagePreference(languagePreference: LanguagePreference)
    suspend fun setDeveloperModeUnlocked(unlocked: Boolean)
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    suspend fun setDriveSyncConnectionStatus(status: DriveSyncConnectionStatus)
    suspend fun setDriveBackupDeletionState(state: DriveBackupDeletionState)
    suspend fun setBrowseLayout(layout: BrowseLayout)
    /** Atomically persists deletion intent and pauses sync before remote I/O. */
    suspend fun pauseSyncForDriveBackupDeletion()
    suspend fun markCloudSetupPromptInteracted()
    suspend fun markDriveFreshInstallRestoreAttempted()
    val modelPromptShown: Flow<Boolean>
    suspend fun setModelPromptShown(shown: Boolean)
    val modelMobileDataApproved: Flow<Boolean>
    suspend fun setModelMobileDataApproved(approved: Boolean)
    /** Stable id of this install, written into exported snapshots as the writer; created on first read. */
    suspend fun deviceId(): String
    /** Revision of the Drive snapshot this phone last saw or uploaded; 0 when never connected. */
    val lastKnownRemoteRevision: Flow<Long>
    /** Writer of the backup at [lastKnownRemoteRevision]; null when unknown (a base recorded before this was stored). */
    val lastKnownRemoteWriterDeviceId: Flow<String?>
    suspend fun setLastKnownRemoteRevision(revision: Long, writerDeviceId: String?)
    /** True while a newer backup from another phone blocks uploads until the user chooses. */
    val syncConflict: Flow<Boolean>
    /** Remote revision the open conflict was raised against; cleared together with [syncConflict]. */
    val syncConflictRemoteRevision: Flow<Long>
    /** Opens the conflict against [remoteRevision], or clears it and its revision when [conflict] is false. */
    suspend fun setSyncConflict(conflict: Boolean, remoteRevision: Long = 0L)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<RobiaSettings> = dataStore.data.map { preferences -> preferences.toRobiaSettings() }

    override suspend fun setLanguagePreference(languagePreference: LanguagePreference) {
        dataStore.edit { preferences ->
            val storageValue = languagePreference.storageValue
            if (storageValue == null) preferences.remove(languageKey) else preferences[languageKey] = storageValue
        }
    }

    override suspend fun setDeveloperModeUnlocked(unlocked: Boolean) {
        dataStore.edit { preferences ->
            preferences[developerModeUnlockedKey] = unlocked
            if (!unlocked) preferences[developerModeEnabledKey] = false
        }
    }

    override suspend fun setDeveloperModeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            val unlocked = preferences[developerModeUnlockedKey] ?: false
            preferences[developerModeEnabledKey] = enabled && unlocked
        }
    }

    override suspend fun setDriveSyncConnectionStatus(status: DriveSyncConnectionStatus) {
        dataStore.edit { preferences ->
            preferences[driveSyncConnectionStatusKey] = status.name
            if (status != DriveSyncConnectionStatus.NotConfigured) preferences[cloudSetupPromptInteractedKey] = true
        }
    }

    override suspend fun setDriveBackupDeletionState(state: DriveBackupDeletionState) {
        dataStore.edit { preferences -> preferences[driveBackupDeletionStateKey] = state.name }
    }

    override suspend fun setBrowseLayout(layout: BrowseLayout) {
        dataStore.edit { preferences -> preferences[browseLayoutKey] = layout.name }
    }

    override suspend fun pauseSyncForDriveBackupDeletion() {
        dataStore.edit { preferences ->
            preferences[driveBackupDeletionStateKey] = DriveBackupDeletionState.Deleting.name
            preferences[driveSyncConnectionStatusKey] = DriveSyncConnectionStatus.Disabled.name
            preferences[cloudSetupPromptInteractedKey] = true
        }
    }

    override suspend fun markCloudSetupPromptInteracted() {
        dataStore.edit { preferences -> preferences[cloudSetupPromptInteractedKey] = true }
    }

    override suspend fun markDriveFreshInstallRestoreAttempted() {
        dataStore.edit { preferences -> preferences[driveFreshInstallRestoreAttemptedKey] = true }
    }

    override val modelPromptShown: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[modelPromptShownKey] ?: false
    }

    override suspend fun setModelPromptShown(shown: Boolean) {
        dataStore.edit { preferences -> preferences[modelPromptShownKey] = shown }
    }

    override val modelMobileDataApproved: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[modelMobileDataApprovedKey] ?: false
    }

    override suspend fun setModelMobileDataApproved(approved: Boolean) {
        dataStore.edit { preferences -> preferences[modelMobileDataApprovedKey] = approved }
    }

    override suspend fun deviceId(): String {
        val preferences = dataStore.edit { preferences ->
            if (preferences[deviceIdKey].isNullOrBlank()) preferences[deviceIdKey] = UUID.randomUUID().toString()
        }
        return preferences[deviceIdKey].orEmpty()
    }

    override val lastKnownRemoteRevision: Flow<Long> = dataStore.data.map { preferences ->
        preferences[lastKnownRemoteRevisionKey] ?: 0L
    }

    override val lastKnownRemoteWriterDeviceId: Flow<String?> = dataStore.data.map { preferences ->
        preferences[lastKnownRemoteWriterDeviceIdKey]
    }

    /** Revision and writer are one write, so the base is never recorded with a writer from another revision. */
    override suspend fun setLastKnownRemoteRevision(revision: Long, writerDeviceId: String?) {
        dataStore.edit { preferences ->
            preferences[lastKnownRemoteRevisionKey] = revision
            if (writerDeviceId == null) {
                preferences.remove(lastKnownRemoteWriterDeviceIdKey)
            } else {
                preferences[lastKnownRemoteWriterDeviceIdKey] = writerDeviceId
            }
        }
    }

    override val syncConflict: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[syncConflictKey] ?: false
    }

    override val syncConflictRemoteRevision: Flow<Long> = dataStore.data.map { preferences ->
        preferences[syncConflictRemoteRevisionKey] ?: 0L
    }

    override suspend fun setSyncConflict(conflict: Boolean, remoteRevision: Long) {
        dataStore.edit { preferences ->
            preferences[syncConflictKey] = conflict
            if (conflict) {
                preferences[syncConflictRemoteRevisionKey] = remoteRevision
            } else {
                preferences.remove(syncConflictRemoteRevisionKey)
            }
        }
    }
}

/** Maps stored preferences to settings; absent or unknown values fall back to the defaults. */
internal fun Preferences.toRobiaSettings(): RobiaSettings {
    val preferences = this
    val developerModeUnlocked = preferences[developerModeUnlockedKey] ?: false
    return RobiaSettings(
        languagePreference = preferences[languageKey].toLanguagePreference(),
        developerModeUnlocked = developerModeUnlocked,
        developerModeEnabled = developerModeUnlocked && (preferences[developerModeEnabledKey] ?: false),
        driveSyncConnectionStatus = preferences[driveSyncConnectionStatusKey].toDriveSyncConnectionStatus(),
        cloudSetupPromptInteracted = preferences[cloudSetupPromptInteractedKey] ?: false,
        driveFreshInstallRestoreAttempted = preferences[driveFreshInstallRestoreAttemptedKey] ?: false,
        driveBackupDeletionState = preferences[driveBackupDeletionStateKey].toDriveBackupDeletionState(),
        browseLayout = preferences[browseLayoutKey].toBrowseLayout(),
    )
}

private fun String?.toLanguagePreference(): LanguagePreference =
    LanguagePreference.entries.firstOrNull { it.storageValue == this } ?: LanguagePreference.System

private fun String?.toDriveSyncConnectionStatus(): DriveSyncConnectionStatus =
    DriveSyncConnectionStatus.entries.firstOrNull { it.name == this } ?: DriveSyncConnectionStatus.NotConfigured

private fun String?.toDriveBackupDeletionState(): DriveBackupDeletionState =
    DriveBackupDeletionState.entries.firstOrNull { it.name == this } ?: DriveBackupDeletionState.None

private fun String?.toBrowseLayout(): BrowseLayout =
    BrowseLayout.entries.firstOrNull { it.name == this } ?: BrowseLayout.PhotosOnly

private val languageKey = stringPreferencesKey("language")
private val developerModeUnlockedKey = booleanPreferencesKey("developer_mode_unlocked")
private val developerModeEnabledKey = booleanPreferencesKey("developer_mode_enabled")
private val driveSyncConnectionStatusKey = stringPreferencesKey("drive_sync_connection_status")
private val cloudSetupPromptInteractedKey = booleanPreferencesKey("cloud_setup_prompt_interacted")
private val driveFreshInstallRestoreAttemptedKey = booleanPreferencesKey("drive_fresh_install_restore_attempted")
private val driveBackupDeletionStateKey = stringPreferencesKey("drive_backup_deletion_state")
private val browseLayoutKey = stringPreferencesKey("browse_layout")
private val modelPromptShownKey = booleanPreferencesKey("model_prompt_shown")
private val modelMobileDataApprovedKey = booleanPreferencesKey("model_mobile_data_approved")
private val deviceIdKey = stringPreferencesKey("sync_device_id")
private val lastKnownRemoteRevisionKey = longPreferencesKey("sync_last_known_remote_revision")
private val lastKnownRemoteWriterDeviceIdKey = stringPreferencesKey("sync_last_known_remote_writer")
private val syncConflictKey = booleanPreferencesKey("sync_conflict")
private val syncConflictRemoteRevisionKey = longPreferencesKey("sync_conflict_remote_revision")
