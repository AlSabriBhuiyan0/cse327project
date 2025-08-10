package com.google.ai.edge.gallery.bridge

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.gallery.bridge.gmail.GmailConnectionState
import com.google.ai.edge.gallery.bridge.gmail.GmailLabel
import com.google.ai.edge.gallery.bridge.gmail.GmailService
import com.google.ai.edge.gallery.bridge.telegram.TelegramChat
import com.google.ai.edge.gallery.bridge.telegram.TelegramConnectionState
import com.google.ai.edge.gallery.bridge.telegram.TelegramService
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MessageBridgeViewModel @Inject constructor(
    private val telegramService: TelegramService,
    private val gmailService: GmailService
) : ViewModel() {

    private val TAG = "MessageBridgeViewModel"

    // UI state
    private val _uiState = MutableStateFlow<MessageBridgeUiState>(MessageBridgeUiState.Loading)
    val uiState: StateFlow<MessageBridgeUiState> = _uiState.asStateFlow()

    // Bridge rules
    val bridgeRules = MessageBridgeRepository.bridgeRules

    // Recent messages
    val recentMessages = MessageBridgeRepository.recentMessages

    // Connection states
    val telegramConnected = MessageBridgeRepository.telegramConnected
    val gmailConnected = MessageBridgeRepository.gmailConnected

    // Telegram chats
    val telegramChats = telegramService.chats

    // Gmail labels
    val gmailLabels = gmailService.labels

    // Connection states
    val telegramConnectionState = telegramService.connectionState
    val gmailConnectionState = gmailService.connectionState

    init {
        viewModelScope.launch {
            // Monitor Telegram connection state
            telegramService.connectionState.collectLatest { state ->
                when (state) {
                    TelegramConnectionState.CONNECTED -> {
                        updateUiState()
                        telegramService.loadChats()
                    }
                    TelegramConnectionState.ERROR -> {
                        _uiState.value = MessageBridgeUiState.Error("Telegram connection error")
                    }
                    else -> {
                        updateUiState()
                    }
                }
            }
        }

        viewModelScope.launch {
            // Monitor Gmail connection state
            gmailService.connectionState.collectLatest { state ->
                when (state) {
                    GmailConnectionState.CONNECTED -> {
                        updateUiState()
                        gmailService.loadRecentMessages()
                    }
                    GmailConnectionState.ERROR -> {
                        _uiState.value = MessageBridgeUiState.Error("Gmail connection error")
                    }
                    else -> {
                        updateUiState()
                    }
                }
            }
        }
    }

    /**
     * Updates the UI state based on current connection states
     */
    private fun updateUiState() {
        val telegramState = telegramService.connectionState.value
        val gmailState = gmailService.connectionState.value

        _uiState.value = when {
            telegramState == TelegramConnectionState.CONNECTED &&
                    gmailState == GmailConnectionState.CONNECTED -> {
                MessageBridgeUiState.BothConnected
            }
            telegramState == TelegramConnectionState.CONNECTED -> {
                MessageBridgeUiState.TelegramOnlyConnected
            }
            gmailState == GmailConnectionState.CONNECTED -> {
                MessageBridgeUiState.GmailOnlyConnected
            }
            telegramState == TelegramConnectionState.WAITING_FOR_PHONE ||
                    telegramState == TelegramConnectionState.WAITING_FOR_CODE ||
                    telegramState == TelegramConnectionState.WAITING_FOR_PASSWORD -> {
                MessageBridgeUiState.TelegramAuthInProgress(telegramState)
            }
            gmailState == GmailConnectionState.CONNECTING -> {
                MessageBridgeUiState.GmailAuthInProgress
            }
            else -> {
                MessageBridgeUiState.NoneConnected
            }
        }
    }

    /**
     * Initializes the Telegram client
     */
    fun initializeTelegram(apiId: Int, apiHash: String) {
        telegramService.initialize(apiId, apiHash)
    }

    /**
     * Starts Telegram authentication with a phone number
     */
    fun startTelegramAuth(phoneNumber: String) {
        telegramService.startAuthentication(phoneNumber)
    }

    /**
     * Submits the Telegram authentication code
     */
    fun submitTelegramCode(code: String) {
        telegramService.submitAuthCode(code)
    }

    /**
     * Submits the Telegram password for 2FA
     */
    fun submitTelegramPassword(password: String) {
        telegramService.submitPassword(password)
    }

    /**
     * Logs out from Telegram
     */
    fun disconnectTelegram() {
        telegramService.logout()
    }

    /**
     * Initializes the Gmail service with a Google account
     */
    fun initializeGmail(account: GoogleSignInAccount) {
        gmailService.initializeGmailService(account)
    }

    /**
     * Disconnects from Gmail
     */
    fun disconnectGmail() {
        gmailService.disconnect()
    }

    /**
     * Gets the Google Sign-In options needed for Gmail
     */
    fun getGoogleSignInOptions() = gmailService.getGoogleSignInOptions()

    /**
     * Adds a new bridge rule
     */
    fun addBridgeRule(rule: BridgeRule): Boolean {
        val added = MessageBridgeRepository.addBridgeRule(rule)
        if (added) {
            // Start the bridge service when a rule is added
            MessageBridgeService.start(getApplication())
            registerGeofences()
        }
        return added
    }

    /**
     * Initializes the bridge service if there are active rules
     */
    fun initializeBridgeServiceIfNeeded() {
        val rules = MessageBridgeRepository.bridgeRules.value
        if (rules.isNotEmpty() && rules.any { it.enabled }) {
            MessageBridgeService.start(getApplication())
        }
    }

    /**
     * Removes a bridge rule
     */
    fun removeBridgeRule(ruleId: String): Boolean {
        return MessageBridgeRepository.removeBridgeRule(ruleId)
    }

    /**
     * Sends a test message via Telegram
     */
    fun sendTelegramMessage(
        chatId: String,
        text: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        telegramService.sendMessage(chatId, text, onSuccess, onError)
    }

    /**
     * Sends a test email via Gmail
     */
    fun sendEmail(
        to: String,
        subject: String,
        body: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        gmailService.sendEmail(to, subject, body, onSuccess, onError)
    }

    /**
     * Clears the recent messages history
     */
    fun clearRecentMessages() {
        MessageBridgeRepository.clearRecentMessages()
    }
}

/**
 * Sealed class representing the various UI states for the message bridge
 */
sealed class MessageBridgeUiState {
    object Loading : MessageBridgeUiState()
    object NoneConnected : MessageBridgeUiState()
    object TelegramOnlyConnected : MessageBridgeUiState()
    object GmailOnlyConnected : MessageBridgeUiState()
    object BothConnected : MessageBridgeUiState()
    data class TelegramAuthInProgress(val state: TelegramConnectionState) : MessageBridgeUiState()
    object GmailAuthInProgress : MessageBridgeUiState()
    data class Error(val message: String) : MessageBridgeUiState()
}
