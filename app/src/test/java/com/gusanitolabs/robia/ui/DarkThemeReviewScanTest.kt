package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** V-REV-CARD-3 (R3-DEC-052): every colour literal in the review screen and its card files says why it stays the same. */
class DarkThemeReviewScanTest {
    @Test
    fun reviewFilesMarkEveryColourLiteral() {
        val violations = ThemeSourceScan.scan(
            listOf(
                "app/src/main/java/com/gusanitolabs/robia/ui/TagReviewScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/ColorReviewScreen.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/ReviewScaffold.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/ReviewChangeChips.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/TagReviewCard.kt",
                "app/src/main/java/com/gusanitolabs/robia/ui/TagReviewCardModel.kt",
            ),
        )

        assertEquals(emptyList<String>(), violations)
    }
}
