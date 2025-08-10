package com.google.ai.edge.gallery.bridge.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.ai.edge.gallery.MainActivity
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.bridge.service.MessageBridgeService
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Service that handles Firebase Cloud Messaging notifications.
 * Used for receiving notifications about new messages from Telegram and Gmail.
 */
@AndroidEntryPoint
class BridgeMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fcmTokenManager by lazy { FCMTokenManager(context = applicationContext) }

    companion object {
        private const val TAG = "BridgeMessagingService"
        private const val CHANNEL_ID = "message_bridge_channel"
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        
        // Register the token with our backend
        serviceScope.launch {
            fcmTokenManager.getAndRegisterFCMToken()
        }
    }
    
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        Log.d(TAG, "Message received from: ${message.from}")
        
        // Extract message data
        val data = message.data
        
        // Check if this is a bridge-related message
        if (data.containsKey("type")) {
            // Process the message based on type
            when (data["type"]) {
                "telegram" -> processTelegramNotification(data)
                "gmail" -> processGmailNotification(data)
            }
            
            // Start the bridge service to process any pending messages
            MessageBridgeService.start(applicationContext)
        }
    }
    
    /**
     * Process a notification about new Telegram messages
     */
    private fun processTelegramNotification(data: Map<String, String>) {
        val chatId = data["chat_id"] ?: return
        val chatName = data["chat_name"] ?: "Telegram"
        val messageCount = data["message_count"]?.toIntOrNull() ?: 1
        
        // Create and show notification
        val title = "New Telegram message${if (messageCount > 1) "s" else ""}"
        val text = "You have $messageCount new message${if (messageCount > 1) "s" else ""} in $chatName"
        
        showNotification(title, text)
        
        // Trigger message processing in the background
        serviceScope.launch {
            // In a real implementation, you would trigger immediate processing
            // of messages from this chat
            Log.d(TAG, "Would process new Telegram messages from chat $chatId")
        }
    }
    
    /**
     * Process a notification about new Gmail messages
     */
    private fun processGmailNotification(data: Map<String, String>) {
        val label = data["label"] ?: "Inbox"
        val messageCount = data["message_count"]?.toIntOrNull() ?: 1
        
        // Create and show notification
        val title = "New Gmail message${if (messageCount > 1) "s" else ""}"
        val text = "You have $messageCount new message${if (messageCount > 1) "s" else ""} in $label"
        
        showNotification(title, text)
        
        // Trigger message processing in the background
        serviceScope.launch {
            // In a real implementation, you would trigger immediate processing
            // of messages from this label
            Log.d(TAG, "Would process new Gmail messages from label $label")
        }
    }
    
    /**
     * Shows a notification to the user
     */
    private fun showNotification(title: String, text: String) {
        // Create notification channel for Android O and above
        createNotificationChannel()
        
        // Create intent for when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("openBridge", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Build the notification
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use an appropriate icon
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        
        // Show the notification
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }
    
    /**
     * Creates the notification channel for Android O and above
     */
    private fun createNotificationChannel() {
        // Create notification channel for Android O and above
        val name = "Message Bridge"
        val description = "Notifications for Telegram and Gmail messages"
        val importance = NotificationManager.IMPORTANCE_HIGH

        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            this.description = description
            enableVibration(true)
        }

        // Register the channel with the system
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}
