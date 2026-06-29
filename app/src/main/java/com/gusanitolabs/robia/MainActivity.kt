package com.gusanitolabs.robia

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.datastore.preferences.preferencesDataStore
import com.gusanitolabs.robia.data.DataStoreSettingsRepository
import com.gusanitolabs.robia.data.LocalTagRepository
import com.gusanitolabs.robia.data.LocalWardrobeRepository
import com.gusanitolabs.robia.data.local.RobiaDatabase
import com.gusanitolabs.robia.ui.RobiaApp

private val Context.settingsDataStore by preferencesDataStore(name = "robia_settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val database = RobiaDatabase.getInstance(applicationContext)
        val settingsRepository = DataStoreSettingsRepository(settingsDataStore)
        val wardrobeRepository = LocalWardrobeRepository(database.wardrobeDao())
        val tagRepository = LocalTagRepository(database.tagDao(), database.syncTombstoneDao())

        setContent {
            RobiaApp(
                settingsRepository = settingsRepository,
                wardrobeRepository = wardrobeRepository,
                tagRepository = tagRepository,
            )
        }
    }
}
