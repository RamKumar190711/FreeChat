package com.toqsoft.freechat.coreNetwork

import com.google.firebase.firestore.FirebaseFirestore
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

    private fun chatId(user1: String, user2: String) =
        listOf(user1, user2).sorted().joinToString("_")

    // ---------------- Messages ----------------
    fun sendMessage(message: ChatMessage) {
        val chatDoc = firestore.collection("chats")
            .document(chatId(message.senderId, message.receiverId))

        chatDoc.collection("messages")
            .document(message.id)
            .set(message.copy(timestamp = System.currentTimeMillis()))
    }

    fun getMessagesFlow(otherUserId: String, myUserId: String): Flow<List<ChatMessage>> =
        callbackFlow {
            val chatDoc = firestore.collection("chats")
                .document(chatId(myUserId, otherUserId))

            val listener = chatDoc.collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener { snapshots, e ->
                    if (e != null) {
                        close(e)
                        return@addSnapshotListener
                    }

                    val messages = snapshots?.documents
                        ?.mapNotNull { it.toObject(ChatMessage::class.java) }
                        ?: emptyList()

                    trySend(messages)
                }

            awaitClose { listener.remove() }
        }

    // ---------------- Users ----------------
    fun saveUser(userId: String, username: String) {
        firestore.collection("users")
            .document(userId)
            .set(
                mapOf(
                    "username" to username,
                    "isOnline" to true,
                    "lastSeen" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            )
    }

    fun updatePresence(userId: String, isOnline: Boolean) {
        firestore.collection("users")
            .document(userId)
            .update(
                "isOnline", isOnline,
                "lastSeen", if (!isOnline) System.currentTimeMillis() else null
            )
    }

    fun getUsersFlow(): Flow<List<String>> = callbackFlow {
        val listener = firestore.collection("users")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    close(e)
                    return@addSnapshotListener
                }

                val users = snapshots?.documents
                    ?.mapNotNull { it.getString("username") }
                    ?: emptyList()

                trySend(users)
            }

        awaitClose { listener.remove() }
    }
}
