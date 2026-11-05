package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.ColorPalette
import java.text.Collator
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

/** V-TH2-3 (REQ-TH-7): the palette rows follow the app locale's collation, not the order the palettes are declared in. */
class PaletteOrderTest {
    @Test
    fun englishLabelsSortEarthSteelFirst() {
        val labels = linkedMapOf(
            ColorPalette.TobaccoBordeaux to "Tobacco & Bordeaux",
            ColorPalette.EarthSteel to "Earth & Steel",
        )

        assertEquals(
            listOf(ColorPalette.EarthSteel, ColorPalette.TobaccoBordeaux),
            sortedPalettes(labels, Collator.getInstance(Locale.ENGLISH)),
        )
    }

    @Test
    fun spanishLabelsSortTobacoBeforeTierra() {
        val labels = linkedMapOf(
            ColorPalette.EarthSteel to "Tierra y acero",
            ColorPalette.TobaccoBordeaux to "Tabaco y burdeos",
        )

        assertEquals(
            listOf(ColorPalette.TobaccoBordeaux, ColorPalette.EarthSteel),
            sortedPalettes(labels, Collator.getInstance(Locale.forLanguageTag("es"))),
        )
    }

    @Test
    fun germanLabelsSortErdeBeforeTabak() {
        val labels = linkedMapOf(
            ColorPalette.TobaccoBordeaux to "Tabak & Bordeaux",
            ColorPalette.EarthSteel to "Erde & Stahl",
        )

        assertEquals(
            listOf(ColorPalette.EarthSteel, ColorPalette.TobaccoBordeaux),
            sortedPalettes(labels, Collator.getInstance(Locale.GERMAN)),
        )
    }
}
