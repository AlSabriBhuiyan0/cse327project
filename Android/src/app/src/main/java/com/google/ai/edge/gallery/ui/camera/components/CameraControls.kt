package com.google.ai.edge.gallery.ui.camera.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R

/**
 * Bottom controls for the camera screen.
 */
@Composable
fun BottomControls(
    isProcessing: Boolean,
    onCaptureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val buttonScale by animateFloatAsState(
        targetValue = if (isProcessing) 0.9f else 1f,
        label = "buttonScale"
    )
    
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left padding for balance
        Spacer(modifier = Modifier.width(72.dp))
        
        // Capture button with enhanced visual feedback
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    color = if (isProcessing) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    shape = CircleShape
                )
                .border(
                    width = 4.dp,
                    color = if (isProcessing) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    },
                    shape = CircleShape
                )
                .scale(buttonScale)
                .clickable(
                    interactionSource = interactionSource,
                    indication = rememberRipple(bounded = false, radius = 64.dp),
                    enabled = !isProcessing,
                    onClick = onCaptureClick
                )
                .semantics {
                    this.contentDescription = if (isProcessing) {
                        "Processing, please wait"
                    } else {
                        "Capture photo"
                    }
                    this.role = Role.Button
                },
            contentAlignment = Alignment.Center
        ) {
            if (isProcessing) {
                // Pulsing animation for processing state
                val infiniteTransition = rememberInfiniteTransition()
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulse"
                )
                
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
                
                CircularProgressIndicator(
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 4.dp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            } else {
                // Outer ring for idle state
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.error,
                            shape = CircleShape
                        )
                        .border(
                            width = 4.dp,
                            color = MaterialTheme.colorScheme.onError.copy(alpha = 0.3f),
                            shape = CircleShape
                        )
                )
                
                // Inner circle with press feedback
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            color = MaterialTheme.colorScheme.error,
                            shape = CircleShape
                        )
                        .border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.onError.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
            }
        }
        
        // Right padding for balance
        Spacer(modifier = Modifier.width(72.dp))
    }
}

/**
 * Displays a sensor value with a label.
 */
@Composable
fun SensorValueLabel(
    label: String,
    value: Float,
    format: String = "%.2f",
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f)
        )
        
        Text(
            text = format.format(value),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Wrapper for showing/hiding content with a fade animation.
 */
@Composable
fun FadeInOut(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        content()
    }
}

/**
 * Tap target that shows/hides content when tapped.
 */
@Composable
fun TapToToggle(
    isVisible: Boolean,
    onToggle: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggle(!isVisible) },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
