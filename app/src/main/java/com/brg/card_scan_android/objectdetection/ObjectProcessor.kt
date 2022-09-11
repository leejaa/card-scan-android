package com.brg.card_scan_android.objectdetection

import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.ObjectDetector
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions


class ObjectProcessor {
    private val detector: ObjectDetector

    init {
        var options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableClassification()
            .build()

        this.detector = ObjectDetection.getClient(options)
    }
}