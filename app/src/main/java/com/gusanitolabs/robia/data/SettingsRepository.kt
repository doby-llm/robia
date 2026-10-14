package com.gusanitolabs.robia.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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
    suspend fun setLastKnownRemoteRevision(revision: Long)
    /** True while a newer backup from another phone blocks uploads until the user chooses. */
    val syncConflict: Flow<Boolean>
    suspend fun setSyncConflict(conflict: Boolean)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {
    override val settings: Flow<RobiaSettings> = dataStore.data.map { preferences ->
        val developerModeUnlocked = preferences[developerModeUnlockedKey] ?: false
        RobiaSettings(
            languagePreference = preferences[languageKey].toLanguagePreference(),
            developerModeUnlocked = developerModeUnlocked,
            developerModeEnabled = developerModeUnlocked && (preferences[developerModeEnabledKey] ?: false),
            driveSyncConnectionStatus = preferences[driveSyncConnectionStatusKey].toDriveSyncConnectionStatus(),
            cloudSetupPromptInteracted = preferences[cloudSetupPromptInteractedKey] ?: false,
            driveFreshInstallRestoreAttempted = preferences[driveFreshInstallRestoreAttemptedKey] ?: false,
            driveBackupDeletionState = preferences[driveBackupDeletionStateKey].toDriveBackupDeletionState(),
        )
    }

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

    override suspend fun setLastKnownRemoteRevision(revision: Long) {
        dataStore.edit { preferences -> preferences[lastKnownRemoteRevisionKey] = revision }
    }

    override val syncConflict: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[syncConflictKey] ?: false
    }

    override suspend fun setSyncConflict(conflict: Boolean) {
        dataStore.edit { preferences -> preferences[syncConflictKey] = conflict }
    }

    private fun String?.toLanguagePreference(): LanguagePreference =
        LanguagePreference.entries.firstOrNull { it.storageValue == this } ?: LanguagePreference.System

    private fun String?.toDriveSyncConnectionStatus(): DriveSyncConnectionStatus =
        DriveSyncConnectionStatus.entries.firstOrNull { it.name == this } ?: DriveSyncConnectionStatus.NotConfigured

    private fun String?.toDriveBackupDeletionState(): DriveBackupDeletionState =
        DriveBackupDeletionState.entries.firstOrNull { it.name == this } ?: DriveBackupDeletionState.None

    private companion object {
        val languageKey = stringPreferencesKey("language")
        val developerModeUnlockedKey = booleanPreferencesKey("developer_mode_unlocked")
        val developerModeEnabledKey = booleanPreferencesKey("developer_mode_enabled")
        val driveSyncConnectionStatusKey = stringPreferencesKey("drive_sync_connection_status")
        val cloudSetupPromptInteractedKey = booleanPreferencesKey("cloud_setup_prompt_interacted")
        val driveFreshInstallRestoreAttemptedKey = booleanPreferencesKey("drive_fresh_install_restore_attempted")
        val driveBackupDeletionStateKey = stringPreferencesKey("drive_backup_deletion_state")
        val modelPromptShownKey = booleanPreferencesKey("model_prompt_shown")
        val modelMobileDataApprovedKey = booleanPreferencesKey("model_mobile_data_approved")
        val deviceIdKey = stringPreferencesKey("sync_device_id")
        val lastKnownRemoteRevisionKey = longPreferencesKey("sync_last_known_remote_revision")
        val syncConflictKey = booleanPreferencesKey("sync_conflict")
    }
}
