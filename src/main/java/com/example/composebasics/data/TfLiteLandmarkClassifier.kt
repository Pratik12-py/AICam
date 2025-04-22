package com.example.composebasics.data

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import androidx.annotation.VisibleForTesting
import com.example.composebasics.domain.Classification
import com.example.composebasics.domain.LandmarkClassifier
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.task.core.BaseOptions
import org.tensorflow.lite.task.core.vision.ImageProcessingOptions
import org.tensorflow.lite.task.vision.classifier.ImageClassifier
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class TfLiteLandmarkClassifier @VisibleForTesting constructor(
    private val classifier: ImageClassifier?
) : LandmarkClassifier {

    private val lock = ReentrantLock()

    constructor(
        context: Context,
        modelName: String = "landmarks.tflite",
        threshold: Float = 0.5f,
        maxResults: Int = 3,
        numThreads: Int = 2
    ) : this(
        try {
            ImageClassifier.createFromFileAndOptions(
                context,
                modelName,
                ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setNumThreads(numThreads)
                            .useGpu()
                            .build()
                    )
                    .setMaxResults(maxResults)
                    .setScoreThreshold(threshold)
                    .build()
            )
        } catch (e: Exception) {
            null.also { e.printStackTrace() }
        }
    )

    override fun classify(bitmap: Bitmap, rotation: Int): List<Classification> {
        if (classifier == null) return emptyList()

        return lock.withLock {
            try {
                val tensorImage = TensorImage.fromBitmap(bitmap)
                val imageProcessingOptions = ImageProcessingOptions.builder()
                    .setOrientation(getOrientationFromRotation(rotation))
                    .build()

                classifier.classify(tensorImage, imageProcessingOptions)
                    .flatMap { classifications ->
                        classifications.categories.map { category ->
                            Classification(
                                name = category.displayName,
                                score = category.score
                            )
                        }
                    }
                    .distinctBy { it.name }
                    .sortedByDescending { it.score }

            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    @VisibleForTesting
    internal fun getOrientationFromRotation(rotation: Int): ImageProcessingOptions.Orientation {
        return when (rotation) {
            Surface.ROTATION_270 -> ImageProcessingOptions.Orientation.BOTTOM_RIGHT
            Surface.ROTATION_90 -> ImageProcessingOptions.Orientation.TOP_LEFT
            Surface.ROTATION_180 -> ImageProcessingOptions.Orientation.RIGHT_BOTTOM
            else -> ImageProcessingOptions.Orientation.RIGHT_TOP
        }
    }

    fun close() {
        classifier?.close()
    }
}