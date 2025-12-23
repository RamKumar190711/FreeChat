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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
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

    // Timeout for missed call
    LaunchedEffect(Unit) {
        delay(50000)
        if (!navigated) {
            FirebaseFirestore.getInstance()
                .collection("chats").document(chatId)
                .collection("messages").document(callId)
                .update("status", "missed")
                .addOnCompleteListener { onCancel() }
        }
    }

    // Listen for call status updates
    DisposableEffect(callId) {
        val docRef = FirebaseFirestore.getInstance()
            .collection("chats").document(chatId)
            .collection("messages").document(callId)

        val listener = docRef.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, _ ->
            if (snapshot == null || !snapshot.exists()) return@addSnapshotListener

            val status = snapshot.getString("status")
            val channel = snapshot.getString("channel")
            val token = snapshot.getString("token")
            val delivered = snapshot.getBoolean("delivered") ?: false

            // Update label based on delivered flag
            callLabel = when {
                status == "accepted" -> "Accepted"
                delivered -> "Ringing"
                else -> "Calling"
            }

            when (status) {
                "accepted" -> {
                    if (!navigated && !channel.isNullOrEmpty()) {
                        navigated = true
                        if (AgoraManager.rtcEngine == null) AgoraManager.init(context)
                        AgoraManager.rtcEngine?.joinChannel(token, channel, null, 0)
                        navController.navigate("speak/$callId/$callerId/$receiverId/$audioOnly/$receiverId") {
                            popUpTo("calling/$callId/$callerId/$receiverId/$audioOnly") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                "rejected", "ended", "missed", "declined" -> {
                    if (!navigated) {
                        navigated = true
                        NotificationManagerCompat.from(context).cancel(999)
                        onCancel()
                    }
                }
            }
        }
        onDispose { listener.remove() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "$callLabel $receiverId...", color = Color.White, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    FirebaseFirestore.getInstance()
                        .collection("chats").document(chatId)
                        .collection("messages").document(callId)
                        .update("status", "declined")
                        .addOnCompleteListener {
                            NotificationManagerCompat.from(context).cancel(999)
                            onCancel()
                        }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("Cancel") }
        }
    }
}
