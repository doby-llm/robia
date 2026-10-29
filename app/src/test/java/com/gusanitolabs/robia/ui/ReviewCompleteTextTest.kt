package com.gusanitolabs.robia.ui

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ReviewCompleteTextTest {
    @Test
    fun countOfChangedGarmentsIsShownWithItsKind() {
        assertEquals(ReviewCompleteText(ReviewCompleteKind.Tags, 5), reviewCompleteText(ReviewCompleteKind.Tags, 5))
        assertEquals(ReviewCompleteText(ReviewCompleteKind.Colors, 1), reviewCompleteText(ReviewCompleteKind.Colors, 1))
    }

    @Test
    fun noChangedGarmentsMeansNoCompletionScreen() {
        assertNull(reviewCompleteText(ReviewCompleteKind.Tags, 0))
        assertNull(reviewCompleteText(ReviewCompleteKind.Colors, 0))
        assertNull(reviewCompleteText(ReviewCompleteKind.Colors, -1))
    }

    @Test
    fun screenLeavesAfterOneAndSixTenthsSecondsOrOneAndTwoTenthsWithReducedMotion() {
        assertEquals(1600L, reviewCompleteDurationMs(reducedMotion = false))
        assertEquals(1200L, reviewCompleteDurationMs(reducedMotion = true))
    }

    // V-FB3-DUP-DONE-2: no summary for zero removed duplicates (Not duplicates only); a count for one or more.
    @Test
    fun duplicateReviewShowsItsRemovedCountAndNothingForZero() {
        assertNull(reviewCompleteText(ReviewCompleteKind.Duplicates, 0))
        assertNotNull(reviewCompleteText(ReviewCompleteKind.Duplicates, 3))
        assertEquals(ReviewCompleteText(ReviewCompleteKind.Duplicates, 1), reviewCompleteText(ReviewCompleteKind.Duplicates, 1))
    }

    // V-FB3-DUP-DONE-2: the plural exists in EN, ES and DE with the brief's one and other forms.
    @Test
    fun duplicatesPluralExistsInEveryLocaleWithTheBriefsWording() {
        assertEquals(
            mapOf("one" to "%1\$d duplicate removed", "other" to "%1\$d duplicates removed"),
            duplicatesPlural(resource("app/src/main/res/values/strings.xml")),
        )
        assertEquals(
            mapOf("one" to "%1\$d duplicado eliminado", "other" to "%1\$d duplicados eliminados"),
            duplicatesPlural(resource("app/src/main/res/values-es/strings.xml")),
        )
        assertEquals(
            mapOf("one" to "%1\$d Duplikat entfernt", "other" to "%1\$d Duplikate entfernt"),
            duplicatesPlural(resource("app/src/main/res/values-de/strings.xml")),
        )
    }

    private fun duplicatesPlural(text: String): Map<String, String> {
        val block = requireNotNull(
            Regex("""<plurals name="review_complete_duplicates">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).find(text),
        ) { "review_complete_duplicates missing" }
        return Regex("""<item quantity="(\w+)">([^<]*)</item>""").findAll(block.groupValues[1])
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    private fun resource(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
