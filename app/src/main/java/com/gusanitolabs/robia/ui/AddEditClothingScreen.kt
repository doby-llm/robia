package com.gusanitolabs.robia.ui

import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoFixOff
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeviceThermostat
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.core.color.ColorLabelResolver
import com.gusanitolabs.robia.core.color.PaletteColorClassifier
import com.gusanitolabs.robia.core.color.PaletteColorMatch
import com.gusanitolabs.robia.core.color.RgbColor
import com.gusanitolabs.robia.core.model.AdditionalInfoDetectionResult
import com.gusanitolabs.robia.core.model.AdditionalInfoLabelScore
import com.gusanitolabs.robia.core.model.ClothingColorMetrics
import com.gusanitolabs.robia.core.model.ClothingItem
import com.gusanitolabs.robia.core.model.DisplayColorLabel
import com.gusanitolabs.robia.core.model.GarmentTag
import com.gusanitolabs.robia.core.model.MainColor
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.DisplayedImageTransform
import com.gusanitolabs.robia.media.EditorBackgroundRemovalStatus
import com.gusanitolabs.robia.media.EditorPhotoState
import com.gusanitolabs.robia.media.ImageDimensions
import com.gusanitolabs.robia.media.InteractiveGarmentSegmenter
import com.gusanitolabs.robia.media.InteractiveSegmentMask
import com.gusanitolabs.robia.media.InteractiveSegmentResult
import com.gusanitolabs.robia.media.PhotoBackgroundRemover
import com.gusanitolabs.robia.media.QuickEditAdjustments
import com.gusanitolabs.robia.media.QuickEditDraftState
import com.gusanitolabs.robia.media.QuickEditImageProcessor
import com.gusanitolabs.robia.media.createInteractiveGarmentSegmenter
import com.gusanitolabs.robia.media.additionalinfo.AdditionalInfoInputImageExporter
import com.gusanitolabs.robia.media.additionalinfo.TfliteAdditionalInfoDetector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt

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
    onCancel: () -> Unit,
    onSave: (ClothingItem) -> Unit,
    onDelete: ((ClothingItem) -> Unit)? = null,
    onBatchPhotosSelected: ((List<String>) -> Unit)? = null,
) {
    val context = LocalContext.current
    val backgroundRemover = remember { PhotoBackgroundRemover() }
    val quickEditSegmenter = remember(context) { createInteractiveGarmentSegmenter(context) }
    DisposableEffect(quickEditSegmenter) {
        onDispose { (quickEditSegmenter as? Closeable)?.close() }
    }
    val additionalInfoDetector = remember { TfliteAdditionalInfoDetector() }
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
    var colorPickerTarget by rememberSaveable { mutableStateOf<ColorPickerTarget?>(null) }
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var isPhotoProcessing by rememberSaveable { mutableStateOf(false) }
    var photoProcessingStage by rememberSaveable { mutableStateOf<PhotoProcessingStage?>(null) }
    var photoRetrySource by remember { mutableStateOf<PendingPhotoInput?>(null) }
    var developerDiagnostics by remember { mutableStateOf<List<String>>(emptyList()) }
    var developerExportStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var additionalInfoSourceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var showQuickEdit by rememberSaveable { mutableStateOf(false) }
    var quickEditStatus by rememberSaveable { mutableStateOf<String?>(null) }
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

    fun applyExtractedColors(colors: List<PaletteColorMatch>) {
        val primaryColorId = colors.getOrNull(0)?.color?.id
        if (primaryColorId != null) {
            selectedPrimaryColorId = primaryColorId
        }
        selectedSecondaryColorId = colors
            .drop(1)
            .firstOrNull { match ->
                match.ratio >= SECONDARY_COLOR_MIN_RATIO && match.color.id != primaryColorId
            }
            ?.color
            ?.id
    }

    fun queuePhotoForProcessing(uriString: String, sourceStatus: String) {
        val inputId = ++nextPhotoInputId
        photoProcessingToken += 1 // Immediately invalidate any older in-flight pipeline.
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
        developerExportStatus = null
        additionalInfoSourceUri = null
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
        )
        pendingPhotoInput = PendingPhotoInput(
            id = inputId,
            uriString = uriString,
            sourceStatus = sourceStatus,
        )
    }

    suspend fun processSelectedPhoto(inputId: Long, uriString: String, sourceStatus: String) {
        val token = ++photoProcessingToken
        val tagIdsBeforeProcessing = selectedTagIds.toSet()
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
        developerExportStatus = null
        additionalInfoSourceUri = null
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
                    applyExtractedColors(extracted)
                }

                photoProcessingStage = PhotoProcessingStage.DetectingAdditionalInformation
                val detectionStart = SystemClock.elapsedRealtime()
                val tagsForDetection = latestAvailableTags
                val classifierUri = croppedUri
                additionalInfoSourceUri = classifierUri.toString()
                editorPhotoState = editorPhotoState.copy(additionalInfoSourceUri = classifierUri.toString())
                addLine("Additional-info classifier source: cropped foreground uri=$classifierUri")
                var detectionResult = runCatching {
                    withContext(Dispatchers.IO) {
                        additionalInfoDetector.detect(context, classifierUri, tagsForDetection)
                    }
                }.getOrElse { throwable ->
                    addLine("Additional-info detector exception on cropped foreground: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
                    null
                }
                val originalFallbackUri = Uri.parse(originalPhotoUri ?: uriString)
                if (detectionResult?.prediction == null && editorPhotoState.usesOriginalPhotoFallback && originalFallbackUri != classifierUri) {
                    addLine("Additional-info foreground fallback unavailable; falling back to original explicit fallback uri=$originalFallbackUri")
                    additionalInfoSourceUri = originalFallbackUri.toString()
                    editorPhotoState = editorPhotoState.copy(additionalInfoSourceUri = originalFallbackUri.toString())
                    detectionResult = runCatching {
                        withContext(Dispatchers.IO) {
                            additionalInfoDetector.detect(context, originalFallbackUri, tagsForDetection)
                        }
                    }.getOrElse { throwable ->
                        addLine("Additional-info detector exception on original fallback: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
                        detectionResult
                    }
                }
                addLine("Additional-info detection duration: ${SystemClock.elapsedRealtime() - detectionStart}ms")
                addDetectionDebugLines(diagnostics, detectionResult)
                if (token != photoProcessingToken) return
                detectionResult?.prediction?.let { prediction ->
                    if (selectedTagIds.toSet() == tagIdsBeforeProcessing) {
                        selectedTagIds = mergePredictedAdditionalInfoTags(
                            currentTagIds = selectedTagIds,
                            predictedTagIds = prediction.selectedTagIds,
                            availableTags = tagsForDetection,
                        )
                    } else {
                        addLine("Predicted tags not merged because user changed tag selection during processing")
                    }
                }
        } catch (throwable: CancellationException) {
            addLine("Pipeline cancelled because a newer photo input superseded this one")
            throw throwable
        } catch (throwable: Exception) {
                if (token != photoProcessingToken) return
                photoUri = uriString
                captureStatus = PhotoStatus.BackgroundFallback
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
        val tagIdsBeforeProcessing = selectedTagIds.toSet()
        val startedAt = SystemClock.elapsedRealtime()
        val diagnostics = mutableListOf(
            "Quick Edit save started",
            "Edited URI: $editedUri",
            "Palette colors available at start: ${latestMainColors.size}",
            "Tags available at start: ${latestAvailableTags.size}",
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
            if (extracted.isNotEmpty()) applyExtractedColors(extracted)

            photoProcessingStage = PhotoProcessingStage.DetectingAdditionalInformation
            val tagsForDetection = latestAvailableTags
            additionalInfoSourceUri = croppedUri.toString()
            editorPhotoState = editorPhotoState.copy(additionalInfoSourceUri = croppedUri.toString())
            val detectionResult = runCatching {
                withContext(Dispatchers.IO) { additionalInfoDetector.detect(context, croppedUri, tagsForDetection) }
            }.getOrElse { throwable ->
                addLine("Quick Edit additional-info detector exception: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
                null
            }
            addDetectionDebugLines(diagnostics, detectionResult)
            if (token != photoProcessingToken) return
            detectionResult?.prediction?.let { prediction ->
                if (selectedTagIds.toSet() == tagIdsBeforeProcessing) {
                    selectedTagIds = mergePredictedAdditionalInfoTags(
                        currentTagIds = selectedTagIds,
                        predictedTagIds = prediction.selectedTagIds,
                        availableTags = tagsForDetection,
                    )
                } else {
                    addLine("Predicted tags not merged because user changed tag selection during Quick Edit re-analysis")
                }
            }
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
            additionalInfoSourceUri = existingItem?.photoUri,
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
        captureStatus = initialPhotoReviewState?.captureStatus.orEmpty()
        photoRetrySource = initialPhotoReviewState?.let { reviewState ->
            PendingPhotoInput(
                id = ++nextPhotoInputId,
                uriString = reviewState.retrySourceUri,
                sourceStatus = reviewState.retrySourceStatus,
            )
        }
        developerDiagnostics = emptyList()
        developerExportStatus = null
        additionalInfoSourceUri = existingItem?.photoUri
    }

    val isEditing = existingItem != null
    val initialFitValue = existingItem?.fitValue ?: if (existingItem == null) FIT_VALUE_FITS else null
    val developerExportNoPhotoStatus = stringResource(R.string.developer_export_input_image_no_photo)
    val developerExportSavedStatus = stringResource(R.string.developer_export_input_image_saved)
    val developerExportErrorStatus = stringResource(R.string.developer_export_input_image_error)
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

    fun requestClose() {
        if (hasUnsavedChanges) {
            showDiscardDialog = true
        } else {
            onCancel()
        }
    }

    fun exportAdditionalInfoInputImage() {
        val source = editorPhotoState.canonicalAdditionalInfoUri ?: additionalInfoSourceUri ?: photoUri
        if (source.isNullOrBlank()) {
            developerExportStatus = developerExportNoPhotoStatus
            return
        }

        coroutineScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AdditionalInfoInputImageExporter.exportToGallery(context, Uri.parse(source))
                }
            }
            developerExportStatus = result.getOrNull()?.let { export ->
                "$developerExportSavedStatus: ${export.uri}"
            } ?: developerExportErrorStatus
            developerDiagnostics = developerDiagnostics + listOf(
                "Developer export additional-info sourceUri=$source",
                "Developer export status=${developerExportStatus.orEmpty()}",
            ) + result.getOrNull()?.let { export ->
                listOf(
                    "Developer export inputSize=${export.inputWidth}x${export.inputHeight}, sourceSize=${export.sourceWidth}x${export.sourceHeight}",
                    "Developer export preprocessing=${export.preprocessing}",
                )
            }.orEmpty()
        }
    }

    BackHandler { requestClose() }

    if (showQuickEdit && !canonicalPhotoUri.isNullOrBlank()) {
        val quickEditBaseUri = editorPhotoState.quickEditComparisonBaseUri ?: canonicalPhotoUri.orEmpty()
        QuickEditDialog(
            photoUri = canonicalPhotoUri.orEmpty(),
            beforePhotoUri = quickEditBaseUri,
            segmenter = quickEditSegmenter,
            photoAspectRatio = photoAspectRatio,
            onDismiss = { showQuickEdit = false },
            onSave = { draft ->
                val currentPhotoUri = draft.sourceUri
                coroutineScope.launch {
                    showQuickEdit = false
                    isPhotoProcessing = true
                    photoProcessingStage = PhotoProcessingStage.ApplyingQuickEdit
                    val editedResult = runCatching {
                        withContext(Dispatchers.IO) {
                            val adjustedUri = QuickEditImageProcessor.applyAdjustments(context, currentPhotoUri, draft.adjustments)
                            if (draft.committedSegment != null) {
                                quickEditSegmenter.eraseSegment(
                                    context,
                                    adjustedUri,
                                    draft.committedSegment,
                                )
                            } else {
                                adjustedUri
                            }
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
                } else {
                    selectedSecondaryColorId = color?.id
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
                    isPhotoProcessing = isPhotoProcessing,
                    processingStage = photoProcessingStage,
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
            CardSection(title = stringResource(R.string.extracted_colors)) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    PaletteColorCircle(
                        title = stringResource(R.string.primary_color),
                        color = primaryPaletteColor,
                        modifier = Modifier.weight(1f),
                        onClick = { colorPickerTarget = ColorPickerTarget.Primary },
                    )
                    PaletteColorCircle(
                        title = stringResource(R.string.secondary_color),
                        color = secondaryPaletteColor,
                        emptyIcon = Icons.Rounded.Close,
                        modifier = Modifier.weight(1f),
                        onClick = { colorPickerTarget = ColorPickerTarget.Secondary },
                    )
                }
            }
        }

        item {
            MetadataCaptureSection(
                availableTags = availableTags,
                selectedTagIds = selectedTagIds,
                fitValue = fitValue,
                onSelectedTagIdsChange = { selectedTagIds = it },
                onFitValueChange = { fitValue = it },
            )
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
                    onExportInputImage = ::exportAdditionalInfoInputImage,
                )
            }
        }
    }
}

@Composable
private fun DeveloperDiagnosticsSection(
    lines: List<String>,
    exportStatus: String?,
    onExportInputImage: () -> Unit,
) {
    CardSection(title = stringResource(R.string.developer_diagnostics_title)) {
        OutlinedButton(onClick = onExportInputImage) {
            Icon(Icons.Rounded.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.developer_export_input_image))
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

private fun addDetectionDebugLines(
    lines: MutableList<String>,
    detectionResult: AdditionalInfoDetectionResult?,
) {
    if (detectionResult == null) {
        lines += "Additional info: detector returned no result"
        return
    }

    detectionResult.failureReason?.let { reason ->
        lines += "Additional info failure: $reason"
    }
    detectionResult.debug?.let { debug ->
        val stats = debug.tensorStats
        lines += "Additional info sourceUri: ${debug.sourceUri}"
        lines += "Additional info sourceSize: ${debug.sourceWidth}x${debug.sourceHeight}"
        lines += "Additional info model: id=${debug.modelId}, version=${debug.modelVersion}, file=${debug.modelFile}"
        lines += "Additional info input: shape=${debug.inputShape}, externalValueRange=${debug.externalValueRange}, normalization=${debug.normalizationType}"
        lines += "Additional info preprocessing: ${debug.preprocessing}"
        lines += "Additional info resize/background: resize=${debug.resizeStrategy}, background=${debug.backgroundStrategy}"
        lines += "Additional info tensor: min=${stats.min.formatDebugFloat()}, max=${stats.max.formatDebugFloat()}, mean=${stats.mean.formatDebugFloat()}, std=${stats.standardDeviation.formatDebugFloat()}, nonFinite=${stats.nonFiniteCount}, checksum=${stats.checksum}"
        lines += "Additional info tensor channelMeans: ${stats.channelMeans.joinToString { it.formatDebugFloat() }}"
        lines += "Additional info tensor channelMins: ${stats.channelMins.joinToString { it.formatDebugFloat() }}"
        lines += "Additional info tensor channelMaxs: ${stats.channelMaxs.joinToString { it.formatDebugFloat() }}"
        if (debug.outputShapes.isNotEmpty()) {
            lines += "Additional info outputShapes: ${debug.outputShapes.entries.joinToString { (name, shape) -> "$name=$shape" }}"
        }
        if (debug.topScoresByHead.isNotEmpty()) {
            lines += "Additional info debug topK: ${debug.topScoresByHead.formatDebugTopK()}"
        }
    }
    val prediction = detectionResult.prediction
    if (prediction == null) {
        lines += "Additional info: no prediction"
        return
    }

    lines += "Additional info selectedTagIds: ${prediction.selectedTagIds.sorted().joinToString().ifBlank { "none" }}"
    lines += "Additional info category scores (${prediction.categoryScores.size}):"
    prediction.categoryScores.appendDebugScores(lines)
    lines += "Additional info season scores (${prediction.seasonScores.size}):"
    prediction.seasonScores.appendDebugScores(lines)
    lines += "Additional info occasion scores (${prediction.occasionScores.size}):"
    prediction.occasionScores.appendDebugScores(lines)
}

private fun Map<String, List<AdditionalInfoLabelScore>>.formatDebugTopK(): String = entries.joinToString { (name, scores) ->
    "$name=${scores.joinToString { score -> "${score.label}:${score.score.formatDebugFloat()}" }}"
}

private fun List<AdditionalInfoLabelScore>.appendDebugScores(lines: MutableList<String>) {
    sortedByDescending(AdditionalInfoLabelScore::score).forEach { score ->
        lines += "  ${score.label} tag=${score.tagId ?: "n/a"} score=${score.score.formatDebugFloat()}"
    }
}

private fun Float.formatDebugFloat(): String = String.format(Locale.US, "%.4f", this)

@Composable
private fun MetadataCaptureSection(
    availableTags: List<GarmentTag>,
    selectedTagIds: List<String>,
    fitValue: Int?,
    onSelectedTagIdsChange: (List<String>) -> Unit,
    onFitValueChange: (Int?) -> Unit,
) {
    CardSection(title = stringResource(R.string.metadata_section)) {
        if (availableTags.isEmpty()) {
            Text(
                text = stringResource(R.string.tags_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            MetadataSelectorCard(
                title = stringResource(R.string.metadata_category),
                dialogTitle = stringResource(R.string.choose_category),
                subtitle = stringResource(R.string.single_select_metadata_hint),
                icon = categoryIconFor("category"),
                tags = availableTags.forCategory("category"),
                selectedTagIds = selectedTagIds,
                onSelectedTagIdsChange = onSelectedTagIdsChange,
            )
            MetadataTagCard(
                title = stringResource(R.string.metadata_season),
                subtitle = stringResource(R.string.multi_select_metadata_hint),
                icon = categoryIconFor("season"),
                tags = availableTags.forCategory("season"),
                selectedTagIds = selectedTagIds,
                singleSelect = false,
                onSelectedTagIdsChange = onSelectedTagIdsChange,
            )
            FitChoiceCard(
                fitValue = fitValue,
                onFitValueChange = onFitValueChange,
            )
            MetadataSelectorCard(
                title = stringResource(R.string.metadata_location),
                dialogTitle = stringResource(R.string.choose_location),
                subtitle = stringResource(R.string.single_select_metadata_hint),
                icon = categoryIconFor("location"),
                tags = availableTags.forCategory("location"),
                selectedTagIds = selectedTagIds,
                onSelectedTagIdsChange = onSelectedTagIdsChange,
            )
            MetadataTagCard(
                title = stringResource(R.string.metadata_occasions),
                subtitle = stringResource(R.string.multi_select_metadata_hint),
                icon = categoryIconFor("occasion"),
                tags = availableTags.forCategory("occasion"),
                selectedTagIds = selectedTagIds,
                singleSelect = false,
                onSelectedTagIdsChange = onSelectedTagIdsChange,
            )
        }
    }
}

@Composable
private fun MetadataSelectorCard(
    title: String,
    dialogTitle: String,
    subtitle: String,
    icon: ImageVector,
    tags: List<GarmentTag>,
    selectedTagIds: List<String>,
    onSelectedTagIdsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedTag = tags.firstOrNull { tag -> tag.id in selectedTagIds }
    val hasSelectedTag = selectedTag != null
    val collapsedLabel = selectedTag?.localizedLabel() ?: stringResource(R.string.not_set)
    val collapsedDescription = stringResource(R.string.metadata_selection_content_description, title, collapsedLabel)
    val categoryTagIds = remember(tags) { tags.map(GarmentTag::id).toSet() }
    var showSelector by rememberSaveable { mutableStateOf(false) }

    if (showSelector) {
        SingleSelectMetadataDialog(
            title = dialogTitle,
            tags = tags,
            selectedTagId = selectedTag?.id,
            onDismiss = { showSelector = false },
            onSelectionChange = { selectedId ->
                showSelector = false
                onSelectedTagIdsChange(
                    selectedTagIds.filterNot { currentId -> currentId in categoryTagIds } + listOfNotNull(selectedId),
                )
            },
        )
    }

    MetadataShellCard(title = title, subtitle = subtitle, icon = icon, modifier = modifier) {
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.no_tags_in_category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            OutlinedButton(
                onClick = { showSelector = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (hasSelectedTag) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    contentColor = if (hasSelectedTag) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = collapsedDescription
                        selected = hasSelectedTag
                    },
            ) {
                if (hasSelectedTag) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = collapsedLabel,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (hasSelectedTag) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SingleSelectMetadataDialog(
    title: String,
    tags: List<GarmentTag>,
    selectedTagId: String?,
    onDismiss: () -> Unit,
    onSelectionChange: (String?) -> Unit,
) {
    val selectedIndex = selectedTagId?.let { currentId -> tags.indexOfFirst { tag -> tag.id == currentId } }
        ?.takeIf { index -> index >= 0 }
        ?.plus(1)
        ?: 0
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                item(key = "not-set") {
                    MetadataSelectorRow(
                        label = stringResource(R.string.not_set),
                        selected = selectedTagId == null,
                        onClick = { onSelectionChange(null) },
                    )
                }
                items(
                    count = tags.size,
                    key = { index -> tags[index].id },
                ) { index ->
                    val tag = tags[index]
                    MetadataSelectorRow(
                        label = tag.localizedLabel(),
                        selected = tag.id == selectedTagId,
                        onClick = { onSelectionChange(tag.id) },
                    )
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
private fun MetadataSelectorRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val rowBackground = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    } else {
        Color.Transparent
    }
    val rowContentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(rowBackground)
            .semantics { this.selected = selected }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = rowContentColor,
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = rowContentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FitChoiceCard(
    fitValue: Int?,
    onFitValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    MetadataShellCard(
        title = stringResource(R.string.metadata_fit),
        subtitle = stringResource(R.string.metadata_fit_hint),
        icon = Icons.Rounded.Straighten,
        modifier = modifier,
    ) {
        val fits = fitValue.indicatesFits()
        FlowChipRow {
            FitChoiceChip(
                label = stringResource(R.string.fit_good),
                selected = fits,
                onClick = { onFitValueChange(if (fits) FIT_VALUE_DOES_NOT_FIT else FIT_VALUE_FITS) },
            )
        }
    }
}

@Composable
private fun FitChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun MetadataTagCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tags: List<GarmentTag>,
    selectedTagIds: List<String>,
    singleSelect: Boolean,
    onSelectedTagIdsChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    MetadataShellCard(title = title, subtitle = subtitle, icon = icon, modifier = modifier) {
        if (tags.isEmpty()) {
            Text(
                text = stringResource(R.string.no_tags_in_category),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        } else {
            FlowChipRow {
                tags.forEach { tag ->
                    val selected = tag.id in selectedTagIds
                    FilterChip(
                        selected = selected,
                        onClick = {
                            onSelectedTagIdsChange(
                                selectedTagIds.toggleTag(
                                    tag = tag,
                                    selected = selected,
                                    singleSelect = singleSelect,
                                    categoryTagIds = tags.map(GarmentTag::id),
                                ),
                            )
                        },
                        label = { Text(tag.localizedLabel()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MetadataShellCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

private fun List<GarmentTag>.forCategory(categoryId: String): List<GarmentTag> =
    filter { tag -> tag.categoryId == categoryId }.sortedWith(compareBy<GarmentTag> { it.sortOrder }.thenBy { it.name })

private fun List<String>.toggleTag(
    tag: GarmentTag,
    selected: Boolean,
    singleSelect: Boolean,
    categoryTagIds: List<String>,
): List<String> =
    if (selected) {
        this - tag.id
    } else if (singleSelect) {
        filterNot { selectedId -> selectedId in categoryTagIds } + tag.id
    } else {
        this + tag.id
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
    isPhotoProcessing: Boolean,
    processingStage: PhotoProcessingStage?,
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
        isPhotoProcessing = isPhotoProcessing,
        processingStage = processingStage,
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

@Composable
private fun PhotoCaptureCard(
    photoUri: String?,
    photoAspectRatio: Float?,
    captureStatus: String,
    isPhotoProcessing: Boolean,
    processingStage: PhotoProcessingStage?,
    onRetryPhotoProcessing: (() -> Unit)?,
    onQuickEditClick: (() -> Unit)?,
    quickEditStatus: String?,
    onGalleryClick: (() -> Unit)?,
    onCameraClick: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val processingLabel = if (processingStage != null) {
                stringResource(processingStage.labelRes)
            } else {
                null
            }
            PhotoPreview(
                photoUri = photoUri,
                isProcessing = isPhotoProcessing,
                processingLabel = processingLabel,
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
                PhotoStatus.BackgroundFallback -> R.string.background_removal_fallback_status
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
    processingLabel: String? = null,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 3f / 4f,
    onEmptyPhotoClick: (() -> Unit)? = null,
    onQuickEditClick: (() -> Unit)? = null,
) {
    val uri = photoUri?.takeIf { it.isNotBlank() }
    val emptyPhotoContentDescription = stringResource(R.string.content_open_camera)
    val quickEditContentDescription = stringResource(R.string.content_quick_edit_photo)
    Box(
        modifier = modifier
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
            val processingOverlayColors = rememberProcessingOverlayColors(uri, isProcessing)
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        adjustViewBounds = true
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { imageView -> imageView.setImageURI(Uri.parse(uri)) },
                modifier = Modifier.fillMaxSize(),
            )
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
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(processingOverlayColors.scrimColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        CircularProgressIndicator(color = processingOverlayColors.contentColor)
                        processingLabel?.let { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = processingOverlayColors.contentColor,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickEditDialog(
    photoUri: String,
    beforePhotoUri: String,
    segmenter: InteractiveGarmentSegmenter,
    photoAspectRatio: Float?,
    onDismiss: () -> Unit,
    onSave: (QuickEditDraftState) -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var brightness by rememberSaveable { mutableStateOf(0f) }
    var temperature by rememberSaveable { mutableStateOf(0f) }
    var selectedTool by rememberSaveable { mutableStateOf(QuickEditTool.Brightness) }
    var pendingSegment by remember { mutableStateOf<InteractiveSegmentResult?>(null) }
    var committedSegment by remember { mutableStateOf<InteractiveSegmentResult?>(null) }
    var isSegmenting by remember { mutableStateOf(false) }
    var segmentError by rememberSaveable { mutableStateOf<String?>(null) }
    var showBefore by remember { mutableStateOf(false) }
    var draftPreviewUri by remember(photoUri) { mutableStateOf(photoUri) }
    var renderedPreviewDraft by remember(photoUri) { mutableStateOf<QuickEditDraftState?>(null) }
    var previewGenerationId by remember(photoUri) { mutableStateOf(0L) }
    var previewRendering by remember { mutableStateOf(false) }
    var showPreviewLoading by remember { mutableStateOf(false) }
    var sourceImageDimensions by remember(photoUri) { mutableStateOf<ImageDimensions?>(null) }
    val segmenterAvailable = segmenter.isAvailable
    val sourcePhotoUri = remember(photoUri) { Uri.parse(photoUri) }
    val currentDraft = remember(
        sourcePhotoUri,
        brightness,
        temperature,
        pendingSegment,
        committedSegment,
        previewGenerationId,
    ) {
        QuickEditDraftState(
            sourceUri = sourcePhotoUri,
            adjustments = QuickEditAdjustments(brightness, temperature),
            pendingSegment = pendingSegment,
            committedSegment = committedSegment,
            previewGenerationId = previewGenerationId,
        )
    }
    val previewUri = if (showBefore) beforePhotoUri else draftPreviewUri
    val saveableDraft = renderedPreviewDraft?.takeIf { renderedDraft ->
        // Save must consume the exact draft generation that produced the visible preview.
        renderedDraft == currentDraft && !previewRendering && pendingSegment == null && !isSegmenting
    }

    LaunchedEffect(sourcePhotoUri) {
        sourceImageDimensions = withContext(Dispatchers.IO) {
            decodeImageDimensions(context, sourcePhotoUri)
        }
    }

    LaunchedEffect(photoUri, brightness, temperature, pendingSegment, committedSegment) {
        val generationId = previewGenerationId + 1
        previewGenerationId = generationId
        previewRendering = true
        renderedPreviewDraft = null
        val draft = QuickEditDraftState(
            sourceUri = sourcePhotoUri,
            adjustments = QuickEditAdjustments(brightness, temperature),
            pendingSegment = pendingSegment,
            committedSegment = committedSegment,
            previewGenerationId = generationId,
        )
        val renderedUri = withContext(Dispatchers.IO) {
            QuickEditImageProcessor.renderDraftPreview(context, draft)
        }
        if (generationId == previewGenerationId) {
            draftPreviewUri = renderedUri.toString()
            renderedPreviewDraft = draft
            previewRendering = false
        }
    }

    LaunchedEffect(previewRendering) {
        if (previewRendering) {
            delay(180)
            showPreviewLoading = previewRendering
        } else {
            showPreviewLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (pendingSegment != null) {
                pendingSegment = null
                segmentError = null
            } else {
                onDismiss()
            }
        },
        title = { Text(stringResource(R.string.quick_edit_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f / 4f)
                        .clip(MaterialTheme.shapes.large)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .pointerInput(selectedTool, segmenterAvailable, sourceImageDimensions, photoUri, isSegmenting) {
                            detectTapGestures(
                                onPress = {
                                    if (selectedTool != QuickEditTool.Eraser) {
                                        showBefore = true
                                        tryAwaitRelease()
                                        showBefore = false
                                    }
                                },
                                onTap = { offset: Offset ->
                                    if (selectedTool == QuickEditTool.Eraser && segmenterAvailable && !isSegmenting) {
                                        val dimensions = sourceImageDimensions?.takeIf { it.isValid }
                                        val transform = dimensions?.let {
                                            DisplayedImageTransform(
                                                imageWidth = it.width,
                                                imageHeight = it.height,
                                                containerWidth = size.width.toFloat(),
                                                containerHeight = size.height.toFloat(),
                                            )
                                        }
                                        val imagePoint = transform?.toNormalizedPoint(offset.x, offset.y)
                                        if (imagePoint == null) {
                                            segmentError = context.getString(R.string.quick_edit_tap_outside_photo)
                                            pendingSegment = null
                                        } else {
                                            isSegmenting = true
                                            segmentError = null
                                            pendingSegment = null
                                            coroutineScope.launch {
                                                val result = runCatching {
                                                    withContext(Dispatchers.IO) {
                                                        segmenter.highlightSegment(context, Uri.parse(photoUri), imagePoint)
                                                    }
                                                }
                                                val segment = result.getOrNull()
                                                pendingSegment = segment
                                                segmentError = when {
                                                    result.isFailure -> context.getString(R.string.quick_edit_eraser_unavailable)
                                                    segment == null -> context.getString(R.string.quick_edit_no_segment_found)
                                                    segment.mask == null -> context.getString(R.string.quick_edit_brush_fallback_hint)
                                                    else -> null
                                                }
                                                isSegmenting = false
                                            }
                                        }
                                    }
                                },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    AndroidView(
                        factory = { viewContext ->
                            ImageView(viewContext).apply {
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                adjustViewBounds = true
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            }
                        },
                        update = { imageView -> imageView.setImageURI(Uri.parse(previewUri)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (showPreviewLoading && !showBefore) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.46f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                            Text(
                                text = stringResource(R.string.quick_edit_preview_loading),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                            )
                        }
                    }
                    val highlightSegment = pendingSegment ?: committedSegment
                    highlightSegment?.takeIf { selectedTool == QuickEditTool.Eraser }?.let { segment ->
                        SegmentMaskOverlay(
                            segment = segment,
                            imageDimensions = sourceImageDimensions,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Text(
                        text = if (showBefore) stringResource(R.string.quick_edit_before_label) else stringResource(R.string.quick_edit_hold_compare_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.46f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickEditToolIconButton(
                        icon = Icons.Rounded.Brightness6,
                        label = stringResource(R.string.quick_edit_brightness_content_description),
                        selected = selectedTool == QuickEditTool.Brightness,
                        onClick = { selectedTool = QuickEditTool.Brightness },
                    )
                    QuickEditToolIconButton(
                        icon = Icons.Rounded.DeviceThermostat,
                        label = stringResource(R.string.quick_edit_temperature_content_description),
                        selected = selectedTool == QuickEditTool.Temperature,
                        onClick = { selectedTool = QuickEditTool.Temperature },
                    )
                    QuickEditToolIconButton(
                        icon = Icons.Rounded.AutoFixOff,
                        label = stringResource(R.string.quick_edit_erase_segment_content_description),
                        selected = selectedTool == QuickEditTool.Eraser,
                        onClick = { selectedTool = QuickEditTool.Eraser },
                    )
                }

                val selectedToolLabel = when (selectedTool) {
                    QuickEditTool.Brightness -> stringResource(R.string.quick_edit_brightness)
                    QuickEditTool.Temperature -> stringResource(R.string.quick_edit_temperature)
                    QuickEditTool.Eraser -> stringResource(R.string.quick_edit_eraser)
                }
                Text(
                    text = stringResource(R.string.quick_edit_selected_tool_label, selectedToolLabel),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Text(
                    text = stringResource(R.string.quick_edit_apply_on_save_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                when (selectedTool) {
                    QuickEditTool.Brightness -> {
                        Text(stringResource(R.string.quick_edit_brightness_hint), style = MaterialTheme.typography.bodySmall)
                        Slider(value = brightness, onValueChange = { brightness = it }, valueRange = -1f..1f)
                        OutlinedButton(
                            onClick = { brightness = 0f },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.quick_edit_brightness_reset))
                        }
                    }
                    QuickEditTool.Temperature -> {
                        Text(stringResource(R.string.quick_edit_temperature_hint), style = MaterialTheme.typography.bodySmall)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            QuickEditTemperaturePreset(R.string.quick_edit_temperature_cooler, -1f, temperature) { temperature = it }
                            QuickEditTemperaturePreset(R.string.quick_edit_temperature_cool, -0.5f, temperature) { temperature = it }
                            QuickEditTemperaturePreset(R.string.quick_edit_temperature_neutral, 0f, temperature) { temperature = it }
                            QuickEditTemperaturePreset(R.string.quick_edit_temperature_warm, 0.5f, temperature) { temperature = it }
                            QuickEditTemperaturePreset(R.string.quick_edit_temperature_warmer, 1f, temperature) { temperature = it }
                        }
                    }
                    QuickEditTool.Eraser -> {
                        Text(
                            text = when {
                                isSegmenting -> stringResource(R.string.quick_edit_loading_segmenter)
                                !segmenterAvailable -> stringResource(R.string.quick_edit_eraser_unavailable)
                                committedSegment != null -> stringResource(R.string.quick_edit_segment_committed)
                                pendingSegment != null -> stringResource(R.string.quick_edit_segment_pending)
                                else -> stringResource(R.string.quick_edit_eraser_hint)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (segmenterAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        )
                        segmentError?.let { message ->
                            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedButton(
                                enabled = pendingSegment != null && !isSegmenting,
                                onClick = {
                                    committedSegment = pendingSegment
                                    pendingSegment = null
                                    segmentError = null
                                },
                            ) { Text(stringResource(R.string.quick_edit_erase_segment)) }
                            TextButton(
                                enabled = pendingSegment != null || committedSegment != null,
                                onClick = {
                                    pendingSegment = null
                                    committedSegment = null
                                    segmentError = null
                                },
                            ) { Text(stringResource(R.string.undo)) }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = saveableDraft != null,
                onClick = { saveableDraft?.let(onSave) },
            ) {
                Text(stringResource(R.string.save_item))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (pendingSegment != null) {
                        pendingSegment = null
                        segmentError = null
                    } else {
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun SegmentMaskOverlay(
    segment: InteractiveSegmentResult,
    imageDimensions: ImageDimensions?,
    modifier: Modifier = Modifier,
) {
    val mask = segment.mask
    val overlay = remember(mask) { mask?.toOverlayImageBitmap() }
    Canvas(modifier = modifier) {
        val dimensions = imageDimensions?.takeIf { it.isValid }
            ?: mask?.let { ImageDimensions(it.width, it.height) }
            ?: return@Canvas
        val transform = DisplayedImageTransform(
            imageWidth = dimensions.width,
            imageHeight = dimensions.height,
            containerWidth = size.width,
            containerHeight = size.height,
        )
        if (overlay != null) {
            drawImage(
                image = overlay,
                dstOffset = IntOffset(transform.left.roundToInt(), transform.top.roundToInt()),
                dstSize = IntSize(transform.contentWidth.roundToInt(), transform.contentHeight.roundToInt()),
            )
        } else {
            segment.brushPoints.forEach { point ->
                drawCircle(
                    color = Color.Red.copy(alpha = 0.28f),
                    radius = minOf(transform.contentWidth, transform.contentHeight) * 0.08f,
                    center = Offset(
                        transform.left + point.x * transform.contentWidth,
                        transform.top + point.y * transform.contentHeight,
                    ),
                )
            }
        }
    }
}

private fun InteractiveSegmentMask.toOverlayImageBitmap(): ImageBitmap {
    val pixels = IntArray(width * height)
    for (index in pixels.indices) {
        val alphaValue = (alpha[index].toInt() and 0xFF).coerceIn(0, 255)
        pixels[index] = (alphaValue shl 24) or 0x00FF1744
    }
    return android.graphics.Bitmap.createBitmap(pixels, width, height, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

private fun decodeImageDimensions(context: android.content.Context, uri: Uri): ImageDimensions? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    return runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
        ImageDimensions(options.outWidth, options.outHeight).takeIf { it.isValid }
    }.getOrNull()
}

@Composable
private fun QuickEditToolIconButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = label
                this.selected = selected
            },
    ) {
        Icon(imageVector = icon, contentDescription = null)
    }
}

@Composable
private fun QuickEditTemperaturePreset(
    labelRes: Int,
    value: Float,
    selectedValue: Float,
    onSelected: (Float) -> Unit,
) {
    val selected = selectedValue == value
    val label = stringResource(labelRes)
    val contentDescription = stringResource(R.string.quick_edit_temperature_preset_content_description, label)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (value < 0f) Color(0xFFB9D8FF) else if (value > 0f) Color(0xFFFFC58C) else MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { onSelected(value) }
                .semantics {
                    this.contentDescription = contentDescription
                    this.selected = selected
                },
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall)
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
private fun PaletteColorCircle(
    title: String,
    color: MainColor?,
    emptyIcon: ImageVector = Icons.Rounded.Add,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color?.hex?.toComposeColor() ?: MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (color == null) {
                Icon(emptyIcon, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            text = color?.name ?: stringResource(R.string.none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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

private enum class QuickEditTool { Brightness, Temperature, Eraser }

private data class PendingPhotoInput(
    val id: Long,
    val uriString: String,
    val sourceStatus: String,
)

private enum class PhotoProcessingStage(val labelRes: Int) {
    RemovingBackground(R.string.processing_stage_removing_background),
    CroppingPicture(R.string.processing_stage_cropping_picture),
    ExtractingColor(R.string.processing_stage_extracting_color),
    DetectingAdditionalInformation(R.string.processing_stage_detecting_additional_information),
    ApplyingQuickEdit(R.string.processing_stage_applying_quick_edit),
}

private fun mergePredictedAdditionalInfoTags(
    currentTagIds: List<String>,
    predictedTagIds: Set<String>,
    availableTags: List<GarmentTag>,
): List<String> {
    if (predictedTagIds.isEmpty()) return currentTagIds
    val tagsById = availableTags.associateBy(GarmentTag::id)
    val hasCurrentCategory = currentTagIds.any { tagId -> tagsById[tagId]?.categoryId == "category" }
    val inferredTagIds = predictedTagIds.filter { tagId ->
        val categoryId = tagsById[tagId]?.categoryId ?: return@filter false
        categoryId in MODEL_PREDICTED_CATEGORIES && (categoryId != "category" || !hasCurrentCategory)
    }
    return (currentTagIds + inferredTagIds).distinct()
}

private val MODEL_PREDICTED_CATEGORIES = setOf("category", "season", "occasion")

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
