package com.google.ai.edge.gallery.bridge.telegram

import java.util.UUID

/**
 * Data class representing a Telegram message
 */
data class TelegramMessage(
    val id: String = UUID.randomUUID().toString(),
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hasAttachments: Boolean = false,
    val attachmentUrls: List<String> = emptyList()
)
