package com.google.ai.edge.gallery

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receiver for geofence transition events.
 * This class handles all geofence transitions (enter, exit, dwell) and triggers
 * appropriate notifications and updates the geofence repository.
 */
@AndroidEntryPoint
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastRcvr"
        private const val CHANNEL_ID = "geofence_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_GEOFENCE_EVENT = "com.google.ai.edge.gallery.ACTION_GEOFENCE_EVENT"
        
        // Maximum number of geofences to include in a single notification
        private const val MAX_GEOFENCES_IN_NOTIFICATION = 5
    }

    @Inject
    lateinit var geofenceManager: GeofenceManager
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_GEOFENCE_EVENT) {
            Log.i(TAG, "Received intent with wrong action: ${intent.action}")
            return
        }

        val geofencingEvent = GeofencingEvent.fromIntent(intent)
        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Log.e(TAG, "Geofencing error: $errorMessage (${geofencingEvent.errorCode})")
            return
        }

        // Get the transition type
        val geofenceTransition = geofencingEvent.geofenceTransition
        val triggeringGeofences = geofencingEvent.triggeringGeofences ?: emptyList()
        
        Log.d(TAG, "Geofence transition: $geofenceTransition, Count: ${triggeringGeofences.size}")

        // Handle different transition types
        when (geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                handleGeofenceEnter(context, triggeringGeofences)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                handleGeofenceExit(context, triggeringGeofences)
            }
            Geofence.GEOFENCE_TRANSITION_DWELL -> {
                handleGeofenceDwell(context, triggeringGeofences)
            }
            else -> {
                Log.w(TAG, "Unhandled geofence transition: $geofenceTransition")
            }
        }
    }
    
    private fun handleGeofenceEnter(context: Context, triggeringGeofences: List<Geofence>) {
        // Update repository with the first geofence ID
        val geofenceId = triggeringGeofences.firstOrNull()?.requestId
        if (geofenceId != null) {
            GeofenceRepository.updateLastEnteredGeofence(geofenceId)
            Log.d(TAG, "Entered geofence: $geofenceId")
        }
        
        // Show notification
        val message = if (triggeringGeofences.size == 1) {
            "You've entered a tracked area"
        } else {
            "You've entered ${triggeringGeofences.size} tracked areas"
        }
        
        sendNotification(context, message, triggeringGeofences.take(MAX_GEOFENCES_IN_NOTIFICATION))
    }
    
    private fun handleGeofenceExit(context: Context, triggeringGeofences: List<Geofence>) {
        // Update repository or handle exit logic
        Log.d(TAG, "Exited ${triggeringGeofences.size} geofences")
        
        val message = if (triggeringGeofences.size == 1) {
            "You've left a tracked area"
        } else {
            "You've left ${triggeringGeofences.size} tracked areas"
        }
        
        sendNotification(context, message, triggeringGeofences.take(MAX_GEOFENCES_IN_NOTIFICATION))
    }
    
    private fun handleGeofenceDwell(context: Context, triggeringGeofences: List<Geofence>) {
        // Handle dwell (user has been in the geofence for a while)
        Log.d(TAG, "Dwelling in ${triggeringGeofences.size} geofences")
        
        val message = if (triggeringGeofences.size == 1) {
            "You've been in this area for a while"
        } else {
            "You've been in these areas for a while"
        }
        
        sendNotification(context, message, triggeringGeofences.take(MAX_GEOFENCES_IN_NOTIFICATION))
    }

    /**
     * Creates and shows a notification with the given details
     * @param context The application context
     * @param contentText The main notification text
     * @param geofences List of geofences that triggered the notification (optional)
     */
    private fun sendNotification(
        context: Context, 
        contentText: String,
        geofences: List<Geofence> = emptyList()
    ) {
        val notificationManager = NotificationManagerCompat.from(context)
        
        // Create the notification channel for Android Oreo and higher
        createNotificationChannel(context)

        // Create an intent to the main activity when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("Location Alert")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_LOCATION_SHARING)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Add geofence details if available
        if (geofences.isNotEmpty()) {
            val style = NotificationCompat.InboxStyle()
                .setBigContentTitle("Geofence Updates")
                .setSummaryText("${geofences.size} location updates")

            geofences.take(5).forEach { geofence ->
                style.addLine(geofence.requestId)
            }

            if (geofences.size > 5) {
                style.addLine("...and ${geofences.size - 5} more")
            }

            builder.setStyle(style)
        }

        // Show the notification with a unique ID for each geofence
        val notificationId = NOTIFICATION_ID + (geofences.firstOrNull()?.requestId?.hashCode() ?: 0)
        notificationManager.notify(notificationId, builder.build())
    }
    
    /**
     * Creates a notification channel for Android O and above
     */
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Geofence Alerts"
            val descriptionText = "Alerts when you enter or exit tracked locations"
            val importance = NotificationManager.IMPORTANCE_HIGH
            
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(true)
                enableLights(true)
                enableVibration(true)
            }
            
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
