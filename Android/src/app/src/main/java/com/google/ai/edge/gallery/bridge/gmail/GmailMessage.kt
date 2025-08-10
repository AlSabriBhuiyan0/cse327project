package com.google.ai.edge.gallery.bridge.gmail

import java.util.UUID

/**
 * Data class representing a Gmail message
 */
data class GmailMessage(
    val id: String = UUID.randomUUID().toString(),
    val threadId: String,
    val sender: String,
    val recipients: List<String>,
    val subject: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val hasAttachments: Boolean = false,
    val attachmentUrls: List<String> = emptyList(),
    val labels: List<String> = emptyList()
)
