package com.google.ai.edge.gallery

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import com.google.ai.edge.gallery.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Foreground service for handling geofence events in the background.
 * This service is started when the app needs to monitor geofences and runs with a persistent
 * notification to meet Android's foreground service requirements.
 */
@AndroidEntryPoint
class GeofenceForegroundService : LifecycleService() {

    @Inject
    lateinit var geofenceManager: GeofenceManager

    private val notificationId = 1001
    private val channelId = "geofence_service_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        // Handle any intents that started the service
        intent?.let { handleIntent(it) }
        
        // If we get killed, after returning from here, restart with the last intent
        return START_STICKY
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            ACTION_START -> {
                // Start monitoring geofences
                geofenceManager.startMonitoringGeofences()
            }
            ACTION_STOP -> {
                // Stop monitoring geofences and stop self
                geofenceManager.stopMonitoringGeofences()
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Geofence Service"
            val descriptionText = "Monitoring your geofences in the background"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Geofence Monitoring Active")
            .setContentText("Your geofences are being monitored")
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        const val ACTION_START = "com.google.ai.edge.gallery.action.START_GEOFENCE_SERVICE"
        const val ACTION_STOP = "com.google.ai.edge.gallery.action.STOP_GEOFENCE_SERVICE"
        
        /**
         * Starts the foreground service to monitor geofences
         */
        fun startService(context: Context) {
            val intent = Intent(context, GeofenceForegroundService::class.java).apply {
                action = ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * Stops the foreground service
         */
        fun stopService(context: Context) {
            val intent = Intent(context, GeofenceForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }
}
