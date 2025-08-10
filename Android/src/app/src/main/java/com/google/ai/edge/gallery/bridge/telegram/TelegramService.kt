package com.google.ai.edge.gallery.bridge.telegram

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.ai.edge.gallery.bridge.MessageBridgeRepository
import com.google.ai.edge.gallery.bridge.MessagePlatform
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.drinkless.td.libcore.telegram.Client
import org.drinkless.td.libcore.telegram.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for handling Telegram API interactions
 */
@Singleton
class TelegramService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val TAG = "TelegramService"
    private val PREFS_NAME = "telegram_bridge_prefs"
    private val KEY_API_ID = "telegram_api_id"
    private val KEY_API_HASH = "telegram_api_hash"
    private val KEY_PHONE_NUMBER = "telegram_phone_number"

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private var client: Client? = null
    private var authorizationState: TdApi.AuthorizationState? = null

    // Connection state
    private val _connectionState = MutableStateFlow(TelegramConnectionState.DISCONNECTED)
    val connectionState: StateFlow<TelegramConnectionState> = _connectionState.asStateFlow()

    // Chat list
    private val _chats = MutableStateFlow<List<TelegramChat>>(emptyList())
    val chats: StateFlow<List<TelegramChat>> = _chats.asStateFlow()

    init {
        // Try to initialize if we have saved credentials
        val apiId = prefs.getInt(KEY_API_ID, 0)
        val apiHash = prefs.getString(KEY_API_HASH, null)

        if (apiId != 0 && !apiHash.isNullOrEmpty()) {
            initialize(apiId, apiHash)
        }
    }

    /**
     * Initializes the Telegram client with API credentials
     */
    fun initialize(apiId: Int, apiHash: String) {
        // Save credentials
        prefs.edit()
            .putInt(KEY_API_ID, apiId)
            .putString(KEY_API_HASH, apiHash)
            .apply()

        // Create client
        try {
            _connectionState.value = TelegramConnectionState.CONNECTING

            // Initialize TDLib native libraries
            Client.execute(TdApi.SetLogVerbosityLevel(2))

            // Create client
            client = Client.create(object : Client.ResultHandler {
                override fun onResult(result: TdApi.Object) {
                    handleResult(result)
                }
            }, null, null)

            // Set TDLib parameters
            val parameters = TdApi.TdlibParameters().apply {
                databaseDirectory = context.getDir("tdlib", Context.MODE_PRIVATE).absolutePath
                useMessageDatabase = true
                useSecretChats = true
                apiId = apiId
                apiHash = apiHash
                systemLanguageCode = "en"
                deviceModel = "Android"
                applicationVersion = "1.0"
                enableStorageOptimizer = true
            }

            client?.send(TdApi.SetTdlibParameters(parameters), null)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Telegram client", e)
            _connectionState.value = TelegramConnectionState.ERROR
            MessageBridgeRepository.updateTelegramConnection(false)
        }
    }

    /**
     * Handles authorization with the provided phone number
     */
    fun startAuthentication(phoneNumber: String) {
        prefs.edit().putString(KEY_PHONE_NUMBER, phoneNumber).apply()

        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null), object : Client.ResultHandler {
            override fun onResult(result: TdApi.Object) {
                when (result) {
                    is TdApi.Ok -> {
                        Log.d(TAG, "Phone number sent successfully")
                        _connectionState.value = TelegramConnectionState.WAITING_FOR_CODE
                    }
                    is TdApi.Error -> {
                        Log.e(TAG, "Error sending phone number: ${result.message}")
                        _connectionState.value = TelegramConnectionState.ERROR
                    }
                }
            }
        })
    }

    /**
     * Submits the authentication code
     */
    fun submitAuthCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code), object : Client.ResultHandler {
            override fun onResult(result: TdApi.Object) {
                when (result) {
                    is TdApi.Ok -> {
                        Log.d(TAG, "Authentication code accepted")
                    }
                    is TdApi.Error -> {
                        Log.e(TAG, "Error submitting auth code: ${result.message}")
                        _connectionState.value = TelegramConnectionState.ERROR
                    }
                }
            }
        })
    }

    /**
     * Submits the password for 2FA
     */
    fun submitPassword(password: String) {
        client?.send(TdApi.CheckAuthenticationPassword(password), object : Client.ResultHandler {
            override fun onResult(result: TdApi.Object) {
                when (result) {
                    is TdApi.Ok -> {
                        Log.d(TAG, "Password accepted")
                    }
                    is TdApi.Error -> {
                        Log.e(TAG, "Error submitting password: ${result.message}")
                        _connectionState.value = TelegramConnectionState.ERROR
                    }
                }
            }
        })
    }

    /**
     * Loads the list of chats
     */
    fun loadChats() {
        coroutineScope.launch {
            client?.send(TdApi.GetChats(TdApi.ChatListMain(), Long.MAX_VALUE, 0, 100), object : Client.ResultHandler {
                override fun onResult(result: TdApi.Object) {
                    when (result) {
                        is TdApi.Chats -> {
                            loadChatDetails(result.chatIds)
                        }
                        is TdApi.Error -> {
                            Log.e(TAG, "Error getting chats: ${result.message}")
                        }
                    }
                }
            })
        }
    }

    /**
     * Loads details for a list of chat IDs
     */
    private fun loadChatDetails(chatIds: LongArray) {
        val chatsList = mutableListOf<TelegramChat>()

        for (chatId in chatIds) {
            client?.send(TdApi.GetChat(chatId), object : Client.ResultHandler {
                override fun onResult(result: TdApi.Object) {
                    when (result) {
                        is TdApi.Chat -> {
                            val chat = TelegramChat(
                                id = result.id.toString(),
                                title = result.title,
                                type = when (result.type) {
                                    is TdApi.ChatTypePrivate -> "private"
                                    is TdApi.ChatTypeBasicGroup -> "group"
                                    is TdApi.ChatTypeSupergroup -> "supergroup"
                                    is TdApi.ChatTypeSecret -> "secret"
                                    else -> "unknown"
                                }
                            )
                            chatsList.add(chat)

                            // Update the chats list when we've processed all chats
                            if (chatsList.size == chatIds.size) {
                                _chats.value = chatsList
                            }
                        }
                        is TdApi.Error -> {
                            Log.e(TAG, "Error getting chat details: ${result.message}")
                        }
                    }
                }
            })
        }
    }

    /**
     * Sends a message to a specific chat
     */
    fun sendMessage(chatId: String, text: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        try {
            val formattedText = TdApi.FormattedText(text, null)
            val content = TdApi.InputMessageText(formattedText, false, true)

            client?.send(TdApi.SendMessage(chatId.toLong(), 0, 0, null, null, content), object : Client.ResultHandler {
                override fun onResult(result: TdApi.Object) {
                    when (result) {
                        is TdApi.Message -> {
                            Log.d(TAG, "Message sent successfully")
                            onSuccess()
                        }
                        is TdApi.Error -> {
                            Log.e(TAG, "Error sending message: ${result.message}")
                            onError(result.message)
                        }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Exception sending message", e)
            onError(e.message ?: "Unknown error")
        }
    }

    /**
     * Logs out from Telegram
     */
    fun logout() {
        client?.send(TdApi.LogOut(), object : Client.ResultHandler {
            override fun onResult(result: TdApi.Object) {
                when (result) {
                    is TdApi.Ok -> {
                        Log.d(TAG, "Logged out successfully")
                        _connectionState.value = TelegramConnectionState.DISCONNECTED
                        MessageBridgeRepository.updateTelegramConnection(false)
                    }
                    is TdApi.Error -> {
                        Log.e(TAG, "Error logging out: ${result.message}")
                    }
                }
            }
        })
    }

    /**
     * Handles various Telegram API results
     */
    private fun handleResult(result: TdApi.Object) {
        when (result) {
            is TdApi.UpdateAuthorizationState -> {
                authorizationState = result.authorizationState
                handleAuthStateUpdate(result.authorizationState)
            }
            is TdApi.UpdateNewMessage -> {
                handleNewMessage(result.message)
            }
        }
    }

    /**
     * Handles authorization state updates
     */
    private fun handleAuthStateUpdate(authState: TdApi.AuthorizationState) {
        when (authState) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                Log.d(TAG, "AuthorizationStateWaitTdlibParameters")
            }
            is TdApi.AuthorizationStateWaitEncryptionKey -> {
                client?.send(TdApi.CheckDatabaseEncryptionKey(null), null)
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                _connectionState.value = TelegramConnectionState.WAITING_FOR_PHONE

                // Try to use saved phone number if available
                val savedPhone = prefs.getString(KEY_PHONE_NUMBER, null)
                if (!savedPhone.isNullOrEmpty()) {
                    startAuthentication(savedPhone)
                }
            }
            is TdApi.AuthorizationStateWaitCode -> {
                _connectionState.value = TelegramConnectionState.WAITING_FOR_CODE
            }
            is TdApi.AuthorizationStateWaitPassword -> {
                _connectionState.value = TelegramConnectionState.WAITING_FOR_PASSWORD
            }
            is TdApi.AuthorizationStateReady -> {
                _connectionState.value = TelegramConnectionState.CONNECTED
                MessageBridgeRepository.updateTelegramConnection(true)
                loadChats()
            }
            is TdApi.AuthorizationStateLoggingOut -> {
                _connectionState.value = TelegramConnectionState.LOGGING_OUT
            }
            is TdApi.AuthorizationStateClosing -> {
                _connectionState.value = TelegramConnectionState.CLOSING
            }
            is TdApi.AuthorizationStateClosed -> {
                _connectionState.value = TelegramConnectionState.DISCONNECTED
                MessageBridgeRepository.updateTelegramConnection(false)
            }
        }
    }

    /**
     * Handles new incoming messages
     */
    private fun handleNewMessage(message: TdApi.Message) {
        // Extract message content
        val content = message.content
        if (content is TdApi.MessageText) {
            // Get chat details
            client?.send(TdApi.GetChat(message.chatId), object : Client.ResultHandler {
                override fun onResult(result: TdApi.Object) {
                    if (result is TdApi.Chat) {
                        // Get sender info
                        when (message.senderId) {
                            is TdApi.MessageSenderUser -> {
                                val userId = (message.senderId as TdApi.MessageSenderUser).userId
                                client?.send(TdApi.GetUser(userId), object : Client.ResultHandler {
                                    override fun onResult(userResult: TdApi.Object) {
                                        if (userResult is TdApi.User) {
                                            val telegramMessage = TelegramMessage(
                                                id = message.id.toString(),
                                                chatId = message.chatId.toString(),
                                                senderId = userId.toString(),
                                                senderName = "${userResult.firstName} ${userResult.lastName}",
                                                text = content.text.text,
                                                timestamp = message.date.toLong() * 1000
                                            )

                                            // Process message for bridge
                                            processIncomingMessage(telegramMessage)
                                        }
                                    }
                                })
                            }
                            is TdApi.MessageSenderChat -> {
                                val telegramMessage = TelegramMessage(
                                    id = message.id.toString(),
                                    chatId = message.chatId.toString(),
                                    senderId = message.chatId.toString(),
                                    senderName = result.title,
                                    text = content.text.text,
                                    timestamp = message.date.toLong() * 1000
                                )

                                // Process message for bridge
                                processIncomingMessage(telegramMessage)
                            }
                        }
                    }
                }
            })
        }
    }

    /**
     * Processes an incoming message for potential bridging
     */
    private fun processIncomingMessage(message: TelegramMessage) {
        // Create bridge message
        val bridgeMessage = com.google.ai.edge.gallery.bridge.BridgeMessage.TelegramBridgeMessage(
            id = message.id,
            timestamp = message.timestamp,
            forwarded = false,
            message = message
        )

        // Add to recent messages
        MessageBridgeRepository.addRecentMessage(bridgeMessage)

        // Check if any bridge rules apply to this message
        coroutineScope.launch {
            val rules = MessageBridgeRepository.bridgeRules.value

            // Find applicable rules
            val applicableRules = rules.filter {
                it.enabled &&
                it.sourceType == MessagePlatform.TELEGRAM &&
                it.sourceIdentifier == message.chatId
            }

            // Process each rule
            applicableRules.forEach { rule ->
                // For now, we'll just log this; in a complete implementation,
                // this would send the message to Gmail
                Log.d(TAG, "Should forward message to Gmail: ${rule.targetIdentifier}")
            }
        }
    }
}

/**
 * Enum representing the various Telegram connection states
 */
enum class TelegramConnectionState {
    DISCONNECTED,
    CONNECTING,
    WAITING_FOR_PHONE,
    WAITING_FOR_CODE,
    WAITING_FOR_PASSWORD,
    CONNECTED,
    LOGGING_OUT,
    CLOSING,
    ERROR
}

/**
 * Data class representing a Telegram chat
 */
data class TelegramChat(
    val id: String,
    val title: String,
    val type: String
)
