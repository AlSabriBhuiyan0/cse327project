package com.google.ai.edge.gallery.ui.camera.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.sensor.RotationData
import com.google.ai.edge.gallery.sensor.SensorState
import com.google.ai.edge.gallery.sensor.WifiState
import com.google.ai.edge.gallery.ui.component.OrientationCompass
import kotlin.math.roundToInt

/**
 * Displays sensor data and processing information in an overlay panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorPanel(
    rotationData: RotationData,
    sensorState: SensorState,
    wifiState: WifiState,
    processingTimeMs: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sensor_data),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        // Sensor data content
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Gyroscope data
            item {
                SensorCard(
                    title = stringResource(R.string.gyroscope)
                ) {
                    // 3D visualization of gyroscope data
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        OrientationCompass(
                            pitch = rotationData.pitch,
                            roll = rotationData.roll,
                            yaw = rotationData.yaw,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Raw values
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SensorValueLabel(
                            label = "X",
                            value = rotationData.x,
                            format = "%.2f rad/s",
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        SensorValueLabel(
                            label = "Y",
                            value = rotationData.y,
                            format = "%.2f rad/s",
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        SensorValueLabel(
                            label = "Z",
                            value = rotationData.z,
                            format = "%.2f rad/s",
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            
            // WiFi status
            item {
                SensorCard(
                    title = stringResource(R.string.network_status)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = null,
                            tint = when (wifiState) {
                                is WifiState.Connected -> MaterialTheme.colorScheme.primary
                                is WifiState.Connecting -> MaterialTheme.colorScheme.tertiary
                                is WifiState.Disconnected -> MaterialTheme.colorScheme.error
                                is WifiState.Disabled -> MaterialTheme.colorScheme.outline
                                is WifiState.Error -> MaterialTheme.colorScheme.error
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Text(
                            text = when (wifiState) {
                                is WifiState.Connected -> stringResource(R.string.wifi_connected, wifiState.ssid)
                                is WifiState.Connecting -> stringResource(R.string.wifi_connecting)
                                is WifiState.Disconnected -> stringResource(R.string.wifi_disconnected)
                                is WifiState.Disabled -> stringResource(R.string.wifi_disabled)
                                is WifiState.Error -> stringResource(R.string.wifi_error, wifiState.error)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (wifiState is WifiState.Connected) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.signal_strength),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = "${wifiState.signalLevel}%",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        LinearProgressIndicator(
                            progress = { wifiState.signalLevel / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                wifiState.signalLevel > 70 -> MaterialTheme.colorScheme.primary
                                wifiState.signalLevel > 30 -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.error
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    }
                }
            }
            
            // Performance metrics
            item {
                SensorCard(
                    title = stringResource(R.string.performance_metrics)
                ) {
                    // Processing time
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.processing_time),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = "${processingTimeMs}ms",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // FPS (if available)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.frame_rate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        val fps = if (processingTimeMs > 0) {
                            (1000f / processingTimeMs).coerceAtMost(30f)
                        } else {
                            0f
                        }
                        
                        Text(
                            text = "${fps.roundToInt()} FPS",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card container for sensor data with consistent styling.
 */
@Composable
private fun SensorCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            content()
        }
    }
}
