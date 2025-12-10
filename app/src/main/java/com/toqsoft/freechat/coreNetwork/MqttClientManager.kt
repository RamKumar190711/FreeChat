package com.toqsoft.freechat.coreNetwork

import android.util.Log
import com.google.gson.Gson
import com.toqsoft.freechat.coreModel.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.eclipse.paho.client.mqttv3.IMqttMessageListener
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence

class MqttClientManager {

    private val gson = Gson()
    private val serverUri = "tcp://broker.hivemq.com:1883"
    private lateinit var mqttClient: MqttClient
    private lateinit var myClientId: String

    // ---------------- Connection ----------------
    fun connect(myUserId: String) {
        try {
            myClientId = "FreeChat_${myUserId}_${System.currentTimeMillis()}"
            mqttClient = MqttClient(serverUri, myClientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                isCleanSession = true
                keepAliveInterval = 60
                connectionTimeout = 10
            }
            mqttClient.connect(options)
            Log.d("MqttClientManager", "Connected as $myClientId")
        } catch (e: Exception) {
            Log.e("MqttClientManager", "Connection error", e)
        }
    }

    fun isConnected(): Boolean = ::mqttClient.isInitialized && mqttClient.isConnected

    // ---------------- Presence ----------------
    fun publishPresenceStatus(userId: String, isOnline: Boolean, lastSeen: Long?) {
        if (!isConnected()) return
        val topic = "chat/users/$userId/presence"
        val presence = UserPresence(userId, isOnline, lastSeen)
        mqttClient.publish(
            topic,
            MqttMessage(gson.toJson(presence).toByteArray()).apply {
                qos = 0
                isRetained = true
            }
        )
    }

    fun observeUserPresence(userId: String): Flow<UserPresence> = callbackFlow {
        if (!isConnected()) { awaitClose { }; return@callbackFlow }
        val topic = "chat/users/$userId/presence"
        val listener = IMqttMessageListener { _, mqttMessage ->
            try {
                val presence = gson.fromJson(String(mqttMessage.payload), UserPresence::class.java)
                trySend(presence)
            } catch (e: Exception) { Log.e("MqttClientManager", "Presence parse error", e) }
        }
        mqttClient.subscribe(topic, listener)
        awaitClose { mqttClient.unsubscribe(topic) }
    }

    // ---------------- Messaging ----------------
    fun publishMessage(message: ChatMessage) {
        if (!isConnected()) return
        val topic = "chat/users/${message.receiverId}/incoming"
        mqttClient.publish(topic, MqttMessage(gson.toJson(message).toByteArray()).apply {
            qos = 1
            isRetained = false
        })
    }

    fun observeIncomingMessages(myUserId: String): Flow<ChatMessage> = callbackFlow {
        if (!isConnected()) { awaitClose { }; return@callbackFlow }
        val topic = "chat/users/$myUserId/incoming"
        val listener = IMqttMessageListener { _, mqttMessage ->
            try {
                val msg = gson.fromJson(String(mqttMessage.payload), ChatMessage::class.java)
                if (msg.receiverId == myUserId) trySend(msg)
            } catch (e: Exception) { Log.e("MqttClientManager", "Message parse error", e) }
        }
        mqttClient.subscribe(topic, listener)
        awaitClose { mqttClient.unsubscribe(topic) }
    }

    // ---------------- Typing ----------------
    fun publishTyping(event: TypingEvent) {
        if (!isConnected()) return
        val topic = "chat/users/${event.receiverId}/typing"
        mqttClient.publish(topic, MqttMessage(gson.toJson(event).toByteArray()).apply { qos = 0 })
    }

    fun observeTyping(myUserId: String): Flow<TypingEvent> = callbackFlow {
        if (!isConnected()) { awaitClose { }; return@callbackFlow }
        val topic = "chat/users/$myUserId/typing"
        val listener = IMqttMessageListener { _, mqttMessage ->
            try {
                val ev = gson.fromJson(String(mqttMessage.payload), TypingEvent::class.java)
                if (ev.receiverId == myUserId) trySend(ev)
            } catch (e: Exception) { Log.e("MqttClientManager", "Typing parse error", e) }
        }
        mqttClient.subscribe(topic, listener)
        awaitClose { mqttClient.unsubscribe(topic) }
    }

    // ---------------- Status ----------------
    fun publishStatus(event: StatusEvent) {
        if (!isConnected()) return
        val topic = "chat/users/${event.receiverId}/status"
        mqttClient.publish(topic, MqttMessage(gson.toJson(event).toByteArray()).apply { qos = 1 })
    }

    fun observeStatus(myUserId: String): Flow<StatusEvent> = callbackFlow {
        if (!isConnected()) { awaitClose { }; return@callbackFlow }
        val topic = "chat/users/$myUserId/status"
        val listener = IMqttMessageListener { _, mqttMessage ->
            try {
                val ev = gson.fromJson(String(mqttMessage.payload), StatusEvent::class.java)
                if (ev.receiverId == myUserId) trySend(ev)
            } catch (e: Exception) { Log.e("MqttClientManager", "Status parse error", e) }
        }
        mqttClient.subscribe(topic, listener)
        awaitClose { mqttClient.unsubscribe(topic) }
    }
}
