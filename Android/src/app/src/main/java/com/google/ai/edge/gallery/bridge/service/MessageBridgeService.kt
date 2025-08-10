package com.google.ai.edge.gallery.bridge.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.ai.edge.gallery.bridge.MessageBridgeRepository
import com.google.ai.edge.gallery.bridge.worker.MessageMonitoringWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Service that manages the bridge between Telegram and Gmail.
 * It schedules the background worker and handles notifications.
 */
@AndroidEntryPoint
class MessageBridgeService : Service() {
    companion object {
        private const val TAG = "MessageBridgeService"

        /**
         * Starts the bridge service
         */
        fun start(context: Context) {
            context.startService(Intent(context, MessageBridgeService::class.java))
        }

        /**
         * Stops the bridge service
         */
        fun stop(context: Context) {
            context.stopService(Intent(context, MessageBridgeService::class.java))
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isMonitoring = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "MessageBridgeService created")

        // Start observing the bridge rule changes and connection states
        observeBridgeRules()
        observeConnectionStates()
    }

    /**
     * Observes changes to bridge rules and updates the worker accordingly
     */
    private fun observeBridgeRules() {
        serviceScope.launch {
            MessageBridgeRepository.bridgeRules.collectLatest { rules ->
                Log.d(TAG, "Bridge rules updated: ${rules.size} rules")

                if (rules.isEmpty()) {
                    // If no rules, cancel the worker
                    if (isMonitoring) {
                        MessageMonitoringWorker.cancel(applicationContext)
                        isMonitoring = false
                    }
                } else if (rules.any { it.enabled }) {
                    // If any enabled rules, ensure worker is scheduled
                    if (!isMonitoring) {
                        MessageMonitoringWorker.schedule(applicationContext)
                        isMonitoring = true
                    }
                }
            }
        }
    }

    /**
     * Observes connection states for Telegram and Gmail
     */
    private fun observeConnectionStates() {
        serviceScope.launch {
            // Combine Telegram and Gmail connection states
            // If both disconnect, stop monitoring

            // For simplicity, we'll just log here, but in a real implementation,
            // you'd use Flow.combine to watch both states simultaneously
            Log.d(TAG, "Started observing connection states")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "MessageBridgeService started")

        // Check if we should start monitoring immediately
        val rules = MessageBridgeRepository.bridgeRules.value
        val telegramConnected = MessageBridgeRepository.telegramConnected.value
        val gmailConnected = MessageBridgeRepository.gmailConnected.value

        if (rules.isNotEmpty() && rules.any { it.enabled } && (telegramConnected || gmailConnected)) {
            MessageMonitoringWorker.schedule(applicationContext)
            isMonitoring = true
        }

        // Return sticky to ensure the service keeps running
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "MessageBridgeService destroyed")

        // Cancel worker when service is destroyed
        if (isMonitoring) {
            MessageMonitoringWorker.cancel(applicationContext)
        }

        // Cancel coroutines
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
