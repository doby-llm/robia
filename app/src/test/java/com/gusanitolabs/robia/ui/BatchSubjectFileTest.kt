package com.gusanitolabs.robia.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch subject file (`subject-*.png`) is deleted only after the garment save succeeds, or with the draft when it is
 * discarded or cancelled (R3-DEC-033, B2). cutoutStage touches Android Uri and files, so the stage is pinned in source.
 */
class BatchSubjectFileTest {
    @Test
    fun discardingADraftDeletesItsSubjectFile() {
        val draft = BatchDraftItem(
            orderIndex = 0,
            originalPhotoUri = "content://raw",
            photoUri = "content://cropped",
            originalUri = "content://original",
            subjectUri = "content://subject",
        )

        assertEquals(listOf("content://raw", "content://cropped", "content://original", "content://subject"), draft.tempFileUris())
    }

    @Test
    fun cutoutStageDoesNotDeleteTheSubjectBeforeTheSave() {
        val source = File("src/main/java/com/gusanitolabs/robia/ui/BatchAddClothingScreen.kt").readText()
        val stage = source.substringAfter("internal suspend fun cutoutStage(")
            .substringBefore("/** Deletes the files a stage made")

        assertFalse(stage.contains("finally"))
        // Registered for release on save, next to the raw copy.
        assertTrue(stage.contains("listOfNotNull(sourceUri.toString(), subjectFile?.toString())"))
        // Deleted only on the paths that end without a Ready result.
        assertTrue(stage.substringAfter("} catch (throwable: CancellationException) {").contains("deleteStageFiles(context, keptOriginal, subjectFile)"))
    }
}
