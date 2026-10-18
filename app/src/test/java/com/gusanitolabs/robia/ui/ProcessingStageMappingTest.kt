package com.gusanitolabs.robia.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingStageMappingTest {
    @Test
    fun processingStepMapsEveryStageToTheTs7Step() {
        assertEquals(1, processingStep(PhotoProcessingStage.RemovingBackground))
        assertEquals(2, processingStep(PhotoProcessingStage.CroppingPicture))
        assertEquals(3, processingStep(PhotoProcessingStage.ExtractingColor))
        assertEquals(3, processingStep(PhotoProcessingStage.PreparingSuggestions))
        assertEquals(2, processingStep(PhotoProcessingStage.ApplyingQuickEdit))
    }

    @Test
    fun everyStageMapsIntoTheThreeSegments() {
        PhotoProcessingStage.entries.forEach { stage ->
            assertTrue("$stage", processingStep(stage) in 1..3)
        }
    }
}
