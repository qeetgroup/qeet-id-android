package com.qeetgroup.qeetid.scanner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.ViewGroup
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.qeetgroup.qeetid.ui.LocalAppContainer
import com.qeetgroup.qeetrix.components.scanner.QeetrixQRScanner
import com.qeetgroup.qeetrix.components.scanner.QeetrixScannerControlsLayout
import com.qeetgroup.qeetrix.components.scanner.QeetrixScannerState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Qeetrix's QR scanner over the back camera (CameraX), reading QR codes on the phone with ZXing — nothing leaves the
 * phone, and what it reads (a setup key, a pairing code) is never logged. It asks for the camera with Qeetrix's
 * primer, says how to fix a refusal, and keeps [onEnterKey] in every state.
 *
 * [onCode] gets each QR code read while [paused] is false; return true to stop reading (the screen is done with it).
 */
@Composable
fun QrScanner(
    guidance: String,
    enterKeyLabel: String,
    deniedDescription: String,
    onEnterKey: () -> Unit,
    onCode: (String) -> Boolean,
    modifier: Modifier = Modifier,
    paused: Boolean = false,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val preferences = LocalAppContainer.current.preferences
    var state by remember { mutableStateOf(cameraState(context, activity, preferences.cameraAsked)) }
    var torch by remember { mutableStateOf(false) }
    var hasTorch by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    val currentOnCode by rememberUpdatedState(onCode)
    val currentPaused by rememberUpdatedState(paused)

    LifecycleResumeEffect(Unit) {
        // The person may have allowed the camera in Settings meanwhile.
        state = cameraState(context, activity, preferences.cameraAsked)
        onPauseOrDispose { torch = false }
    }
    val ask =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            preferences.cameraAsked = true
            state = cameraState(context, activity, asked = true)
        }

    QeetrixQRScanner(
        state = state,
        modifier = modifier,
        guidance = guidance,
        torch = torch,
        onTorchChange = if (hasTorch) ({ torch = it }) else null,
        onEnterKey = onEnterKey,
        onAllowCamera = { ask.launch(Manifest.permission.CAMERA) },
        enterKeyLabel = enterKeyLabel,
        deniedDescription = deniedDescription,
        // The typed way in and the torch in one row, the typed way filling it.
        controlsLayout = QeetrixScannerControlsLayout.Row,
    ) {
        CameraPreview(
            torch = torch,
            onTorchAvailable = { hasTorch = it },
            onCode = { text ->
                if (!done && !currentPaused && currentOnCode(text)) done = true
            },
        )
    }
}

private fun cameraState(
    context: Context,
    activity: android.app.Activity?,
    asked: Boolean,
): QeetrixScannerState =
    when {
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) -> {
            QeetrixScannerState.NoCamera
        }

        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
            QeetrixScannerState.Scanning
        }

        asked && activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
            QeetrixScannerState.PermissionDenied
        }

        else -> {
            QeetrixScannerState.PermissionNeeded
        }
    }

@Composable
private fun CameraPreview(
    torch: Boolean,
    onTorchAvailable: (Boolean) -> Unit,
    onCode: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val previewView =
        remember {
            PreviewView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val analyzer: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    LaunchedEffect(lifecycleOwner) {
        val provider = ProcessCameraProvider.awaitInstance(context)
        cameraProvider = provider
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { image ->
                    image.setAnalyzer(analyzer, QrAnalyzer { text -> previewView.post { currentOnCode(text) } })
                }
        provider.unbindAll()
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        onTorchAvailable(camera?.cameraInfo?.hasFlashUnit() == true)
    }
    DisposableEffect(Unit) {
        onDispose {
            cameraProvider?.unbindAll()
            analyzer.shutdown()
        }
    }
    LaunchedEffect(camera, torch) { camera?.cameraControl?.enableTorch(torch) }

    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

/** Reads QR codes from the camera's luminance plane with ZXing, dark-on-light and light-on-dark. */
private class QrAnalyzer(
    private val onText: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val reader = QRCodeReader()
    private val hints =
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.ALSO_INVERTED to true,
        )

    override fun analyze(image: ImageProxy) {
        image.use {
            val plane = it.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val source = PlanarYUVLuminanceSource(data, plane.rowStride, it.height, 0, 0, it.width, it.height, false)
            try {
                val text = reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
                if (!text.isNullOrBlank()) onText(text)
            } catch (_: ReaderException) {
                // No QR code in this frame.
            } finally {
                reader.reset()
            }
        }
    }
}
