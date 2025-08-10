package com.google.ai.edge.gallery.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Displays a 3D visualization of sensor data.
 *
 * @param values The sensor values to visualize (x, y, z)
 * @param label The label to display above the visualization
 * @param modifier Modifier for the composable
 * @param size The size of the visualization
 * @param showAxes Whether to show the coordinate axes
 * @param showLabels Whether to show value labels
 */
@Composable
fun SensorVisualizer(
    values: FloatArray?,
    label: String,
    modifier: Modifier = Modifier,
    size: Dp = 150.dp,
    showAxes: Boolean = true,
    showLabels: Boolean = true
) {
    val x = values?.getOrNull(0) ?: 0f
    val y = values?.getOrNull(1) ?: 0f
    val z = values?.getOrNull(2) ?: 0f
    
    // Animate the values for smooth transitions
    val animatedX by animateFloatAsState(targetValue = x, label = "x")
    val animatedY by animateFloatAsState(targetValue = y, label = "y")
    val animatedZ by animateFloatAsState(targetValue = z, label = "z")
    
    // Calculate the magnitude of the vector
    val magnitude = kotlin.math.sqrt(
        (animatedX * animatedX + 
         animatedY * animatedY + 
         animatedZ * animatedZ).toDouble()
    ).toFloat()
    
    // Convert Dp to Px
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val center = sizePx / 2f
    val radius = sizePx * 0.4f
    
    // Colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val errorColor = MaterialTheme.colorScheme.error
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    
    // Paint for the sphere
    val spherePaint = remember {
        Paint().apply {
            color = primaryContainer.copy(alpha = 0.3f)
            style = PaintingStyle.Fill
        }
    }
    
    // Paint for the vector
    val vectorPaint = remember {
        Paint().apply {
            color = primaryColor
            style = PaintingStyle.Stroke
            strokeWidth = 4f
            isAntiAlias = true
        }
    }
    
    // Paint for the axes
    val axisPaint = remember {
        Paint().apply {
            color = onSurfaceColor.copy(alpha = 0.5f)
            style = PaintingStyle.Stroke
            strokeWidth = 1f
            isAntiAlias = true
        }
    }
    
    // Paint for the vector head
    val headPaint = remember {
        Paint().apply {
            color = errorColor
            style = PaintingStyle.Fill
            isAntiAlias = true
        }
    }
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = onSurfaceColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // 3D Visualization
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // Draw the sphere
                drawCircle(
                    color = spherePaint.color,
                    radius = radius,
                    center = Offset(center, center),
                    style = spherePaint.style,
                    alpha = spherePaint.alpha
                )
                
                // Draw the axes if enabled
                if (showAxes) {
                    // X-axis (red)
                    drawLine(
                        color = Color.Red.copy(alpha = 0.5f),
                        start = Offset(0f, center),
                        end = Offset(sizePx, center),
                        strokeWidth = 1f
                    )
                    
                    // Y-axis (green)
                    drawLine(
                        color = Color.Green.copy(alpha = 0.5f),
                        start = Offset(center, 0f),
                        end = Offset(center, sizePx),
                        strokeWidth = 1f
                    )
                    
                    // Z-axis (blue) - represented as a circle for 2D visualization
                    drawCircle(
                        color = Color.Blue.copy(alpha = 0.2f),
                        radius = radius,
                        center = Offset(center, center),
                        style = Stroke(width = 1f)
                    )
                }
                
                // Calculate the end point of the vector
                val scale = radius / 10f // Scale factor to fit the vector in the view
                val endX = center + animatedX * scale
                val endY = center - animatedY * scale // Invert Y for screen coordinates
                
                // Draw the vector
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(primaryColor, errorColor),
                        start = Offset(center, center),
                        end = Offset(endX, endY)
                    ),
                    start = Offset(center, center),
                    end = Offset(endX, endY),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round
                )
                
                // Draw the vector head
                drawCircle(
                    color = errorColor,
                    radius = 8f,
                    center = Offset(endX, endY)
                )
                
                // Draw the projection on the XY plane
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f),
                    radius = 4f,
                    center = Offset(endX, endY)
                )
            }
        }
        
        // Value labels if enabled
        if (showLabels) {
            Spacer(modifier = Modifier.height(8.dp))
            
            // X, Y, Z values
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                SensorValueLabel("X", animatedX, "%.2f", Color.Red)
                SensorValueLabel("Y", animatedY, "%.2f", Color.Green)
                SensorValueLabel("Z", animatedZ, "%.2f", Color.Blue)
            }
            
            // Magnitude
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Magnitude: %.2f".format(magnitude),
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceColor.copy(alpha = 0.8f)
            )
        }
    }
}

/**
 * Displays a single sensor value with a label.
 */
@Composable
private fun SensorValueLabel(
    label: String,
    value: Float,
    format: String = "%.2f",
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontSize = 10.sp
        )
        Text(
            text = format.format(value),
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Displays orientation angles (pitch, roll, yaw) in a compass-like visualization.
 */
@Composable
fun OrientationCompass(
    pitch: Float,
    roll: Float,
    yaw: Float,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    
    // Convert angles to radians
    val pitchRad = Math.toRadians(pitch.toDouble()).toFloat()
    val rollRad = Math.toRadians(roll.toDouble()).toFloat()
    val yawRad = Math.toRadians(yaw.toDouble()).toFloat()
    
    // Calculate the end point of the compass needle
    val needleLength = with(LocalDensity.current) { size.toPx() * 0.4f }
    val centerX = with(LocalDensity.current) { size.toPx() / 2f }
    val centerY = with(LocalDensity.current) { size.toPx() / 2f }
    
    // Calculate the end point based on yaw (azimuth)
    val endX = centerX + needleLength * sin(yawRad)
    val endY = centerY - needleLength * cos(yawRad) // Negative because y increases downward
    
    // Animate the needle movement
    val animatedEndX by animateFloatAsState(targetValue = endX, label = "needleX")
    val animatedEndY by animateFloatAsState(targetValue = endY, label = "needleY")
    
    Surface(
        modifier = modifier.size(size),
        shape = MaterialTheme.shapes.medium,
        color = surfaceColor,
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                // Draw the outer circle
                drawCircle(
                    color = primaryColor.copy(alpha = 0.1f),
                    radius = size.toPx() * 0.45f,
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 2f)
                )
                
                // Draw the crosshair
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.3f),
                    start = Offset(centerX, 0f),
                    end = Offset(centerX, size.toPx()),
                    strokeWidth = 1f
                )
                drawLine(
                    color = onSurfaceColor.copy(alpha = 0.3f),
                    start = Offset(0f, centerY),
                    end = Offset(size.toPx(), centerY),
                    strokeWidth = 1f
                )
                
                // Draw the compass needle
                drawLine(
                    color = primaryColor,
                    start = Offset(centerX, centerY),
                    end = Offset(animatedEndX, animatedEndY),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
                
                // Draw the needle head
                drawCircle(
                    color = MaterialTheme.colorScheme.error,
                    radius = 6f,
                    center = Offset(animatedEndX, animatedEndY)
                )
                
                // Draw the center point
                drawCircle(
                    color = primaryColor,
                    radius = 4f,
                    center = Offset(centerX, centerY)
                )
            }
            
            // Draw the angle labels
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                // Pitch (top)
                Text(
                    text = "P: %.1f°".format(pitch),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceColor,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                
                // Roll (right)
                Text(
                    text = "R: %.1f°".format(roll),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceColor,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
                
                // Yaw (bottom)
                Text(
                    text = "Y: %.1f°".format(yaw),
                    style = MaterialTheme.typography.labelSmall,
                    color = onSurfaceColor,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                
                // Left label
                Text(
                    text = "N",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }
    }
}
