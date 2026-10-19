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
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Undo
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import com.gusanitolabs.robia.media.AlphaRefiner
import com.gusanitolabs.robia.media.ClothingImageStore
import com.gusanitolabs.robia.media.QuickEditAdjustments
import com.gusanitolabs.robia.media.cutout.CutoutEngineRegistry
import com.gusanitolabs.robia.media.cutout.MediaPipeInteractiveSelector
import com.gusanitolabs.robia.media.cutout.RefineSession
import com.gusanitolabs.robia.media.cutout.brushMask
import com.gusanitolabs.robia.media.cutout.brushRadiusPx
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

private const val SMART_SPACING_DP = 24f
private const val SMART_DRAG_MAX_MS = 600L
private const val PULSE_MS = 300L
private const val PULSE_RADIUS_DP = 20f
private const val BRUSH_RADIUS_DP = 24f
private const val BRUSH_FEATHER_DP = 6f
private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f
private const val CHECKER_CELL_DP = 12f
// Same offsets as QuickEditImageProcessor, which applies the Light adjustments on Done; keep them in step.
private const val LIGHT_BRIGHTNESS_OFFSET = 110f
private const val LIGHT_TEMPERATURE_OFFSET = 46f

private enum class EditorTab { Cutout, Light }

/** The processed PNG being edited: its RGB stays fixed, its alpha is changed through [session]. */
private class CutoutPhoto(val source: Bitmap, val session: RefineSession)

/** A tap or drag that must be pulsed for [PULSE_MS]; a new instance per touch so repeated touches restart the pulse. */
private class PulseMark(val at: Offset)

/**
 * Full-screen photo editor (SG3, UX-DEC-011): Cutout (smart tap/swipe selection and a brush) and Light (brightness and
 * temperature). Works on the stored processed PNG, so it needs no downloadable pack. Cancel writes nothing; Done writes
 * one new PNG for the cutout and hands it to the caller, which applies the Light adjustments and re-analyses the garment.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(
    photoUri: String,
    onCancel: () -> Unit,
    onDiagnostic: (String) -> Unit,
    onDone: (cutoutUri: Uri?, adjustments: QuickEditAdjustments) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val noSourceHint = stringResource(R.string.refine_no_source_hint)
    val snackbarHostState = remember { SnackbarHostState() }
    val selector = remember { lazy { MediaPipeInteractiveSelector(context) } }
    // Guards every read of photo.source and every use of the selector, so dispose can close and recycle after they finish.
    val sourceMutex = remember { Mutex() }

    var tab by rememberSaveable { mutableStateOf(EditorTab.Cutout) }
    var mode by rememberSaveable { mutableStateOf(RefineSession.Mode.Keep) }
    var brush by rememberSaveable { mutableStateOf(false) }
    var brightness by rememberSaveable { mutableStateOf(0f) }
    var temperature by rememberSaveable { mutableStateOf(0f) }
    var selectedTool by rememberSaveable { mutableStateOf(QuickEditTool.Brightness) }
    var showBefore by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var photo by remember(photoUri) { mutableStateOf<CutoutPhoto?>(null) }
    var revision by remember(photoUri) { mutableIntStateOf(0) }
    var preview by remember(photoUri) { mutableStateOf<ImageBitmap?>(null) }
    var zoom by remember { mutableFloatStateOf(MIN_ZOOM) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var strokeScreen by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var pulse by remember { mutableStateOf<PulseMark?>(null) }

    val session = photo?.session
    val canUndo = remember(revision, session) { session?.canUndo == true }
    val changed = remember(revision, session) { session?.isChanged == true }
    val adjustments = QuickEditAdjustments(brightness, temperature)
    val doneEnabled = !saving && photo != null && (changed || adjustments.hasColorAdjustment)

    DisposableEffect(Unit) {
        onDispose {
            val leaving = photo
            // Runs outside the composition scope, which is cancelled here and would not wait for a running select or save.
            CoroutineScope(Dispatchers.Default).launch {
                sourceMutex.withLock {
                    if (selector.isInitialized()) selector.value.close()
                    leaving?.source?.recycle()
                }
            }
        }
    }

    LaunchedEffect(photoUri) {
        val bitmap = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(Uri.parse(photoUri))?.use(BitmapFactory::decodeStream)
        } ?: return@LaunchedEffect
        val alpha = withContext(Dispatchers.Default) { alphaOf(bitmap) }
        photo = CutoutPhoto(bitmap, RefineSession(bitmap.width, bitmap.height, alpha))
        // The processed PNG is the only source here: the original photo is not kept with the garment (see receipt).
        snackbarHostState.showSnackbar(noSourceHint)
    }

    LaunchedEffect(photo, revision) {
        val current = photo ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) {
            sourceMutex.withLock {
                val alphaBytes = toAlphaBytes(current.session.currentAlpha())
                composeWithAlpha(argbOf(current.source), alphaBytes, current.source.width, current.source.height).asImageBitmap()
            }
        }
    }

    LaunchedEffect(pulse) {
        if (pulse != null) {
            delay(PULSE_MS)
            pulse = null
        }
    }

    fun commitStroke(points: List<Offset>, current: CutoutPhoto, fit: FitRect) {
        val w = current.source.width
        val h = current.source.height
        val radius = brushRadiusPx(BRUSH_RADIUS_DP, density, zoom) * (w / fit.width)
        val feather = brushRadiusPx(BRUSH_FEATHER_DP, density, zoom) * (w / fit.width)
        val mask = brushMask(points.map { it.x to it.y }, radius, feather, w, h)
        current.session.apply(mode, mask, hard = false)
        revision++
    }

    fun runSmartSelection(points: List<Offset>, current: CutoutPhoto) {
        scope.launch {
            sourceMutex.withLock {
                val w = current.source.width
                val h = current.source.height
                val union = FloatArray(w * h)
                val timings = mutableListOf<Long>()
                val result = runCatching {
                    withContext(Dispatchers.Default) {
                        val instance = selector.value
                        for (point in points) {
                            val started = SystemClock.elapsedRealtime()
                            val mask = instance.select(
                                current.source,
                                point.x.roundToInt().coerceIn(0, w - 1),
                                point.y.roundToInt().coerceIn(0, h - 1),
                            )
                            timings += SystemClock.elapsedRealtime() - started
                            for (i in union.indices) if (mask[i] > union[i]) union[i] = mask[i]
                        }
                    }
                }
                timings.forEach { onDiagnostic("refine_tap_ms=$it") }
                result.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    onDiagnostic("refine_select_failure: ${throwable::class.java.name}: ${throwable.message ?: "n/a"}")
                    return@withLock
                }
                current.session.apply(mode, union, hard = true)
                revision++
            }
        }
    }

    fun finishGesture(
        path: List<Offset>,
        moved: Boolean,
        startedAt: Long,
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
        // A smart drag that lasts 600 ms or more does nothing (accepted choice); a tap or a short drag selects.
        if (moved && SystemClock.uptimeMillis() - startedAt >= SMART_DRAG_MAX_MS) return
        val spacing = brushRadiusPx(SMART_SPACING_DP, density, zoom) * (w / fit.width)
        val points = if (moved) sampleEvery(photoPath, spacing) else photoPath.take(1)
        pulse = PulseMark(touchedAt)
        runSmartSelection(points, current)
    }

    fun done() {
        val current = photo ?: return
        saving = true
        scope.launch {
            val cutoutUri = try {
                Result.success(
                    if (current.session.isChanged) {
                        sourceMutex.withLock { withContext(Dispatchers.IO) { writeCutout(context, current) } }
                    } else {
                        null
                    },
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                Result.failure(throwable)
            }
            cutoutUri.fold(
                onSuccess = { uri -> onDone(uri, adjustments) },
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
                                val startedAt = SystemClock.uptimeMillis()
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
                                        if (tab == EditorTab.Cutout && brush) strokeScreen = path.toList()
                                        change.consume()
                                    }
                                    anyPressed = event.changes.any { it.pressed }
                                }
                                if (tab == EditorTab.Light) showBefore = false
                                strokeScreen = emptyList()
                                val current = photo ?: return@awaitEachGesture
                                if (tab != EditorTab.Cutout || multiTouch || containerSize == IntSize.Zero) return@awaitEachGesture
                                val fit = fitRect(containerSize, current.source.width, current.source.height)
                                finishGesture(path.toList(), moved, startedAt, fit, current, down.position)
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
                                drawPath(
                                    path = strokePath,
                                    color = overlayColor,
                                    style = Stroke(width = BRUSH_RADIUS_DP.dp.toPx() * 2f, cap = StrokeCap.Round, join = StrokeJoin.Round),
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
                    if (current == null) {
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
                            canUndo = canUndo,
                            canReset = changed,
                            onUndo = {
                                photo?.session?.undo()
                                revision++
                            },
                            onReset = {
                                photo?.session?.reset()
                                revision++
                            },
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CutoutControls(
    mode: RefineSession.Mode,
    onModeChange: (RefineSession.Mode) -> Unit,
    brush: Boolean,
    onBrushChange: (Boolean) -> Unit,
    canUndo: Boolean,
    canReset: Boolean,
    onUndo: () -> Unit,
    onReset: () -> Unit,
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
        IconButton(onClick = onUndo, enabled = canUndo) {
            Icon(Icons.Rounded.Undo, contentDescription = stringResource(R.string.refine_undo))
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
private class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fitRect(container: IntSize, photoWidth: Int, photoHeight: Int): FitRect {
    val containerWidth = container.width.toFloat()
    val containerHeight = container.height.toFloat()
    val scale = min(containerWidth / photoWidth, containerHeight / photoHeight)
    val width = photoWidth * scale
    val height = photoHeight * scale
    return FitRect((containerWidth - width) / 2f, (containerHeight - height) / 2f, width, height)
}

/** Inverse of the preview transform (scale about the centre, then pan) and of the fit: a screen point in photo pixels. */
private fun screenToPhoto(screen: Offset, container: IntSize, zoom: Float, pan: Offset, photoWidth: Int, photoHeight: Int): Offset {
    val center = Offset(container.width / 2f, container.height / 2f)
    val local = center + (screen - center - pan) / zoom
    val fit = fitRect(container, photoWidth, photoHeight)
    val photoPerFit = photoWidth / fit.width
    return Offset((local.x - fit.left) * photoPerFit, (local.y - fit.top) * photoPerFit)
}

private fun clampPan(pan: Offset, container: IntSize, zoom: Float): Offset {
    val limitX = (zoom - 1f) * container.width / 2f
    val limitY = (zoom - 1f) * container.height / 2f
    return Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))
}

/** Keeps a point every [spacing] photo pixels along the drag, so a long drag selects a few points rather than every frame. */
private fun sampleEvery(points: List<Offset>, spacing: Float): List<Offset> {
    if (points.isEmpty()) return emptyList()
    val sampled = mutableListOf(points.first())
    for (point in points.drop(1)) {
        if ((point - sampled.last()).getDistance() >= spacing) sampled += point
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
 */
private fun writeCutout(context: Context, photo: CutoutPhoto): Uri {
    val source = photo.source
    val argb = argbOf(source)
    val alpha = AlphaRefiner.refine(argb, photo.session.currentAlpha(), source.width, source.height, CutoutEngineRegistry.options().edges)
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
    if (cropped != intermediate) context.contentResolver.delete(intermediate, null, null)
    return cropped
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
