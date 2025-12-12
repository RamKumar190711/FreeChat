package com.toqsoft.freechat.app

import android.app.PendingIntent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.MainActivity
import com.toqsoft.freechat.coreModel.UserPreferencesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.firstOrNull

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "New token: $token")
        updateFcmToken(token)
    }


    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val data = remoteMessage.data
        val senderId = data["senderId"] ?: return
        val roomId = data["roomId"] ?: ""
        val messageText = data["text"] ?: ""
        val messageId = data["messageId"] ?: ""

        // Show notification
        showNotification(senderId, messageText, senderId, roomId)

        // Mark message as delivered in Firestore
        markMessageDelivered(messageId, senderId)
    }

    /** ---------------- Notification ---------------- */
    private fun showNotification(title: String?, body: String?, senderId: String, roomId: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("senderId", senderId)
            putExtra("roomId", roomId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(this, "chat_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title ?: "New Message")
            .setContentText(body ?: "")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(soundUri)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }

    /** ---------------- Firestore Updates ---------------- */
    private fun markMessageDelivered(messageId: String, senderId: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val userId = getCurrentUserId() ?: return@launch
            try {
                val statusMap = mapOf("status" to "DELIVERED")
                FirebaseFirestore.getInstance()
                    .collection("chats")
                    .document(getChatId(userId, senderId))
                    .collection("messages")
                    .document(messageId)
                    .update(statusMap)
            } catch (e: Exception) {
                Log.e("FCMService", "Error updating message status", e)
            }
        }
    }

    private fun updateFcmToken(token: String?) {
        if (token == null) return
        CoroutineScope(Dispatchers.IO).launch {
            val userId = getCurrentUserId() ?: return@launch
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .update("fcmToken", token)
            } catch (e: Exception) {
                Log.e("FCMService", "Error updating FCM token", e)
            }
        }
    }

    /** ---------------- Helper ---------------- */
    private suspend fun getCurrentUserId(): String? {
        return UserPreferencesRepository(this@MyFirebaseMessagingService)
            .usernameFlow
            .firstOrNull()
    }

    private fun getChatId(user1: String, user2: String): String {
        return listOf(user1, user2).sorted().joinToString("_")
    }

    /** ---------------- Notification Channel ---------------- */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "chat_channel",
                "Chat Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifications for new chat messages" }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
