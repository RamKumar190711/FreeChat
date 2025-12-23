package com.toqsoft.freechat.app

import android.app.*
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.toqsoft.freechat.MainActivity
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreNetwork.IncomingCallManager
import kotlinx.coroutines.*

class MyFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val EXTRA_TYPE = "extra_type"
        const val TYPE_CALL = "CALL"
        const val TYPE_CALL_ENDED = "CALL_ENDED"
        const val CALL_CHANNEL = "call_channel"
        const val NOTIF_ID = 999
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        when (data["type"]) {
            TYPE_CALL -> handleIncomingCall(data)
            TYPE_CALL_ENDED -> handleCallEnded(data)
        }
    }

    private fun handleIncomingCall(data: Map<String, String>) {
        CoroutineScope(Dispatchers.Main).launch {
            val callerId = data["callerId"] ?: "Unknown"
            val callId = data["callId"] ?: ""
            val channel = data["channel"] ?: ""
            val token = data["token"] ?: ""
            val receiverId = data["receiverId"] ?: ""
            val audioOnly = data["audioOnly"]?.toBoolean() ?: true

            IncomingCallManager.showIncomingCall(callerId, receiverId, channel, token, callId, audioOnly)

            val intent = Intent(this@MyFirebaseMessagingService, MainActivity::class.java).apply {
                action = "ACTION_CALL_$callId"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TYPE, TYPE_CALL)
                putExtra("EXTRA_CALLER_ID", callerId)
                putExtra("EXTRA_CALL_ID", callId)
                putExtra("EXTRA_CHANNEL", channel)
                putExtra("EXTRA_TOKEN", token)
                putExtra("EXTRA_RECEIVER_ID", receiverId)
                putExtra("EXTRA_AUDIO_ONLY", audioOnly)
            }

            val pendingIntent = PendingIntent.getActivity(
                this@MyFirebaseMessagingService,
                NOTIF_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this@MyFirebaseMessagingService, CALL_CHANNEL)
                .setSmallIcon(R.drawable.audio)
                .setContentTitle("Incoming Call")
                .setContentText("$callerId is calling")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(pendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(true)
                .build()

            NotificationManagerCompat.from(this@MyFirebaseMessagingService).notify(NOTIF_ID, notification)
        }
    }

    private fun handleCallEnded(data: Map<String, String>) {
        val callerId = data["callerId"] ?: "Unknown"
        val notificationManager = NotificationManagerCompat.from(this)

        notificationManager.cancel(NOTIF_ID)

        CoroutineScope(Dispatchers.Main).launch {
            IncomingCallManager.clearCall()
        }

        val missedNotif = NotificationCompat.Builder(this, CALL_CHANNEL)
            .setSmallIcon(R.drawable.audio)
            .setContentTitle("Missed Call")
            .setContentText("Call from $callerId ended")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1001, missedNotif)
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(NotificationChannel(CALL_CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH))
        }
    }
}