package com.manhunt.app.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.manhunt.app.R
import com.manhunt.app.ui.theme.AppleSeekerOrange
import com.manhunt.app.ui.theme.AppleTextPrimary
import com.manhunt.app.ui.theme.AppleTextSecondary
import com.manhunt.app.util.CatchQrPayload
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "QrScanner"

/**
 * Seeker-side reader for the Hider's tag QR.
 *
 * Asks for the camera only when it is first shown (i.e. when the Seeker taps "Scan QR"), shows a
 * plain explanation plus a way to recover if the permission was denied, and decodes frames locally
 * with ZXing. Nothing is recorded, saved or uploaded. Typing the code always remains available.
 *
 * [onPayload] fires for a well-formed MANHNT QR, [onUnrecognized] for any other QR. Both are
 * delivered on the main thread, and the same QR is not re-reported for 2.5 s.
 */
@Composable
fun QrScannerView(
    onPayload: (CatchQrPayload) -> Unit,
    onUnrecognized: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    fun cameraGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(cameraGranted()) }
    var requestedOnce by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        requestedOnce = true
    }

    // The permission may be granted from system settings; pick that up when the app resumes.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = cameraGranted()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        CameraPreviewWithAnalyzer(
            onPayload = onPayload,
            onUnrecognized = onUnrecognized,
            modifier = modifier
        )
    } else {
        CameraPermissionPrompt(
            showSettingsButton = requestedOnce,
            onAllow = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                val intent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            },
            modifier = modifier
        )
    }
}

@Composable
private fun CameraPreviewWithAnalyzer(
    onPayload: (CatchQrPayload) -> Unit,
    onUnrecognized: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPayload by rememberUpdatedState(onPayload)
    val currentOnUnrecognized by rememberUpdatedState(onUnrecognized)
    val viewfinderDescription = stringResource(R.string.scan_camera_description)

    // Analysis only: no still-capture use case, which keeps the camera pipeline (and battery) lean.
    // Use cases must be set before the controller is attached to the PreviewView.
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
        }
    }

    DisposableEffect(cameraController, lifecycleOwner) {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val mainHandler = Handler(Looper.getMainLooper())
        cameraController.setImageAnalysisAnalyzer(
            executor,
            QrFrameAnalyzer { raw ->
                val payload = CatchQrPayload.parse(raw)
                mainHandler.post {
                    if (payload != null) currentOnPayload(payload) else currentOnUnrecognized()
                }
            }
        )
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.clearImageAnalysisAnalyzer()
            cameraController.unbind()
            executor.shutdown()
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    AndroidView(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .semantics { contentDescription = viewfinderDescription },
        factory = { ctx ->
            PreviewView(ctx).apply {
                // TextureView-backed: behaves inside a bottom sheet's window and clips to the rounded shape.
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                controller = cameraController
            }
        }
    )
}

@Composable
private fun CameraPermissionPrompt(
    showSettingsButton: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.scan_permission_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AppleTextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.scan_permission_body),
            fontSize = 14.sp,
            color = AppleTextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        ApplePrimaryButton(
            text = stringResource(R.string.scan_permission_allow),
            backgroundColor = AppleSeekerOrange,
            onClick = onAllow
        )
        if (showSettingsButton) {
            Spacer(modifier = Modifier.height(8.dp))
            AppleSecondaryButton(
                text = stringResource(R.string.scan_permission_settings),
                onClick = onOpenSettings
            )
        }
    }
}

/** Decodes QR codes from the camera's luminance plane; runs on the analysis executor. */
private class QrFrameAnalyzer(private val onText: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }
    private var lastText: String? = null
    private var lastReportedAtMs = 0L

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            val source = PlanarYUVLuminanceSource(
                data, plane.rowStride, image.height, 0, 0, image.width, image.height, false
            )
            val text = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            val now = SystemClock.elapsedRealtime()
            if (text != lastText || now - lastReportedAtMs > REPEAT_WINDOW_MS) {
                lastText = text
                lastReportedAtMs = now
                onText(text)
            }
        } catch (e: ReaderException) {
            // No QR code in this frame: the normal case, ignore.
        } catch (e: RuntimeException) {
            // A malformed frame must never take the app down mid-match; skip it.
            Log.w(TAG, "Skipped a camera frame", e)
        } finally {
            reader.reset()
            image.close()
        }
    }

    private companion object {
        const val REPEAT_WINDOW_MS = 2_500L
    }
}
