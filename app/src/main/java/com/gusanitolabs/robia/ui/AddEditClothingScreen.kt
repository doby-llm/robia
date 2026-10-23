package com.gusanitolabs.robia.ui

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.color.PaletteColorClassifier
import com.gusanitolabs.robia.core.color.PaletteColorMatch
import com.gusanitolabs.robia.core.color.RgbColor
import com.gusanitolabs.robia.core.designsystem.PopIn
import com.gusanitolabs.robia.core.designsystem.SparkleBurst
import com.gusanitolabs.robia.core.designsystem.sparkleBurstAfterProcessing
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.media.BackgroundRemovalFailureReason
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.ImagePurpose
import com.gusanitolabs.robia.media.EditorBackgroundRemovalStatus
import com.gusanitolabs.robia.media.EditorPhotoState
import com.gusanitolabs.robia.media.PhotoBackgroundRemover
import com.gusanitolabs.robia.media.QuickEditImageProcessor
import com.gusanitolabs.robia.media.SegmentationModuleGates
import com.gusanitolabs.robia.suggestions.SuggestionCapture
import com.gusanitolabs.robia.suggestions.SuggestionOutcome
import com.gusanitolabs.robia.suggestions.SuggestionService
import com.gusanitolabs.robia.suggestions.diagnosticLines
import com.gusanitolabs.robia.suggestions.engine.ImageInputEncoder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

private const val EDITOR_PREVIEW_MAX_EDGE_PX = 512

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditClothingScreen(
    innerPadding: PaddingValues,
    availableTags: List<GarmentTag>,
    mainColors: List<MainColor>,
    existingItem: ClothingItem?,
    developerModeEnabled: Boolean,
    initialPhotoReviewState: AddEditPhotoReviewState? = null,
    titleRes: Int? = null,
    bodyRes: Int = R.string.add_edit_body,
    saveButtonTextRes: Int = R.string.save_item,
    suggestionService: SuggestionService,
    onCancel: () -> Unit,
    onSave: (ClothingItem, SuggestionCapture?) -> Unit,
    onDelete: ((ClothingItem) -> Unit)? = null,
    onBatchPhotosSelected: ((List<String>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val backgroundRemover = remember { PhotoBackgroundRemover() }
    val latestMainColors by rememberUpdatedState(mainColors)
    val latestAvailableTags by rememberUpdatedState(availableTags)
    var name by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var originalPhotoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var photoUri by rememberSaveable { mutableStateOf<String?>(null) }
    var editorPhotoState by remember { mutableStateOf(EditorPhotoState()) }
    var photoAspectRatio by rememberSaveable { mutableStateOf<Float?>(null) }
    var selectedTagIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var fitValue by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedPrimaryColorId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSecondaryColorId by rememberSaveable { mutableStateOf<String?>(null) }
    var captureStatus by rememberSaveable { mutableStateOf("") }
    var backgroundFallbackModuleMissing by rememberSaveable { mutableStateOf(false) }
    var colorPickerTarget by rememberSaveable { mutableStateOf<ColorPickerTarget?>(null) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var isPhotoProcessing by rememberSaveable { mutableStateOf(false) }
    var photoProcessingStage by rememberSaveable { mutableStateOf<PhotoProcessingStage?>(null) }
    var photoRetrySource by remember { mutableStateOf<PendingPhotoInput?>(null) }
    var developerDiagnostics by remember { mutableStateOf<List<String>>(emptyList()) }
    var developerExportStatus by remember { mutableStateOf<String?>(null) }
    var suggestionCapture by remember { mutableStateOf<SuggestionCapture?>(null) }
    var suggestionSourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showQuickEdit by rememberSaveable { mutableStateOf(false) }
    var quickEditStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var photoAnalysisGeneration by rememberSaveable { mutableStateOf(0L) }
    var modelOwnedTagIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var userOverriddenTagCategories by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var modelPrimaryColorGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var modelSecondaryColorGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var userPrimaryColorOverrideGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var userSecondaryColorOverrideGeneration by rememberSaveable { mutableStateOf<Long?>(null) }
    var photoProcessingToken by remember { mutableStateOf(0) }
    var nextPhotoInputId by remember { mutableStateOf(0L) }
    var pendingPhotoInput by remember { mutableStateOf<PendingPhotoInput?>(null) }
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val coroutineScope = rememberCoroutineScope()

    val primaryPaletteColor = mainColors.colorForId(selectedPrimaryColorId)
    val secondaryPaletteColor = mainColors.colorForId(selectedSecondaryColorId)
    val primaryRawColor = primaryPaletteColor?.hex.orEmpty()
    val secondaryRawColor = secondaryPaletteColor?.hex.orEmpty()
    val primaryLabel = remember(primaryRawColor) { ColorLabelResolver.fromRawValue(primaryRawColor) }
    val secondaryLabel = remember(secondaryRawColor) { ColorLabelResolver.fromRawValue(secondaryRawColor) }

    fun beginPhotoAnalysisGeneration(availableTagsSnapshot: List<GarmentTag>): Long {
        val nextGeneration = photoAnalysisGeneration + 1
        photoAnalysisGeneration = nextGeneration
        val tagsById = availableTagsSnapshot.associateBy(GarmentTag::id)
        val modelOwnedIds = modelOwnedTagIds.toSet()
        selectedTagIds = selectedTagIds.filterNot { tagId ->
            tagId in modelOwnedIds && tagsById[tagId]?.categoryId in MODEL_PREDICTED_CATEGORIES
        }
        modelOwnedTagIds = emptyList()
        userOverriddenTagCategories = emptyList()
        if (modelPrimaryColorGeneration != null) {
            selectedPrimaryColorId = null
        }
        if (modelSecondaryColorGeneration != null) {
            selectedSecondaryColorId = null
        }
        modelPrimaryColorGeneration = null
        modelSecondaryColorGeneration = null
        userPrimaryColorOverrideGeneration = null
        userSecondaryColorOverrideGeneration = null
        return nextGeneration
    }

    fun applyExtractedColors(generation: Long, colors: List<PaletteColorMatch>) {
        val primaryColorId = colors.getOrNull(0)?.color?.id
        if (userPrimaryColorOverrideGeneration != generation) {
            selectedPrimaryColorId = primaryColorId
            modelPrimaryColorGeneration = primaryColorId?.let { generation }
        }
        if (userSecondaryColorOverrideGeneration != generation) {
            selectedSecondaryColorId = colors
                .drop(1)
                .firstOrNull { match ->
                    match.ratio >= SECONDARY_COLOR_MIN_RATIO && match.color.id != primaryColorId
                }
                ?.color
                ?.id
            modelSecondaryColorGeneration = selectedSecondaryColorId?.let { generation }
        }
    }

    fun applyPredictedTags(predictedTagIds: Set<String>, availableTagsSnapshot: List<GarmentTag>): PredictedTagReconciliation {
        val result = reconcilePredictedTags(
            currentTagIds = selectedTagIds,
            currentModelOwnedTagIds = modelOwnedTagIds,
            predictedTagIds = predictedTagIds,
            availableTags = availableTagsSnapshot,
            userOverriddenCategories = userOverriddenTagCategories,
        )
        selectedTagIds = result.selectedTagIds
        modelOwnedTagIds = result.modelOwnedTagIds
        return result
    }

    /** Keeps the capture vector for save and marks the predicted tags. Unavailable leaves everything untouched. */
    fun applySuggestionOutcome(outcome: SuggestionOutcome, uriString: String) {
        suggestionCapture = null
        if (outcome !is SuggestionOutcome.Ready) return
        suggestionCapture = SuggestionCapture(uriString, outcome.imageVector, outcome.imageInputHash, outcome.durationMs)
        applyPredictedTags(outcome.predictedTagIds, latestAvailableTags)
    }

    fun selectedCategoryIdForSuggestions(): String? =
        latestAvailableTags.firstOrNull { tag -> tag.categoryId == "category" && tag.id in selectedTagIds }?.id

    fun updateUserSelectedTagIds(newTagIds: List<String>) {
        val tagsById = latestAvailableTags.associateBy(GarmentTag::id)
        val oldIds = selectedTagIds.toSet()
        val newIds = newTagIds.toSet()
        val touchedCategories = (oldIds + newIds)
            .mapNotNull { tagId -> tagsById[tagId]?.categoryId }
            .filter { categoryId -> categoryId in MODEL_PREDICTED_CATEGORIES }
            .filter { categoryId ->
                oldIds.filter { tagsById[it]?.categoryId == categoryId }.toSet() !=
                    newIds.filter { tagsById[it]?.categoryId == categoryId }.toSet()
            }
            .toSet()
        selectedTagIds = newTagIds
        if (touchedCategories.isNotEmpty()) {
            userOverriddenTagCategories = (userOverriddenTagCategories + touchedCategories).distinct()
            modelOwnedTagIds = modelOwnedTagIds.filterNot { tagId -> tagsById[tagId]?.categoryId in touchedCategories }
        }
    }

    fun queuePhotoForProcessing(uriString: String, sourceStatus: String) {
        val inputId = ++nextPhotoInputId
        photoProcessingToken += 1 // Immediately invalidate any older in-flight pipeline.
        val generation = beginPhotoAnalysisGeneration(latestAvailableTags)
        originalPhotoUri = uriString
        photoUri = uriString
        editorPhotoState = EditorPhotoState(
            rawSourceUri = uriString,
            currentEditedUri = uriString,
            backgroundRemovalStatus = EditorBackgroundRemovalStatus.Queued,
            processingEventId = inputId,
            processingToken = photoProcessingToken,
        )
        captureStatus = sourceStatus
        isPhotoProcessing = true
        photoProcessingStage = PhotoProcessingStage.RemovingBackground
        suggestionSourceUri = null
        suggestionCapture = null
        quickEditStatus = null
        photoRetrySource = PendingPhotoInput(
            id = inputId,
            uriString = uriString,
            sourceStatus = sourceStatus,
        )
        developerDiagnostics = listOf(
            "Photo processing queued",
            "source=$sourceStatus",
            "uri=$uriString",
            "event=$inputId",
            "analysisGeneration=$generation",
            "cleared stale model-owned tags/colors for new image content",
        )
        pendingPhotoInput = PendingPhotoInput(
            id = inputId,
            uriString = uriString,
            sourceStatus = sourceStatus,
        )
    }

    suspend fun processSelectedPhoto(inputId: Long, uriString: String, sourceStatus: String) {
        val token = ++photoProcessingToken
        val generation = photoAnalysisGeneration
        originalPhotoUri = uriString
        photoUri = uriString
        editorPhotoState = EditorPhotoState(
            rawSourceUri = uriString,
            currentEditedUri = uriString,
            backgroundRemovalStatus = EditorBackgroundRemovalStatus.RemovingBackground,
            processingEventId = inputId,
            processingToken = token,
        )
        captureStatus = sourceStatus
        isPhotoProcessing = true
        photoProcessingStage = PhotoProcessingStage.RemovingBackground
        suggestionSourceUri = null
        suggestionCapture = null
        quickEditStatus = null
        developerDiagnostics = listOf(
            "Photo processing started",
            "source=$sourceStatus",
            "uri=$uriString",
        )

        val startedAt = SystemClock.elapsedRealtime()
        val diagnostics = mutableListOf(
            "Source: $sourceStatus",
            "Input URI: $uriString",
            "Pipeline event: $inputId",
            "Palette colors available at start: ${latestMainColors.size}",
            "Tags available at start: ${latestAvailableTags.size}",
            "Analysis generation: $generation",
        )

        fun elapsed(): Long = SystemClock.elapsedRealtime() - startedAt
        fun addLine(line: String) {
            diagnostics += "${elapsed()}ms  $line"
        }

        try {
                val aspectStart = SystemClock.elapsedRealtime()
                val sourceAspectRatio = withContext(Dispatchers.IO) {
                    ClothingImageStore.readImageAspectRatio(context, Uri.parse(uriString))
                }
                photoAspectRatio = sourceAspectRatio?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO)
                addLine("Source aspect ratio: ${sourceAspectRatio?.formatDebugFloat() ?: "unknown"} (${SystemClock.elapsedRealtime() - aspectStart}ms)")

                val backgroundStart = SystemClock.elapsedRealtime()
                val result = backgroundRemover.removeBackground(context, Uri.parse(uriString))
                addLine("Background removal: status=${result.status}, usedFallback=${result.usedFallback}, output=${result.outputUri}")
                editorPhotoState = editorPhotoState.copy(
                    foregroundUri = result.outputUri.toString(),
                    currentEditedUri = result.outputUri.toString(),
                    backgroundRemovalStatus = if (result.usedFallback) {
                        EditorBackgroundRemovalStatus.OriginalFallback
                    } else {
                        EditorBackgroundRemovalStatus.ForegroundReady
                    },
                    processingEventId = inputId,
                    processingToken = token,
                )
                result.failure?.let { failure ->
                    addLine("Background failure: reason=${failure.reason}, cause=${failure.causeClass ?: "n/a"}, message=${failure.message ?: "n/a"}")
                }
                addLine("Background removal duration: ${SystemClock.elapsedRealtime() - backgroundStart}ms")
                if (token != photoProcessingToken) return

                photoProcessingStage = PhotoProcessingStage.CroppingPicture
                val cropStart = SystemClock.elapsedRealtime()
                val croppedResult = runCatching {
                    withContext(Dispatchers.IO) {
                        ClothingImageStore.cropTransparentPixels(context, result.outputUri)
                    }
                }
                val croppedUri = croppedResult.getOrDefault(result.outputUri)
                addLine("Crop: success=${croppedResult.isSuccess}, uri=$croppedUri, duration=${SystemClock.elapsedRealtime() - cropStart}ms")
                croppedResult.exceptionOrNull()?.let { throwable ->
                    addLine("Crop failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
                }
                if (token != photoProcessingToken) return

                val finalAspectRatio = withContext(Dispatchers.IO) {
                    ClothingImageStore.readImageAspectRatio(context, croppedUri)
                }
                photoAspectRatio = finalAspectRatio?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO) ?: photoAspectRatio
                addLine("Display aspect ratio: ${finalAspectRatio?.formatDebugFloat() ?: "unknown"}")
                val displayUri = croppedUri.toString()
                photoUri = displayUri
                val finalStatus = if (result.usedFallback || croppedResult.isFailure) {
                    PhotoStatus.BackgroundFallback
                } else {
                    PhotoStatus.BackgroundRemoved
                }
                captureStatus = finalStatus
                backgroundFallbackModuleMissing = result.failure?.reason == BackgroundRemovalFailureReason.MODEL_UNAVAILABLE
                editorPhotoState = editorPhotoState.copy(
                    croppedForegroundUri = displayUri,
                    currentEditedUri = displayUri,
                    backgroundRemovalStatus = if (finalStatus == PhotoStatus.BackgroundFallback) {
                        EditorBackgroundRemovalStatus.OriginalFallback
                    } else {
                        EditorBackgroundRemovalStatus.CroppedForegroundReady
                    },
                    processingEventId = inputId,
                    processingToken = token,
                )
                addLine("Final photo status: $finalStatus")
                if (finalStatus != PhotoStatus.BackgroundFallback) {
                    photoRetrySource = null
                }

                photoProcessingStage = PhotoProcessingStage.ExtractingColor
                val colorStart = SystemClock.elapsedRealtime()
                val colorPalette = latestMainColors
                val colorResult = runCatching {
                    withContext(Dispatchers.IO) {
                        ClothingImageStore.extractPaletteColorDiagnostics(context, croppedUri, colorPalette)
                    }
                }
                val colorDiagnostics = colorResult.getOrNull()
                val extracted = colorDiagnostics?.matches.orEmpty()
                addLine("Color extraction: success=${colorResult.isSuccess}, matches=${extracted.size}, duration=${SystemClock.elapsedRealtime() - colorStart}ms")
                colorDiagnostics?.let { stats ->
                    addLine("Color stats: bitmap=${stats.width ?: "unknown"}x${stats.height ?: "unknown"}, sampleStep=${stats.sampleStep ?: "n/a"}, estimatedSamples=${stats.sampleGridEstimate ?: "n/a"}, paletteSize=${stats.paletteSize}")
                }
                colorResult.exceptionOrNull()?.let { throwable ->
                    addLine("Color extraction failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
                }
                extracted.forEachIndexed { index, match ->
                    addLine("Color[$index]: ${match.color.name} ${match.color.hex}, pixels=${match.pixelCount}, ratio=${match.ratio.formatDebugFloat()}")
                }
                if (token != photoProcessingToken) return
                if (extracted.isNotEmpty()) {
                    applyExtractedColors(generation, extracted)
                }

                photoProcessingStage = PhotoProcessingStage.PreparingSuggestions
                val suggestion = suggestionService.suggestForPhoto(
                    photoUri = Uri.parse(displayUri),
                    tags = latestAvailableTags,
                    selectedCategoryId = selectedCategoryIdForSuggestions(),
                    excludeGarmentId = existingItem?.id,
                )
                if (token != photoProcessingToken) return
                suggestion.diagnosticLines().forEach { line -> addLine(line) }
                applySuggestionOutcome(suggestion, displayUri)
        } catch (throwable: CancellationException) {
            addLine("Pipeline cancelled because a newer photo input superseded this one")
            throw throwable
        } catch (throwable: Exception) {
                if (token != photoProcessingToken) return
                photoUri = uriString
                captureStatus = PhotoStatus.BackgroundFallback
                backgroundFallbackModuleMissing = false
                editorPhotoState = EditorPhotoState(
                    rawSourceUri = uriString,
                    currentEditedUri = uriString,
                    backgroundRemovalStatus = EditorBackgroundRemovalStatus.OriginalFallback,
                    processingEventId = inputId,
                    processingToken = token,
                )
                photoRetrySource = PendingPhotoInput(
                    id = inputId,
                    uriString = uriString,
                    sourceStatus = sourceStatus,
                )
                addLine("Pipeline failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
        } finally {
            if (token == photoProcessingToken) {
                addLine("Total duration: ${elapsed()}ms")
                developerDiagnostics = diagnostics
                isPhotoProcessing = false
                photoProcessingStage = null
            }
        }
    }

    suspend fun reprocessEditedPhoto(editedUri: Uri) {
        val token = ++photoProcessingToken
        val generation = beginPhotoAnalysisGeneration(latestAvailableTags)
        val startedAt = SystemClock.elapsedRealtime()
        val diagnostics = mutableListOf(
            "Quick Edit save started",
            "Edited URI: $editedUri",
            "Palette colors available at start: ${latestMainColors.size}",
            "Tags available at start: ${latestAvailableTags.size}",
            "Analysis generation: $generation",
        )

        fun elapsed(): Long = SystemClock.elapsedRealtime() - startedAt
        fun addLine(line: String) {
            diagnostics += "${elapsed()}ms  $line"
        }

        try {
            photoUri = editedUri.toString()
            captureStatus = PhotoStatus.QuickEdited
            editorPhotoState = editorPhotoState.copy(
                currentEditedUri = editedUri.toString(),
                backgroundRemovalStatus = EditorBackgroundRemovalStatus.QuickEdited,
                processingToken = token,
            )
            isPhotoProcessing = true
            quickEditStatus = null
            photoProcessingStage = PhotoProcessingStage.CroppingPicture
            val croppedResult = runCatching {
                withContext(Dispatchers.IO) { ClothingImageStore.cropTransparentPixels(context, editedUri) }
            }
            val croppedUri = croppedResult.getOrDefault(editedUri)
            photoUri = croppedUri.toString()
            editorPhotoState = editorPhotoState.copy(
                croppedForegroundUri = croppedUri.toString(),
                currentEditedUri = croppedUri.toString(),
                backgroundRemovalStatus = EditorBackgroundRemovalStatus.QuickEdited,
                processingToken = token,
            )
            addLine("Quick Edit crop: success=${croppedResult.isSuccess}, uri=$croppedUri")
            if (token != photoProcessingToken) return

            val finalAspectRatio = withContext(Dispatchers.IO) { ClothingImageStore.readImageAspectRatio(context, croppedUri) }
            photoAspectRatio = finalAspectRatio?.coerceIn(PHOTO_PREVIEW_MIN_ASPECT_RATIO, PHOTO_PREVIEW_MAX_ASPECT_RATIO) ?: photoAspectRatio

            photoProcessingStage = PhotoProcessingStage.ExtractingColor
            val colorResult = runCatching {
                withContext(Dispatchers.IO) { ClothingImageStore.extractPaletteColorDiagnostics(context, croppedUri, latestMainColors) }
            }
            val colorDiagnostics = colorResult.getOrNull()
            val extracted = colorDiagnostics?.matches.orEmpty()
            addLine("Quick Edit color extraction: success=${colorResult.isSuccess}, matches=${extracted.size}")
            colorDiagnostics?.let { stats ->
                addLine("Color stats: bitmap=${stats.width ?: "unknown"}x${stats.height ?: "unknown"}, sampleStep=${stats.sampleStep ?: "n/a"}, estimatedSamples=${stats.sampleGridEstimate ?: "n/a"}, paletteSize=${stats.paletteSize}")
            }
            colorResult.exceptionOrNull()?.let { throwable ->
                addLine("Color extraction failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
            }
            if (token != photoProcessingToken) return
            if (extracted.isNotEmpty()) applyExtractedColors(generation, extracted)

            photoProcessingStage = PhotoProcessingStage.PreparingSuggestions
            val suggestion = suggestionService.suggestForPhoto(
                photoUri = croppedUri,
                tags = latestAvailableTags,
                selectedCategoryId = selectedCategoryIdForSuggestions(),
                excludeGarmentId = existingItem?.id,
            )
            if (token != photoProcessingToken) return
            suggestion.diagnosticLines().forEach { line -> addLine(line) }
            applySuggestionOutcome(suggestion, croppedUri.toString())

            captureStatus = PhotoStatus.QuickEdited
            quickEditStatus = context.getString(R.string.quick_edit_saved_status)
        } catch (throwable: CancellationException) {
            addLine("Quick Edit re-analysis cancelled because a newer photo input superseded this one")
            throw throwable
        } catch (throwable: Exception) {
            if (token != photoProcessingToken) return
            addLine("Quick Edit re-analysis failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
            quickEditStatus = context.getString(R.string.quick_edit_save_failed)
        } finally {
            if (token == photoProcessingToken) {
                addLine("Total duration: ${elapsed()}ms")
                developerDiagnostics = diagnostics
                isPhotoProcessing = false
                photoProcessingStage = null
            }
        }
    }

    LaunchedEffect(Unit) {
        suggestionService.preload()
    }

    LaunchedEffect(pendingPhotoInput) {
        val input = pendingPhotoInput ?: return@LaunchedEffect
        processSelectedPhoto(input.id, input.uriString, input.sourceStatus)
    }

    LaunchedEffect(existingItem?.id, initialPhotoReviewState) {
        name = existingItem?.name.orEmpty()
        notes = existingItem?.notes.orEmpty()
        originalPhotoUri = existingItem?.photoUri
        photoUri = existingItem?.photoUri
        editorPhotoState = EditorPhotoState(
            rawSourceUri = existingItem?.photoUri,
            croppedForegroundUri = existingItem?.photoUri,
            currentEditedUri = existingItem?.photoUri,
            suggestionSourceUri = existingItem?.photoUri,
            backgroundRemovalStatus = if (existingItem?.photoUri.isNullOrBlank()) {
                EditorBackgroundRemovalStatus.None
            } else {
                EditorBackgroundRemovalStatus.CroppedForegroundReady
            },
        )
        photoAspectRatio = null
        selectedTagIds = existingItem?.tags?.map(GarmentTag::id).orEmpty()
        fitValue = existingItem?.fitValue ?: if (existingItem == null) FIT_VALUE_FITS else null
        selectedPrimaryColorId = existingItem?.colorMetrics?.primaryPaletteColorId
            ?: mainColors.nearestColor(existingItem?.colorMetrics?.primaryPaletteColorHex ?: existingItem?.colorMetrics?.primaryRawValue)?.id
        selectedSecondaryColorId = existingItem?.colorMetrics?.secondaryPaletteColorId
            ?: mainColors.nearestColor(existingItem?.colorMetrics?.secondaryPaletteColorHex ?: existingItem?.colorMetrics?.secondaryRawValue)?.id
        modelOwnedTagIds = emptyList()
        userOverriddenTagCategories = emptyList()
        modelPrimaryColorGeneration = null
        modelSecondaryColorGeneration = null
        userPrimaryColorOverrideGeneration = null
        userSecondaryColorOverrideGeneration = null
        captureStatus = initialPhotoReviewState?.captureStatus.orEmpty()
        photoRetrySource = initialPhotoReviewState?.let { reviewState ->
            PendingPhotoInput(
                id = ++nextPhotoInputId,
                uriString = reviewState.retrySourceUri,
                sourceStatus = reviewState.retrySourceStatus,
            )
        }
        developerDiagnostics = emptyList()
        suggestionSourceUri = existingItem?.photoUri
        suggestionCapture = null
    }

    val isEditing = existingItem != null
    val initialFitValue = existingItem?.fitValue ?: if (existingItem == null) FIT_VALUE_FITS else null
    val canonicalPhotoUri = editorPhotoState.canonicalEditorUri ?: photoUri
    val hasUsablePhoto = !canonicalPhotoUri.isNullOrBlank()
    val canSaveItem = hasUsablePhoto && !isPhotoProcessing
    val saveHelperTextRes = when {
        isPhotoProcessing -> R.string.item_save_photo_processing_helper
        canSaveItem -> R.string.item_save_ready_helper
        else -> R.string.item_save_photo_required_helper
    }
    val hasUnsavedChanges = name != existingItem?.name.orEmpty() ||
        notes != existingItem?.notes.orEmpty() ||
        canonicalPhotoUri != existingItem?.photoUri ||
        selectedTagIds != existingItem?.tags?.map(GarmentTag::id).orEmpty() ||
        fitValue != initialFitValue ||
        selectedPrimaryColorId != existingItem?.colorMetrics?.primaryPaletteColorId ||
        selectedSecondaryColorId != existingItem?.colorMetrics?.secondaryPaletteColorId

    fun exportSuggestionInputImage() {
        val source = canonicalPhotoUri ?: return
        coroutineScope.launch {
            val saved = runCatching {
                val encoded = ImageInputEncoder.encode(context, Uri.parse(source))
                withContext(Dispatchers.IO) { saveSuggestionInputPng(context, encoded.bytes) }
            }
            developerExportStatus = context.getString(
                if (saved.isSuccess) R.string.developer_export_suggestion_input_saved else R.string.developer_export_suggestion_input_error,
            )
        }
    }

    fun requestClose() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onCancel()
        }
    }

    BackHandler { requestClose() }

    if (showQuickEdit && !canonicalPhotoUri.isNullOrBlank()) {
        PhotoEditorScreen(
            photoUri = canonicalPhotoUri.orEmpty(),
            rawSourceUri = redoSourceUri(editorPhotoState.rawSourceUri, existingItem?.photoUri),
            onCancel = { showQuickEdit = false },
            onDiagnostic = { line -> developerDiagnostics = developerDiagnostics + line },
            onDone = { cutoutUri, adjustments ->
                coroutineScope.launch {
                    showQuickEdit = false
                    isPhotoProcessing = true
                    photoProcessingStage = PhotoProcessingStage.ApplyingQuickEdit
                    val editedResult = runCatching {
                        withContext(Dispatchers.IO) {
                            QuickEditImageProcessor.applyAdjustments(
                                context,
                                cutoutUri ?: Uri.parse(canonicalPhotoUri.orEmpty()),
                                adjustments,
                            )
                        }
                    }
                    editedResult.fold(
                        onSuccess = { editedUri -> reprocessEditedPhoto(editedUri) },
                        onFailure = { throwable ->
                            quickEditStatus = context.getString(R.string.quick_edit_save_failed)
                            developerDiagnostics = developerDiagnostics + "Quick Edit apply failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}"
                            isPhotoProcessing = false
                            photoProcessingStage = null
                        },
                    )
                }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.unsaved_changes_title)) },
            text = { Text(stringResource(R.string.unsaved_changes_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onCancel()
                    },
                ) { Text(stringResource(R.string.discard_changes)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }

    if (showDeleteDialog && existingItem != null && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_item_title)) },
            text = { Text(stringResource(R.string.delete_item_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(existingItem)
                    },
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    colorPickerTarget?.let { target ->
        ColorPalettePickerDialog(
            title = stringResource(if (target == ColorPickerTarget.Primary) R.string.primary_color else R.string.secondary_color),
            colors = mainColors,
            allowNoColor = target == ColorPickerTarget.Secondary,
            onColorSelected = { color ->
                if (target == ColorPickerTarget.Primary) {
                    selectedPrimaryColorId = color?.id
                    modelPrimaryColorGeneration = null
                    userPrimaryColorOverrideGeneration = photoAnalysisGeneration
                } else {
                    selectedSecondaryColorId = color?.id
                    modelSecondaryColorGeneration = null
                    userSecondaryColorOverrideGeneration = photoAnalysisGeneration
                }
                colorPickerTarget = null
            },
            onDismiss = { colorPickerTarget = null },
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(titleRes ?: if (isEditing) R.string.edit_clothing_title else R.string.add_edit_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(bodyRes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            if (activityResultRegistryOwner != null) {
                PhotoCaptureCardWithLaunchers(
                    photoUri = canonicalPhotoUri,
                    photoAspectRatio = photoAspectRatio,
                    captureStatus = captureStatus,
                    backgroundFallbackModuleMissing = backgroundFallbackModuleMissing,
                    isPhotoProcessing = isPhotoProcessing,
                    processingStage = photoProcessingStage,
                    rawSourceUri = editorPhotoState.rawSourceUri,
                    foregroundUri = editorPhotoState.foregroundUri,
                    primaryColor = primaryPaletteColor,
                    secondaryColor = secondaryPaletteColor,
                    onCaptureStatusChange = { captureStatus = it },
                    onPhotoSelected = ::queuePhotoForProcessing,
                    onRetryPhotoProcessing = photoRetrySource?.let { retryInput ->
                        { queuePhotoForProcessing(retryInput.uriString, retryInput.sourceStatus) }
                    },
                    onQuickEditClick = if (hasUsablePhoto && !isPhotoProcessing) ({ showQuickEdit = true }) else null,
                    quickEditStatus = quickEditStatus,
                    onBatchPhotosSelected = onBatchPhotosSelected,
                )
            } else {
                PhotoCaptureCard(
                    photoUri = canonicalPhotoUri,
                    photoAspectRatio = photoAspectRatio,
                    captureStatus = PhotoStatus.MediaUnavailable,
                    isPhotoProcessing = false,
                    processingStage = null,
                    onRetryPhotoProcessing = null,
                    onQuickEditClick = null,
                    quickEditStatus = null,
                    onGalleryClick = null,
                    onCameraClick = null,
                )
            }
        }

        item {
            Text(
                text = stringResource(saveHelperTextRes),
                style = MaterialTheme.typography.bodySmall,
                color = if (canSaveItem) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            GarmentFieldRowsCard(
                availableTags = availableTags,
                selectedTagIds = selectedTagIds,
                modelOwnedTagIds = modelOwnedTagIds,
                fitValue = fitValue,
                primaryColor = primaryPaletteColor,
                secondaryColor = secondaryPaletteColor,
                colorsAiSuggested = isColorAiSuggested(modelPrimaryColorGeneration, modelSecondaryColorGeneration),
                onSelectedTagIdsChange = { updateUserSelectedTagIds(it) },
                onFitValueChange = { fitValue = it },
                onColorTargetPick = { target -> colorPickerTarget = target },
            )
        }

        item {
            CardSection(title = stringResource(R.string.item_details_section)) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.item_notes_label)) },
                    placeholder = { Text(stringResource(R.string.item_notes_placeholder)) },
                    minLines = 3,
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (existingItem != null && onDelete != null) {
                    val deleteLabel = stringResource(R.string.delete)
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .width(64.dp)
                            .semantics { contentDescription = deleteLabel },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                } else {
                    TextButton(
                        onClick = ::requestClose,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cancel))
                    }
                }
                Button(
                    enabled = canSaveItem,
                    onClick = {
                        val now = System.currentTimeMillis()
                        val selectedTags = availableTags.filter { it.id in selectedTagIds }
                        onSave(
                            ClothingItem(
                                id = existingItem?.id ?: UUID.randomUUID().toString(),
                                name = existingItem?.name.orEmpty(),
                                notes = notes,
                                photoUri = canonicalPhotoUri,
                                tags = selectedTags,
                                fitValue = fitValue,
                                colorMetrics = ClothingColorMetrics(
                                    primaryRawValue = primaryPaletteColor?.hex,
                                    primaryDisplayLabel = primaryLabel.takeIf { primaryPaletteColor != null },
                                    primaryPaletteColorId = primaryPaletteColor?.id,
                                    primaryPaletteColorName = primaryPaletteColor?.name,
                                    primaryPaletteColorHex = primaryPaletteColor?.hex,
                                    secondaryRawValue = secondaryPaletteColor?.hex,
                                    secondaryDisplayLabel = secondaryLabel.takeIf { secondaryPaletteColor != null },
                                    secondaryPaletteColorId = secondaryPaletteColor?.id,
                                    secondaryPaletteColorName = secondaryPaletteColor?.name,
                                    secondaryPaletteColorHex = secondaryPaletteColor?.hex,
                                ),
                                isFavorite = existingItem?.isFavorite ?: false,
                                isArchived = existingItem?.isArchived ?: false,
                                createdAtEpochMillis = existingItem?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = now,
                            ),
                            suggestionCapture,
                        )
                    },
                    modifier = Modifier.weight(2f),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(saveButtonTextRes))
                }
            }
        }

        if (developerModeEnabled) {
            item {
                DeveloperDiagnosticsSection(
                    lines = developerDiagnostics.ifEmpty {
                        listOf(stringResource(R.string.developer_diagnostics_empty))
                    },
                    exportStatus = developerExportStatus,
                    exportEnabled = hasUsablePhoto && !isPhotoProcessing,
                    onExportInputImage = ::exportSuggestionInputImage,
                )
            }
        }
    }
}

@Composable
private fun DeveloperDiagnosticsSection(
    lines: List<String>,
    exportStatus: String?,
    exportEnabled: Boolean,
    onExportInputImage: () -> Unit,
) {
    CardSection(title = stringResource(R.string.developer_diagnostics_title)) {
        OutlinedButton(onClick = onExportInputImage, enabled = exportEnabled) {
            Icon(Icons.Rounded.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.developer_export_suggestion_input))
        }
        exportStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = lines.joinToString(separator = "\n"),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Float.formatDebugFloat(): String = String.format(Locale.US, "%.4f", this)

@Composable
private fun GarmentFieldRowsCard(
    availableTags: List<GarmentTag>,
    selectedTagIds: List<String>,
    modelOwnedTagIds: List<String>,
    fitValue: Int?,
    primaryColor: MainColor?,
    secondaryColor: MainColor?,
    colorsAiSuggested: Boolean,
    onSelectedTagIdsChange: (List<String>) -> Unit,
    onFitValueChange: (Int?) -> Unit,
    onColorTargetPick: (ColorPickerTarget) -> Unit,
) {
    val context = LocalContext.current
    val collator = remember { browseCollator() }
    val none = stringResource(R.string.none)
    val tagsById = remember(availableTags) { availableTags.associateBy(GarmentTag::id) }
    var openCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var showColorTargetChooser by rememberSaveable { mutableStateOf(false) }

    fun selectedIn(categoryId: String): List<String> =
        selectedTagIds.filter { tagId -> tagsById[tagId]?.categoryId == categoryId }

    fun isSuggested(categoryId: String): Boolean = isAiSuggested(selectedIn(categoryId), modelOwnedTagIds)

    fun valueIn(categoryId: String): String = fieldValueText(
        selected = availableTags.filter { tag -> tag.categoryId == categoryId && tag.id in selectedTagIds },
        collator = collator,
        none = none,
    ) { tag -> tag.localizedTagLabel(context) }

    val fits = fitValue.indicatesFits()
    val colorValue = colorFieldValueText(primary = primaryColor?.name, secondary = secondaryColor?.name, none = none)
    val colorSwatches = listOfNotNull(primaryColor?.hex?.toComposeColor(), secondaryColor?.hex?.toComposeColor())

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            GarmentFieldRow(
                icon = categoryIconFor("category"),
                label = stringResource(R.string.metadata_category),
                value = valueIn("category"),
                aiSuggested = isSuggested("category"),
                onClick = { openCategoryId = "category" },
            )
            GarmentFieldRow(
                icon = categoryIconFor("occasion"),
                label = stringResource(R.string.metadata_occasions),
                value = valueIn("occasion"),
                aiSuggested = isSuggested("occasion"),
                onClick = { openCategoryId = "occasion" },
            )
            GarmentFieldRow(
                icon = categoryIconFor("season"),
                label = stringResource(R.string.metadata_season),
                value = valueIn("season"),
                aiSuggested = isSuggested("season"),
                onClick = { openCategoryId = "season" },
            )
            GarmentFieldRow(
                icon = Icons.Outlined.Palette,
                label = stringResource(R.string.colors_section),
                value = colorValue,
                aiSuggested = colorsAiSuggested,
                onClick = { showColorTargetChooser = true },
                swatches = colorSwatches,
            )
            GarmentFieldRow(
                icon = categoryIconFor("location"),
                label = stringResource(R.string.metadata_location),
                value = valueIn("location"),
                aiSuggested = isSuggested("location"),
                onClick = { openCategoryId = "location" },
            )
            GarmentFieldRow(
                icon = Icons.Rounded.Straighten,
                label = stringResource(R.string.metadata_fit),
                value = "",
                aiSuggested = false,
                onClick = { onFitValueChange(if (fits) FIT_VALUE_DOES_NOT_FIT else FIT_VALUE_FITS) },
                trailing = { Switch(checked = fits, onCheckedChange = null) },
            )
        }
    }

    openCategoryId?.let { categoryId ->
        TagPickerSheet(
            title = stringResource(
                when (categoryId) {
                    "category" -> R.string.choose_category
                    "location" -> R.string.choose_location
                    "occasion" -> R.string.metadata_occasions
                    else -> R.string.metadata_season
                },
            ),
            tags = availableTags.forCategory(categoryId),
            selectedIds = selectedTagIds,
            multi = categoryId == "occasion" || categoryId == "season",
            suggestedIds = modelOwnedTagIds.toSet(),
            onSelectedIdsChange = onSelectedTagIdsChange,
            onDismiss = { openCategoryId = null },
        )
    }

    if (showColorTargetChooser) {
        ColorTargetChooserDialog(
            primaryLabel = stringResource(
                R.string.metadata_selection_content_description,
                stringResource(R.string.primary_color),
                primaryColor?.name ?: none,
            ),
            secondaryLabel = stringResource(
                R.string.metadata_selection_content_description,
                stringResource(R.string.secondary_color),
                secondaryColor?.name ?: none,
            ),
            onPick = { target ->
                showColorTargetChooser = false
                onColorTargetPick(target)
            },
            onDismiss = { showColorTargetChooser = false },
        )
    }
}

@Composable
private fun ColorTargetChooserDialog(
    primaryLabel: String,
    secondaryLabel: String,
    onPick: (ColorPickerTarget) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.colors_section)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onPick(ColorPickerTarget.Primary) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(primaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { onPick(ColorPickerTarget.Secondary) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun List<GarmentTag>.forCategory(categoryId: String): List<GarmentTag> {
    val context = LocalContext.current
    return filter { tag -> tag.categoryId == categoryId }
        .sortedAlphabetically(label = { tag -> tag.localizedTagLabel(context) })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClothingDetailScreen(
    innerPadding: PaddingValues,
    item: ClothingItem?,
    onEditClick: () -> Unit,
    onAddClick: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (item == null) {
            item { EmptyDetailCard(onAddClick = onAddClick) }
        } else {
            item {
                PhotoPreview(photoUri = item.photoUri, modifier = Modifier.fillMaxWidth())
            }
            item {
                CardSection(title = stringResource(R.string.item_detail_title)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (item.notes.isNotBlank()) {
                                Text(
                                    text = item.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        AssistChip(
                            onClick = onEditClick,
                            label = { Text(stringResource(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        )
                    }
                }
            }
            item {
                CardSection(title = stringResource(R.string.colors_section)) {
                    ColorSummaryRow(
                        title = stringResource(R.string.primary_color),
                        rawValue = item.colorMetrics.primaryRawValue,
                        label = item.colorMetrics.primaryDisplayLabel ?: DisplayColorLabel.Unknown,
                    )
                    ColorSummaryRow(
                        title = stringResource(R.string.secondary_color),
                        rawValue = item.colorMetrics.secondaryRawValue,
                        label = item.colorMetrics.secondaryDisplayLabel ?: DisplayColorLabel.Unknown,
                    )
                }
            }
            item {
                CardSection(title = stringResource(R.string.tags_section)) {
                    if (item.tags.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_tags_selected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        FlowChipRow {
                            item.tags.forEach { tag -> DetailTonalTag(text = tag.localizedLabel()) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCaptureCardWithLaunchers(
    photoUri: String?,
    photoAspectRatio: Float?,
    captureStatus: String,
    backgroundFallbackModuleMissing: Boolean = false,
    isPhotoProcessing: Boolean,
    processingStage: PhotoProcessingStage?,
    rawSourceUri: String? = null,
    foregroundUri: String? = null,
    primaryColor: MainColor? = null,
    secondaryColor: MainColor? = null,
    onCaptureStatusChange: (String) -> Unit,
    onPhotoSelected: (String, String) -> Unit,
    onRetryPhotoProcessing: (() -> Unit)?,
    onQuickEditClick: (() -> Unit)?,
    quickEditStatus: String?,
    onBatchPhotosSelected: ((List<String>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingCameraUri by rememberSaveable { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            onCaptureStatusChange("")
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val storedUris = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching { ClothingImageStore.copyContentUriToPrivateStorage(context, uri).toString() }.getOrNull()
                }
            }
            when {
                storedUris.size > 1 && onBatchPhotosSelected != null -> onBatchPhotosSelected(storedUris)
                storedUris.isNotEmpty() -> onPhotoSelected(storedUris.first(), PhotoStatus.Gallery)
                else -> onCaptureStatusChange(PhotoStatus.MediaUnavailable)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { captured ->
        val capturedUri = pendingCameraUri
        if (captured && capturedUri != null) {
            onPhotoSelected(capturedUri, PhotoStatus.Camera)
        } else {
            onCaptureStatusChange("")
        }
        pendingCameraUri = null
    }

    PhotoCaptureCard(
        photoUri = photoUri,
        photoAspectRatio = photoAspectRatio,
        captureStatus = captureStatus,
        backgroundFallbackModuleMissing = backgroundFallbackModuleMissing,
        isPhotoProcessing = isPhotoProcessing,
        processingStage = processingStage,
        rawSourceUri = rawSourceUri,
        foregroundUri = foregroundUri,
        primaryColor = primaryColor,
        secondaryColor = secondaryColor,
        onRetryPhotoProcessing = onRetryPhotoProcessing,
        onQuickEditClick = onQuickEditClick,
        quickEditStatus = quickEditStatus,
        onGalleryClick = {
            runCatching { galleryLauncher.launch(arrayOf("image/*")) }
                .onFailure { onCaptureStatusChange(PhotoStatus.MediaUnavailable) }
        },
        onCameraClick = {
            runCatching {
                val cameraUri = ClothingImageStore.createCaptureUri(context)
                pendingCameraUri = cameraUri.toString()
                cameraLauncher.launch(cameraUri)
            }.onFailure {
                pendingCameraUri = null
                onCaptureStatusChange(PhotoStatus.MediaUnavailable)
            }
        },
    )
}

/** Edit photo exists only once a usable photo does; while one is processing it stays shown, disabled by the caller. */
internal fun quickEditButtonVisible(hasUsablePhoto: Boolean): Boolean = hasUsablePhoto

@Composable
private fun PhotoCaptureCard(
    photoUri: String?,
    photoAspectRatio: Float?,
    captureStatus: String,
    backgroundFallbackModuleMissing: Boolean = false,
    isPhotoProcessing: Boolean,
    processingStage: PhotoProcessingStage?,
    rawSourceUri: String? = null,
    foregroundUri: String? = null,
    primaryColor: MainColor? = null,
    secondaryColor: MainColor? = null,
    onRetryPhotoProcessing: (() -> Unit)?,
    onQuickEditClick: (() -> Unit)?,
    quickEditStatus: String?,
    onGalleryClick: (() -> Unit)?,
    onCameraClick: (() -> Unit)?,
) {
    val quickEditVisible = quickEditButtonVisible(hasUsablePhoto = !photoUri.isNullOrBlank())
    var sparkleBurstPending by remember { mutableStateOf(false) }
    var wasProcessing by remember { mutableStateOf(isPhotoProcessing) }
    LaunchedEffect(isPhotoProcessing) {
        if (sparkleBurstAfterProcessing(wasProcessing, isPhotoProcessing, quickEditVisible)) sparkleBurstPending = true
        wasProcessing = isPhotoProcessing
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val backgroundPreparing by SegmentationModuleGates.get(LocalContext.current).preparing.collectAsState()
            val processingLabel = if (isPhotoProcessing && backgroundPreparing) {
                stringResource(R.string.background_removal_preparing)
            } else {
                null
            }
            PhotoPreview(
                photoUri = photoUri,
                isProcessing = isPhotoProcessing,
                processingStep = processingStage?.let(::processingStep),
                processingLabel = processingLabel,
                rawSourceUri = rawSourceUri,
                foregroundUri = foregroundUri,
                primaryColor = primaryColor,
                secondaryColor = secondaryColor,
                modifier = Modifier.fillMaxWidth(),
                aspectRatio = photoAspectRatio ?: 4f / 3f,
                onEmptyPhotoClick = onCameraClick,
                onQuickEditClick = onQuickEditClick,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onGalleryClick?.invoke() }, enabled = onGalleryClick != null) {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = stringResource(R.string.content_open_gallery),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.gallery))
                    }
                    if (quickEditVisible) {
                        Box {
                            FilledTonalButton(
                                onClick = { onQuickEditClick?.invoke() },
                                enabled = onQuickEditClick != null && !isPhotoProcessing,
                            ) {
                                Text(stringResource(R.string.edit_photo_action))
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            if (sparkleBurstPending) {
                                SparkleBurst(
                                    modifier = Modifier.matchParentSize(),
                                    onFinished = { sparkleBurstPending = false },
                                )
                            }
                        }
                    }
                }
                Button(
                    onClick = { onCameraClick?.invoke() },
                    enabled = onCameraClick != null,
                    shape = CircleShape,
                ) {
                    Icon(
                        Icons.Rounded.PhotoCamera,
                        contentDescription = stringResource(R.string.content_open_camera),
                    )
                }
            }
            val statusText = when (captureStatus) {
                PhotoStatus.Gallery -> R.string.gallery_selected_status
                PhotoStatus.Camera -> R.string.camera_selected_status
                PhotoStatus.Processed -> R.string.photo_processed_status
                PhotoStatus.BackgroundRemoved -> R.string.background_removal_success_status
                PhotoStatus.BackgroundFallback -> if (backgroundFallbackModuleMissing) {
                    R.string.background_removal_fallback_module
                } else {
                    R.string.background_removal_fallback_status
                }
                PhotoStatus.QuickEdited -> R.string.quick_edit_saved_status
                PhotoStatus.MediaUnavailable -> R.string.media_actions_unavailable_status
                else -> null
            }
            if (statusText != null) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(statusText),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    quickEditStatus?.let { status ->
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    if (captureStatus == PhotoStatus.BackgroundFallback && onRetryPhotoProcessing != null) {
                        OutlinedButton(
                            onClick = onRetryPhotoProcessing,
                            enabled = !isPhotoProcessing,
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.retry_photo_cleanup))
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun PhotoPreview(
    photoUri: String?,
    isProcessing: Boolean = false,
    processingStep: Int? = null,
    processingLabel: String? = null,
    rawSourceUri: String? = null,
    foregroundUri: String? = null,
    primaryColor: MainColor? = null,
    secondaryColor: MainColor? = null,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 3f / 4f,
    onEmptyPhotoClick: (() -> Unit)? = null,
    onQuickEditClick: (() -> Unit)? = null,
) {
    val uri = photoUri?.takeIf { it.isNotBlank() }
    val emptyPhotoContentDescription = stringResource(R.string.content_open_camera)
    val quickEditContentDescription = stringResource(R.string.content_quick_edit_photo)
    // The shimmer only runs while processing; once the foreground exists the raw photo fades behind it.
    val shimmerForeground = foregroundUri?.takeIf { isProcessing && it.isNotBlank() }
    val rawAlpha = rememberRawPhotoAlpha(fadeRaw = shimmerForeground != null)
    val swatches = listOfNotNull(primaryColor, secondaryColor).map { it.swatchColor() }
    val swatchesVisible = photoSwatchesVisible(isProcessing, hasPhoto = uri != null, swatchCount = swatches.size)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .then(
                    if (uri == null && onEmptyPhotoClick != null) {
                        Modifier
                            .clickable(onClick = onEmptyPhotoClick)
                            .semantics { contentDescription = emptyPhotoContentDescription }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (uri != null) {
                BoundedGarmentImage(
                    photoUri = if (shimmerForeground != null) rawSourceUri?.takeIf { it.isNotBlank() } ?: uri else uri,
                    thumbnailMaxEdgePx = EDITOR_PREVIEW_MAX_EDGE_PX,
                    purpose = ImagePurpose.Editor,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = rawAlpha },
                )
                if (shimmerForeground != null) {
                    MaskedShimmer(foregroundUri = shimmerForeground, modifier = Modifier.fillMaxSize())
                } else if (isProcessing) {
                    SweepOverlay(modifier = Modifier.fillMaxSize())
                }
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    PopIn(visible = swatchesVisible) {
                        PhotoColorSwatches(colors = swatches)
                    }
                }
                if (!isProcessing && onQuickEditClick != null) {
                    IconButton(
                        onClick = onQuickEditClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.88f))
                            .semantics { contentDescription = quickEditContentDescription },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Style,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        text = stringResource(R.string.photo_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (onEmptyPhotoClick != null) {
                        Text(
                            text = stringResource(R.string.photo_placeholder_tap_camera),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (isProcessing && processingStep != null) {
            SegmentedProgress(
                current = processingStep,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            ProcessingStageLabel(
                step = processingStep,
                label = processingLabel,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun CardSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            content()
        }
    }
}

@Composable
private fun ColorPalettePickerDialog(
    title: String,
    colors: List<MainColor>,
    allowNoColor: Boolean,
    onColorSelected: (MainColor?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            if (colors.isEmpty() && !allowNoColor) {
                Text(stringResource(R.string.palette_empty))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (allowNoColor) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onColorSelected(null) }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                }
                                Text(stringResource(R.string.no_color), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    items(colors.size) { index ->
                        val color = colors[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onColorSelected(color) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ColorSwatch(rawColor = color.hex)
                            Column {
                                Text(color.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(color.hex, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun ColorSummaryRow(
    title: String,
    rawValue: String?,
    label: DisplayColorLabel,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ColorSwatch(rawColor = rawValue.orEmpty())
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.raw_color_summary, rawValue ?: stringResource(R.string.none), stringResource(label.stringRes)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorSwatch(rawColor: String) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(rawColor.toComposeColor() ?: MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
        if (rawColor.toComposeColor() == null) {
            Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun EmptyDetailCard(onAddClick: () -> Unit) {
    CardSection(title = stringResource(R.string.item_detail_title)) {
        Text(
            text = stringResource(R.string.item_detail_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AssistChip(
            onClick = onAddClick,
            label = { Text(stringResource(R.string.add_clothing)) },
            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        )
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FlowChipRow(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        content()
    }
}

@Composable
private fun DetailTonalTag(text: String) {
    androidx.compose.material3.Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun GarmentTag.localizedLabel(): String = localizedTagLabel()

private enum class ColorPickerTarget { Primary, Secondary }

private data class PendingPhotoInput(
    val id: Long,
    val uriString: String,
    val sourceStatus: String,
)

internal enum class PhotoProcessingStage(val labelRes: Int) {
    RemovingBackground(R.string.processing_stage_removing_background),
    CroppingPicture(R.string.processing_stage_cropping_picture),
    ExtractingColor(R.string.processing_stage_extracting_color),
    PreparingSuggestions(R.string.processing_stage_preparing_suggestions),
    ApplyingQuickEdit(R.string.processing_stage_applying_quick_edit),
}

internal data class PredictedTagReconciliation(
    val selectedTagIds: List<String>,
    val modelOwnedTagIds: List<String>,
    val skippedCategories: Set<String>,
)

/** Writes the exact encoder bytes to Pictures/Robia (developer export, TS§3.12). */
private fun saveSuggestionInputPng(context: Context, png: ByteArray): Uri {
    val values = ContentValues().apply {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        put(MediaStore.Images.Media.DISPLAY_NAME, "robia-suggestion-input-$stamp.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Robia")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val resolver = context.contentResolver
    val target = resolver.insert(collection, values) ?: error("MediaStore insert failed")
    try {
        resolver.openOutputStream(target)?.use { output -> output.write(png) } ?: error("Output stream unavailable")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(target, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
    } catch (failure: Exception) {
        resolver.delete(target, null, null)
        throw failure
    }
    return target
}

internal fun reconcilePredictedTags(
    currentTagIds: List<String>,
    currentModelOwnedTagIds: List<String>,
    predictedTagIds: Set<String>,
    availableTags: List<GarmentTag>,
    userOverriddenCategories: List<String>,
): PredictedTagReconciliation {
    val tagsById = availableTags.associateBy(GarmentTag::id)
    val userOverridden = userOverriddenCategories.toSet()
    val predictedByCategory = MODEL_PREDICTED_CATEGORIES.associateWith { categoryId ->
        predictedTagIds.filter { tagId -> tagsById[tagId]?.categoryId == categoryId }
    }
    val categoriesWithPredictions = predictedByCategory
        .filterValues { tagIds -> tagIds.isNotEmpty() }
        .keys
    val replacedCategories = MODEL_PREDICTED_CATEGORIES - userOverridden
    val modelOwnedIds = currentModelOwnedTagIds.toSet()
    val selected = currentTagIds.filterNot { tagId ->
        val categoryId = tagsById[tagId]?.categoryId
        // A category is replaced only when this photo predicts one, so a low-margin photo keeps the chosen category.
        categoryId in replacedCategories &&
            (tagId in modelOwnedIds || (categoryId == "category" && categoryId in categoriesWithPredictions))
    }.toMutableList()
    val nextModelOwned = currentModelOwnedTagIds.filterNot { tagId ->
        tagsById[tagId]?.categoryId in replacedCategories
    }.toMutableList()

    categoriesWithPredictions.forEach { categoryId ->
        if (categoryId in userOverridden) return@forEach
        // A new photo generation owns only the suggestion output for category/occasion.
        // Previous model-owned selections are removed first, so trousers -> shirt cannot keep trousers.
        predictedByCategory.getValue(categoryId).forEach { tagId ->
            if (tagId !in selected) selected += tagId
            if (tagId !in nextModelOwned) nextModelOwned += tagId
        }
    }

    return PredictedTagReconciliation(
        selectedTagIds = selected.distinct(),
        modelOwnedTagIds = nextModelOwned.distinct(),
        skippedCategories = categoriesWithPredictions.intersect(userOverridden),
    )
}

private val MODEL_PREDICTED_CATEGORIES = setOf("category", "occasion")

private const val FIT_VALUE_DOES_NOT_FIT = 0
private const val FIT_VALUE_FITS = 2
private const val SECONDARY_COLOR_MIN_RATIO = 0.20f
private const val PHOTO_PREVIEW_MIN_ASPECT_RATIO = 0.66f
private const val PHOTO_PREVIEW_MAX_ASPECT_RATIO = 1.6f

data class AddEditPhotoReviewState(
    val captureStatus: String,
    val retrySourceUri: String,
    val retrySourceStatus: String,
)

internal object PhotoStatus {
    const val Gallery = "gallery"
    const val Camera = "camera"
    const val Processed = "processed"
    const val BackgroundProcessing = "background_processing"
    const val BackgroundRemoved = "background_removed"
    const val BackgroundDisabled = "background_disabled"
    const val BackgroundFallback = "background_fallback"
    const val QuickEdited = "quick_edited"
    const val MediaUnavailable = "media_unavailable"
}

private fun Int?.indicatesFits(): Boolean = this != FIT_VALUE_DOES_NOT_FIT

private fun List<MainColor>.colorForId(id: String?): MainColor? = firstOrNull { color -> color.id == id }

private fun List<MainColor>.nearestColor(rawHex: String?): MainColor? {
    val rgb = RgbColor.fromHexOrNull(rawHex) ?: return null
    return PaletteColorClassifier.Default.nearestColor(this, rgb)?.color
}

@Composable
private fun DisplayColorLabel.localizedLabel(): String = stringResource(stringRes)

private val DisplayColorLabel.stringRes: Int
    get() = when (this) {
        DisplayColorLabel.Black -> R.string.color_black
        DisplayColorLabel.Blue -> R.string.color_blue
        DisplayColorLabel.Brown -> R.string.color_brown
        DisplayColorLabel.Gray -> R.string.color_gray
        DisplayColorLabel.Green -> R.string.color_green
        DisplayColorLabel.Orange -> R.string.color_orange
        DisplayColorLabel.Pink -> R.string.color_pink
        DisplayColorLabel.Purple -> R.string.color_purple
        DisplayColorLabel.Red -> R.string.color_red
        DisplayColorLabel.White -> R.string.color_white
        DisplayColorLabel.Yellow -> R.string.color_yellow
        DisplayColorLabel.Multicolor -> R.string.color_multicolor
        DisplayColorLabel.Unknown -> R.string.color_unknown
    }

private fun String.toComposeColor(): Color? {
    val normalized = trim().removePrefix("#")
    if (normalized.length != 6 || normalized.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) {
        return null
    }
    return Color(android.graphics.Color.parseColor("#$normalized"))
}
