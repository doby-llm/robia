package com.gusanitolabs.robia.ui

import com.gusanitolabs.robia.core.model.CutoutEngineChoice
import org.junit.Assert.assertEquals
import org.junit.Test

/** V-SG4-1 (Settings rule), V-FB3-CUT-NAMES-2: hanger options are live only with a ready pack and a method other than Standard. */
class CutoutHangerOptionsTest {
    @Test
    fun hangerOptions_needReadyPackAndAMethodOtherThanStandard() {
        assertEquals(true, cutoutHangerOptionsAvailable(packReady = true, engine = CutoutEngineChoice.Automatic))
        assertEquals(true, cutoutHangerOptionsAvailable(packReady = true, engine = CutoutEngineChoice.Precise))
        assertEquals(false, cutoutHangerOptionsAvailable(packReady = false, engine = CutoutEngineChoice.Automatic))
        assertEquals(false, cutoutHangerOptionsAvailable(packReady = false, engine = CutoutEngineChoice.Precise))
        assertEquals(false, cutoutHangerOptionsAvailable(packReady = true, engine = CutoutEngineChoice.Standard))
        assertEquals(false, cutoutHangerOptionsAvailable(packReady = false, engine = CutoutEngineChoice.Standard))
    }
}
