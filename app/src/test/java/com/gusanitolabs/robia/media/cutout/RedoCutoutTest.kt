package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.core.model.CutoutEdges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** SG4 Redo cutout: the one-off options of each choice, the choices offered, and the hanger stage gate (V-SG4-3 seam). */
class RedoCutoutTest {
    private val current = CutoutOptions(
        usePrecise = true,
        removeHanger = true,
        hangerThreshold = HANGER_THRESHOLD_HIGH,
        edges = CutoutEdges.Sharp,
    )

    @Test
    fun standardRunsMlKitAndKeepsTheCurrentThresholdAndEdges() {
        val options = redoCutoutOptions(RedoCutoutChoice.Standard, current)

        assertFalse(options.usePrecise)
        assertEquals(HANGER_THRESHOLD_HIGH, options.hangerThreshold)
        assertEquals(CutoutEdges.Sharp, options.edges)
    }

    @Test
    fun preciseRemovesHangersWhateverTheSettingIs() {
        val options = redoCutoutOptions(RedoCutoutChoice.Precise, current.copy(removeHanger = false))

        assertTrue(options.usePrecise)
        assertTrue(options.removeHanger)
        assertEquals(CutoutEdges.Sharp, options.edges)
    }

    @Test
    fun preciseWithoutHangerRemovalTurnsTheHangerStagesOff() {
        val options = redoCutoutOptions(RedoCutoutChoice.PreciseKeepHanger, current)

        assertTrue(options.usePrecise)
        assertFalse(options.removeHanger)
        assertEquals(HANGER_THRESHOLD_HIGH, options.hangerThreshold)
    }

    @Test
    fun preciseChoicesAreOfferedOnlyWithTheReadyPack() {
        assertEquals(listOf(RedoCutoutChoice.Standard), redoCutoutChoices(packReady = false))
        assertEquals(RedoCutoutChoice.entries, redoCutoutChoices(packReady = true))
    }

    @Test
    fun hangerStagesRunOnlyWhenHangerRemovalIsOn() {
        assertTrue(hangerStagesRun(current))
        assertFalse(hangerStagesRun(current.copy(removeHanger = false)))
    }
}
