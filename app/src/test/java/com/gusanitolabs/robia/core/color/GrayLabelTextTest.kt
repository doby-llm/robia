package com.gusanitolabs.robia.core.color

import com.gusanitolabs.robia.core.model.DisplayColorLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class GrayLabelTextTest {
    @Test
    fun colorGrayStringReadsGrayInEnglishSpanishAndGerman() {
        assertEquals("Gray", colorGrayText("values"))
        assertEquals("Gris", colorGrayText("values-es"))
        assertEquals("Grau", colorGrayText("values-de"))
    }

    @Test
    fun colorGrayStringIsNeverBeige() {
        listOf("values", "values-es", "values-de").forEach { resource ->
            assertNotEquals("Beige", colorGrayText(resource))
        }
    }

    @Test
    fun midGreyAndGreyNameResolveToGrayLabel() {
        assertEquals(DisplayColorLabel.Gray, ColorLabelResolver.fromRawValue("#8E8E8E"))
        assertEquals(DisplayColorLabel.Gray, ColorLabelResolver.fromRawValue("grey"))
    }

    private fun colorGrayText(resourceDirectory: String): String {
        val text = source("app/src/main/res/$resourceDirectory/strings.xml")
        val match = Regex("""<string name="color_gray">([^<]*)</string>""").find(text)
        return requireNotNull(match) { "color_gray missing in $resourceDirectory" }.groupValues[1]
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of("..").resolve(relativePath),
        )
        return candidates.first { Files.exists(it) }.toFile().readText()
    }
}
