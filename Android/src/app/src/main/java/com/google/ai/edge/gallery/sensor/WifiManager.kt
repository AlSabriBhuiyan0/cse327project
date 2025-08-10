package com.google.ai.edge.gallery.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Data class representing WiFi network information
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val capabilities: String,
    val frequency: Int,
    val level: Int,
    val timestamp: Long
)

/**
 * Sealed class representing WiFi connection state
 */
sealed class WifiState {
    object Disabled : WifiState()
    object Disconnected : WifiState()
    data class Connected(val ssid: String, val strength: Int) : WifiState()
    data class Error(val message: String) : WifiState()
}

/**
 * Manages WiFi scanning and connection state monitoring
 */
class WifiManager(private val context: Context) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    private val _wifiState = MutableStateFlow<WifiState>(WifiState.Disconnected)
    val wifiState: StateFlow<WifiState> = _wifiState.asStateFlow()
    
    private val _availableNetworks = MutableStateFlow<List<WifiNetwork>>(emptyList())
    val availableNetworks: StateFlow<List<WifiNetwork>> = _availableNetworks.asStateFlow()
    
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                updateAvailableNetworks()
            }
        }
    }
    
    private val connectivityCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            super.onAvailable(network)
            updateConnectionState()
        }
        
        override fun onLost(network: android.net.Network) {
            super.onLost(network)
            updateConnectionState()
        }
    }
    
    init {
        // Register WiFi scan receiver
        context.registerReceiver(
            wifiScanReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        
        // Register connectivity callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(connectivityCallback)
        }
        
        // Initial state update
        updateConnectionState()
    }
    
    /**
     * Start WiFi scanning
     */
    fun startScan() {
        if (!wifiManager.isWifiEnabled) {
            _wifiState.value = WifiState.Disabled
            return
        }
        
        wifiManager.startScan()
    }
    
    /**
     * Update available networks from scan results
     */
    private fun updateAvailableNetworks() {
        val results = wifiManager.scanResults ?: return
        
        val networks = results.map { scanResult ->
            WifiNetwork(
                ssid = scanResult.SSID.ifEmpty { "<hidden>" },
                bssid = scanResult.BSSID,
                capabilities = scanResult.capabilities,
                frequency = scanResult.frequency,
                level = scanResult.level,
                timestamp = scanResult.timestamp
            )
        }
        
        _availableNetworks.value = networks
    }
    
    /**
     * Update current WiFi connection state
     */
    private fun updateConnectionState() {
        if (!wifiManager.isWifiEnabled) {
            _wifiState.value = WifiState.Disabled
            return
        }
        
        val networkInfo = connectivityManager.activeNetworkInfo
        if (networkInfo?.isConnected == true && networkInfo.type == ConnectivityManager.TYPE_WIFI) {
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo.ssid.removeSurrounding("\"")
            val strength = WifiManager.calculateSignalLevel(wifiInfo.rssi, 5)
            _wifiState.value = WifiState.Connected(ssid, strength)
        } else {
            _wifiState.value = WifiState.Disconnected
        }
    }
    
    /**
     * Clean up resources
     */
    fun dispose() {
        try {
            context.unregisterReceiver(wifiScanReceiver)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                connectivityManager.unregisterNetworkCallback(connectivityCallback)
            }
        } catch (e: Exception) {
            // Ignore if already unregistered
        }
    }
}

/**
 * Composable function to collect WiFi state
 */
@Composable
fun rememberWifiState(context: Context = LocalContext.current): State<WifiState> {
    val wifiManager = remember { WifiManager(context) }
    val wifiState = remember { mutableStateOf<WifiState>(WifiState.Disconnected) }
    
    DisposableEffect(Unit) {
        val job = kotlinx.coroutines.GlobalScope.launch {
            wifiManager.wifiState.collect { state ->
                wifiState.value = state
            }
        }
        
        onDispose {
            job.cancel()
            wifiManager.dispose()
        }
    }
    
    return wifiState
}

/**
 * Composable function to collect available WiFi networks
 */
@Composable
fun rememberAvailableNetworks(context: Context = LocalContext.current): List<WifiNetwork> {
    val wifiManager = remember { WifiManager(context) }
    val networks = remember { mutableStateListOf<WifiNetwork>() }
    
    DisposableEffect(Unit) {
        val job = kotlinx.coroutines.GlobalScope.launch {
            wifiManager.availableNetworks.collect { newNetworks ->
                networks.clear()
                networks.addAll(newNetworks)
            }
        }
        
        // Start scanning
        wifiManager.startScan()
        
        onDispose {
            job.cancel()
            wifiManager.dispose()
        }
    }
    
    return networks
}
