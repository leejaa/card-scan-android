package com.brg.card_scan_android

import android.annotation.SuppressLint
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class ObjectAnalyzer(
    private val coroutineScope: CoroutineScope,
    private val detectedObjects: SnapshotStateList<DetectedObject>,
    val onChange: (widthFactor: Int, heightFactor: Int) -> Unit
) : ImageAnalysis.Analyzer {
    private val options = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        .build()
    private val objectDetector = ObjectDetection.getClient(options)

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val frame = imageProxy.image?.let {
            InputImage.fromMediaImage(
                it,
                imageProxy.imageInfo.rotationDegrees
            )
        }

        imageProxy.image?.height?.let { imageProxy.image?.width?.let { it1 -> onChange(it, it1) } }

        coroutineScope.launch {
            if (frame != null) {
                objectDetector.process(frame)
                    .addOnSuccessListener { detectedObjects ->
                        // Task completed successfully
                        with(this@ObjectAnalyzer.detectedObjects) {
                            clear()
                            addAll(detectedObjects)
                        }
                    }
                    .addOnFailureListener { e ->
                        // Task failed with an exception
                        // ...
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            }
        }

    }

}