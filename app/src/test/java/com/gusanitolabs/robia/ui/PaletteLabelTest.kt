package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.model.ColorPalette
import org.junit.Assert.assertEquals
import org.junit.Test

/** V-TH2-1 (REQ-TH-7): every palette names its own string, so the dialog never shows two palettes under one label. */
class PaletteLabelTest {
    @Test
    fun everyPaletteMapsToADistinctLabelResource() {
        val labels = ColorPalette.entries.map(::paletteLabelRes)

        assertEquals(ColorPalette.entries.size, labels.distinct().size)
        assertEquals(R.string.palette_earth_steel, paletteLabelRes(ColorPalette.EarthSteel))
        assertEquals(R.string.palette_tobacco_bordeaux, paletteLabelRes(ColorPalette.TobaccoBordeaux))
    }
}
