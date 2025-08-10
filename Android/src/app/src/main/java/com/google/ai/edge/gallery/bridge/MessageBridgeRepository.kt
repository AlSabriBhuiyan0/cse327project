package com.google.ai.edge.gallery.bridge

import com.google.ai.edge.gallery.bridge.gmail.GmailMessage
import com.google.ai.edge.gallery.bridge.telegram.TelegramMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Repository for managing the Telegram-Gmail bridge feature
 */
object MessageBridgeRepository {
    // Connection state for both platforms
    private val _gmailConnected = MutableStateFlow(false)
    val gmailConnected: StateFlow<Boolean> = _gmailConnected.asStateFlow()

    private val _telegramConnected = MutableStateFlow(false)
    val telegramConnected: StateFlow<Boolean> = _telegramConnected.asStateFlow()

    // Bridge rules
    private val _bridgeRules = MutableStateFlow<List<BridgeRule>>(emptyList())
    val bridgeRules: StateFlow<List<BridgeRule>> = _bridgeRules.asStateFlow()

    // Recent messages
    private val _recentMessages = MutableStateFlow<List<BridgeMessage>>(emptyList())
    val recentMessages: StateFlow<List<BridgeMessage>> = _recentMessages.asStateFlow()

    /**
     * Updates the Gmail connection state
     */
    fun updateGmailConnection(connected: Boolean) {
        _gmailConnected.value = connected
    }

    /**
     * Updates the Telegram connection state
     */
    fun updateTelegramConnection(connected: Boolean) {
        _telegramConnected.value = connected
    }

    /**
     * Adds a new bridge rule
     */
    fun addBridgeRule(rule: BridgeRule): Boolean {
        val currentRules = _bridgeRules.value

        // Check if a similar rule already exists
        if (currentRules.any { it.sourceType == rule.sourceType && it.targetType == rule.targetType &&
                               it.sourceIdentifier == rule.sourceIdentifier }) {
            return false
        }

        _bridgeRules.value = currentRules + rule
        return true
    }

    /**
     * Removes a bridge rule
     */
    fun removeBridgeRule(ruleId: String): Boolean {
        val currentRules = _bridgeRules.value
        val newRules = currentRules.filter { it.id != ruleId }

        if (newRules.size < currentRules.size) {
            _bridgeRules.value = newRules
            return true
        }

        return false
    }

    /**
     * Adds a new message to the recent messages list
     */
    fun addRecentMessage(message: BridgeMessage) {
        val currentMessages = _recentMessages.value
        _recentMessages.value = (listOf(message) + currentMessages).take(100) // Keep last 100 messages
    }

    /**
     * Clears all recent messages
     */
    fun clearRecentMessages() {
        _recentMessages.value = emptyList()
    }
}

/**
 * Enum representing the supported message platforms
 */
enum class MessagePlatform {
    TELEGRAM,
    GMAIL
}

/**
 * Data class representing a bridge rule
 */
data class BridgeRule(
    val id: String = UUID.randomUUID().toString(),
    val sourceType: MessagePlatform,
    val targetType: MessagePlatform,
    val sourceIdentifier: String, // Telegram chat ID or Gmail label/address
    val targetIdentifier: String, // Telegram chat ID or Gmail address
    val enabled: Boolean = true
)

/**
 * Sealed class representing a message that can be bridged
 */
sealed class BridgeMessage {
    abstract val id: String
    abstract val timestamp: Long
    abstract val sourceType: MessagePlatform
    abstract val forwarded: Boolean

    data class TelegramBridgeMessage(
        override val id: String,
        override val timestamp: Long,
        override val forwarded: Boolean,
        val message: TelegramMessage
    ) : BridgeMessage() {
        override val sourceType = MessagePlatform.TELEGRAM
    }

    data class GmailBridgeMessage(
        override val id: String,
        override val timestamp: Long,
        override val forwarded: Boolean,
        val message: GmailMessage
    ) : BridgeMessage() {
        override val sourceType = MessagePlatform.GMAIL
    }
}
