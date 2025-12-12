package com.toqsoft.freechat.coreNetwork

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.toqsoft.freechat.coreModel.ChatMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreChatRepository @Inject constructor(
    val firestore: FirebaseFirestore
) {
    private val db = firestore

    private fun chatId(user1: String, user2: String) =
        listOf(user1, user2).sorted().joinToString("_")

    // ---------------- SEND MESSAGE ----------------
    fun sendMessage(message: ChatMessage) {
        val chatId = chatId(message.senderId, message.receiverId)
        val chatDoc = firestore.collection("chats").document(chatId)

        chatDoc.collection("messages")
            .document(message.id)
            .set(message.copy(timestamp = System.currentTimeMillis()))

        chatDoc.set(
            mapOf(
                "lastMessage" to message.text,
                "lastTimestamp" to System.currentTimeMillis(),
                "lastSender" to message.senderId
            ),
            SetOptions.merge()
        )
    }

    // ---------------- LOAD MESSAGES ----------------
    fun getMessagesFlow(otherUserId: String, myUserId: String): Flow<List<ChatMessage>> =
        callbackFlow {
            val chatDoc = firestore.collection("chats").document(chatId(myUserId, otherUserId))
            val listener = chatDoc.collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) { close(e); return@addSnapshotListener }
                    val messages = snapshots?.documents?.mapNotNull { it.toObject(ChatMessage::class.java) } ?: emptyList()
                    trySend(messages)
                }
            awaitClose { listener.remove() }
        }

    // ---------------- USERS ----------------
    fun saveUser(userId: String, username: String) {
        firestore.collection("users").document(userId)
            .set(
                mapOf(
                    "username" to username,
                    "isOnline" to true,
                    "lastSeen" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
    }

    fun getUsersFlow(): Flow<List<String>> = callbackFlow {
        val listener = db.collection("users")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot != null) {
                    val list = snapshot.documents.mapNotNull { it.id }
                    trySend(list)
                }
            }
        awaitClose { listener.remove() }
    }

    // ---------------- UNREAD COUNT ----------------
    fun getUnreadCountFlow(myId: String): Flow<Map<String, Int>> = callbackFlow {
        val chatListener = db.collection("chats")
            .addSnapshotListener { chats, _ ->
                if (chats == null) return@addSnapshotListener

                val unreadMap = mutableMapOf<String, Int>()

                chats.documents.forEach { doc ->
                    val chatId = doc.id
                    if (!chatId.contains(myId)) return@forEach

                    val otherUser = chatId.split("_").first { it != myId }
                    val lastRead = doc.getLong("lastRead_$myId") ?: 0L

                    doc.reference.collection("messages")
                        .whereEqualTo("receiverId", myId)
                        .addSnapshotListener { messages, _ ->
                            val unread = messages?.count { it.getLong("timestamp") ?: 0L > lastRead } ?: 0
                            unreadMap[otherUser] = unread
                            trySend(unreadMap.toMap())
                        }
                }
            }

        awaitClose { chatListener.remove() }
    }


    fun markChatAsRead(myId: String, otherUserId: String) {
        val chatDocId = chatId(myId, otherUserId)
        db.collection("chats").document(chatDocId)
            .set(mapOf("lastRead_$myId" to System.currentTimeMillis()), SetOptions.merge())
    }

    // ---------------- LAST MESSAGE ----------------
    fun getLastMessageFlow(myId: String): Flow<Map<String, Pair<String, Long>>> = callbackFlow {
        val listener = db.collection("chats")
            .addSnapshotListener { chats, _ ->
                if (chats == null) return@addSnapshotListener
                val map = mutableMapOf<String, Pair<String, Long>>()
                for (doc in chats.documents) {
                    val parts = doc.id.split("_")
                    if (!parts.contains(myId)) continue
                    val otherUser = parts.first { it != myId }
                    val lastMsg = doc.getString("lastMessage") ?: ""
                    val lastTime = doc.getLong("lastTimestamp") ?: 0L
                    map[otherUser] = lastMsg to lastTime
                }
                trySend(map)
            }
        awaitClose { listener.remove() }
    }
}

