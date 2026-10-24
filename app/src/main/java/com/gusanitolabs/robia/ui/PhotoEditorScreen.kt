package com.gusanitolabs.robia.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material.icons.rounded.Redo
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gusanitolabs.robia.R
import com.gusanitolabs.robia.RobiaApplication
import com.gusanitolabs.robia.core.model.CanvasOffset
import com.gusanitolabs.robia.core.model.CutoutRecipe
import com.gusanitolabs.robia.media.AlphaRefiner
import com.gusanitolabs.robia.media.BackgroundRemovalEngine
import com.gusanitolabs.robia.media.BackgroundRemovalStatus
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.MlKitSubjectSegmentationBackgroundRemovalEngine
import com.gusanitolabs.robia.media.QuickEditAdjustments
import com.gusanitolabs.robia.media.TransparentCrop
import com.gusanitolabs.robia.media.cutout.CutoutEngineRegistry
import com.gusanitolabs.robia.media.cutout.CutoutOptions
import com.gusanitolabs.robia.media.cutout.CutoutPackManifest
import com.gusanitolabs.robia.media.cutout.EditBudget
import com.gusanitolabs.robia.media.cutout.PhotoHistory
import com.gusanitolabs.robia.media.cutout.PreciseCutoutEngine
import com.gusanitolabs.robia.media.cutout.RedoCutoutChoice
import com.gusanitolabs.robia.media.cutout.RefineSession
import com.gusanitolabs.robia.media.cutout.SmartTapOutcome
import com.gusanitolabs.robia.media.cutout.atEngineCanvas
import com.gusanitolabs.robia.media.cutout.brushRadiusPx
import com.gusanitolabs.robia.media.cutout.componentsToRemove
import com.gusanitolabs.robia.media.cutout.originalLayer
import com.gusanitolabs.robia.media.cutout.redoCutoutChoices
import com.gusanitolabs.robia.media.cutout.redoCutoutOptions
import com.gusanitolabs.robia.media.cutout.releaseOnFailure
import com.gusanitolabs.robia.media.cutout.smartTapOutcome
import com.gusanitolabs.robia.media.cutout.snapRadiusPhotoPx
import com.gusanitolabs.robia.media.cutout.trimmedBy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

/** A smart selection runs the segmenter on at most this many points of a drag, evenly spaced along its length. */
private const val SMART_MAX_POINTS = 12
private const val SMART_TRAIL_DASH_DP = 16f
private const val SMART_TRAIL_GAP_DP = 12f
/** A smart tap on a transparent pixel picks the loose piece within this distance on screen (see [snapRadiusPhotoPx]). */
private const val SMART_SNAP_DP = 16f
private const val PULSE_MS = 300L
private const val PULSE_RADIUS_DP = 20f
private const val BRUSH_RADIUS_DP = 24f
private const val BRUSH_FEATHER_DP = 6f
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val CHECKER_CELL_DP = 12f
private const val REDO_CACHE_DIRECTORY = "redo-cutout"
// Same offsets as QuickEditImageProcessor, which applies the Light adjustments on Done; keep them in step.
private const val LIGHT_BRIGHTNESS_OFFSET = 110f
private const val LIGHT_TEMPERATURE_OFFSET = 46f

/** Cache subdirectory of the photos that Cut out again sets aside; each editor deletes its own, on close (REQ-ED-03). */
private const val EDITOR_HISTORY_DIRECTORY = "editor-history"

private enum class EditorTab { Cutout, Light }

/**
 * The working photo: its RGB stays fixed, its alpha is changed through [session]. [fromRedo] is true for a cutout made by
 * Redo cutout, which Done must write even when nothing else was edited.
 */
private class CutoutPhoto(val source: Bitmap, val session: RefineSession, val fromRedo: Boolean = false) {
    /** The colour of the photo: the stored RGB, with the original's RGB under the pixels the stored cutout made transparent. */
    fun colour(): IntArray = session.restoredColour(argbOf(source))
}

/** A tap or drag that must be pulsed for [PULSE_MS]; a new instance per touch so repeated touches restart the pulse. */
private class PulseMark(val at: Offset)

/**
 * The original photo that Redo cutout runs on, or null when Redo is hidden (SG4). Only a photo picked in this editing session
 * has one: for a saved garment the stored photo is the only copy (answer 3A), so [savedPhotoUri] hides it. [originalUri] is the
 * kept original of the photo being edited (REQ-OR-05): when there is one, Redo runs on it instead.
 */
internal fun redoSourceUri(editorRawUri: String?, savedPhotoUri: String?, originalUri: String? = null): String? =
    originalUri?.takeIf { it.isNotBlank() } ?: editorRawUri?.takeIf { it.isNotBlank() && it != savedPhotoUri }

/**
 * Full-screen photo editor (SG3, UX-DEC-011): Cutout (smart tap/swipe selection and a brush) and Light (brightness and
 * temperature). Works on the stored processed PNG, so it needs no downloadable pack. Cancel writes nothing; Done writes
 * one new PNG for the cutout and hands it to the caller, which applies the Light adjustments and re-analyses the garment.
 * With [rawSourceUri], Redo cutout re-runs one engine on the original photo (SG4); the stored photo changes only on Done.
 * [keepsHistory] is true only while a new garment's photo is added (REQ-ED-03, R3-DEC-027): a Cut out again of a saved garment
 * then keeps no photo before it, so nothing can be undone across it.
 * With [originalUri] and [recipe] (REQ-OR-05), Keep restores the removed background from the original, and Done passes back the
 * recipe of the cutout it wrote, so the caller can store it with the original. Without them, the photo is edited as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    photoUri: String,
    rawSourceUri: String?,
    onCancel: () -> Unit,
    onDiagnostic: (String) -> Unit,
    onDone: (cutoutUri: Uri?, adjustments: QuickEditAdjustments, recipe: CutoutRecipe?) -> Unit,
    keepsHistory: Boolean = true,
    originalUri: String? = null,
    recipe: CutoutRecipe? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val noSourceHint = stringResource(R.string.refine_no_source_hint)
    val snackbarHostState = remember { SnackbarHostState() }
    // Guards every read of photo.source, so dispose can close and recycle after they finish.
    val sourceMutex = remember { Mutex() }
    val app = context.applicationContext as RobiaApplication
    val packReady by app.cutoutPack.ready.collectAsState(initial = false)
    val redoFailedMessage = stringResource(R.string.redo_cutout_failed)
    val wholeGarmentMessage = stringResource(R.string.editor_smart_whole_garment)
    val keepHintMessage = stringResource(R.string.editor_smart_keep_hint)

    var tab by rememberSaveable { mutableStateOf(EditorTab.Cutout) }
    var mode by rememberSaveable { mutableStateOf(RefineSession.Mode.Keep) }
    var brush by rememberSaveable { mutableStateOf(false) }
    var brightness by rememberSaveable { mutableStateOf(0f) }
    var temperature by rememberSaveable { mutableStateOf(0f) }
    var selectedTool by rememberSaveable { mutableStateOf(QuickEditTool.Brightness) }
    var showBefore by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var photo by remember(photoUri) { mutableStateOf<CutoutPhoto?>(null) }
    // The edit history of every photo of this editor, on screen or kept, counts against this budget (REQ-ED-03).
    val editBudget = remember(photoUri) { EditBudget() }
    // The photos before and after the one on screen, as cache files (Cut out again, REQ-ED-03); their edits stay in their sessions.
    val photoHistory = remember(photoUri) {
        PhotoHistory<Bitmap>(
            budget = editBudget,
            root = File(context.cacheDir, EDITOR_HISTORY_DIRECTORY),
            write = { bitmap, file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } },
            read = { file -> checkNotNull(BitmapFactory.decodeFile(file.path)) { "Photo ${file.name} is unreadable" } },
            release = { bitmap -> bitmap.recycle() },
        )
    }
    var redoing by remember { mutableStateOf(false) }
    // True while an Undo, a Redo or a move across a Cut out again runs on the photo: they wait for it, and so do the gestures.
    var replaying by remember { mutableStateOf(false) }
    var showRedoSheet by remember { mutableStateOf(false) }
    var revision by remember(photoUri) { mutableIntStateOf(0) }
    var preview by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(MIN_ZOOM) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var strokeScreen by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var pulse by remember { mutableStateOf<PulseMark?>(null) }

    val session = photo?.session
    val canUndo = remember(revision, session) { session?.canUndo == true || photoHistory.canGoBack }
    val canRedo = remember(revision, session) { session?.canRedo == true || photoHistory.canGoForward }
    val changed = remember(revision, session) { session?.isChanged == true }
    val adjustments = QuickEditAdjustments(brightness, temperature)
    val doneEnabled = !saving && !redoing && !replaying && photo != null && (changed || photo?.fromRedo == true || adjustments.hasColorAdjustment)

    DisposableEffect(Unit) {
        onDispose {
            val leaving = photo
            // Runs outside the composition scope, which is cancelled here and would not wait for a running select or save.
            CoroutineScope(Dispatchers.Default).launch {
                sourceMutex.withLock {
                    leaving?.source?.recycle()
                    leaving?.session?.close()
                }
            }
        }
    }

    // The history files of this editing session go when the editor closes; nothing of it outlives the screen.
    DisposableEffect(photoHistory) {
        onDispose {
            CoroutineScope(Dispatchers.IO).launch { sourceMutex.withLock { photoHistory.close() } }
        }
    }

    LaunchedEffect(photoUri) {
        val bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use(BitmapFactory::decodeStream)
        } ?: return@LaunchedEffect
        val alpha = withContext(Dispatchers.Default) { alphaOf(bitmap) }
        // A layer that cannot be loaded leaves the editor as it is without an original, so the hint below applies (R3-DEC-039).
        val layer = withContext(Dispatchers.IO) {
            if (originalUri != null && recipe != null) {
                try {
                    loadOriginalLayer(context, originalUri, recipe, bitmap.width, bitmap.height)
                } catch (failure: Exception) {
                    null
                }
            } else {
                null
            }
        }
        photo = CutoutPhoto(
            bitmap,
            RefineSession(bitmap.width, bitmap.height, alpha, editBudget, layer, recipe.takeIf { layer != null }),
        )
        // Without the original the removed parts cannot come back: the hint says so.
        if (layer == null) snackbarHostState.showSnackbar(noSourceHint)
    }

    LaunchedEffect(photo, revision) {
        val current = photo ?: return@LaunchedEffect
        val rendered = withContext(Dispatchers.Default) {
            sourceMutex.withLock {
                // Queued before an Undo or Redo replaced this photo; its bitmap is already recycled.
                if (current.source.isRecycled) return@withLock null
                val alphaBytes = toAlphaBytes(current.session.currentAlpha())
                composeWithAlpha(current.colour(), alphaBytes, current.source.width, current.source.height).asImageBitmap()
            }
        }
        rendered?.let { preview = it }
    }

    LaunchedEffect(pulse) {
        if (pulse != null) {
            delay(PULSE_MS)
            pulse = null
        }
    }

    fun commitStroke(points: List<Offset>, current: CutoutPhoto, fit: FitRect) {
        val w = current.source.width
        val radius = brushRadiusPx(BRUSH_RADIUS_DP, density, zoom) * (w / fit.width)
        val feather = brushRadiusPx(BRUSH_FEATHER_DP, density, zoom) * (w / fit.width)
        val flat = FloatArray(points.size * 2)
        points.forEachIndexed { i, point ->
            flat[2 * i] = point.x
            flat[2 * i + 1] = point.y
        }
        scope.launch {
            sourceMutex.withLock {
                // Queued before an Undo, a Redo or a Cut out again replaced this photo; its bitmap is already recycled.
                if (current.source.isRecycled) return@withLock
                current.session.applyBrush(mode, flat, radius, feather)
                photoHistory.discardRedo()
                revision++
            }
        }
    }

    /** Reset: back to the automatic cutout. Like any edit, it discards what was undone. */
    fun resetEdits() {
        val current = photo ?: return
        scope.launch {
            sourceMutex.withLock {
                if (current.source.isRecycled || !current.session.isChanged) return@withLock
                current.session.reset()
                photoHistory.discardRedo()
                revision++
            }
        }
    }

    fun runSmartSelection(points: List<Offset>, current: CutoutPhoto, snapRadiusPx: Int) {
        scope.launch {
            val hint = sourceMutex.withLock {
                // Queued before an Undo or Redo replaced this photo; its bitmap is already recycled.
                if (current.source.isRecycled) return@withLock null
                val w = current.source.width
                val h = current.source.height
                val touched = points.map { selectPixelOf(it, w, h) }
                val alpha = current.session.currentAlpha()
                val pick = withContext(Dispatchers.Default) { componentsToRemove(alpha, w, h, touched, snapRadiusPx) }
                onDiagnostic("refine_component_tap components=${pick.removedComponents} primary=${pick.touchedPrimary}")
                when (smartTapOutcome(mode, pick, alpha)) {
                    SmartTapOutcome.Remove -> {
                        current.session.apply(RefineSession.Mode.Remove, pick.mask, hard = true)
                        photoHistory.discardRedo()
                        revision++
                        null
                    }
                    SmartTapOutcome.WholeGarmentHint -> wholeGarmentMessage
                    SmartTapOutcome.KeepHint -> keepHintMessage
                    SmartTapOutcome.NothingToRemove -> null
                }
            }
            hint?.let { snackbarHostState.showSnackbar(it) }
        }
    }

    fun finishGesture(
        path: List<Offset>,
        moved: Boolean,
        fit: FitRect,
        current: CutoutPhoto,
        touchedAt: Offset,
    ) {
        val w = current.source.width
        val h = current.source.height
        val photoPath = path.map { screenToPhoto(it, containerSize, zoom, pan, w, h) }
        if (brush) {
            commitStroke(photoPath, current, fit)
            return
        }
        pulse = PulseMark(touchedAt)
        val snapRadiusPx = snapRadiusPhotoPx(SMART_SNAP_DP, density, fit.width / w, zoom)
        runSmartSelection(smartSelectionPoints(photoPath, moved), current, snapRadiusPx)
    }

    /**
     * Moves between the photos of Cut out again: the photo on screen is written to a cache file and the other one is read
     * back with its own alpha history. A failure leaves the photo on screen as it was. Runs under sourceMutex.
     */
    suspend fun movePhoto(current: CutoutPhoto, forward: Boolean) {
        val restored = try {
            withContext(Dispatchers.IO) {
                if (forward) {
                    photoHistory.forward(current.source, current.session, current.fromRedo)
                } else {
                    photoHistory.back(current.source, current.session, current.fromRedo)
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            onDiagnostic("photo_history_failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
            return
        }
        if (restored == null) return
        photo = CutoutPhoto(restored.photo, restored.session, restored.fromRedo)
        current.source.recycle()
        revision++
    }

    /**
     * One Undo or Redo: the session's own edits first, replayed on Dispatchers.Default; once none are left, the photo before
     * or after it across a Cut out again (see [movePhoto]). Undo, Redo and the gestures wait until the step is done.
     */
    fun historyStep(forward: Boolean) {
        if (replaying) return
        replaying = true
        scope.launch {
            try {
                sourceMutex.withLock {
                    // Read under the lock: a stroke or a smart selection queued before this step may have replaced the photo.
                    val current = photo ?: return@withLock
                    val ownEdit = if (forward) current.session.canRedo else current.session.canUndo
                    if (ownEdit) {
                        withContext(Dispatchers.Default) {
                            if (forward) current.session.redo() else current.session.undo()
                        }
                        revision++
                    } else {
                        movePhoto(current, forward)
                    }
                }
            } finally {
                replaying = false
            }
        }
    }

    /** Undo: the session's own edits first; once none are left, the photo before the last Cut out again, with its edits. */
    fun undoEdit() = historyStep(forward = false)

    /** Redo: the session's own undone edits first; once none are left, the photo after it, with its edits. */
    fun redoEdit() = historyStep(forward = true)

    /**
     * Redo cutout: [choice] runs once on the raw photo. The working photo is replaced only when the new cutout is decoded,
     * and the stored photo is untouched until Done. A failure leaves the photo and its undo step as they were.
     */
    fun redoCutout(choice: RedoCutoutChoice, rawUri: Uri) {
        if (redoing || replaying || photo == null) return
        redoing = true
        scope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            val failure = try {
                val options = redoCutoutOptions(choice, CutoutEngineRegistry.options())
                // The new photo and its alpha are ready before the current photo is written away, so a failure changes nothing.
                // For a moment two bitmaps exist: the current photo and the new one (at most 2048 px on the long edge from ML Kit,
                // 1024 px from the precise engine).
                val cutout = runRedoCutout(context, rawUri, options, File(app.filesDir, CutoutPackManifest.DIRECTORY))
                val bitmap = cutout.bitmap
                // Until the new photo is on screen, any failure or cancellation recycles it.
                releaseOnFailure(release = { bitmap.recycle() }) {
                    val alpha = withContext(Dispatchers.Default) { alphaOf(bitmap) }
                    // The new cutout's recipe is kept only when the photo has an original: its canvas maps back through it.
                    val redoRecipe = if (originalUri != null) cutout.recipe?.atEngineCanvas(CanvasOffset(0, 0)) else null
                    val layer = withContext(Dispatchers.IO) {
                        if (originalUri != null && redoRecipe != null) loadOriginalLayer(context, originalUri, redoRecipe, bitmap.width, bitmap.height) else null
                    }
                    sourceMutex.withLock {
                        val previous = checkNotNull(photo)
                        // The session is built before the photo is written away, so a failed Cut out closes it.
                        val next = RefineSession(bitmap.width, bitmap.height, alpha, editBudget, layer, redoRecipe.takeIf { layer != null })
                        try {
                            withContext(Dispatchers.IO) {
                                if (keepsHistory) {
                                    photoHistory.cutOut(previous.source, previous.session, previous.fromRedo)
                                } else {
                                    previous.session.close()
                                }
                            }
                        } catch (throwable: Throwable) {
                            next.close()
                            throw throwable
                        }
                        previous.source.recycle()
                        photo = CutoutPhoto(bitmap, next, fromRedo = true)
                    }
                }
                onDiagnostic(redoDoneDiagnostic(choice, options, SystemClock.elapsedRealtime() - startedAt))
                null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                // Any throwable, LinkageError included, ends this Redo only: a native or class loading failure of the precise
                // engine must not crash the editor (the same rule as cutoutFallbackChain in CutoutEngineRegistry.kt).
                throwable
            }
            redoing = false
            if (failure != null) {
                onDiagnostic("redo_cutout_failure: ${failure::class.java.name}: ${failure.message ?: "n/a"}")
                snackbarHostState.showSnackbar(redoFailedMessage)
            }
        }
    }

    fun done() {
        val current = photo ?: return
        saving = true
        scope.launch {
            val written = try {
                Result.success(
                    if (current.session.isChanged || current.fromRedo) {
                        // A Redo result that was not edited is written as it is: its edges were refined when it was made.
                        sourceMutex.withLock {
                            withContext(Dispatchers.IO) { writeCutout(context, current, refine = current.session.isChanged) }
                        }
                    } else {
                        null
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
            written.fold(
                onSuccess = { crop ->
                    // The recipe follows the canvas: the cutout written here is cropped further than the editor's canvas.
                    val recipe = crop?.let { current.session.recipe?.trimmedBy(it.offset) } ?: current.session.recipe
                    onDone(crop?.uri, adjustments, recipe)
                },
                onFailure = { throwable ->
                    saving = false
                    onDiagnostic("refine_save_failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
                },
            )
        }
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cancel))
                    }
                    Text(
                        text = stringResource(R.string.photo_editor_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    TextButton(onClick = ::done, enabled = doneEnabled) {
                        Text(stringResource(R.string.done))
                    }
                }
            },
        ) { innerPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .onSizeChanged { containerSize = it }
                        .then(
                            if (tab == EditorTab.Light) {
                                Modifier.background(Brush.verticalGradient(listOf(Color(0xFFE9E4DC), Color(0xFFCFC3B4))))
                            } else {
                                Modifier
                            },
                        )
                        .pointerInput(tab, brush, mode, photo) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val path = mutableListOf(down.position)
                                var moved = false
                                var multiTouch = false
                                var anyPressed = true
                                if (tab == EditorTab.Light) showBefore = true
                                while (anyPressed) {
                                    val event = awaitPointerEvent()
                                    val pressed = event.changes.filter { it.pressed }
                                    if (pressed.size > 1) {
                                        multiTouch = true
                                        val (first, second) = pressed[0] to pressed[1]
                                        val previousDistance = (first.previousPosition - second.previousPosition).getDistance()
                                        val distance = (first.position - second.position).getDistance()
                                        val nextZoom = if (previousDistance > 0f) {
                                            (zoom * distance / previousDistance).coerceIn(MIN_ZOOM, MAX_ZOOM)
                                        } else {
                                            zoom
                                        }
                                        val delta = ((first.position + second.position) - (first.previousPosition + second.previousPosition)) / 2f
                                        zoom = nextZoom
                                        pan = clampPan(pan + delta, containerSize, nextZoom)
                                        event.changes.forEach(PointerInputChange::consume)
                                    } else if (pressed.size == 1 && !multiTouch) {
                                        val change = pressed.first()
                                        if ((change.position - down.position).getDistance() > touchSlop) moved = true
                                        path += change.position
                                        if (tab == EditorTab.Cutout) strokeScreen = path.toList()
                                        change.consume()
                                    }
                                    anyPressed = event.changes.any { it.pressed }
                                }
                                if (tab == EditorTab.Light) showBefore = false
                                strokeScreen = emptyList()
                                val current = photo ?: return@awaitEachGesture
                                if (tab != EditorTab.Cutout || multiTouch || redoing || replaying || containerSize == IntSize.Zero) {
                                    return@awaitEachGesture
                                }
                                val fit = fitRect(containerSize, current.source.width, current.source.height)
                                finishGesture(path.toList(), moved, fit, current, down.position)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val current = photo
                    if (tab == EditorTab.Light) {
                        // Hold shows the original photo as it was before this editor; otherwise the cutout with the Light filter.
                        val lightImage = if (showBefore) current?.source?.asImageBitmap() else preview
                        lightImage?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                colorFilter = if (showBefore) null else lightColorFilter(adjustments),
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = pan.x
                                    translationY = pan.y
                                },
                            )
                        }
                        if (showBefore) {
                            PreviewChip(stringResource(R.string.quick_edit_before_label), Modifier.align(Alignment.BottomCenter))
                        } else {
                            PreviewChip(stringResource(R.string.quick_edit_hold_compare_hint), Modifier.align(Alignment.BottomCenter))
                        }
                        if (preview == null && !showBefore) {
                            PreviewChip(stringResource(R.string.quick_edit_preview_loading), Modifier.align(Alignment.TopCenter))
                        }
                    } else {
                        Canvas(Modifier.fillMaxSize()) { drawCheckerboard() }
                        preview?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().graphicsLayer {
                                    scaleX = zoom
                                    scaleY = zoom
                                    translationX = pan.x
                                    translationY = pan.y
                                },
                            )
                        }
                        val overlayColor = (if (mode == RefineSession.Mode.Keep) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error).copy(alpha = 0.35f)
                        if (strokeScreen.size > 1) {
                            Canvas(Modifier.fillMaxSize()) {
                                val strokePath = Path().apply {
                                    moveTo(strokeScreen.first().x, strokeScreen.first().y)
                                    strokeScreen.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                // A smart trail is the brush trail, dashed; butt caps so the gaps show.
                                drawPath(
                                    path = strokePath,
                                    color = overlayColor,
                                    style = Stroke(
                                        width = BRUSH_RADIUS_DP.dp.toPx() * 2f,
                                        cap = if (brush) StrokeCap.Round else StrokeCap.Butt,
                                        join = StrokeJoin.Round,
                                        pathEffect = if (brush) {
                                            null
                                        } else {
                                            PathEffect.dashPathEffect(floatArrayOf(SMART_TRAIL_DASH_DP.dp.toPx(), SMART_TRAIL_GAP_DP.dp.toPx()))
                                        },
                                    ),
                                )
                            }
                        }
                        val pulseColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                        pulse?.let { mark ->
                            Canvas(Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = pulseColor,
                                    radius = PULSE_RADIUS_DP.dp.toPx(),
                                    center = mark.at,
                                )
                            }
                        }
                    }
                    if (current == null || redoing) {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                }

                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (tab == EditorTab.Cutout) {
                        CutoutControls(
                            mode = mode,
                            onModeChange = { mode = it },
                            brush = brush,
                            onBrushChange = { brush = it },
                            canUndo = canUndo && !redoing && !replaying && !saving,
                            canRedo = canRedo && !redoing && !replaying,
                            canReset = changed && !redoing && !replaying && !saving,
                            onUndo = ::undoEdit,
                            onRedoEdit = ::redoEdit,
                            onReset = ::resetEdits,
                            onRedo = if (rawSourceUri != null) ({ showRedoSheet = true }) else null,
                            redoEnabled = !redoing && !replaying && photo != null,
                        )
                    } else {
                        LightControls(
                            brightness = brightness,
                            temperature = temperature,
                            selectedTool = selectedTool,
                            onToolChange = { selectedTool = it },
                            onBrightnessChange = { brightness = it },
                            onTemperatureChange = { temperature = it },
                        )
                    }
                }

                TabRow(
                    selectedTabIndex = tab.ordinal,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                ) {
                    Tab(
                        selected = tab == EditorTab.Cutout,
                        onClick = { tab = EditorTab.Cutout },
                        text = { Text(stringResource(R.string.photo_editor_tab_cutout)) },
                    )
                    Tab(
                        selected = tab == EditorTab.Light,
                        onClick = { tab = EditorTab.Light },
                        text = { Text(stringResource(R.string.photo_editor_tab_light)) },
                    )
                }
            }
        }

        if (showRedoSheet && rawSourceUri != null) {
            ModalBottomSheet(onDismissRequest = { showRedoSheet = false }) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.redo_cutout),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    redoCutoutChoices(packReady, preciseTripped = CutoutEngineRegistry.preciseLatch.isTripped).forEach { choice ->
                        TextButton(
                            onClick = {
                                showRedoSheet = false
                                redoCutout(choice, Uri.parse(rawSourceUri))
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(redoChoiceLabelRes(choice)))
                        }
                    }
                }
            }
        }
    }
}

private fun redoChoiceLabelRes(choice: RedoCutoutChoice): Int = when (choice) {
    RedoCutoutChoice.Standard -> R.string.redo_cutout_standard
    RedoCutoutChoice.Precise -> R.string.redo_cutout_precise
    RedoCutoutChoice.PreciseKeepHanger -> R.string.redo_cutout_precise_keep_hanger
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutoutControls(
    mode: RefineSession.Mode,
    onModeChange: (RefineSession.Mode) -> Unit,
    brush: Boolean,
    onBrushChange: (Boolean) -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    canReset: Boolean,
    onUndo: () -> Unit,
    onRedoEdit: () -> Unit,
    onReset: () -> Unit,
    onRedo: (() -> Unit)?,
    redoEnabled: Boolean,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == RefineSession.Mode.Keep,
            onClick = { onModeChange(RefineSession.Mode.Keep) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            label = { Text(stringResource(R.string.refine_keep)) },
        )
        SegmentedButton(
            selected = mode == RefineSession.Mode.Remove,
            onClick = { onModeChange(RefineSession.Mode.Remove) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            label = { Text(stringResource(R.string.refine_remove)) },
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilterChip(
            selected = brush,
            onClick = { onBrushChange(!brush) },
            label = { Text(stringResource(R.string.refine_brush)) },
        )
        Spacer(Modifier.weight(1f))
        if (onRedo != null) {
            TextButton(onClick = onRedo, enabled = redoEnabled) {
                Text(stringResource(R.string.redo_cutout))
            }
        }
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.Rounded.Undo, contentDescription = stringResource(R.string.refine_undo))
        }
        IconButton(onClick = onRedoEdit, enabled = canRedo) {
            Icon(Icons.Rounded.Redo, contentDescription = stringResource(R.string.refine_redo))
        }
        IconButton(onClick = onReset, enabled = canReset) {
            Icon(Icons.Rounded.RestartAlt, contentDescription = stringResource(R.string.refine_reset))
        }
    }
    Text(
        text = stringResource(R.string.refine_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ColumnScope.LightControls(
    brightness: Float,
    temperature: Float,
    selectedTool: QuickEditTool,
    onToolChange: (QuickEditTool) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onTemperatureChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuickEditToolIconButton(
            icon = Icons.Rounded.Brightness6,
            label = stringResource(R.string.quick_edit_brightness_content_description),
            selected = selectedTool == QuickEditTool.Brightness,
            onClick = { onToolChange(QuickEditTool.Brightness) },
        )
        QuickEditToolIconButton(
            icon = Icons.Rounded.DeviceThermostat,
            label = stringResource(R.string.quick_edit_temperature_content_description),
            selected = selectedTool == QuickEditTool.Temperature,
            onClick = { onToolChange(QuickEditTool.Temperature) },
        )
    }

    val selectedToolLabel = when (selectedTool) {
        QuickEditTool.Brightness -> stringResource(R.string.quick_edit_brightness)
        QuickEditTool.Temperature -> stringResource(R.string.quick_edit_temperature)
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
            QuickEditBrightnessSlider(
                value = brightness,
                onValueChange = onBrightnessChange,
            )
        }
        QuickEditTool.Temperature -> {
            Text(stringResource(R.string.quick_edit_temperature_hint), style = MaterialTheme.typography.bodySmall)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuickEditTemperaturePreset(
                    labelRes = R.string.quick_edit_temperature_cooler,
                    value = -1f,
                    selectedValue = temperature,
                    modifier = Modifier.weight(1f),
                    onSelected = onTemperatureChange,
                )
                QuickEditTemperaturePreset(
                    labelRes = R.string.quick_edit_temperature_cool,
                    value = -0.5f,
                    selectedValue = temperature,
                    modifier = Modifier.weight(1f),
                    onSelected = onTemperatureChange,
                )
                QuickEditTemperaturePreset(
                    labelRes = R.string.quick_edit_temperature_neutral,
                    value = 0f,
                    selectedValue = temperature,
                    modifier = Modifier.weight(1f),
                    onSelected = onTemperatureChange,
                )
                QuickEditTemperaturePreset(
                    labelRes = R.string.quick_edit_temperature_warm,
                    value = 0.5f,
                    selectedValue = temperature,
                    modifier = Modifier.weight(1f),
                    onSelected = onTemperatureChange,
                )
                QuickEditTemperaturePreset(
                    labelRes = R.string.quick_edit_temperature_warmer,
                    value = 1f,
                    selectedValue = temperature,
                    modifier = Modifier.weight(1f),
                    onSelected = onTemperatureChange,
                )
            }
        }
    }
}

@Composable
private fun PreviewChip(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = modifier
            .padding(12.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.46f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private fun DrawScope.drawCheckerboard() {
    val cell = CHECKER_CELL_DP.dp.toPx()
    val columns = ceil(size.width / cell).toInt()
    val rows = ceil(size.height / cell).toInt()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val color = if ((row + column) % 2 == 0) Color(0xFFEEEEEE) else Color.White
            drawRect(color = color, topLeft = Offset(column * cell, row * cell), size = Size(cell, cell))
        }
    }
}

/** Where the photo sits inside the preview at zoom 1 (ContentScale.Fit), in container pixels. */
internal class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

internal fun fitRect(container: IntSize, photoWidth: Int, photoHeight: Int): FitRect {
    val containerWidth = container.width.toFloat()
    val containerHeight = container.height.toFloat()
    val scale = min(containerWidth / photoWidth, containerHeight / photoHeight)
    val width = photoWidth * scale
    val height = photoHeight * scale
    return FitRect((containerWidth - width) / 2f, (containerHeight - height) / 2f, width, height)
}

/** Inverse of the preview transform (scale about the centre, then pan) and of the fit: a screen point in photo pixels. */
internal fun screenToPhoto(screen: Offset, container: IntSize, zoom: Float, pan: Offset, photoWidth: Int, photoHeight: Int): Offset {
    val center = Offset(container.width / 2f, container.height / 2f)
    val local = center + (screen - center - pan) / zoom
    val fit = fitRect(container, photoWidth, photoHeight)
    val photoPerFit = photoWidth / fit.width
    return Offset((local.x - fit.left) * photoPerFit, (local.y - fit.top) * photoPerFit)
}

internal fun clampPan(pan: Offset, container: IntSize, zoom: Float): Offset {
    val limitX = (zoom - 1f) * container.width / 2f
    val limitY = (zoom - 1f) * container.height / 2f
    return Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
}

/**
 * The pixel a smart tap at photo point [point] selects, as the (x, y) that select() receives. A photo point is continuous and
 * pixel i covers [i, i + 1), so the pixel is the floor of the point, clamped to the photo. Mask values are row-major: index y * width + x.
 */
internal fun selectPixelOf(point: Offset, width: Int, height: Int): Pair<Int, Int> =
    floor(point.x).toInt().coerceIn(0, width - 1) to floor(point.y).toInt().coerceIn(0, height - 1)

/**
 * The points a smart gesture selects: a tap its first point; a drag of any length its path sampled to at most [max] points. There is no
 * time limit on a drag, so a slow drag selects like a quick one.
 */
internal fun smartSelectionPoints(path: List<Offset>, moved: Boolean, max: Int = SMART_MAX_POINTS): List<Offset> =
    if (moved) samplePath(path, max) else path.take(1)

/**
 * [points] sampled to at most [max] points, evenly spaced by distance along the path and always starting and ending on its
 * first and last point. A path that is already short enough is returned as it is.
 */
internal fun samplePath(points: List<Offset>, max: Int = SMART_MAX_POINTS): List<Offset> {
    require(max >= 2) { "A sampled path needs at least its two ends" }
    if (points.size <= max) return points.toList()
    // Distance along the path at each vertex.
    val along = FloatArray(points.size)
    for (i in 1 until points.size) along[i] = along[i - 1] + (points[i] - points[i - 1]).getDistance()
    val total = along.last()
    if (total == 0f) return listOf(points.first())
    val sampled = ArrayList<Offset>(max)
    var segment = 0
    for (k in 0 until max) {
        val target = total * k / (max - 1)
        while (segment < points.size - 2 && along[segment + 1] < target) segment++
        val span = along[segment + 1] - along[segment]
        val t = if (span > 0f) ((target - along[segment]) / span).coerceIn(0f, 1f) else 0f
        sampled += points[segment] + (points[segment + 1] - points[segment]) * t
    }
    return sampled
}

private fun alphaOf(bitmap: Bitmap): FloatArray {
    val argb = argbOf(bitmap)
    return FloatArray(argb.size) { i -> (argb[i] ushr 24) / 255f }
}

private fun argbOf(bitmap: Bitmap): IntArray {
    val pixels = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return pixels
}

private fun toAlphaBytes(alpha: FloatArray): ByteArray =
    ByteArray(alpha.size) { i -> (alpha[i].coerceIn(0f, 1f) * 255f).roundToInt().toByte() }

private fun composeWithAlpha(argb: IntArray, alpha: ByteArray, width: Int, height: Int): Bitmap {
    val out = IntArray(argb.size) { i -> ((alpha[i].toInt() and 0xFF) shl 24) or (argb[i] and 0xFFFFFF) }
    return Bitmap.createBitmap(out, width, height, Bitmap.Config.ARGB_8888)
}

/**
 * The line Developer diagnostics get after a Redo that ran. The flags say what really ran: hanger removal applies only to the
 * precise engine, so a Standard run reports removeHanger=false whatever the saved setting is.
 */
internal fun redoDoneDiagnostic(choice: RedoCutoutChoice, options: CutoutOptions, elapsedMs: Long): String =
    "redo_cutout_done: choice=${choice.name} precise=${options.usePrecise} " +
        "removeHanger=${options.usePrecise && options.removeHanger} ms=$elapsedMs"

/**
 * The Light transform as a colour matrix: brightness adds the same offset to every channel, temperature moves red up and
 * blue down (the same translation as QuickEditImageProcessor). Shown in memory, so the preview writes no file.
 */
private fun lightColorFilter(adjustments: QuickEditAdjustments): ColorFilter {
    val brightnessOffset = adjustments.brightness.coerceIn(-1f, 1f) * LIGHT_BRIGHTNESS_OFFSET
    val warmth = adjustments.temperature.coerceIn(-1f, 1f) * LIGHT_TEMPERATURE_OFFSET
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, warmth + brightnessOffset,
                0f, 1f, 0f, 0f, brightnessOffset,
                0f, 0f, 1f, 0f, brightnessOffset - warmth,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
}

/**
 * Done path for a changed cutout: AlphaRefiner edges on the edited alpha, RGB from the stored PNG, then the same
 * alpha crop as the garment import. The only write of the editor; the uncropped intermediate is removed.
 * [refine] is false for an unedited Redo result, whose edges were refined when it was made.
 */
private fun writeCutout(context: Context, photo: CutoutPhoto, refine: Boolean = true): TransparentCrop {
    val source = photo.source
    val argb = photo.colour()
    val alpha = if (refine) {
        AlphaRefiner.refine(argb, photo.session.currentAlpha(), source.width, source.height, CutoutEngineRegistry.options().edges)
    } else {
        toAlphaBytes(photo.session.currentAlpha())
    }
    val bitmap = composeWithAlpha(argb, alpha, source.width, source.height)
    val intermediate = try {
        ClothingImageStore.writeProcessedBitmap(context, bitmap, prefix = "cutout-edit")
    } finally {
        bitmap.recycle()
    }
    val cropped = try {
        ClothingImageStore.cropTransparentPixels(context, intermediate)
    } catch (throwable: Throwable) {
        context.contentResolver.delete(intermediate, null, null)
        throw throwable
    }
    if (cropped.uri != intermediate) context.contentResolver.delete(intermediate, null, null)
    return cropped
}

/** The original in the canvas of a stored cutout of [canvasW] × [canvasH], or null when it cannot be read (R3-DEC-039). */
private fun loadOriginalLayer(context: Context, originalUri: String, recipe: CutoutRecipe, canvasW: Int, canvasH: Int): IntArray? {
    val original = decodeOriginal(context, originalUri) ?: return null
    return try {
        originalLayer(argbOf(original), original.width, original.height, canvasW, canvasH, recipe)
    } finally {
        original.recycle()
    }
}

/** The saved original, or null when it is missing or unreadable (a deleted file, a denied URI, a failed read). */
private fun decodeOriginal(context: Context, originalUri: String): Bitmap? = try {
    context.contentResolver.openInputStream(Uri.parse(originalUri))?.use(BitmapFactory::decodeStream)
} catch (failure: IOException) {
    null
} catch (failure: SecurityException) {
    null
}

/** The output of one Redo run: the cutout as a bitmap, and the recipe that cut it. */
private class RedoCutout(val bitmap: Bitmap, val recipe: CutoutRecipe?)

/**
 * One Redo run: [options] on the raw photo, decoded to a bitmap. The engine's output is copied to a temp PNG in the app
 * cache, decoded and deleted, and the engine's copy in the pictures folder is removed too. The temp file therefore never
 * outlives this call (nothing is left behind by a failure or a cancellation). File and resolver work runs on IO.
 */
private suspend fun runRedoCutout(context: Context, rawUri: Uri, options: CutoutOptions, packDir: File): RedoCutout {
    val engine: BackgroundRemovalEngine = if (options.usePrecise) {
        PreciseCutoutEngine(packDir = packDir, options = { options })
    } else {
        MlKitSubjectSegmentationBackgroundRemovalEngine(edges = { options.edges })
    }
    val result = engine.removeBackground(context, rawUri)
    check(result.status == BackgroundRemovalStatus.REMOVED) { "Cutout was not removed" }
    return withContext(Dispatchers.IO) {
        try {
            val temp = File.createTempFile("redo-cutout-", ".png", File(context.cacheDir, REDO_CACHE_DIRECTORY).apply { mkdirs() })
            try {
                val input = context.contentResolver.openInputStream(result.outputUri) ?: error("Unable to read the new cutout")
                input.use { source -> temp.outputStream().use { target -> source.copyTo(target) } }
                RedoCutout(BitmapFactory.decodeFile(temp.path) ?: error("Unable to decode the new cutout"), result.recipe)
            } finally {
                temp.delete()
            }
        } finally {
            context.contentResolver.delete(result.outputUri, null, null)
        }
    }
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
private fun QuickEditBrightnessSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val normalizedValue = ((value.coerceIn(-1f, 1f) + 1f) / 2f).coerceIn(0f, 1f)
    val sliderContentDescription = stringResource(R.string.quick_edit_brightness_content_description)
    val thumbSize = 24.dp

    fun updateValue(pointerX: Float, width: Int) {
        if (width <= 0) return
        val normalized = (pointerX / width).coerceIn(0f, 1f)
        onValueChange((normalized * 2f) - 1f)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics { contentDescription = sliderContentDescription }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    updateValue(down.position.x, size.width)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChange = event.changes.firstOrNull { change -> change.pressed } ?: break
                        updateValue(pressedChange.position.x, size.width)
                    }
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(normalizedValue)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Box(
            modifier = Modifier
                .padding(start = (maxWidth - thumbSize) * normalizedValue)
                .size(thumbSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape),
        )
    }
}

@Composable
private fun QuickEditTemperaturePreset(
    labelRes: Int,
    value: Float,
    selectedValue: Float,
    modifier: Modifier = Modifier,
    onSelected: (Float) -> Unit,
) {
    val selected = selectedValue == value
    val label = stringResource(labelRes)
    val selectedStatus = stringResource(
        if (selected) R.string.quick_edit_temperature_selected else R.string.quick_edit_temperature_not_selected,
    )
    val contentDescription = stringResource(
        R.string.quick_edit_temperature_preset_content_description,
        label,
        selectedStatus,
    )
    val swatchColor = when (value) {
        -1f -> Color(0xFF0B4FB3)
        -0.5f -> Color(0xFF5FA8FF)
        0f -> MaterialTheme.colorScheme.surfaceContainerHigh
        0.5f -> Color(0xFFFFB36B)
        else -> Color(0xFFE56A1F)
    }
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { onSelected(value) }
            .semantics {
                this.contentDescription = contentDescription
                this.selected = selected
            }
            .padding(2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(swatchColor)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

private enum class QuickEditTool { Brightness, Temperature }
