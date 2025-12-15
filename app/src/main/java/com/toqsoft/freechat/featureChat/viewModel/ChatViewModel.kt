package com.toqsoft.freechat.featureChat.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.app.FreeChatApplication
import com.toqsoft.freechat.coreModel.*
import com.toqsoft.freechat.coreNetwork.AgoraManager
import com.toqsoft.freechat.coreNetwork.FirestoreChatRepository
import com.toqsoft.freechat.coreNetwork.IncomingCallManager
import com.toqsoft.freechat.coreNetwork.MqttClientManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val firestoreRepo: FirestoreChatRepository,
    private val prefs: UserPreferencesRepository,
    private val mqttManager: MqttClientManager
) : ViewModel() {

    private var myUserIdInternal: String? = null
    val myUserId: String get() = myUserIdInternal ?: "unknown"

    /** ------------------ State ------------------ */
    private val _messagesMap = MutableStateFlow<Map<String, List<ChatMessage>>>(emptyMap())
    val messagesMap: StateFlow<Map<String, List<ChatMessage>>> = _messagesMap.asStateFlow()

    private val _typingMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val typingMap: StateFlow<Map<String, Boolean>> = _typingMap.asStateFlow()

    private val _otherUserOnline = MutableStateFlow(false)
    private val _otherUserLastSeen = MutableStateFlow<Long?>(null)

    val combinedUserStatus: StateFlow<String> = combine(
        _otherUserOnline,
        _otherUserLastSeen,
        FreeChatApplication.isAppInForeground
    ) { online, lastSeen, inForeground ->
        when {
            online && inForeground -> "Online"
            !online && lastSeen != null -> "Last seen: ${formatTime(lastSeen)}"
            else -> "Offline"
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Offline")

    /** ------------------ MQTT Publish Queue ------------------ */
    private val mqttQueue = Channel<Any>(Channel.UNLIMITED)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            for (event in mqttQueue) {
                try {
                    when (event) {
                        is StatusEvent -> mqttManager.publishStatus(event)
                        is TypingEvent -> mqttManager.publishTyping(event)
                        is ChatMessage -> mqttManager.publishMessage(event)
                    }
                    delay(5) // small throttle to avoid flooding
                } catch (e: Exception) {
                    e.printStackTrace() // optionally log or retry
                }
            }
        }
    }

    /** ------------------ Init ------------------ */
    init {
        viewModelScope.launch {
            prefs.usernameFlow.collect { name ->
                if (!name.isNullOrBlank()) {
                    myUserIdInternal = name
                    firestoreRepo.saveUser(name, name)
                    connectMqttAndObserve()
                }
            }
        }
    }

    private fun connectMqttAndObserve() {
        viewModelScope.launch(Dispatchers.IO) {
            myUserIdInternal?.let { id ->
                mqttManager.connect(id)
                observeIncomingMessages()
                observeTypingEvents()
                observeStatusEvents()
            }
        }
    }

    fun startCall(
        otherUserId: String,
        audioOnly: Boolean,
        onAccepted: () -> Unit,
        onRejected: () -> Unit
    ) {
        val myId = myUserIdInternal ?: return

        viewModelScope.launch(Dispatchers.IO) {
            val channel = "call_${UUID.randomUUID()}"
            val callId = UUID.randomUUID().toString()
            val chatId = getChatId(myId, otherUserId)

            val callData = mapOf(
                "type" to "call",
                "status" to "ringing",
                "callerId" to myId,
                "receiverId" to otherUserId,
                "channel" to channel,
                "audioOnly" to audioOnly,
                "timestamp" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(callId)
                .set(callData)

            // 👂 listen for accept/reject
            observeCallStatus(
                chatId = chatId,
                callId = callId,
                onAccepted = onAccepted,
                onRejected = onRejected
            )
        }
    }


    private fun getChatId(a: String, b: String): String =
        listOf(a, b).sorted().joinToString("_")



    fun observeCallStatus(
        chatId: String,
        callId: String,
        onAccepted: () -> Unit,
        onRejected: () -> Unit
    ) {
        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .document(callId)
            .addSnapshotListener { snap, _ ->
                when (snap?.getString("status")) {
                    "accepted" -> onAccepted()
                    "rejected", "ended" -> onRejected()
                }
            }
    }




    // Replace this with your backend or Firestore approach
    private suspend fun sendCallNotification(
        toUserId: String,
        channel: String,
        token: String,
        callerId: String,
        audioOnly: Boolean
    ) {
        val callData = mapOf(
            "type" to "CALL",
            "callerId" to callerId,
            "channel" to channel,
            "token" to token,
            "callId" to UUID.randomUUID().toString(),
            "audioOnly" to audioOnly.toString()
        )

        // Save to Firestore (receiver can observe)
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(toUserId)
            .collection("incomingCalls")
            .add(callData)
    }


    // Replace this with your Firebase Function call to generate an Agora token
        private suspend fun getAgoraToken(channelName: String, uid: Int): String {
            // TODO: Call your backend and return the token
            return "TEMPORARY_TOKEN"
        }



    /** ------------------ Chat ------------------ */
    fun sendMessage(text: String, toUserId: String) {
        val myId = myUserIdInternal ?: return
        val msg = ChatMessage(
            id = firestoreRepo.firestore.collection("chats").document().id,
            senderId = myId,
            receiverId = toUserId,
            text = text,
            status = MessageStatus.SENT
        )
        updateLocalMessages(toUserId, msg)
        viewModelScope.launch(Dispatchers.IO) {
            mqttQueue.send(msg)
            firestoreRepo.sendMessage(msg)
        }
    }

    fun markMessageSeen(message: ChatMessage) {
        val seenEvent = StatusEvent(
            messageId = message.id,
            senderId = myUserIdInternal!!,
            receiverId = message.senderId,
            status = MessageStatus.SEEN
        )
        viewModelScope.launch(Dispatchers.IO) {
            mqttQueue.send(seenEvent)
        }
        updateMessageStatus(message.senderId, message.id, MessageStatus.SEEN)
    }

    fun sendTyping(toUserId: String, isTyping: Boolean) {
        val event = TypingEvent(senderId = myUserId, receiverId = toUserId, isTyping = isTyping)
        viewModelScope.launch(Dispatchers.IO) { mqttQueue.send(event) }
    }

    /** ---------------- Load messages from Firestore ---------------- */
    fun observeChatWithUser(otherUserId: String) {
        val myId = myUserIdInternal ?: return
        viewModelScope.launch(Dispatchers.IO) {
            firestoreRepo.getMessagesFlow(otherUserId, myId).collect { messages ->
                _messagesMap.value = _messagesMap.value.toMutableMap().apply {
                    put(otherUserId, messages.sortedBy { it.timestamp })
                }
            }
        }
    }

    /** ---------------- MQTT Observers ---------------- */
    private fun observeIncomingMessages() {
        val myId = myUserIdInternal ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.observeIncomingMessages(myId).collect { msg ->
                withContext(Dispatchers.Main) {
                    updateLocalMessages(msg.senderId, msg)
                    firestoreRepo.sendMessage(msg)
                    val deliveredEvent = StatusEvent(
                        messageId = msg.id,
                        senderId = myId,
                        receiverId = msg.senderId,
                        status = MessageStatus.DELIVERED
                    )
                    mqttQueue.send(deliveredEvent)
                }
            }
        }
    }

    private fun observeTypingEvents() {
        val myId = myUserIdInternal ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.observeTyping(myId).collect { event ->
                withContext(Dispatchers.Main) {
                    _typingMap.value = _typingMap.value.toMutableMap().apply {
                        put(event.senderId, event.isTyping)
                    }
                    _otherUserOnline.value = true
                    _otherUserLastSeen.value = System.currentTimeMillis()
                }
            }
        }
    }

    private fun observeStatusEvents() {
        val myId = myUserIdInternal ?: return
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.observeStatus(myId).collect { event ->
                withContext(Dispatchers.Main) {
                    val userIdKey = if (event.receiverId == myId) event.senderId else event.receiverId
                    updateMessageStatus(userIdKey, event.messageId, event.status)
                    _otherUserOnline.value = true
                    _otherUserLastSeen.value = System.currentTimeMillis()
                }
            }
        }
    }

    /** ------------------ Helpers ------------------ */
    private fun updateLocalMessages(userId: String, message: ChatMessage) {
        val currentMessages = _messagesMap.value[userId]?.toMutableList() ?: mutableListOf()
        if (currentMessages.none { it.id == message.id }) {
            currentMessages.add(message)
            _messagesMap.value = _messagesMap.value.toMutableMap().apply { put(userId, currentMessages) }
        }
    }

    private fun updateMessageStatus(userId: String, messageId: String, status: MessageStatus) {
        val messages = _messagesMap.value[userId]?.toMutableList() ?: return
        val index = messages.indexOfFirst { it.id == messageId }
        if (index != -1) {
            messages[index] = messages[index].copy(status = status)
            _messagesMap.value = _messagesMap.value.toMutableMap().apply { put(userId, messages) }
        }
    }

    private fun formatTime(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }
}
