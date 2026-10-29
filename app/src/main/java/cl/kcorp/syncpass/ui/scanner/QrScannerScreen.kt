package cl.kcorp.syncpass.ui.scanner

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun QrScannerScreen(
    modifier: Modifier = Modifier,
    onCode: (String) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Se necesita permiso de cámara para escanear el QR.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Permitir cámara")
            }
            Button(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) {
                Text("Volver")
            }
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val scanner = remember { BarcodeScanning.getClient() }
    val scanned = remember { AtomicBoolean(false) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
            scanner.close()
        }
    }

    LaunchedEffect(previewView) {
        val pv = previewView ?: return@LaunchedEffect
        try {
            imageAnalysis?.clearAnalyzer()
            val provider = context.getCameraProvider()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(pv.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis = analysis

            analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                runCatching {
                    scanner.process(image)
                        .addOnSuccessListener { barcodes ->
                            val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                            if (value != null && scanned.compareAndSet(false, true)) {
                                onCode(value)
                                scope.launch {
                                    delay(1500)
                                    scanned.set(false)
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }.onFailure {
                    imageProxy.close()
                }
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            cameraError = error.localizedMessage ?: "No se pudo abrir la cámara."
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).also { view ->
                    view.scaleType = PreviewView.ScaleType.FILL_CENTER
                    previewView = view
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(240.dp)
                .border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)),
        ) {}

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = cameraError ?: "Apunta la cámara al código QR del asistente",
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
            Button(onClick = onClose, modifier = Modifier.padding(top = 12.dp)) {
                Text("Volver")
            }
        }
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                if (!continuation.isActive) return@addListener
                try {
                    continuation.resume(future.get())
                } catch (error: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(error)
                    }
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }
