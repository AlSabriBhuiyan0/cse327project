package com.google.ai.edge.gallery.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
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
 * A class that performs image analysis using a TensorFlow Lite model.
 * Handles model loading, preprocessing, and inference.
 */
@Singleton
class TFLiteAnalyzer @Inject constructor(
    private val context: Context,
    private val modelConfig: ModelConfig = ModelConfig()
) {
    private val tag = "TFLiteAnalyzer"
    
    // TensorFlow Lite interpreter
    private val interpreter: Interpreter
    
    // Model input/output shapes
    private val inputShape: IntArray
    private val outputShape: IntArray
    
    // Image preprocessing configuration
    private val imageProcessor = ImageProcessor.Builder()
        .add(ResizeOp(
            modelConfig.inputHeight,
            modelConfig.inputWidth,
            ResizeOp.ResizeMethod.BILINEAR
        ))
        .add(NormalizeOp(modelConfig.mean, modelConfig.std))
        .build()
    
    init {
        // Initialize the TensorFlow Lite interpreter
        val options = Interpreter.Options().apply {
            // Enable GPU delegation if available
            if (CompatibilityList().isDelegateSupportedOnThisDevice) {
                addDelegate(GpuDelegate(CompatibilityList().bestGpuDelegateOptions))
                Log.d(tag, "Using GPU acceleration")
            } else {
                // Fallback to CPU with XNNPACK delegate for better performance
                setUseXNNPACK(true)
                numThreads = 4
                Log.d(tag, "Using CPU with XNNPACK")
            }
        }
        
        try {
            // Load the model
            val model = FileUtil.loadMappedFile(context, modelConfig.modelPath)
            interpreter = Interpreter(model, options)
            
            // Get input and output shapes
            inputShape = interpreter.getInputTensor(0).shape()
            outputShape = interpreter.getOutputTensor(0).shape()
            
            Log.d(tag, "Model loaded successfully")
            Log.d(tag, "Input shape: ${inputShape.contentToString()}")
            Log.d(tag, "Output shape: ${outputShape.contentToString()}")
            
        } catch (e: Exception) {
            Log.e(tag, "Failed to load model", e)
            throw RuntimeException("Failed to load TensorFlow Lite model", e)
        }
    }
    
    /**
     * Analyzes the given bitmap using the loaded model.
     * 
     * @param bitmap The input bitmap (will be resized to model input size)
     * @return A list of detected objects with their bounding boxes and confidence scores
     */
    fun analyze(bitmap: Bitmap): List<DetectedObject> {
        try {
            // Preprocess the input image
            val inputImage = preprocessImage(bitmap)
            
            // Prepare output buffers
            val outputLocations = Array(1) { Array(modelConfig.numDetections) { FloatArray(4) } }
            val outputClasses = Array(1) { FloatArray(modelConfig.numDetections) }
            val outputScores = Array(1) { FloatArray(modelConfig.numDetections) }
            val numDetections = FloatArray(1)
            
            // Run inference
            val outputs = mapOf<
                Int, Any
            >(
                0 to outputLocations,
                1 to outputClasses,
                2 to outputScores,
                3 to numDetections
            )
            
            interpreter.runForMultipleInputsOutputs(
                arrayOf(inputImage.buffer),
                outputs
            )
            
            // Process the results
            return processOutputs(
                outputLocations[0],
                outputClasses[0],
                outputScores[0],
                numDetections[0].toInt()
            )
            
        } catch (e: Exception) {
            Log.e(tag, "Error during model inference", e)
            throw RuntimeException("Failed to analyze image", e)
        }
    }
    
    /**
     * Processes the model outputs into a list of detected objects.
     */
    private fun processOutputs(
        locations: Array<FloatArray>,
        classes: FloatArray,
        scores: FloatArray,
        numDetections: Int
    ): List<DetectedObject> {
        val results = mutableListOf<DetectedObject>()
        
        for (i in 0 until minOf(numDetections, modelConfig.numDetections)) {
            val score = scores[i]
            if (score < modelConfig.scoreThreshold) continue
            
            val classId = classes[i].toInt()
            if (classId < 0 || classId >= modelConfig.labels.size) continue
            
            val label = modelConfig.labels[classId]
            
            // Get bounding box coordinates [ymin, xmin, ymax, xmax]
            val ymin = locations[i][0].coerceIn(0f, 1f)
            val xmin = locations[i][1].coerceIn(0f, 1f)
            val ymax = locations[i][2].coerceIn(0f, 1f)
            val xmax = locations[i][3].coerceIn(0f, 1f)
            
            // Skip invalid boxes
            if (xmax <= xmin || ymax <= ymin) continue
            
            results.add(
                DetectedObject(
                    label = label,
                    confidence = score,
                    boundingBox = RectF(xmin, ymin, xmax, ymax)
                )
            )
        }
        
        // Apply non-maximum suppression to remove overlapping boxes
        return applyNMS(results, modelConfig.nmsThreshold)
    }
    
    /**
     * Applies non-maximum suppression to filter out overlapping detections.
     */
    private fun applyNMS(
        detections: List<DetectedObject>,
        iouThreshold: Float
    ): List<DetectedObject> {
        val selected = mutableListOf<DetectedObject>()
        val remaining = detections.sortedByDescending { it.confidence }.toMutableList()
        
        while (remaining.isNotEmpty()) {
            // Select the detection with highest confidence
            val selectedDetection = remaining.removeAt(0)
            selected.add(selectedDetection)
            
            // Remove overlapping detections
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val detection = iterator.next()
                val iou = calculateIOU(selectedDetection.boundingBox, detection.boundingBox)
                if (iou > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        
        return selected
    }
    
    /**
     * Calculates the Intersection over Union (IoU) between two rectangles.
     */
    private fun calculateIOU(rect1: RectF, rect2: RectF): Float {
        val x1 = maxOf(rect1.left, rect2.left)
        val y1 = maxOf(rect1.top, rect2.top)
        val x2 = minOf(rect1.right, rect2.right)
        val y2 = minOf(rect1.bottom, rect2.bottom)
        
        val intersection = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val area1 = (rect1.right - rect1.left) * (rect1.bottom - rect1.top)
        val area2 = (rect2.right - rect2.left) * (rect2.bottom - rect2.top)
        
        return intersection / (area1 + area2 - intersection + 1e-6f)
    }
    
    /**
     * Preprocesses the input image for the model.
     */
    private fun preprocessImage(bitmap: Bitmap): TensorImage {
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
        val labels: List<String> = DEFAULT_LABELS,
        val inputWidth: Int = 300,
        val inputHeight: Int = 300,
        val numDetections: Int = 10,
        val scoreThreshold: Float = 0.5f,
        val nmsThreshold: Float = 0.5f,
        val mean: Float = 0f,
        val std: Float = 255f
    )
    
    companion object {
        // Default COCO labels
        val DEFAULT_LABELS = listOf(
            "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
            "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat",
            "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack",
            "umbrella", "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball",
            "kite", "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket",
            "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
            "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake",
            "chair", "couch", "potted plant", "bed", "dining table", "toilet", "tv", "laptop",
            "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
            "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
        )
    }
}
