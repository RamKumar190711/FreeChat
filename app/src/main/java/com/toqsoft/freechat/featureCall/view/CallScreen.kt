package com.toqsoft.freechat.featureCall.view

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.coreNetwork.AgoraManager

@Composable
fun CallingScreen(
    callId: String,
    callerId: String,
    receiverId: String,
    audioOnly: Boolean,
    navController: NavController,
    onCancel: () -> Unit
) {
    val TAG = "CALL_FLOW_DEBUG"
    val chatId = remember { listOf(callerId, receiverId).sorted().joinToString("_") }
    val context = LocalContext.current
    var navigated by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Log.d(TAG, "CallingScreen Launched: callId=$callId, chatId=$chatId, caller=$callerId, receiver=$receiverId")
    }

    DisposableEffect(callId) {
        Log.d(TAG, "Attaching SnapshotListener to path: chats/$chatId/messages/$callId")

        val docRef = FirebaseFirestore.getInstance()
            .collection("chats").document(chatId)
            .collection("messages").document(callId)

        val listener = docRef.addSnapshotListener { snapshot, error ->
            Log.d(TAG, "SnapshotListener fired")

            if (error != null) {
                Log.e(TAG, "Firestore error: ${error.message}")
                return@addSnapshotListener
            }

            if (snapshot == null || !snapshot.exists()) {
                Log.w(TAG, "Snapshot is null or document does not exist yet")
                return@addSnapshotListener
            }

            val status = snapshot.getString("status")
            val channel = snapshot.getString("channel")
            val token = snapshot.getString("token")

            Log.d(TAG, "Data Received -> status: $status, channel: $channel, token: ${token?.take(10)}...")

            when (status) {
                "accepted" -> {
                    Log.d(TAG, "Status is 'accepted'. Checking navigation flags: navigated=$navigated, channelEmpty=${channel.isNullOrEmpty()}")

                    if (!navigated && !channel.isNullOrEmpty()) {
                        Log.d(TAG, "Conditions met. Preparing to join Agora channel: $channel")
                        navigated = true

                        if (AgoraManager.rtcEngine == null) {
                            Log.d(TAG, "Agora Engine null, initializing now...")
                            AgoraManager.init(context)
                        }

                        val joinResult = AgoraManager.rtcEngine?.joinChannel(token, channel, null, 0)
                        Log.d(TAG, "Agora joinChannel execution result code: $joinResult")

                        val destination = "speak/$callId/$callerId/$receiverId/$audioOnly/$receiverId"
                        Log.d(TAG, "Navigating to: $destination")

                        navController.navigate(destination) {
                            Log.d(TAG, "Popping up 'calling' screen from backstack")
                            popUpTo("calling/$callId/$callerId/$receiverId/$audioOnly") { inclusive = true }
                            launchSingleTop = true
                        }
                    } else if (channel.isNullOrEmpty()) {
                        Log.e(TAG, "Critical Error: Status is accepted but 'channel' string is missing from Firestore!")
                    }
                }
                "rejected", "ended" -> {
                    Log.d(TAG, "Status changed to $status. Triggering onCancel.")
                    if (!navigated) {
                        navigated = true
                        onCancel()
                    }
                }
                else -> {
                    Log.d(TAG, "Status is currently: $status (waiting for 'accepted')")
                }
            }
        }

        onDispose {
            Log.d(TAG, "Disposing listener for callId: $callId")
            listener.remove()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Calling $receiverId...",
                color = Color.White,
                fontSize = 24.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = {
                    Log.d(TAG, "User clicked Cancel button")
                    onCancel()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Cancel")
            }
        }
    }
}