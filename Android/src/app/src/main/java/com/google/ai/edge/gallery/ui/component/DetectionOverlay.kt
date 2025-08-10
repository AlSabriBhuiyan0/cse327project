package com.google.ai.edge.gallery.ui.component

import android.graphics.*
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.ml.DetectedObject
import kotlin.math.max
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * A composable that draws bounding boxes and labels for detected objects with performance optimizations.
 *
 * @param detections List of detected objects
 * @param modifier Modifier for the overlay
 * @param boxColor Color for the bounding box
 * @param textColor Color for the text
 * @param textBackgroundColor Color for the text background
 * @param textSize Text size in SP
 * @param boxStrokeWidth Width of the bounding box stroke
 * @param labelPadding Padding around the label text
 * @param showConfidence Whether to show the confidence score
 * @param onDetectionClick Callback when a detection is clicked
 * @param maxDetections Maximum number of detections to render (for performance)
 * @param minConfidence Minimum confidence score to render (0.0 to 1.0)
 */
@Composable
fun DetectionOverlay(
    detections: List<DetectedObject>,
    modifier: Modifier = Modifier,
    boxColor: Color = Color.Red,
    textColor: Color = Color.White,
    textBackgroundColor: Color = Color.Black.copy(alpha = 0.7f),
    textSize: Float = 12f,
    boxStrokeWidth: Float = 2f,
    labelPadding: Float = 4f,
    showConfidence: Boolean = true,
    onDetectionClick: ((DetectedObject) -> Unit)? = null,
    maxDetections: Int = 20,
    minConfidence: Float = 0.3f
) {
    val density = LocalDensity.current
    val textSizePx = with(density) { textSize.dp.toPx() }
    val boxStrokeWidthPx = with(density) { boxStrokeWidth.dp.toPx() }
    val labelPaddingPx = with(density) { labelPadding.dp.toPx() }
    
    // Track which detection is being pressed
    var pressedDetection: Int? by remember { mutableStateOf(null) }
    
    // Debounce detections to avoid rapid updates
    val debouncedDetections by remember(detections) {
        derivedStateOf {
            detections
                .filter { it.confidence >= minConfidence }
                .take(maxDetections)
        }
    }
    
    // Reuse paint objects to avoid allocations during draw
    val boxPaint = remember { Paint().apply { 
        style = PaintingStyle.Stroke
        isAntiAlias = true
    } }
    
    val textBackgroundPaint = remember { Paint().apply {
        style = PaintingStyle.Fill
        isAntiAlias = true
    } }
    
    val cornerMarkerPaint = remember { Paint().apply {
        style = PaintingStyle.Fill
        isAntiAlias = true
    } }
    
    // Update paints when colors change
    LaunchedEffect(boxColor) {
        boxPaint.color = boxColor.toArgb()
        cornerMarkerPaint.color = boxColor.toArgb()
        boxPaint.strokeWidth = boxStrokeWidthPx
    }
    
    LaunchedEffect(textBackgroundColor) {
        textBackgroundPaint.color = textBackgroundColor.toArgb()
    }
    
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val viewport = Rect(0f, 0f, width, height)
        
        // Only process detections that are visible in the viewport
        val visibleDetections = remember(debouncedDetections, width, height) {
            debouncedDetections.mapNotNull { detection ->
                val left = detection.boundingBox.left * width
                val top = detection.boundingBox.top * height
                val right = detection.boundingBox.right * width
                val bottom = detection.boundingBox.bottom * height
                
                val detectionRect = Rect(left, top, right, bottom)
                if (detectionRect.overlaps(viewport)) {
                    detection to detectionRect
                } else {
                    null
                }
            }
        }
        
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = { offset ->
                            // Find which detection was pressed
                            val pressedIndex = visibleDetections.indexOfFirst { (_, rect) ->
                                rect.contains(offset)
                            }
                            
                            if (pressedIndex >= 0) {
                                pressedDetection = pressedIndex
                                try {
                                    val released = tryAwaitRelease()
                                    if (released) {
                                        onDetectionClick?.invoke(visibleDetections[pressedIndex].first)
                                    }
                                } finally {
                                    pressedDetection = null
                                }
                            }
                        }
                    )
                }
        ) {
            // Draw all visible detections
            visibleDetections.forEachIndexed { index, (detection, rect) ->
                val isPressed = index == pressedDetection
                val alpha = if (isPressed) 0.7f else 1f
                
                // Update paint alpha for pressed state
                boxPaint.alpha = (boxColor.alpha * alpha).toInt()
                textBackgroundPaint.alpha = (textBackgroundColor.alpha * alpha).toInt()
                cornerMarkerPaint.alpha = (boxColor.alpha * alpha).toInt()
                
                // Draw the bounding box
                drawRect(
                    rect = rect,
                    paint = boxPaint
                )
                
                // Draw label if there's enough space
                if (rect.height > textSizePx * 2) {
                    val label = if (showConfidence) {
                        "${detection.label} (${(detection.confidence * 100).toInt()}%)"
                    } else {
                        detection.label
                    }
                    
                    // Draw the label background
                    val textTop = maxOf(0f, rect.top - textSizePx - labelPaddingPx * 2)
                    drawRect(
                        rect = Rect(
                            left = rect.left,
                            top = textTop,
                            right = rect.left + label.length * textSizePx * 0.6f + labelPaddingPx * 2,
                            bottom = rect.top
                        ),
                        paint = textBackgroundPaint
                    )
                    
                    // Draw the label text
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(
                            label,
                            rect.left + labelPaddingPx,
                            rect.top - labelPaddingPx,
                            textSizePx,
                            android.graphics.Paint().apply {
                                color = textColor.copy(alpha = alpha).toArgb()
                                textSize = textSizePx
                                isAntiAlias = true
                            }
                        )
                    }
                }
                
                // Draw corner markers
                val cornerSize = 16f
                drawRect(
                    rect = Rect(
                        left = rect.left,
                        top = rect.top,
                        right = rect.left + cornerSize,
                        bottom = rect.top + cornerSize
                    ),
                    paint = cornerMarkerPaint
                )
            }
        }
    }
}

/**
 * Extension function to convert a RectF to a Path.
 */
private fun Path.addRectF(rect: RectF) {
    moveTo(rect.left, rect.top)
    lineTo(rect.right, rect.top)
    lineTo(rect.right, rect.bottom)
    lineTo(rect.left, rect.bottom)
    close()
}

/**
 * Extension function to convert a RectF to a Path with rounded corners.
 */
private fun Path.addRoundRectF(rect: RectF, radius: Float) {
    moveTo(rect.left + radius, rect.top)
    lineTo(rect.right - radius, rect.top)
    quadTo(rect.right, rect.top, rect.right, rect.top + radius)
    lineTo(rect.right, rect.bottom - radius)
    quadTo(rect.right, rect.bottom, rect.right - radius, rect.bottom)
    lineTo(rect.left + radius, rect.bottom)
    quadTo(rect.left, rect.bottom, rect.left, rect.bottom - radius)
    lineTo(rect.left, rect.top + radius)
    quadTo(rect.left, rect.top, rect.left + radius, rect.top)
    close()
}

/**
 * Extension function to draw text with a background.
 */
private fun DrawScope.drawTextWithBackground(
    text: String,
    position: Offset,
    textColor: Color,
    backgroundColor: Color,
    textSize: Float,
    padding: Float = 4f
) {
    val textPaint = Paint().asFrameworkPaint().apply {
        color = textColor.toArgb()
        textSize = textSize
        isAntiAlias = true
    }
    
    val textBounds = android.graphics.Rect()
    textPaint.getTextBounds(text, 0, text.length, textBounds)
    
    val textWidth = textBounds.width().toFloat()
    val textHeight = textBounds.height().toFloat()
    
    // Draw background
    drawRect(
        color = backgroundColor,
        topLeft = Offset(position.x, position.y - textHeight - padding),
        size = Size(textWidth + 2 * padding, textHeight + 2 * padding)
    )
    
    // Draw text
    drawIntoCanvas { canvas ->
        canvas.nativeCanvas.drawText(
            text,
            position.x + padding,
            position.y - padding,
            textPaint
        )
    }
}
