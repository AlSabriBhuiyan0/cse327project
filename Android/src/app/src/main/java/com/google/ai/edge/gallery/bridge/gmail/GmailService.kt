package com.google.ai.edge.gallery.bridge.gmail

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.ai.edge.gallery.bridge.BridgeMessage
import com.google.ai.edge.gallery.bridge.MessageBridgeRepository
import com.google.ai.edge.gallery.bridge.MessagePlatform
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.gmail.Gmail
import com.google.api.services.gmail.GmailScopes
import com.google.api.services.gmail.model.ListMessagesResponse
import com.google.api.services.gmail.model.Message
import com.google.api.services.gmail.model.MessagePart
import com.google.api.services.gmail.model.MessagePartHeader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Service for handling Gmail API interactions
 */
@Singleton
class GmailService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "GmailService"
    private val PREFS_NAME = "gmail_bridge_prefs"
    private val KEY_EMAIL = "gmail_email"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var gmailService: Gmail? = null
    private var userEmail: String? = null

    // Connection state
    private val _connectionState = MutableStateFlow(GmailConnectionState.DISCONNECTED)
    val connectionState: StateFlow<GmailConnectionState> = _connectionState.asStateFlow()

    // Labels
    private val _labels = MutableStateFlow<List<GmailLabel>>(emptyList())
    val labels: StateFlow<List<GmailLabel>> = _labels.asStateFlow()

    init {
        // Try to initialize if we have saved email
        userEmail = prefs.getString(KEY_EMAIL, null)

        if (!userEmail.isNullOrEmpty()) {
            // Check if we have a valid Google account
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null && account.email == userEmail) {
                initializeGmailService(account)
            }
        }
    }

    /**
     * Gets a GoogleSignInOptions object with the required scopes
     */
    fun getGoogleSignInOptions(): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_SEND, GmailScopes.GMAIL_LABELS)
            .build()
    }

    /**
     * Initializes the Gmail service with the provided Google account
     */
    fun initializeGmailService(account: GoogleSignInAccount) {
        coroutineScope.launch {
            try {
                _connectionState.value = GmailConnectionState.CONNECTING

                userEmail = account.email
                prefs.edit().putString(KEY_EMAIL, userEmail).apply()

                // Set up credentials
                val credential = GoogleAccountCredential.usingOAuth2(
                    context,
                    listOf(GmailScopes.GMAIL_READONLY, GmailScopes.GMAIL_SEND, GmailScopes.GMAIL_LABELS)
                ).apply {
                    selectedAccount = account.account
                    backOff = ExponentialBackOff()
                }

                // Build the Gmail service
                gmailService = Gmail.Builder(
                    NetHttpTransport(),
                    GsonFactory(),
                    credential
                )
                    .setApplicationName("AI Chat Bot")
                    .build()

                // Test connection by loading labels
                loadLabels()

                _connectionState.value = GmailConnectionState.CONNECTED
                MessageBridgeRepository.updateGmailConnection(true)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing Gmail service", e)
                _connectionState.value = GmailConnectionState.ERROR
                MessageBridgeRepository.updateGmailConnection(false)
            }
        }
    }

    /**
     * Loads the list of Gmail labels
     */
    private fun loadLabels() {
        coroutineScope.launch {
            try {
                val listLabelsResponse = gmailService?.users()?.labels()?.list("me")?.execute()
                val labelsList = listLabelsResponse?.labels?.map { label ->
                    GmailLabel(
                        id = label.id,
                        name = label.name
                    )
                } ?: emptyList()

                _labels.value = labelsList
            } catch (e: Exception) {
                Log.e(TAG, "Error loading labels", e)
            }
        }
    }

    /**
     * Loads recent messages from Gmail
     */
    fun loadRecentMessages(maxResults: Int = 20) {
        coroutineScope.launch {
            try {
                val listMessagesResponse = gmailService?.users()?.messages()?.list("me")
                    ?.setMaxResults(maxResults.toLong())
                    ?.execute()

                processMessagesResponse(listMessagesResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading recent messages", e)
            }
        }
    }

    /**
     * Loads messages with a specific label
     */
    fun loadMessagesWithLabel(labelId: String, maxResults: Int = 20) {
        coroutineScope.launch {
            try {
                val listMessagesResponse = gmailService?.users()?.messages()?.list("me")
                    ?.setLabelIds(listOf(labelId))
                    ?.setMaxResults(maxResults.toLong())
                    ?.execute()

                processMessagesResponse(listMessagesResponse)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading messages with label", e)
            }
        }
    }

    /**
     * Processes a list of messages from Gmail API
     */
    private fun processMessagesResponse(response: ListMessagesResponse?) {
        response?.messages?.forEach { messageRef ->
            try {
                val message = gmailService?.users()?.messages()?.get("me", messageRef.id)
                    ?.setFormat("full")
                    ?.execute()

                if (message != null) {
                    val gmailMessage = convertToGmailMessage(message)
                    val bridgeMessage = BridgeMessage.GmailBridgeMessage(
                        id = gmailMessage.id,
                        timestamp = gmailMessage.timestamp,
                        forwarded = false,
                        message = gmailMessage
                    )

                    // Add to recent messages
                    MessageBridgeRepository.addRecentMessage(bridgeMessage)

                    // Check for bridge rules
                    processPotentialBridgeMessage(gmailMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing message ${messageRef.id}", e)
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

                val sentMessage = gmailService?.users()?.messages()?.send("me", message)?.execute()

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
     * Disconnects from Gmail
     */
    fun disconnect() {
        gmailService = null
        userEmail = null
        _connectionState.value = GmailConnectionState.DISCONNECTED
        MessageBridgeRepository.updateGmailConnection(false)

        prefs.edit().remove(KEY_EMAIL).apply()
    }
}

/**
 * Enum representing the various Gmail connection states
 */
enum class GmailConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

/**
 * Data class representing a Gmail label
 */
data class GmailLabel(
    val id: String,
    val name: String
)
