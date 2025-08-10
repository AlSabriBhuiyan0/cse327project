package com.google.ai.edge.gallery.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.camera.CameraState
import com.google.ai.edge.gallery.ml.DetectedObject
import com.google.ai.edge.gallery.sensor.RotationData
import com.google.ai.edge.gallery.sensor.SensorState
import com.google.ai.edge.gallery.sensor.WifiState
import com.google.ai.edge.gallery.ui.camera.components.BottomControls
import com.google.ai.edge.gallery.ui.camera.components.SensorPanel
import com.google.ai.edge.gallery.ui.component.DetectionOverlay
import com.google.ai.edge.gallery.ui.theme.LocalSpacing
import com.google.ai.edge.gallery.util.HapticFeedbackHelper
import kotlinx.coroutines.launch

/**
 * Data class representing the UI state of the camera screen.
 */
data class CameraScreenState(
    val isCameraActive: Boolean = false,
    val isProcessing: Boolean = false,
    val showControls: Boolean = true,
    val showSensors: Boolean = false,
    val cameraLensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val processingTimeMs: Long = 0,
    val detectedObjects: List<DetectedObject> = emptyList(),
    val rotationData: RotationData = RotationData(),
    val sensorState: SensorState = SensorState.Inactive,
    val wifiState: WifiState = WifiState.Disconnected,
    val error: String? = null
)

/**
 * Main camera screen that integrates camera, sensors, and AI model inference.
 *
 * @param onBackPressed Callback when the back button is pressed
 * @param viewModel The ViewModel that handles the business logic
 * @param hapticFeedback Helper for providing haptic feedback
 * @param modifier Modifier for the composable
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(
    onBackPressed: () -> Unit,
    viewModel: CameraViewModel = viewModel(),
    hapticFeedback: HapticFeedbackHelper = remember { HapticFeedbackHelper(LocalContext.current) },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val spacing = LocalSpacing.current
    
    // UI State
    var screenState by remember { 
        mutableStateOf(CameraScreenState())
    }
    
    // Permissions state
    val requiredPermissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    val permissionsState = remember {
        requiredPermissions.associateWith { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    // Permission state
    var showPermissionRationale by remember { mutableStateOf(false) }
    var showPermissionSettings by remember { mutableStateOf(false) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val deniedPermissions = permissions.filter { !it.value }.keys
        if (deniedPermissions.isNotEmpty()) {
            showPermissionRationale = true
        } else {
            // All permissions granted, start services
            viewModel.startCamera(lifecycleOwner as android.app.Activity)
            viewModel.startWifiScan()
        }
    }
    
    // Check permissions when the screen is first displayed
    LaunchedEffect(Unit) {
        val missingPermissions = requiredPermissions.filterNot { permissionsState[it] == true }
        if (missingPermissions.isNotEmpty()) {
            // Check if we should show rationale
            val shouldShowRationale = missingPermissions.any { permission ->
                (lifecycleOwner as? androidx.activity.ComponentActivity)?.shouldShowRequestPermissionRationale(permission) == true
            }
            
            if (shouldShowRationale) {
                showPermissionRationale = true
            } else {
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        } else {
            // Start services if permissions are already granted
            viewModel.startCamera(lifecycleOwner as android.app.Activity)
            viewModel.startWifiScan()
        }
    }
    
    // Collect ViewModel state
    LaunchedEffect(viewModel) {
        // Collect camera state
        viewModel.cameraState.collect { state ->
            screenState = screenState.copy(
                isCameraActive = state is CameraState.ACTIVE,
                error = (state as? CameraState.ERROR)?.error?.message
            )
        }
        
        // Collect processing state
        viewModel.isProcessing.collect { isProcessing ->
            screenState = screenState.copy(isProcessing = isProcessing)
        }
        
        // Collect processing time
        viewModel.processingTimeMs.collect { timeMs ->
            screenState = screenState.copy(processingTimeMs = timeMs)
        }
        
        // Collect analysis results
        viewModel.analysisResults.collect { detections ->
            screenState = screenState.copy(detectedObjects = detections)
        }
        
        // Collect sensor state
        viewModel.sensorState.collect { sensorState ->
            screenState = screenState.copy(
                rotationData = sensorState.rotationData,
                sensorState = sensorState.gyroState,
                wifiState = sensorState.wifiState
            )
        }
    }
    
    // Handle back button press with haptic feedback
    fun handleBackPress() {
        hapticFeedback.performHapticFeedback(
            HapticFeedbackHelper.FeedbackType.CLICK
        )
        onBackPressed()
    }
    
    // Toggle camera controls visibility with haptic feedback
    fun toggleControls() {
        hapticFeedback.performHapticFeedback(
            HapticFeedbackHelper.FeedbackType.SELECTION_CHANGE
        )
        screenState = screenState.copy(showControls = !screenState.showControls)
    }
    
    // Toggle sensor panel visibility with haptic feedback
    fun toggleSensors() {
        hapticFeedback.performHapticFeedback(
            if (screenState.showSensors) {
                HapticFeedbackHelper.FeedbackType.SELECTION_CHANGE
            } else {
                HapticFeedbackHelper.FeedbackType.MEDIUM_IMPACT
            }
        )
        screenState = screenState.copy(showSensors = !screenState.showSensors)
    }
    
    // Toggle between front and back camera with haptic feedback
    fun toggleCameraLens() {
        hapticFeedback.performHapticFeedback(
            HapticFeedbackHelper.FeedbackType.VIRTUAL_KEY
        )
        viewModel.toggleCamera()
        screenState = screenState.copy(
            cameraLensFacing = if (screenState.cameraLensFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
        )
    }
    
    // Capture image with haptic feedback
    fun captureImage() {
        hapticFeedback.performHapticFeedback(
            HapticFeedbackHelper.FeedbackType.CAMERA_CLICK
        )
        scope.launch {
            val bitmap = viewModel.captureImage()
            // Handle the captured image (e.g., save to gallery, display preview, etc.)
            bitmap?.let {
                // TODO: Handle the captured image
                Log.d("CameraScreen", "Image captured: ${it.width}x${it.height}")
            }
        }
    }
    
    // Main content
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Camera preview
        AndroidView(
            factory = { PreviewView(context) },
            modifier = Modifier.fillMaxSize()
        )
        
        // Detection overlay
        if (screenState.isCameraActive) {
            DetectionOverlay(
                detections = screenState.detectedObjects,
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // Processing indicator
        if (screenState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        
        // Permission rationale dialog
        if (showPermissionRationale) {
            AlertDialog(
                onDismissRequest = { showPermissionRationale = false },
                title = { Text(stringResource(R.string.permission_required_title)) },
                text = { Text(stringResource(R.string.camera_location_permission_rationale)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionRationale = false
                            showPermissionSettings = true
                        }
                    ) {
                        Text(stringResource(R.string.open_settings))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showPermissionRationale = false }
                    ) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
        
        // Open app settings intent
        if (showPermissionSettings) {
            LaunchedEffect(showPermissionSettings) {
                try {
                    val intent = android.content.Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Fallback to app info screen
                    try {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.parse("package:" + context.packageName)
                        )
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // Last resort - open app info
                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                }
                showPermissionSettings = false
            }
        }
        
        // Error message
        screenState.error?.let { error ->
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Sensor visualization panel (shown when toggled)
        if (screenState.showSensors) {
            SensorPanel(
                rotationData = screenState.rotationData,
                sensorState = screenState.sensorState,
                wifiState = screenState.wifiState,
                processingTimeMs = screenState.processingTimeMs,
                onClose = { screenState = screenState.copy(showSensors = false) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
            )
        }
        
        // Top app bar
        TopAppBar(
            title = { 
                Text(
                    text = stringResource(R.string.camera_title),
                    style = MaterialTheme.typography.titleMedium
                ) 
            },
            navigationIcon = {
                IconButton(onClick = { handleBackPress() }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back)
                    )
                }
            },
            actions = {
                // Toggle sensor panel with accessibility support
                val sensorButtonLabel = if (screenState.showSensors) 
                    stringResource(R.string.hide_sensors)
                else 
                    stringResource(R.string.show_sensors)
                
                IconButton(
                    onClick = { toggleSensors() },
                    modifier = Modifier
                        .padding(8.dp)
                        .size(40.dp)
                        .semantics {
                            contentDescription = sensorButtonLabel
                            role = Role.Button
                            isTraversalGroup = true
                            isHeading = false
                        }
                ) {
                    Icon(
                        imageVector = if (screenState.showSensors) Icons.Default.SensorsOff else Icons.Default.Sensors,
                        contentDescription = null, // Handled by parent
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                // Toggle camera lens with accessibility support
                IconButton(
                    onClick = { toggleCameraLens() },
                    modifier = Modifier
                        .padding(8.dp)
                        .size(40.dp)
                        .semantics {
                            contentDescription = stringResource(R.string.toggle_camera)
                            role = Role.Button
                            isTraversalGroup = true
                            isHeading = false
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Cameraswitch,
                        contentDescription = null, // Handled by parent
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.6f),
                        Color.Transparent
                    )
                )
            )
        )
        
        // Bottom controls with accessibility support
        AnimatedVisibility(
            visible = screenState.showControls && !screenState.showSensors,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .semantics(mergeDescendants = true) {
                    isTraversalGroup = true
                }
        ) {
            BottomControls(
                isProcessing = screenState.isProcessing,
                onCaptureClick = { captureImage() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            )
        }
        
        // Tap to show/hide controls with accessibility support
        Box(
            modifier = Modifier
                .fillMaxSize()
                .semantics(mergeDescendants = true) {
                    contentDescription = stringResource(R.string.camera_preview)
                    isTraversalGroup = true
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            // Only toggle controls if not tapping on interactive elements
                            if (!screenState.showSensors) {
                                toggleControls()
                            }
                        },
                        onDoubleTap = { offset ->
                            // Double tap to toggle camera lens
                            toggleCameraLens()
                        },
                        onLongPress = { offset ->
                            // Long press to lock focus
                            viewModel.lockFocus(offset)
                            hapticFeedback.performHapticFeedback(
                                HapticFeedbackHelper.FeedbackType.MEDIUM_IMPACT
                            )
                        }
                    )
                }
        )
    }

@Composable
private fun GyroscopeOverlay(
    rotationData: RotationData,
    sensorState: SensorState,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(
                text = "Gyroscope Data",
                style = MaterialTheme.typography.labelLarge
            )
            
            when (sensorState) {
                is SensorState.Active -> {
                    Text(
                        text = rotationData.getFormattedString(),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is SensorState.Error -> {
                    Text(
                        text = "Sensor error: ${sensorState.message}",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                is SensorState.Stopped -> {
                    Text(
                        text = "Sensor stopped",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}
