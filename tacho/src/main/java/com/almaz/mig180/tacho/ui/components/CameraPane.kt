package com.almaz.mig180.tacho.ui.components

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.almaz.mig180.tacho.core.FrameGeometry
import com.almaz.mig180.tacho.core.Roi
import com.almaz.mig180.tacho.core.RoiMapper
import com.almaz.mig180.tacho.ui.theme.Amber
import com.almaz.mig180.tacho.ui.theme.Mint
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val TARGET_RESOLUTION = Size(1280, 720)

/**
 * Превью камеры с оверлеем ROI. Тап по кадру задаёт точку наблюдения за меткой.
 * Камера освобождается при уходе с экрана / в фон (DisposableEffect + жизненный цикл).
 */
@Composable
fun CameraPane(
    running: Boolean,
    torchOn: Boolean,
    analyzer: ImageAnalysis.Analyzer,
    geometry: FrameGeometry,
    roi: Roi?,
    highlight: Boolean,
    onRoiPicked: (Roi) -> Unit,
    onTorchAvailable: (Boolean) -> Unit,
    onCameraStopped: () -> Unit,
    onError: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    val displayRotation = remember(configuration) {
        ContextCompat.getDisplayOrDefault(context).rotation
    }

    LaunchedEffect(Unit) {
        provider = try {
            context.awaitCameraProvider()
        } catch (t: Throwable) {
            onError("Не удалось открыть камеру: ${t.message}")
            null
        }
    }

    DisposableEffect(provider, running, displayRotation, lifecycleOwner) {
        val activeProvider = provider
        if (activeProvider != null && running) {
            try {
                camera = bindUseCases(
                    provider = activeProvider,
                    lifecycleOwner = lifecycleOwner,
                    previewView = previewView,
                    analyzer = analyzer,
                    executor = executor,
                    rotation = displayRotation,
                )
                onError(null)
                onTorchAvailable(camera?.cameraInfo?.hasFlashUnit() == true)
            } catch (t: Throwable) {
                camera = null
                onError("Камера занята или недоступна: ${t.message}")
            }
        }
        onDispose {
            try {
                activeProvider?.unbindAll()
            } catch (_: Throwable) {
                // Провайдер мог уже быть освобождён — это не ошибка.
            }
            camera = null
            onCameraStopped()
        }
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    LaunchedEffect(camera, torchOn) {
        val active = camera ?: return@LaunchedEffect
        if (active.cameraInfo.hasFlashUnit()) {
            try {
                active.cameraControl.enableTorch(torchOn)
            } catch (_: Throwable) {
                // Фонарик мог быть недоступен на этой сессии камеры.
            }
        }
    }

    Box(
        modifier = modifier.pointerInput(geometry) {
            detectTapGestures { offset ->
                val picked = RoiMapper.viewToBuffer(
                    tapX = offset.x,
                    tapY = offset.y,
                    viewWidth = size.width.toFloat(),
                    viewHeight = size.height.toFloat(),
                    geometry = geometry,
                )
                if (picked != null) onRoiPicked(picked)
            }
        }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        Canvas(modifier = Modifier.fillMaxSize()) {
            val current = roi ?: return@Canvas
            val mapped = RoiMapper.bufferToView(current, size.width, size.height, geometry)
                ?: return@Canvas
            val (cx, cy, radius) = mapped
            val ringColor = if (highlight) Mint else Amber
            val ringWidth = if (highlight) 6f else 3f
            drawCircle(
                color = ringColor,
                radius = radius.coerceAtLeast(6f),
                center = Offset(cx, cy),
                style = Stroke(width = ringWidth),
            )
            drawCircle(
                color = ringColor.copy(alpha = if (highlight) 0.35f else 0.12f),
                radius = radius.coerceAtLeast(6f),
                center = Offset(cx, cy),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = 2f,
                center = Offset(cx, cy),
            )
        }
    }
}

private suspend fun Context.awaitCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                continuation.resume(future.get())
            } catch (t: Throwable) {
                continuation.resumeWithException(t)
            }
        }, ContextCompat.getMainExecutor(this))
    }

private fun bindUseCases(
    provider: ProcessCameraProvider,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    analyzer: ImageAnalysis.Analyzer,
    executor: ExecutorService,
    rotation: Int,
): Camera {
    provider.unbindAll()
    val selector = CameraSelector.DEFAULT_BACK_CAMERA

    val preview = Preview.Builder()
        .setTargetResolution(TARGET_RESOLUTION)
        .setTargetRotation(rotation)
        .build()
    preview.setSurfaceProvider(previewView.surfaceProvider)

    val fpsRange = pickFpsRange(provider, selector)
    return try {
        val analysis = buildAnalysis(analyzer, executor, rotation, fpsRange)
        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
    } catch (t: Throwable) {
        // Откат: устройство не приняло запрошенный диапазон кадров — берём режим по умолчанию.
        provider.unbindAll()
        val analysis = buildAnalysis(analyzer, executor, rotation, null)
        provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
    }
}

@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun buildAnalysis(
    analyzer: ImageAnalysis.Analyzer,
    executor: ExecutorService,
    rotation: Int,
    fpsRange: Range<Int>?,
): ImageAnalysis {
    val builder = ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
        .setTargetResolution(TARGET_RESOLUTION)
        .setTargetRotation(rotation)
    if (fpsRange != null) {
        Camera2Interop.Extender(builder)
            .setCaptureRequestOption(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange)
    }
    val analysis = builder.build()
    analysis.setAnalyzer(executor, analyzer)
    return analysis
}

/** Просим 60 fps; если аппарат столько не умеет — откатываемся на 30. */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
private fun pickFpsRange(provider: ProcessCameraProvider, selector: CameraSelector): Range<Int>? = try {
    val info = selector.filter(provider.availableCameraInfos).firstOrNull()
    val ranges = info?.let {
        Camera2CameraInfo.from(it)
            .getCameraCharacteristic(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
    }
    val list = ranges?.toList().orEmpty()
    list.firstOrNull { it.lower == 60 && it.upper == 60 }
        ?: list.firstOrNull { it.upper == 60 }
        ?: list.firstOrNull { it.lower == 30 && it.upper == 30 }
        ?: list.filter { it.upper <= 60 }.maxByOrNull { it.upper }
} catch (t: Throwable) {
    null
}
