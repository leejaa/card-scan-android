package com.brg.card_scan_android

import android.Manifest
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionRequired
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mlkit.vision.objects.DetectedObject


class CameraActivity : AppCompatActivity() {

    @ExperimentalPermissionsApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var widthFactor by remember { mutableStateOf<Int>(480) }
            var heightFactor by remember { mutableStateOf<Int>(640) }


            ComposePlaygroundTheme {
                // A surface container using the 'background' color from the theme
                Surface(color = MaterialTheme.colors.background) {
                    val permission = Manifest.permission.CAMERA
                    val permissionState = rememberPermissionState(permission)
                    val context = LocalContext.current

                    LaunchedEffect(Unit) {
                        permissionState.launchPermissionRequest()
                    }

                    PermissionRequired(
                        permissionState = permissionState,
                        {}, {}, {
                            val detectedObjects = mutableStateListOf<DetectedObject>()

                            Box {

                                CameraPreview(detectedObjects, onChange = { newWidthFactor, newHeightFactor ->
                                    widthFactor = newWidthFactor
                                    heightFactor = newHeightFactor
                                })
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawIntoCanvas { canvas ->
                                        detectedObjects.forEach {
                                            val rect = it.boundingBox.toComposeRect()
                                            val newRect = Rect(Offset(x = rect.left - 30F, y = rect.top), Offset(x = rect.right + 30F, y = rect.bottom))
                                            canvas.scale(size.width / widthFactor, size.height / heightFactor)
                                            canvas.drawRect(
                                                newRect,
                                                Paint().apply {
                                                    color = Color.White
                                                    style = PaintingStyle.Stroke
                                                    strokeWidth = 3f
                                                })
//                                            canvas.nativeCanvas.drawText(
//                                                "TrackingId_${it.trackingId}",
//                                                it.boundingBox.left.toFloat(),
//                                                it.boundingBox.top.toFloat(),
//                                                android.graphics.Paint().apply {
//                                                    color = Color.Green.toArgb()
//                                                    textSize = 20f
//                                                })
                                        }

                                    }

                                }
                            }

                        }
                    )

                }
            }
        }
    }
}


@Composable
private fun CameraPreview(
    detectedObjects: SnapshotStateList<DetectedObject>,
    onChange: (widthFactor: Int, heightFactor: Int) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val coroutineScope = rememberCoroutineScope()
    val objectAnalyzer = remember { ObjectAnalyzer(coroutineScope, detectedObjects, onChange) }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor, objectAnalyzer)
                }

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            }, executor)
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )

}

