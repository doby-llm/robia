package com.gusanitolabs.robia.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.gusanitolabs.robia.core.model.LanguagePreference
import com.gusanitolabs.robia.core.model.RobiaSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SettingsRepository {
    val settings: Flow<RobiaSettings>
    suspend fun setLanguagePreference(languagePreference: LanguagePreference)
    suspend fun setDeveloperModeUnlocked(unlocked: Boolean)
    suspend fun setDeveloperModeEnabled(enabled: Boolean)
    suspend fun markCloudSetupPromptInteracted()
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
            cloudSetupPromptInteracted = preferences[cloudSetupPromptInteractedKey] ?: false,
        )
    }

    override suspend fun setLanguagePreference(languagePreference: LanguagePreference) {
        dataStore.edit { preferences ->
            val storageValue = languagePreference.storageValue
            if (storageValue == null) {
                preferences.remove(languageKey)
            } else {
                preferences[languageKey] = storageValue
            }
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

    override suspend fun markCloudSetupPromptInteracted() {
        dataStore.edit { preferences ->
            preferences[cloudSetupPromptInteractedKey] = true
        }
    }

    private fun String?.toLanguagePreference(): LanguagePreference =
        LanguagePreference.entries.firstOrNull { it.storageValue == this } ?: LanguagePreference.System

    private companion object {
        val languageKey = stringPreferencesKey("language")
        val developerModeUnlockedKey = booleanPreferencesKey("developer_mode_unlocked")
        val developerModeEnabledKey = booleanPreferencesKey("developer_mode_enabled")
        val cloudSetupPromptInteractedKey = booleanPreferencesKey("cloud_setup_prompt_interacted")
    }
}
