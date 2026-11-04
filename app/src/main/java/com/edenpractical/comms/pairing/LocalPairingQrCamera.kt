package com.edenpractical.comms.pairing

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Local-only QR UI. QR data stays in memory; no network API, file upload,
 * photo capture or external scanning application is used.
 */
internal object LocalQrArtwork {
    fun render(payload: String, size: Int = 440): Bitmap {
        require(payload.length in 1..170) { "Unexpected QR payload length" }
        val matrix = QRCodeWriter().encode(
            payload, BarcodeFormat.QR_CODE, size, size,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2)
        )
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            pixels[y * size + x] = if (matrix[x, y]) android.graphics.Color.BLACK
                else android.graphics.Color.WHITE
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

@Composable
internal fun LocalPairingQrDisplay(payload: String) {
    val bitmap = remember(payload) { LocalQrArtwork.render(payload).asImageBitmap() }
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            bitmap = bitmap,
            contentDescription = "Pairing QR code. Scan with the other phone's Eden Comms app.",
            modifier = Modifier.size(248.dp)
                .background(Color.White, RoundedCornerShape(6.dp)).padding(8.dp)
        )
    }
}

/**
 * Permission is requested only after tapping Scan. CameraX is active solely
 * while this composable is on screen. QR bytes are processed in memory.
 */
@Composable
internal fun LocalPairingQrScanner(
    onPayload: (String) -> Unit,
    onCameraError: (String) -> Unit
) {
    val context = LocalContext.current
    var permitted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED)
    }
    val permissionRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> permitted = granted }
    val payloadCallback by rememberUpdatedState(onPayload)
    val errorCallback by rememberUpdatedState(onCameraError)

    if (!permitted) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "The camera is needed only to scan the QR on the other phone. " +
                    "No photo is saved or uploaded.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { permissionRequest.launch(Manifest.permission.CAMERA) }) {
                Text("Allow QR camera")
            }
        }
        return
    }

    val owner = context as? LifecycleOwner
    if (owner == null) {
        Text("Camera is not available in this screen.")
        return
    }
    val previewView = remember(context) {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxWidth().height(268.dp)
    )
    Text(
        "Point the camera at the QR in the other phone's Eden Comms app.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    DisposableEffect(context, owner, previewView) {
        val executor = Executors.newSingleThreadExecutor()
        val alive = AtomicBoolean(true)
        val delivered = AtomicBoolean(false)
        var cameraProvider: ProcessCameraProvider? = null
        val future = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)

        future.addListener({
            if (!alive.get()) return@addListener
            try {
                val provider = future.get()
                if (!alive.get()) return@addListener
                cameraProvider = provider

                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val reader = MultiFormatReader().apply {
                    setHints(mapOf(
                        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                        DecodeHintType.TRY_HARDER to true
                    ))
                }
                analysis.setAnalyzer(executor) { frame ->
                    try {
                        if (alive.get() && !delivered.get()) {
                            val luminance = frame.lumaBytes()
                            val source = PlanarYUVLuminanceSource(
                                luminance, frame.width, frame.height,
                                0, 0, frame.width, frame.height, false
                            )
                            val qr = BinaryBitmap(HybridBinarizer(source))
                            try {
                                val result = reader.decodeWithState(qr)
                                if (result.text != null &&
                                    delivered.compareAndSet(false, true) && alive.get()) {
                                    mainExecutor.execute {
                                        if (alive.get()) payloadCallback(result.text)
                                    }
                                }
                            } catch (_: ReaderException) {
                                // Ordinary camera frame with no QR; do not log pixels.
                            } finally {
                                reader.reset()
                            }
                        }
                    } catch (_: Exception) {
                        // Discard invalid/incompatible camera frames; no plaintext logs.
                    } finally {
                        frame.close()
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                )
            } catch (_: Exception) {
                if (alive.get()) errorCallback("Camera couldn't start on this device.")
            }
        }, mainExecutor)

        onDispose {
            alive.set(false)
            try { cameraProvider?.unbindAll() } catch (_: Exception) {}
            executor.shutdownNow()
        }
    }
}

/** Extract grayscale Y plane accounting for row and pixel strides. */
private fun ImageProxy.lumaBytes(): ByteArray {
    val plane = planes.first()
    val buffer = plane.buffer.duplicate()
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride
    val bytes = ByteArray(width * height)
    for (y in 0 until height) {
        for (x in 0 until width) {
            bytes[y * width + x] = buffer.get(y * rowStride + x * pixelStride)
        }
    }
    return bytes
}
