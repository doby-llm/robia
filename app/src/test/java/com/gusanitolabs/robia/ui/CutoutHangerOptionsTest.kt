package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.CutoutEngineChoice
import org.junit.Assert.assertEquals
import org.junit.Test

/** V-SG4-1 (Settings rule): hanger options are live only with a ready pack and the Automatic method. */
class CutoutHangerOptionsTest {
    @Test
    fun hangerOptions_needReadyPackAndAutomatic() {
        assertEquals(true, cutoutHangerOptionsAvailable(packReady = true, engine = CutoutEngineChoice.Automatic))
        assertEquals(false, cutoutHangerOptionsAvailable(packReady = false, engine = CutoutEngineChoice.Automatic))
        assertEquals(false, cutoutHangerOptionsAvailable(packReady = true, engine = CutoutEngineChoice.Standard))
        assertEquals(false, cutoutHangerOptionsAvailable(packReady = false, engine = CutoutEngineChoice.Standard))
    }
}
