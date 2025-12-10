package com.toqsoft.freechat.featureChat.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toqsoft.freechat.coreModel.*
import com.toqsoft.freechat.coreNetwork.MqttClientManager
import com.toqsoft.freechat.coreModel.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val mqttManager: MqttClientManager,
    private val prefs: UserPreferencesRepository
) : ViewModel()
{

    private var myUserIdInternal: String? = null
    val myUserId: String get() = myUserIdInternal ?: "unknown"

    private val _messagesMap = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messagesMap = _messagesMap.asStateFlow()

    private val _typingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingMap = _typingMap.asStateFlow()

    val otherUserLastSeen = MutableStateFlow<Long?>(null)
    private val _lastMessageStatus = MutableStateFlow<MessageStatus?>(null)
    val lastMessageStatus = _lastMessageStatus.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.usernameFlow.collectLatest { name ->
                if (!name.isNullOrBlank()) {
                    myUserIdInternal = name
                    mqttManager.connect(name)
                    publishMyPresence(true)
                    beginListening(name)
                }
            }
        }
    }

    private fun beginListening(myUserId: String) {
        // Incoming messages
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.observeIncomingMessages(myUserId).collectLatest { msg ->
                if (msg.receiverId == myUserId) {
                    _messagesMap.value = _messagesMap.value.toMutableMap().apply {
                        val current = get(msg.senderId) ?: emptyList()
                        put(msg.senderId, current + msg.copy(status = MessageStatus.DELIVERED))
                    }
                    mqttManager.publishStatus(StatusEvent(msg.id, myUserId, msg.senderId, MessageStatus.DELIVERED))
                }
            }
        }

        // Typing
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.observeTyping(myUserId).collectLatest { event ->
                _typingMap.value = _typingMap.value.toMutableMap().apply { put(event.senderId, event.isTyping) }
            }
        }

        // Status updates (delivered / seen)
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.observeStatus(myUserId).collectLatest { ev ->
                _messagesMap.value = _messagesMap.value.toMutableMap().apply {
                    val current = get(ev.senderId) ?: emptyList()
                    put(ev.senderId, current.map { if (it.id == ev.messageId) it.copy(status = ev.status) else it })
                }
            }
        }
    }

    fun sendMessage(text: String, toUserId: String) {
        val myId = myUserIdInternal ?: return
        val msg = ChatMessage(senderId = myId, receiverId = toUserId, text = text, status = MessageStatus.SENT)
        _messagesMap.value = _messagesMap.value.toMutableMap().apply {
            val current = get(toUserId) ?: emptyList()
            put(toUserId, current + msg)
        }
        viewModelScope.launch(Dispatchers.IO) { mqttManager.publishMessage(msg) }
    }

    fun sendTyping(toUserId: String, isTyping: Boolean) {
        _typingMap.value = _typingMap.value.toMutableMap().apply { put(toUserId, isTyping) }
        val myId = myUserIdInternal ?: return
        viewModelScope.launch(Dispatchers.IO) { mqttManager.publishTyping(TypingEvent(myId, toUserId, isTyping)) }
    }

    fun markMessageSeen(message: ChatMessage) {
        val myId = myUserIdInternal ?: return
        _messagesMap.value = _messagesMap.value.toMutableMap().apply {
            val current = get(message.senderId) ?: emptyList()
            put(message.senderId, current.map { if (it.id == message.id) it.copy(status = MessageStatus.SEEN) else it })
        }
        _lastMessageStatus.value = MessageStatus.SEEN
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.publishStatus(StatusEvent(message.id, myId, message.senderId, MessageStatus.SEEN))
        }
    }

    fun publishMyPresence(isOnline: Boolean) {
        val myId = myUserIdInternal ?: return
        val lastSeenTime = if (!isOnline) System.currentTimeMillis() else null
        viewModelScope.launch(Dispatchers.IO) { mqttManager.publishPresenceStatus(myId, isOnline, lastSeenTime) }
    }

    fun goOffline() { publishMyPresence(false) }
}
