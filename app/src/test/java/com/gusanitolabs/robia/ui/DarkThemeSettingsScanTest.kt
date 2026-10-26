package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkThemeSettingsScanTest {
    @Test
    fun settingsSyncAndDuplicateFilesMarkEveryColourLiteral() {
        val violations = ThemeSourceScan.scan(
            listOf(
                "app/src/main/java/com/gusanitolabs/robia/ui/SettingsScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/DriveSettingsScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/PreciseCutoutSettings.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/LanguagePickerScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/ModelDownloadUi.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/SyncAttentionCard.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/SuggestionDiagnosticsCard.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/DuplicateCalibrationScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/DuplicateReviewScreen.kt",
            ),
        )

        assertEquals(emptyList<String>(), violations)
    }
}
