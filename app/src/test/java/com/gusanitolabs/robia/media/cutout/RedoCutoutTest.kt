package com.gusanitolabs.robia.media.cutout

import com.gusanitolabs.robia.core.model.CutoutEdges
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
    fun preciseChoicesAreOfferedOnlyWithTheReadyPackAndAWorkingEngine() {
        assertEquals(listOf(RedoCutoutChoice.Standard), redoCutoutChoices(packReady = false, preciseTripped = false))
        assertEquals(listOf(RedoCutoutChoice.Standard), redoCutoutChoices(packReady = true, preciseTripped = true))
        assertEquals(RedoCutoutChoice.entries, redoCutoutChoices(packReady = true, preciseTripped = false))
    }

    // V-SG4-3: the hanger gate with fake stages. The stages record their calls; none of them touches a model.
    private val selection = FloatArray(16) { 0.5f }
    private val hanger = MaskBox(1, 0, 3, 1)
    private val kill = BooleanArray(16) { it < 4 }
    private val reselected = FloatArray(16) { 0.9f }

    @Test
    fun hangerRemovalOff_returnsTheSelectionUntouched_andNoStageRuns() {
        var stageCalls = 0

        val result = applyHangerGate(
            removeHanger = false,
            threshold = HANGER_THRESHOLD_HIGH,
            selection = selection,
            detect = { stageCalls++; hanger },
            kill = { stageCalls++; kill },
            reselect = { _, _ -> stageCalls++; reselected },
        )

        assertSame(selection, result)
        assertEquals(0, stageCalls)
    }

    @Test
    fun hangerRemovalOn_detectsWithTheThreshold_thenKillsAndReselects() {
        var detectedWith = -1f
        var killedHanger: MaskBox? = null
        var reselectedFrom: BooleanArray? = null

        val result = applyHangerGate(
            removeHanger = true,
            threshold = HANGER_THRESHOLD_NORMAL,
            selection = selection,
            detect = { threshold -> detectedWith = threshold; hanger },
            kill = { found -> killedHanger = found; kill },
            reselect = { found, mask -> reselectedFrom = mask; assertSame(hanger, found); reselected },
        )

        assertEquals(HANGER_THRESHOLD_NORMAL, detectedWith, 0f)
        assertSame(hanger, killedHanger)
        assertSame(kill, reselectedFrom)
        assertSame(reselected, result)
    }

    @Test
    fun hangerRemovalOn_withNoHanger_keepsTheSelection_andSkipsKillAndReselect() {
        var killCalls = 0
        var reselectCalls = 0

        val result = applyHangerGate(
            removeHanger = true,
            threshold = HANGER_THRESHOLD_HIGH,
            selection = selection,
            detect = { null },
            kill = { killCalls++; kill },
            reselect = { _, _ -> reselectCalls++; reselected },
        )

        assertSame(selection, result)
        assertEquals(0, killCalls)
        assertEquals(0, reselectCalls)
    }

    @Test
    fun hangerRemovalOn_withNoKillMask_keepsTheSelection_andSkipsReselect() {
        var reselectCalls = 0

        val result = applyHangerGate(
            removeHanger = true,
            threshold = HANGER_THRESHOLD_HIGH,
            selection = selection,
            detect = { hanger },
            kill = { null },
            reselect = { _, _ -> reselectCalls++; reselected },
        )

        assertSame(selection, result)
        assertEquals(0, reselectCalls)
    }
}
