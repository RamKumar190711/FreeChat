package com.toqsoft.freechat.featureCall.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.coreNetwork.AgoraManager
import kotlinx.coroutines.delay

@Composable
fun CallingScreen(
    callId: String,
    callerId: String,
    receiverId: String,
    audioOnly: Boolean,
    navController: NavController,
    onCancel: () -> Unit
) {
    val chatId = remember { listOf(callerId, receiverId).sorted().joinToString("_") }
    val context = LocalContext.current
    var navigated by remember { mutableStateOf(false) }
    var callLabel by remember { mutableStateOf("Calling") }

    LaunchedEffect(Unit) {
        delay(45000) // 45 Seconds
        if (!navigated) {
            FirebaseFirestore.getInstance()
                .collection("chats").document(chatId)
                .collection("messages").document(callId)
                .update("status", "missed")
            onCancel()
        }
    }

    DisposableEffect(callId) {
        val docRef = FirebaseFirestore.getInstance()
            .collection("chats").document(chatId)
            .collection("messages").document(callId)

        val listener = docRef.addSnapshotListener { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val status = snapshot.getString("status")
            val channel = snapshot.getString("channel")
            val token = snapshot.getString("token")
            val delivered = snapshot.getBoolean("delivered") ?: false

            callLabel = when {
                status == "accepted" -> "Connecting..."
                delivered -> "Ringing"
                else -> "Calling"
            }

            if (status == "accepted" && !navigated && !channel.isNullOrEmpty()) {
                navigated = true
                if (AgoraManager.rtcEngine == null) AgoraManager.init(context)
                AgoraManager.rtcEngine?.joinChannel(token, channel, null, 0)
                navController.navigate("speak/$callId/$callerId/$receiverId/$audioOnly/$receiverId") {
                    popUpTo("calling/$callId/$callerId/$receiverId/$audioOnly") { inclusive = true }
                }
            } else if (listOf("rejected", "declined", "missed", "ended").contains(status)) {
                if (!navigated) {
                    navigated = true
                    onCancel()
                }
            }
        }
        onDispose { listener.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0B1014)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$callLabel $receiverId", color = Color.White, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(40.dp))
            Button(
                onClick = {
                    FirebaseFirestore.getInstance()
                        .collection("chats").document(chatId)
                        .collection("messages").document(callId)
                        .update("status", "declined")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                modifier = Modifier.size(width = 120.dp, height = 50.dp)
            ) { Text("Cancel", color = Color.White) }
        }
    }
}