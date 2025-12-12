package com.toqsoft.freechat.coreModel

import java.util.UUID

enum class MessageStatus { SENT, DELIVERED, SEEN }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENT
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

