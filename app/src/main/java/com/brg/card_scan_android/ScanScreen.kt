package com.brg.card_scan_android

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import java.util.concurrent.Executors

@Composable
fun ScanScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    val executor = ContextCompat.getMainExecutor(context)
    val cameraProvider = cameraProviderFuture.get()
    var detectedObject by remember { mutableStateOf<DetectedObject?>(null) }

    val configuration = LocalConfiguration.current

    val width = configuration.screenWidthDp
    val height = configuration.screenHeightDp

    val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
        .enableClassification()  // Optional
        .build()
    val objectDetector = remember { ObjectDetection.getClient(options) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxHeight(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            cameraProviderFuture.addListener({
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(
                            cameraExecutor,
                            imageAnalyzer(objectDetector, detectedObjectParams = detectedObject, onChange = { newDetectedObject ->
                                detectedObject = newDetectedObject
                            }
                        ))
                    }
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    imageAnalysis,
                    preview
                )
            }, executor)
            preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            previewView
        }
    )
    Canvas(modifier = Modifier.fillMaxSize()) {

        val unit = 2.5f

        val width = detectedObject?.boundingBox?.width()?.times(unit) ?: 0f
        val height = detectedObject?.boundingBox?.height()?.times(unit) ?: 0f
        val top = detectedObject?.boundingBox?.top?.times(unit) ?: 0f
        val left = detectedObject?.boundingBox?.left?.times(unit) ?: 0f

        val canvasSize = size
        val canvasWidth = size.width
        val canvasHeight = size.height

        drawRect(
            color = Color.LightGray,
            topLeft = Offset(x = top, y = left),
            size = Size(width = width, height = height)
        )
    }
}

class imageAnalyzer(
    private val objectDetector: ObjectDetector,
    private var detectedObjectParams: DetectedObject?,
    onChange: (detectedObject: DetectedObject) -> Unit,
) : ImageAnalysis.Analyzer {
    val handleChange = onChange

    @SuppressLint("UnsafeOptInUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            objectDetector.process(image)
                .addOnSuccessListener { detectedObjects ->
                    for (detectedObject in detectedObjects) {
                        detectedObjectParams = detectedObject
                        handleChange(detectedObject)
                    }
                    imageProxy.close()
                }
        }
    }
}

