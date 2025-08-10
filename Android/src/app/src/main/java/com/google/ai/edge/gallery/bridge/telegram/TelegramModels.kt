package com.google.ai.edge.gallery.bridge.telegram

import it.tdlight.jni.TdApi
import java.util.*

/**
 * Represents the authentication state of the Telegram client
 */
sealed class TelegramAuthState {
    object DISCONNECTED : TelegramAuthState()
    object CONNECTING : TelegramAuthState()
    object WAITING_FOR_PHONE_NUMBER : TelegramAuthState()
    object WAITING_FOR_CODE : TelegramAuthState()
    object WAITING_FOR_PASSWORD : TelegramAuthState()
    object READY : TelegramAuthState()
    object LOGGING_OUT : TelegramAuthState()
    object ERROR : TelegramAuthState()
    data class AUTH_ERROR(val error: String) : TelegramAuthState()
}

/**
 * Represents a message in a Telegram chat
 */
data class TelegramMessage(
    val id: Long,
    val chatId: Long,
    val senderId: Long,
    val date: Int,
    val isOutgoing: Boolean,
    val isChannelPost: Boolean,
    val content: TelegramMessageContent,
    val status: MessageStatus = MessageStatus.SENT
)

/**
 * Represents the content of a Telegram message
 */
sealed class TelegramMessageContent {
    data class Text(
        val text: String,
        val formattedText: String? = null
    ) : TelegramMessageContent()

    data class Photo(
        val caption: String,
        val photo: TdApi.Photo,
        val sizes: List<TelegramPhotoSize>
    ) : TelegramMessageContent()

    data class Document(
        val document: TdApi.Document,
        val caption: String
    ) : TelegramMessageContent()

    data class Sticker(
        val sticker: TdApi.Sticker
    ) : TelegramMessageContent()

    data class Video(
        val video: TdApi.Video,
        val caption: String
    ) : TelegramMessageContent()

    data class VoiceNote(
        val voiceNote: TdApi.VoiceNote,
        val caption: String
    ) : TelegramMessageContent()

    data class Audio(
        val audio: TdApi.Audio,
        val caption: String
    ) : TelegramMessageContent()

    data class Animation(
        val animation: TdApi.Animation,
        val caption: String
    ) : TelegramMessageContent()

    data class Unsupported(
        val reason: String
    ) : TelegramMessageContent()
}

/**
 * Represents the status of a message
 */
enum class MessageStatus {
    PENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}

/**
 * Represents a photo size in a Telegram message
 */
data class TelegramPhotoSize(
    val type: String,
    val width: Int,
    val height: Int,
    val fileId: String,
    val fileSize: Int
)

/**
 * Represents a Telegram chat
 */
data class TelegramChat(
    val id: Long,
    val title: String,
    val photo: String?,
    val unreadCount: Int,
    val lastMessage: TelegramMessage?,
    val chatInfo: TelegramChatInfo,
    val messages: List<TelegramMessage> = emptyList()
)

/**
 * Represents information about a chat
 */
sealed class TelegramChatInfo {
    data class Private(
        val userId: Long,
        val firstName: String,
        val lastName: String,
        val username: String?,
        val phoneNumber: String
    ) : TelegramChatInfo()

    data class Group(
        val groupId: Long,
        val title: String,
        val memberCount: Int,
        val onlineCount: Int = 0
    ) : TelegramChatInfo()

    data class Supergroup(
        val supergroupId: Long,
        val title: String,
        val username: String?,
        val memberCount: Int,
        val isChannel: Boolean,
        val onlineCount: Int = 0
    ) : TelegramChatInfo()

    data class Secret(
        val secretChatId: Int,
        val userId: Long,
        val firstName: String,
        val lastName: String,
        val username: String?
    ) : TelegramChatInfo()
}

/**
 * Represents a Telegram user
 */
data class TelegramUser(
    val id: Long,
    val firstName: String,
    val lastName: String,
    val username: String?,
    val phoneNumber: String,
    val status: UserStatus,
    val profilePhoto: String?,
    val isVerified: Boolean,
    val isSupport: Boolean,
    val isContact: Boolean
)

/**
 * Represents the status of a Telegram user
 */
sealed class UserStatus {
    object Online : UserStatus()
    object Offline : UserStatus()
    object Recently : UserStatus()
    object LastWeek : UserStatus()
    object LastMonth : UserStatus()
    object LongTimeAgo : UserStatus()
    data class Custom(val status: String) : UserStatus()
}
