package com.gusanitolabs.robia.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch edit route opens the editor with the draft's AI marks, and the plain edit route opens it with none (BS1).
 * The editor's reset block applies the marks, so the sparkle shows as it does in single add.
 */
class BatchAiMarksSourceContractTest {
    private val app = File("src/main/java/com/gusanitolabs/robia/ui/RobiaApp.kt").readText()
    private val addEdit = File("src/main/java/com/gusanitolabs/robia/ui/AddEditClothingScreen.kt").readText()

    @Test
    fun batchEditRoutePassesTheDraftsAiMarks() {
        val route = app.substringAfter("RobiaRoute.BatchEditClothing -> selectedBatchDraft?.let { draft ->")
            .substringBefore("} ?: BatchAddClothingScreen(")

        assertTrue(route.contains("initialAiMarks = draft.toAiMarks(),"))
    }

    @Test
    fun plainEditRouteOpensTheEditorWithoutAiMarks() {
        val route = app.substringAfter("RobiaRoute.AddEditClothing -> AddEditClothingScreen(")
            .substringBefore("RobiaRoute.BatchAddClothing ->")

        assertFalse(route.contains("initialAiMarks"))
    }

    @Test
    fun editorResetBlockAppliesTheInitialAiMarks() {
        val reset = addEdit.substringAfter("LaunchedEffect(existingItem?.id, initialPhotoReviewState, initialAiMarks) {")
            .substringBefore("captureStatus = initialPhotoReviewState")

        assertTrue(reset.contains("modelOwnedTagIds = initialAiMarks?.modelOwnedTagIds.orEmpty()"))
        assertTrue(reset.contains("modelPrimaryColorGeneration = photoAnalysisGeneration.takeIf { initialAiMarks?.primaryColor == true }"))
        assertTrue(reset.contains("modelSecondaryColorGeneration = photoAnalysisGeneration.takeIf { initialAiMarks?.secondaryColor == true }"))
    }
}
