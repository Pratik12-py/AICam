package com.example.composebasics.cameraxguide

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import android.widget.Toast
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.compose.ui.platform.LocalContext
import com.example.composebasics.domain.Classification
import com.example.composebasics.domain.LandmarkClassifier
import java.io.ByteArrayOutputStream

class LandmarkImageAnalyzer(
    val classifier: LandmarkClassifier,
    private val onResults: (List<Classification>) -> Unit
): ImageAnalysis.Analyzer {

    private  var frameSkipCounter = 0

//    override fun analyze(image: ImageProxy) {
////        Log.d("check analyze","running");
//        if (frameSkipCounter % 60 == 0) {
//            val rotationDegrees = image.imageInfo.rotationDegrees
//            val bitmap = image
//                .toBitmap()
//                .safeCenterCrop(321, 321)
//
//            val results = classifier.classify(bitmap, rotationDegrees)
////            Log.d("results are","$results")
//
//
//            onResults(results)
//        }
//        frameSkipCounter++
//
//        image.close()
//    }
private var lastAnalyzedTime = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastAnalyzedTime >= 5000) { // 5 seconds

            val bitmap = imageProxy.toBitmap() // Make sure you have this function
            val rotation = imageProxy.imageInfo.rotationDegrees

            val results = classifier.classify(bitmap, rotation)
            Log.d("Results", "$results")
            onResults(results)

            lastAnalyzedTime = currentTime
        }

        imageProxy.close()
    }


}

fun ImageProxy.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}


fun Bitmap.safeCenterCrop(width: Int, height: Int): Bitmap {
    val srcWidth = this.width
    val srcHeight = this.height

    // If the image is smaller than the target size, scale up
    val scale = maxOf(
        width.toFloat() / srcWidth,
        height.toFloat() / srcHeight
    )

    val scaledWidth = (srcWidth * scale).toInt()
    val scaledHeight = (srcHeight * scale).toInt()

    val scaledBitmap = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)

    val xOffset = (scaledWidth - width) / 2
    val yOffset = (scaledHeight - height) / 2

    return Bitmap.createBitmap(scaledBitmap, xOffset, yOffset, width, height)
}
