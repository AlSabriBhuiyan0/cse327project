package com.google.ai.edge.gallery.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.gallery.util.TestUtils
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class WifiTest {
    
    private lateinit var wifiManager: WifiManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiStateManager: WifiStateManager
    private lateinit var testUtils: TestUtils
    
    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiStateManager = WifiStateManager(context)
        testUtils = TestUtils()
        
        // Ensure WiFi is enabled for testing
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
            // Add delay to allow WiFi to enable
            Thread.sleep(2000)
        }
    }
    
    @Test
    fun testWifiStateFlow() = runTest {
        // Start collecting WiFi state
        val job = launch {
            wifiStateManager.wifiState.collect { wifiState ->
                // Verify WiFi state is valid
                assertThat(wifiState).isNotNull()
                // Additional assertions based on expected state
            }
        }
        
        // Trigger a WiFi state change
        val wasWifiEnabled = wifiManager.isWifiEnabled
        wifiManager.isWifiEnabled = !wasWifiEnabled
        
        // Wait for state to propagate
        delay(1000)
        
        // Verify state changed
        val currentState = wifiStateManager.wifiState.value
        if (wasWifiEnabled) {
            assertThat(currentState).isInstanceOf(WifiState.Disabled::class.java)
        } else {
            assertThat(currentState).isInstanceOf(WifiState.Connected::class.java)
        }
        
        // Cleanup
        wifiManager.isWifiEnabled = wasWifiEnabled
        job.cancel()
    }
    
    @Test
    fun testNetworkConnectivity() = runTest {
        // Check network capabilities
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        
        // Verify we have internet connectivity
        assertThat(capabilities).isNotNull()
        assertThat(capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)).isTrue()
        
        // Verify we have WiFi transport
        assertThat(capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)).isTrue()
    }
    
    @Test
    fun testWifiScanResults() = runTest {
        // Request a WiFi scan
        val scanResults = wifiStateManager.scanForNetworks()
        
        // Verify we got some results (could be empty if no networks in range)
        assertThat(scanResults).isNotNull()
        
        // If there are networks, verify their structure
        if (scanResults.isNotEmpty()) {
            scanResults.forEach { network ->
                assertThat(network.ssid).isNotEmpty()
                assertThat(network.bssid).isNotEmpty()
                assertThat(network.frequency).isAtLeast(2400) // 2.4 GHz
                assertThat(network.level).isAtMost(0) // Signal level in dBm, should be <= 0
            }
        }
    }
    
    @Test
    fun testWifiConnectionState() = runTest {
        // Get current connection info
        val connectionInfo = wifiManager.connectionInfo
        
        // Check if connected to a network
        if (connectionInfo.networkId != -1) {
            // We're connected to a network
            assertThat(connectionInfo.ssid).isNotEmpty()
            assertThat(connectionInfo.bssid).isNotEmpty()
            
            // Verify signal strength is within valid range
            val signalStrength = WifiManager.calculateSignalLevel(connectionInfo.rssi, 5)
            assertThat(signalStrength).isIn(0..4)
        }
    }
}
