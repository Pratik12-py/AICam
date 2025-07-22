package com.example.composebasics.data

//import android.content.Context
//import android.graphics.Bitmap
//import android.view.Surface
//import androidx.annotation.VisibleForTesting
//import com.example.composebasics.domain.Classification
//import com.example.composebasics.domain.LandmarkClassifier
//import org.tensorflow.lite.support.image.TensorImage
//import org.tensorflow.lite.task.core.BaseOptions
//import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
//import org.tensorflow.lite.task.vision.classifier.ImageClassifier
//import java.util.concurrent.locks.ReentrantLock
//import kotlin.concurrent.withLock
//
//class TfLiteLandmarkClassifier @VisibleForTesting constructor(
//    private val classifier: ImageClassifier?
//) : LandmarkClassifier {
//
//    private val lock = ReentrantLock()
//
//    constructor(
//        context: Context,
//        modelName: String = "landmarks.tflite",
//        threshold: Float = 0.5f,
//        maxResults: Int = 3,
//        numThreads: Int = 2
//    ) : this(
//        try {
//            ImageClassifier.createFromFileAndOptions(
//                context,
//                modelName,
//                ImageClassifier.ImageClassifierOptions.builder()
//                    .setBaseOptions(
//                        BaseOptions.builder()
//                            .setNumThreads(numThreads)
//                            .useGpu()
//                            .build()
//                    )
//                    .setMaxResults(maxResults)
//                    .setScoreThreshold(threshold)
//                    .build()
//            )
//        } catch (e: Exception) {
//            null.also { e.printStackTrace() }
//        }
//    )
//
//    override fun classify(bitmap: Bitmap, rotation: Int): List<Classification> {
//        if (classifier == null) return emptyList()
//
//        return lock.withLock {
//            try {
//                val tensorImage = TensorImage.fromBitmap(bitmap)
//                val imageProcessingOptions = ImageProcessingOptions.builder()
//                    .setOrientation(getOrientationFromRotation(rotation))
//                    .build()
//
//                classifier.classify(tensorImage, imageProcessingOptions)
//                    .flatMap { classifications ->
//                        classifications.categories.map { category ->
//                            Classification(
//                                name = category.displayName,
//                                score = category.score
//                            )
//                        }
//                    }
//                    .distinctBy { it.name }
//                    .sortedByDescending { it.score }
//
//            } catch (e: Exception) {
//                e.printStackTrace()
//                emptyList()
//            }
//        }
//    }
//
//    @VisibleForTesting
//    internal fun getOrientationFromRotation(rotation: Int): ImageProcessingOptions.Orientation {
//        return when (rotation) {
//            Surface.ROTATION_270 -> ImageProcessingOptions.Orientation.BOTTOM_RIGHT
//            Surface.ROTATION_90 -> ImageProcessingOptions.Orientation.TOP_LEFT
//            Surface.ROTATION_180 -> ImageProcessingOptions.Orientation.RIGHT_BOTTOM
//            else -> ImageProcessingOptions.Orientation.RIGHT_TOP
//        }
//    }
//
//    fun close() {
//        classifier?.close()
//    }
//}


import android.content.Context
import android.graphics.Bitmap
import com.example.composebasics.domain.Classification
import com.example.composebasics.domain.LandmarkClassifier
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import java.io.FileInputStream

class TfLiteLandmarkClassifier(
    context: Context,
    private val modelName: String = "landmarks_classifier_asia_V1.tflite",
    private val labelFile: String = "landmarks_classifier_asia_V1_label.txt",
    private val inputSize: Int = 321,
    private val topK: Int = 3
) : LandmarkClassifier {

    private val interpreter: Interpreter
    private val labels: List<String>
    private val lock = ReentrantLock()

    init {
        val model = loadModelFile(context, modelName)
        interpreter = Interpreter(model, Interpreter.Options().apply {
            setNumThreads(2)
        })
        labels = loadLabels(context, labelFile)
    }

    override fun classify(bitmap: Bitmap, rotation: Int): List<Classification> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val input = preprocess(resized)

        val output = Array(1) { FloatArray(labels.size) }

        lock.withLock {
            interpreter.run(input, output)
        }

        return output[0]
            .mapIndexed { index, score -> index to score }
            .sortedByDescending { it.second }
            .take(1)
            .map { (i, score) ->
                Classification(name = labels.getOrElse(i) { "Unknown" }, score = score)
            }
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3)
        buffer.order(ByteOrder.nativeOrder())

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = bitmap.getPixel(x, y)
                buffer.put(((pixel shr 16) and 0xFF).toByte()) // R
                buffer.put(((pixel shr 8) and 0xFF).toByte())  // G
                buffer.put((pixel and 0xFF).toByte())          // B
            }
        }

        return buffer
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val channel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return channel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun loadLabels(context: Context, labelFile: String): List<String> {
        return context.assets.open(labelFile).bufferedReader().useLines { it.toList() }
    }

    fun close() {
        interpreter.close()
    }
}
