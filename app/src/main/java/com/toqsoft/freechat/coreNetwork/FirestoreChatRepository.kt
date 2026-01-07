package com.toqsoft.freechat.coreNetwork

import android.util.Log
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.toqsoft.freechat.coreModel.ChatMessage
import com.toqsoft.freechat.coreModel.Group
import com.toqsoft.freechat.coreModel.GroupMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreChatRepository @Inject constructor(
    val firestore: FirebaseFirestore
) {
    private val db = firestore

    private fun chatId(user1: String, user2: String) =
        listOf(user1, user2).sorted().joinToString("_")

    private val usersCollection = firestore.collection("users")
    private val groupsCollection = firestore.collection("groups")
    private val groupMessagesCollection = firestore.collection("group_messages")
    private val userGroupsCollection = firestore.collection("user_groups")

    // ==================== GROUP OPERATIONS ====================

    suspend fun createGroup(group: Group): String {
        try {
            // Generate group ID if not provided
            val groupId = group.id.ifEmpty { "group_${UUID.randomUUID().toString().substring(0, 8)}" }
            val groupWithId = group.copy(id = groupId)

            // Save group to groups collection
            groupsCollection.document(groupId).set(groupWithId).await()

            // Add group reference to each member's user_groups subcollection
            group.members.forEach { memberId ->
                userGroupsCollection.document(memberId)
                    .collection("groups")
                    .document(groupId)
                    .set(mapOf(
                        "groupId" to groupId,
                        "joinedAt" to System.currentTimeMillis(),
                        "role" to if (memberId == group.createdBy) "admin" else "member"
                    ))
                    .await()
            }

            Log.d("FirestoreChatRepository", "Group created: $groupId")
            return groupId
        } catch (e: Exception) {
            Log.e("FirestoreChatRepository", "Error creating group", e)
            throw e
        }
    }

    fun getGroupsFlow(userId: String): Flow<List<Group>> = callbackFlow {
        val listener = userGroupsCollection.document(userId)
            .collection("groups")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreChatRepository", "Error listening to groups", error)
                    close(error)
                    return@addSnapshotListener
                }

                val groups = snapshot?.documents?.mapNotNull { doc ->
                    val groupId = doc.getString("groupId")
                    groupId
                } ?: emptyList()

                // Fetch actual group details
                if (groups.isNotEmpty()) {
                    groupsCollection.whereIn(FieldPath.documentId(), groups)
                        .get()
                        .addOnSuccessListener { groupSnapshot ->
                            val groupList = groupSnapshot.documents.mapNotNull { groupDoc ->
                                try {
                                    groupDoc.toObject(Group::class.java)?.copy(id = groupDoc.id)
                                } catch (e: Exception) {
                                    Log.e("FirestoreChatRepository", "Error parsing group", e)
                                    null
                                }
                            }
                            trySend(groupList.sortedByDescending { it.lastMessageTime })
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirestoreChatRepository", "Error fetching groups", e)
                        }
                } else {
                    trySend(emptyList())
                }
            }

        awaitClose { listener.remove() }
    }

    fun getGroupMessagesFlow(groupId: String): Flow<List<GroupMessage>> = callbackFlow {
        val listener = groupMessagesCollection
            .whereEqualTo("groupId", groupId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreChatRepository", "Error listening to group messages", error)
                    close(error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(GroupMessage::class.java)?.copy(id = doc.id)
                    } catch (e: Exception) {
                        Log.e("FirestoreChatRepository", "Error parsing group message", e)
                        null
                    }
                } ?: emptyList()

                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    suspend fun sendGroupMessage(message: GroupMessage): String {
        try {
            // Generate message ID
            val messageId = "msg_${UUID.randomUUID().toString().substring(0, 8)}"
            val messageWithId = message.copy(id = messageId)

            // Save message
            groupMessagesCollection.document(messageId).set(messageWithId).await()

            // Update group's last message
            updateGroupLastMessage(message.groupId, "${message.senderName}: ${message.content}", message.timestamp)

            Log.d("FirestoreChatRepository", "Group message sent: $messageId")
            return messageId
        } catch (e: Exception) {
            Log.e("FirestoreChatRepository", "Error sending group message", e)
            throw e
        }
    }

    private suspend fun updateGroupLastMessage(groupId: String, lastMessage: String, timestamp: Long) {
        try {
            groupsCollection.document(groupId).update(
                mapOf(
                    "lastMessage" to lastMessage,
                    "lastMessageTime" to timestamp
                )
            ).await()
        } catch (e: Exception) {
            Log.e("FirestoreChatRepository", "Error updating group last message", e)
        }
    }

    suspend fun getGroupById(groupId: String): Group? {
        return try {
            val doc = groupsCollection.document(groupId).get().await()
            doc.toObject(Group::class.java)?.copy(id = doc.id)
        } catch (e: Exception) {
            Log.e("FirestoreChatRepository", "Error getting group", e)
            null
        }
    }

    suspend fun addMemberToGroup(groupId: String, userId: String, addedBy: String) {
        try {
            // Get current group
            val group = getGroupById(groupId)
            group?.let {
                val updatedMembers = it.members.toMutableList().apply { add(userId) }

                // Update group members
                groupsCollection.document(groupId).update("members", updatedMembers).await()

                // Add to user's groups
                userGroupsCollection.document(userId)
                    .collection("groups")
                    .document(groupId)
                    .set(mapOf(
                        "groupId" to groupId,
                        "joinedAt" to System.currentTimeMillis(),
                        "role" to "member"
                    ))
                    .await()
            }
        } catch (e: Exception) {
            Log.e("FirestoreChatRepository", "Error adding member to group", e)
            throw e
        }
    }

    suspend fun removeMemberFromGroup(groupId: String, userId: String, removedBy: String) {
        try {
            // Get current group
            val group = getGroupById(groupId)
            group?.let {
                val updatedMembers = it.members.toMutableList().apply { remove(userId) }

                // Update group members
                groupsCollection.document(groupId).update("members", updatedMembers).await()

                // Remove from user's groups
                userGroupsCollection.document(userId)
                    .collection("groups")
                    .document(groupId)
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            Log.e("FirestoreChatRepository", "Error removing member from group", e)
            throw e
        }
    }

    suspend fun updateGroupName(groupId: String, newName: String) {
        try {
            groupsCollection.document(groupId).update("name", newName).await()
        } catch (e: Exception) {
            Log.e("FirestoreChatRepository", "Error updating group name", e)
            throw e
        }
    }

    suspend fun markGroupMessageAsRead(messageId: String, userId: String) {
        try {
            val messageRef = groupMessagesCollection.document(messageId)
            firestore.runTransaction { transaction ->
                val messageDoc = transaction.get(messageRef)
                val message = messageDoc.toObject(GroupMessage::class.java)
                message?.let {
                    val updatedReadBy = it.readBy.toMutableList().apply {
                        if (!contains(userId)) add(userId)
                    }
                    transaction.update(messageRef, "readBy", updatedReadBy)
                }
            }.await()
        } catch (e: Exception) {
            Log.e("FirestoreChatRepository", "Error marking group message as read", e)
        }
    }




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

