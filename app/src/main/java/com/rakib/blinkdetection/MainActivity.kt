package com.rakib.blinkdetection

import android.os.Bundle
import android.util.Size
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startCamera()
    }

    // High-accuracy landmark detection and face classification
    private val highAccuracyOpts = FirebaseVisionFaceDetectorOptions.Builder()
        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
        .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
        .build()

    val faceDetector =
        FirebaseVision.getInstance()
            .getVisionFaceDetector(highAccuracyOpts)

    // Function that creates and displays the camera preview
    private fun startCamera() {
        val previewConfig = PreviewConfig.Builder().setLensFacing(CameraX.LensFacing.FRONT)
            .apply {
                setTargetResolution(Size(1280, 720))
            }
            .build()

        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {
            val parent = textureView.parent as ViewGroup
            parent.removeView(textureView)
            parent.addView(textureView, 0)
            textureView.surfaceTexture = it.surfaceTexture
        }

        val analyzerConfig =
            ImageAnalysisConfig.Builder().setLensFacing(CameraX.LensFacing.FRONT).apply {
                setImageReaderMode(
                    ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
                )
            }.build()

        val imageAnalysis = ImageAnalysis(analyzerConfig).apply {
            analyzer = ImageProcessor()
        }

        CameraX.bindToLifecycle(this, preview, imageAnalysis)
    }

    inner class ImageProcessor : ImageAnalysis.Analyzer {

        private var lastAnalyzedTimestamp = 0L

        private fun degreesToFirebaseRotation(degrees: Int): Int = when (degrees) {
            0 -> FirebaseVisionImageMetadata.ROTATION_0
            90 -> FirebaseVisionImageMetadata.ROTATION_90
            180 -> FirebaseVisionImageMetadata.ROTATION_180
            270 -> FirebaseVisionImageMetadata.ROTATION_270
            else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
        }

        override fun analyze(image: ImageProxy?, rotationDegrees: Int) {
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - lastAnalyzedTimestamp >=
                TimeUnit.SECONDS.toMillis(1)
            ) {
                val imageRotation = degreesToFirebaseRotation(rotationDegrees)
                image?.image?.let {
                    val visionImage = FirebaseVisionImage.fromMediaImage(it, imageRotation)
                    faceDetector.detectInImage(visionImage)
                        .addOnSuccessListener { faces ->
                            faces.forEach { face ->
                                if (face.leftEyeOpenProbability < 0.4 || face.rightEyeOpenProbability < 0.4) {
                                    textView.text = getString(R.string.blinking)
                                } else {
                                    textView.text = getString(R.string.not_blinking)
                                }
                            }
                        }
                        .addOnFailureListener {
                            it.printStackTrace()
                        }
                }
            }
        }
    }
}