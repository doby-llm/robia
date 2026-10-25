package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DarkThemeBrowseScanTest {
    @Test
    fun browseDetailAddEditBatchAndTagFilesMarkEveryColourLiteral() {
        val violations = ThemeSourceScan.scan(
            listOf(
                "app/src/main/java/com/gusanitolabs/robia/ui/BrowseGrid.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/BrowseFilterBar.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/AdvancedFiltersScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/ItemDetailScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/GarmentFieldRows.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/ManageTagsScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/PhotoProcessingVisuals.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/SuggestionsProgressBanner.kt",
            ),
        )

        assertEquals(emptyList<String>(), violations)
    }
}
