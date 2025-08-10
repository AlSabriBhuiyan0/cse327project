package com.google.ai.edge.gallery.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import com.google.ai.edge.gallery.util.ImageProcessingUtils
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A class that performs object detection using a TensorFlow Lite model.
 */
@Singleton
class ObjectDetector @Inject constructor(
    private val context: Context,
    private val modelConfig: ModelConfig = ModelConfig()
) {
    private val tag = "ObjectDetector"
    
    // TensorFlow Lite interpreter
    private val interpreter: Interpreter
    
    // Image preprocessing configuration
    private val inputWidth = modelConfig.inputWidth
    private val inputHeight = modelConfig.inputHeight
    private val numDetections = modelConfig.numDetections
    private val numClasses = modelConfig.numClasses
    
    // Detection parameters
    private val scoreThreshold = modelConfig.scoreThreshold
    private val nmsThreshold = modelConfig.nmsThreshold
    
    // Label map
    private val labels = modelConfig.labels
    
    init {
        // Initialize the TensorFlow Lite interpreter
        val options = Interpreter.Options().apply {
            // Enable GPU delegation if available
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate(CompatibilityList().bestGpuDelegateOptions))
            } else {
                // Fallback to CPU with XNNPACK delegate for better performance
                setUseXNNPACK(true)
                numThreads = 4
            }
        }
        
        // Load the model
        val model = FileUtil.loadMappedFile(context, modelConfig.modelPath)
        interpreter = Interpreter(model, options)
        
        Log.d(tag, "ObjectDetector initialized with input size: ${inputWidth}x$inputHeight")
    }
    
    /**
     * Detects objects in the given bitmap.
     * 
     * @param bitmap The input bitmap (will be resized to model input size)
     * @return A list of detected objects with their bounding boxes and confidence scores
     */
    fun detect(bitmap: Bitmap): List<DetectedObject> {
        // Preprocess the input image
        val inputImage = preprocessImage(bitmap)
        
        // Prepare output buffers
        val outputLocations = Array(1) { Array(numDetections) { FloatArray(4) } }
        val outputClasses = Array(1) { FloatArray(numDetections) }
        val outputScores = Array(1) { FloatArray(numDetections) }
        val numDetectionsArray = FloatArray(1)
        
        // Run inference
        val outputs = mapOf<
            Int, Any
        >(
            0 to outputLocations,
            1 to outputClasses,
            2 to outputScores,
            3 to numDetectionsArray
        )
        
        interpreter.runForMultipleInputsOutputs(
            arrayOf(inputImage.buffer),
            outputs
        )
        
        // Process the results
        val detections = mutableListOf<DetectedObject>()
        val numDetectionsFound = numDetectionsArray[0].toInt()
        
        for (i in 0 until numDetectionsFound) {
            val score = outputScores[0][i]
            if (score < scoreThreshold) continue
            
            val classId = outputClasses[0][i].toInt()
            if (classId < 0 || classId >= numClasses) continue
            
            val label = labels.getOrElse(classId) { "Class $classId" }
            
            // Get bounding box coordinates [ymin, xmin, ymax, xmax]
            val ymin = outputLocations[0][i][0].coerceIn(0f, 1f)
            val xmin = outputLocations[0][i][1].coerceIn(0f, 1f)
            val ymax = outputLocations[0][i][2].coerceIn(0f, 1f)
            val xmax = outputLocations[0][i][3].coerceIn(0f, 1f)
            
            // Skip invalid boxes
            if (xmax <= xmin || ymax <= ymin) continue
            
            detections.add(
                DetectedObject(
                    label = label,
                    confidence = score,
                    boundingBox = RectF(xmin, ymin, xmax, ymax)
                )
            )
        }
        
        // Apply non-maximum suppression to remove overlapping boxes
        return ImageProcessingUtils.applyNMS(detections, nmsThreshold)
    }
    
    /**
     * Preprocesses the input image for the model.
     */
    private fun preprocessImage(bitmap: Bitmap): TensorImage {
        // Create an ImageProcessor with the required transformations
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(inputHeight, inputWidth, ResizeOp.ResizeMethod.BILINEAR))
            .add(NormalizeOp(0f, 255f)) // Normalize to [0, 1]
            .build()
        
        // Create a TensorImage object and load the bitmap
        val tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        
        // Process the image
        return imageProcessor.process(tensorImage)
    }
    
    /**
     * Releases resources used by the interpreter.
     */
    fun close() {
        interpreter.close()
    }
    
    /**
     * Data class for model configuration.
     */
    data class ModelConfig(
        val modelPath: String = "models/ssd_mobilenet_v1_1_default_1.tflite",
        val labels: List<String> = listOf("person", "bicycle", "car", "motorcycle", "airplane",
            "bus", "train", "truck", "boat", "traffic light", "fire hydrant", "stop sign",
            "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
            "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie",
            "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat",
            "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
            "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
            "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster",
            "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
            "hair drier", "toothbrush"),
        val inputWidth: Int = 300,
        val inputHeight: Int = 300,
        val numDetections: Int = 10,
        val numClasses: Int = 91,
        val scoreThreshold: Float = 0.5f,
        val nmsThreshold: Float = 0.5f
    )
}

/**
 * Data class representing a detected object.
 */
data class DetectedObject(
    val label: String,
    val confidence: Float,
    val boundingBox: RectF
)
