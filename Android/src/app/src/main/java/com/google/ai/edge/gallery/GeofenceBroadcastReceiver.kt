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
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receiver for geofence transition events.
 * This class handles geofence transitions and triggers notifications
 * when a user enters a monitored geofence area.
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "GeofenceBroadcastRcvr"
        private const val CHANNEL_ID = "geofence_channel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent == null) {
            Log.e(TAG, "GeofencingEvent is null")
            return
        }

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofencingEvent.getErrorCode()
            Log.e(TAG, "Geofencing error: $errorMessage")
            return
        }

        // Get the transition type
        val geofenceTransition = geofencingEvent.geofenceTransition

        // Check if the transition type is ENTER
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            // Get the geofences that were triggered
            val triggeringGeofences = geofencingEvent.triggeringGeofences

            // Get the first triggering geofence ID
            val geofenceId = triggeringGeofences?.firstOrNull()?.requestId

            // Update the repository with the geofence ID
            if (geofenceId != null) {
                GeofenceRepository.updateLastEnteredGeofence(geofenceId)
                Log.d(TAG, "Updated repository with geofence ID: $geofenceId")
            }

            // Create notification
            sendNotification(context, "You have entered a tracked area")

            Log.i(TAG, "Geofence ENTER transition detected: ${triggeringGeofences?.size ?: 0} geofences")
        } else {
            // Log the transition that wasn't handled
            Log.i(TAG, "Geofence transition not handled: $geofenceTransition")
        }
    }

    /**
     * Creates and shows a notification with the given details
     */
    private fun sendNotification(context: Context, contentText: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create the notification channel for Android Oreo and higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Geofence Notifications"
            val descriptionText = "Notifications for geofence transitions"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Create an intent to the main activity when notification is tapped
        val intent = Intent(context, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map) // Using a system icon for simplicity
            .setContentTitle("Geofence Alert")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        // Show the notification
        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }
}
