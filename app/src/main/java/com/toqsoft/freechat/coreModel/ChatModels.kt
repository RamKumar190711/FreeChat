package com.toqsoft.freechat.coreModel

import java.util.UUID
import kotlin.String

enum class MessageStatus {
    SENT,
    DELIVERED,
    SEEN,
    ringing,   // Keep this to prevent crash on old database records
    accepted,
    rejected,
    ended,
    missed,
    declined   // Added as per your requirement
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT
)

data class Group(
    val id: String = "",
    val name: String = "",
    val members: List<String> = emptyList(),
    val createdBy: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastMessage: String = "",
    val lastMessageTime: Long = 0L,
    val avatarUrl: String = "",
    val isActive: Boolean = true
)

data class GroupMessage(
    val id: String = "",
    val groupId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val content: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text", // text, image, audio, video, file
    val fileUrl: String = "",
    val fileSize: Long = 0L,
    val duration: Long = 0L, // for audio/video
    val isRead: Boolean = false,
    val readBy: List<String> = emptyList(),
    val reactions: Map<String, String> = emptyMap() // userId -> emoji
)

data class TypingEvent(
    val senderId: String,
    val receiverId: String,
    val isTyping: Boolean
)

data class StatusEvent(
    val messageId: String,
    val senderId: String,
    val receiverId: String,
    val status: MessageStatus
)

data class PresenceEvent(
    val userId: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class UserPresence(
    val userId: String,
    val isOnline: Boolean,
    val lastSeen: Long?
)


data class User(
    val id: String = "",
    val name: String = "",
    val unreadCount: Int = 0
)


// IncomingCallData.kt
data class IncomingCallData(
    val callerId: String,
    val receiverId: String,
    val channel: String,
    val token: String,
    val callId: String,
    val audioOnly: Boolean = false
)
