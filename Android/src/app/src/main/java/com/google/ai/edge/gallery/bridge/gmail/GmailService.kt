package com.google.ai.edge.gallery.bridge.gmail

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.google.ai.edge.gallery.R
import com.google.ai.edge.gallery.bridge.BridgeMessage
import com.google.ai.edge.gallery.bridge.MessageBridgeRepository
import com.google.ai.edge.gallery.bridge.MessagePlatform
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.model.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

// Notification channel ID for background sync
private const val NOTIFICATION_CHANNEL_ID = "gmail_sync_channel"
private const val NOTIFICATION_ID = 1001
private const val SYNC_WORKER_TAG = "gmail_sync_worker"

/**
 * Service for handling Gmail API interactions including email operations,
 * label management, and background synchronization.
 * 
 * This service depends on GmailAuthService for authentication and uses
 * GmailErrorHandler for consistent error handling and user feedback.
 */
@Singleton
class GmailService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authService: GmailAuthService,
    private val workManager: WorkManager,
    private val errorHandler: GmailErrorHandler
) {
    private val TAG = "GmailService"
    private val PREFS_NAME = "gmail_bridge_prefs"
    private val KEY_EMAIL = "gmail_email"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = GsonFactory.getDefaultInstance()
    private val httpTransport = NetHttpTransport()
    
    // Get Gmail service instance from auth service
    private val gmail: Gmail
        get() = authService.getGmailService()
    private var userEmail: String? = null

    // Flows for state management
    private val _connectionState = MutableStateFlow(GmailConnectionState.DISCONNECTED)
    val connectionState: StateFlow<GmailConnectionState> = _labels.asStateFlow()

    // Labels
    private val _labels = MutableStateFlow<List<GmailLabel>>(emptyList())
    val labels: StateFlow<List<GmailLabel>> = _labels.asStateFlow()
    
    // Messages
    private val _messages = MutableStateFlow<List<GmailMessage>>(emptyList())
    val messages: StateFlow<List<GmailMessage>> = _messages.asStateFlow()
    
    // Sync state
    private val _syncState = MutableStateFlow<SyncState>(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    
    // Last sync time
    private val _lastSyncTime = MutableStateFlow<Long?>(null)
    val lastSyncTime: StateFlow<Long?> = _lastSyncTime.asStateFlow()

    init {
        // Create notification channel for sync notifications
        createNotificationChannel()
        
        // Try to initialize if we have saved email
        userEmail = prefs.getString(KEY_EMAIL, null)

        if (!userEmail.isNullOrEmpty()) {
            // Check if we have a valid Google account
            if (authService.isSignedIn()) {
                initializeGmailService()
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.gmail_sync_channel_name)
            val descriptionText = context.getString(R.string.gmail_sync_channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Initializes the Gmail service with proper error handling and state management.
     * This method attempts to establish a connection to Gmail and load the user's labels.
     */
    private fun initializeGmailService() {
        coroutineScope.launch {
            try {
                _connectionState.value = GmailConnectionState.CONNECTING
                Timber.d("Initializing Gmail service")

                // Test connection by loading labels
                loadLabels()

                _connectionState.value = GmailConnectionState.CONNECTED
                MessageBridgeRepository.updateGmailConnection(true)
                Timber.i("Gmail service initialized successfully")
            } catch (e: Exception) {
                val error = errorHandler.handleError(e, "Failed to initialize Gmail service")
                Timber.e(e, "Error initializing Gmail service: ${error.message}")
                _connectionState.value = GmailConnectionState.ERROR
                MessageBridgeRepository.updateGmailConnection(false)
            }
        }
    }

    /**
     * Loads the list of Gmail labels with proper error handling and retry logic.
     * Updates the labels state flow on success or updates the error state on failure.
     */
    fun loadLabels() {
        coroutineScope.launch {
            try {
                _connectionState.value = GmailConnectionState.LOADING
                Timber.d("Loading Gmail labels")
                
                val listLabelsResponse = withContext(Dispatchers.IO) {
                    gmail.users().labels().list("me").execute()
                }
                
                val labelsList = listLabelsResponse.labels.mapNotNull { label ->
                    try {
                        GmailLabel(
                            id = label.id,
                            name = label.name,
                            messageListVisibility = label.messageListVisibility,
                            labelListVisibility = label.labelListVisibility,
                            type = label.type,
                            messagesTotal = label.messagesTotal?.toInt() ?: 0,
                            messagesUnread = label.messagesUnread?.toInt() ?: 0,
                            threadsTotal = label.threadsTotal?.toInt() ?: 0,
                            threadsUnread = label.threadsUnread?.toInt() ?: 0
                        )
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing label: ${label.id}")
                        null
                    }
                }

                _labels.value = labelsList
                _connectionState.value = GmailConnectionState.CONNECTED
                Timber.d("Successfully loaded ${labelsList.size} labels")
            } catch (e: Exception) {
                val error = errorHandler.handleError(e, "Failed to load labels")
                Timber.e(e, "Error loading labels: ${error.message}")
                _connectionState.value = GmailConnectionState.ERROR
            }
        }
    }
    
    /**
     * Creates a new label
     */
    fun createLabel(name: String, onComplete: (Result<GmailLabel>) -> Unit) {
        coroutineScope.launch {
            try {
                val label = Label().apply {
                    this.name = name
                    labelListVisibility = "labelShow"
                    messageListVisibility = "show"
                }
                
                val createdLabel = gmail.users().labels().create("me", label).execute()
                
                if (createdLabel != null) {
                    val gmailLabel = GmailLabel(
                        id = createdLabel.id,
                        name = createdLabel.name,
                        messageListVisibility = createdLabel.messageListVisibility,
                        labelListVisibility = createdLabel.labelListVisibility,
                        type = createdLabel.type
                    )
                    
                    // Update the labels list
                    _labels.value = _labels.value + gmailLabel
                    onComplete(Result.success(gmailLabel))
                } else {
                    onComplete(Result.failure(Exception("Failed to create label")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error creating label", e)
                onComplete(Result.failure(e))
            }
        }
    }
    
    /**
     * Updates an existing label
     */
    fun updateLabel(labelId: String, newName: String, onComplete: (Result<GmailLabel>) -> Unit) {
        coroutineScope.launch {
            try {
                val label = Label().apply {
                    id = labelId
                    name = newName
                }
                
                val updatedLabel = gmail.users().labels().update("me", labelId, label).execute()
                
                if (updatedLabel != null) {
                    val gmailLabel = GmailLabel(
                        id = updatedLabel.id,
                        name = updatedLabel.name,
                        messageListVisibility = updatedLabel.messageListVisibility,
                        labelListVisibility = updatedLabel.labelListVisibility,
                        type = updatedLabel.type
                    )
                    
                    // Update the labels list
                    _labels.value = _labels.value.map { 
                        if (it.id == labelId) gmailLabel else it 
                    }
                    onComplete(Result.success(gmailLabel))
                } else {
                    onComplete(Result.failure(Exception("Failed to update label")))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating label", e)
                onComplete(Result.failure(e))
            }
        }
    }
    
    /**
     * Deletes a label
     */
    fun deleteLabel(labelId: String, onComplete: (Result<Unit>) -> Unit) {
        coroutineScope.launch {
            try {
                gmail.users().labels().delete("me", labelId).execute()
                
                // Update the labels list
                _labels.value = _labels.value.filter { it.id != labelId }
                onComplete(Result.success(Unit))
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting label", e)
                onComplete(Result.failure(e))
            }
        }
    }

    /**
     * Loads recent messages from Gmail with optional label filtering and query support.
     * 
     * @param maxResults Maximum number of messages to return (default: 20, max: 100)
     * @param labelIds Optional list of label IDs to filter by
     * @param query Optional search query string
     * @param onError Optional callback for error handling
     */
    fun loadRecentMessages(
        maxResults: Int = 20,
        labelIds: List<String>? = null,
        query: String? = null,
        onError: ((String) -> Unit)? = null
    ) {
        coroutineScope.launch {
            try {
                _connectionState.value = GmailConnectionState.LOADING
                Timber.d("Loading recent messages (max: $maxResults, labels: ${labelIds?.size ?: 0}, query: $query)")
                
                val request = gmail.users().messages().list("me")
                    .setMaxResults(maxResults.coerceIn(1, 100).toLong())
                
                labelIds?.takeIf { it.isNotEmpty() }?.let { request.setLabelIds(it) }
                query?.takeIf { it.isNotBlank() }?.let { request.setQ(it) }
                
                val listMessagesResponse = withContext(Dispatchers.IO) {
                    request.execute()
                }
                
                processMessagesResponse(listMessagesResponse)
                _connectionState.value = GmailConnectionState.CONNECTED
                Timber.d("Successfully loaded ${listMessagesResponse.messages?.size ?: 0} messages")
            } catch (e: Exception) {
                val error = errorHandler.handleError(e, "Failed to load messages")
                Timber.e(e, "Error loading messages: ${error.message}")
                _connectionState.value = GmailConnectionState.ERROR
                onError?.invoke(error.userMessage)
            }
        }
    }
    
    /**
     * Loads a specific message by ID
     */
    suspend fun getMessage(messageId: String): GmailMessage? {
        return withContext(Dispatchers.IO) {
            try {
                val message = gmail.users().messages()
                    .get("me", messageId)
                    .setFormat("full")
                    .execute()
                
                message?.let { convertToGmailMessage(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting message", e)
                null
            }
        }
    }
    
    /**
     * Sends an email with proper error handling and retry logic.
     * 
     * @param to Recipient email address(es), comma-separated if multiple
     * @param subject Email subject
     * @param body Email body content
     * @param cc List of CC recipients (optional)
     * @param bcc List of BCC recipients (optional)
     * @param onComplete Callback with the result of the operation
     */
    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList(),
        onComplete: (Result<GmailMessage>) -> Unit
    ) {
        coroutineScope.launch {
            try {
                Timber.d("Preparing to send email to: $to, subject: $subject")
                
                val mimeMessage = try {
                    createMimeMessage(
                        to = to,
                        subject = subject,
                        body = body,
                        cc = cc,
                        bcc = bcc
                    )
                } catch (e: MessagingException) {
                    val error = errorHandler.handleError(e, "Failed to create email message")
                    Timber.e(e, "Error creating MIME message: ${error.message}")
                    return@launch onComplete(Result.failure(Exception(error.userMessage)))
                }
                
                val bytes = try {
                    ByteArrayOutputStream().use { output ->
                        mimeMessage.writeTo(output)
                        output.toByteArray()
                    }
                } catch (e: Exception) {
                    val error = errorHandler.handleError(e, "Failed to encode email message")
                    Timber.e(e, "Error encoding email: ${error.message}")
                    return@launch onComplete(Result.failure(Exception(error.userMessage)))
                }
                
                val encoded = Base64.getUrlEncoder().encodeToString(bytes)
                val message = com.google.api.services.gmail.model.Message().apply {
                    raw = encoded
                }
                
                val sentMessage = try {
                    withContext(Dispatchers.IO) {
                        gmail.users().messages()
                            .send("me", message)
                            .execute()
                    }
                } catch (e: Exception) {
                    val error = errorHandler.handleError(e, "Failed to send email")
                    Timber.e(e, "Error sending email: ${error.message}")
                    return@launch onComplete(Result.failure(Exception(error.userMessage)))
                }
                
                if (sentMessage != null) {
                    val gmailMessage = convertToGmailMessage(sentMessage)
                    _messages.value = listOf(gmailMessage) + _messages.value
                    Timber.i("Email sent successfully to: $to, message ID: ${sentMessage.id}")
                    onComplete(Result.success(gmailMessage))
                } else {
                    val error = GmailError(
                        "Failed to send email: No response from server",
                        "Failed to send email. Please try again.",
                        GmailErrorType.API_ERROR
                    )
                    Timber.e("Failed to send email: No response from server")
                    onComplete(Result.failure(Exception(error.userMessage)))
                }
            } catch (e: Exception) {
                val error = errorHandler.handleError(e, "Unexpected error sending email")
                Timber.e(e, "Unexpected error sending email: ${error.message}")
                onComplete(Result.failure(Exception(error.userMessage)))
            }
        }
    }
    
    /**
     * Creates a MIME message for sending
     */
    private fun createMimeMessage(
        to: String,
        subject: String,
        body: String,
        cc: List<String> = emptyList(),
        bcc: List<String> = emptyList()
    ): MimeMessage {
        val props = Properties()
        val session = Session.getDefaultInstance(props, null)
        
        return MimeMessage(session).apply {
            setFrom(InternetAddress(userEmail))
            setRecipients(javax.mail.Message.RecipientType.TO, to)
            
            if (cc.isNotEmpty()) {
                setRecipients(
                    javax.mail.Message.RecipientType.CC,
                    cc.joinToString(",")
                )
            }
            
            if (bcc.isNotEmpty()) {
                setRecipients(
                    javax.mail.Message.RecipientType.BCC,
                    bcc.joinToString(",")
                )
            }
            
            setSubject(subject)
            setText(body)
            saveChanges()
        }
    }

    /**
     * Loads messages with a specific label
     */
    fun loadMessagesWithLabel(labelId: String, maxResults: Int = 20) {
        coroutineScope.launch {
            try {
                val listMessagesResponse = gmail.users().messages().list("me")
                    .setLabelIds(listOf(labelId))
                    .setMaxResults(maxResults.toLong())
                    .execute()

                processMessagesResponse(listMessagesResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages with label", e)
            }
        }
    }

    /**
     * Starts a periodic sync with Gmail
     * @param intervalMinutes The interval in minutes between syncs
     * @param onlyOnWifi Whether to sync only when connected to WiFi
     */
    fun startPeriodicSync(intervalMinutes: Long = 15, onlyOnWifi: Boolean = true) {
        // Cancel any existing sync workers
        workManager.cancelAllWorkByTag(SYNC_WORKER_TAG)
        
        // Create constraints
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (onlyOnWifi) NetworkType.UNMETERED 
                else NetworkType.CONNECTED
            )
            .setRequiresBatteryNotLow(true)
            .build()
        
        // Create the sync worker request
        val syncRequest = PeriodicWorkRequestBuilder<GmailSyncWorker>(
            intervalMinutes, 
            TimeUnit.MINUTES,
            5, // Flex interval
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .addTag(SYNC_WORKER_TAG)
            .build()
        
        // Enqueue the work
        workManager.enqueueUniquePeriodicWork(
            "gmail_sync_work",
            ExistingPeriodicWorkPolicy.REPLACE,
            syncRequest
        )
        
        _syncState.value = SyncState.RUNNING
    }
    
    /**
     * Stops the periodic sync
     */
    fun stopPeriodicSync() {
        workManager.cancelAllWorkByTag(SYNC_WORKER_TAG)
        _syncState.value = SyncState.STOPPED
    }
    
    /**
     * Performs a manual sync
     */
    fun syncNow() {
        val syncRequest = OneTimeWorkRequestBuilder<GmailSyncWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(SYNC_WORKER_TAG)
            .build()
        
        workManager.enqueue(syncRequest)
    }
    
    /**
     * Processes a list of messages from Gmail API with proper error handling and logging.
     * 
     * @param response The ListMessagesResponse from Gmail API
     */
    private fun processMessagesResponse(response: ListMessagesResponse?) {
        coroutineScope.launch {
            try {
                val messages = response?.messages?.mapNotNull { messageRef ->
                    try {
                        withContext(Dispatchers.IO) {
                            gmail.users().messages()
                                .get("me", messageRef.id)
                                .setFormat("full")
                                .execute()
                        }?.let { convertToGmailMessage(it) }
                    } catch (e: Exception) {
                        val error = errorHandler.handleError(e, "Error processing message ${messageRef.id}")
                        Timber.e(e, "Error processing message ${messageRef.id}: ${error.message}")
                        null
                    }
                } ?: emptyList()
                
                _messages.value = messages
                _lastSyncTime.value = System.currentTimeMillis()
                Timber.d("Processed ${messages.size} messages")
                
            } catch (e: Exception) {
                val error = errorHandler.handleError(e, "Failed to process messages")
                Timber.e(e, "Error processing messages response: ${error.message}")
                _connectionState.value = GmailConnectionState.ERROR
            }
        }
    }

    /**
     * Converts a Gmail API Message to our GmailMessage model
     */
    private fun convertToGmailMessage(message: Message): GmailMessage {
        // Extract headers
        val headers = message.payload.headers
        val subject = headers.find { it.name.equals("Subject", ignoreCase = true) }?.value ?: ""
        val from = headers.find { it.name.equals("From", ignoreCase = true) }?.value ?: ""
        val to = headers.find { it.name.equals("To", ignoreCase = true) }?.value ?: ""
        val recipients = to.split(",").map { it.trim() }

        // Extract body
        val body = extractMessageBody(message.payload)

        // Check for attachments
        val hasAttachments = message.payload.parts?.any {
            it.filename != null && it.filename.isNotEmpty()
        } ?: false

        return GmailMessage(
            id = message.id,
            threadId = message.threadId,
            sender = from,
            recipients = recipients,
            subject = subject,
            body = body,
            timestamp = message.internalDate,
            hasAttachments = hasAttachments,
            labels = message.labelIds ?: emptyList()
        )
    }

    /**
     * Recursively extracts the text body from a message part
     */
    private fun extractMessageBody(part: MessagePart): String {
        if (part.mimeType == "text/plain" && part.body.data != null) {
            return String(android.util.Base64.decode(
                part.body.data.replace('-', '+').replace('_', '/'),
                android.util.Base64.DEFAULT
            ))
        }

        if (part.parts != null) {
            for (subPart in part.parts) {
                val body = extractMessageBody(subPart)
                if (body.isNotEmpty()) {
                    return body
                }
            }
        }

        return ""
    }

    /**
     * Processes a Gmail message for potential bridging
     */
    private fun processPotentialBridgeMessage(message: GmailMessage) {
        coroutineScope.launch {
            val rules = MessageBridgeRepository.bridgeRules.value

            // Find applicable rules based on labels or sender
            val applicableRules = rules.filter { rule ->
                rule.enabled &&
                rule.sourceType == MessagePlatform.GMAIL &&
                (rule.sourceIdentifier == message.sender ||
                 message.labels.contains(rule.sourceIdentifier))
            }

            // Process each rule
            applicableRules.forEach { rule ->
                // For now, we'll just log this; in a complete implementation,
                // this would send the message to Telegram
                Log.d(TAG, "Should forward message to Telegram: ${rule.targetIdentifier}")
            }
        }
    }

    /**
     * Sends an email
     */
    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        coroutineScope.launch {
            try {
                val props = Properties()
                val session = Session.getDefaultInstance(props, null)
                val email = MimeMessage(session)

                email.setFrom(InternetAddress(userEmail))
                email.addRecipient(javax.mail.Message.RecipientType.TO, InternetAddress(to))
                email.subject = subject
                email.setText(body)

                val buffer = ByteArrayOutputStream()
                email.writeTo(buffer)
                val bytes = buffer.toByteArray()
                val encodedEmail = android.util.Base64.encodeToString(bytes, android.util.Base64.URL_SAFE)

                val message = Message()
                message.raw = encodedEmail

                val sentMessage = gmail.users().messages().send("me", message).execute()

                if (sentMessage != null) {
                    Log.d(TAG, "Email sent successfully: ${sentMessage.id}")
                    onSuccess()
                } else {
                    onError("Failed to send email: Unknown error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error sending email", e)
                onError("Failed to send email: ${e.message}")
            }
        }
    }

    /**
     * Disconnects from Gmail and cleans up resources.
     * This method ensures all ongoing operations are cancelled and resources are released.
     */
    fun disconnect() {
        try {
            // Cancel any pending operations
            coroutineScope.cancel("Disconnecting Gmail service")
            
            // Clear any sensitive data
            _messages.value = emptyList()
            _labels.value = emptyList()
            _lastSyncTime.value = null
            
            // Update connection state
            _connectionState.value = GmailConnectionState.DISCONNECTED
            MessageBridgeRepository.updateGmailConnection(false)
            
            Timber.i("Gmail service disconnected successfully")
        } catch (e: Exception) {
            val error = errorHandler.handleError(e, "Error during Gmail service disconnection")
            Timber.e(e, "Error disconnecting Gmail service: ${error.message}")
            // Even if there's an error, we still want to update the connection state
            _connectionState.value = GmailConnectionState.DISCONNECTED
            MessageBridgeRepository.updateGmailConnection(false)
        }
    }
}

/**
 * Enum representing the various Gmail connection states.
 * This helps track the current state of the Gmail service connection.
 */
enum class GmailConnectionState {
    /** Service is not connected */
    DISCONNECTED,
    
    /** Service is in the process of connecting */
    CONNECTING,
    
    /** Service is connected and ready */
    CONNECTED,
    
    /** Service is loading data */
    LOADING,
    
    /** An error occurred */
    ERROR
}

/**
 * Data class representing a Gmail label
 */
data class GmailLabel(
    val id: String,
    val name: String
)
