package com.toqsoft.freechat.featureCall.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.coreNetwork.AgoraManager
import com.toqsoft.freechat.coreNetwork.IncomingCallManager
import io.agora.rtc2.RtcEngine

@Composable
fun IncomingCallOverlay(rtcEngine: RtcEngine,
                        navController: NavController) {
    val incomingCall by IncomingCallManager.incomingCall.collectAsState()

    incomingCall?.let { call ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xAA000000))
                .clickable { /* consume clicks */ }
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Incoming call from ${call.callerId}", color = Color.White, fontSize = 24.sp)
                Spacer(Modifier.height(16.dp))
                Row {
                    Button(onClick = {

                        val uid = AgoraManager.rtcEngine.hashCode() and 0x7fffffff
                        AgoraManager.rtcEngine?.joinChannel(
                            call.token,
                            call.channel,
                            null,
                            uid
                        )

                        // 🔥 UPDATE CALL STATUS
                        FirebaseFirestore.getInstance()
                            .collection("chats")
                            .document(listOf(call.callerId, /* sam */ call.receiverId).sorted().joinToString("_"))
                            .collection("messages")
                            .document(call.callId)
                            .update("status", "accepted")

                        IncomingCallManager.clearCall()

                        // 🔥 sam → SpeakingScreen
                        navController.navigate("speak/${call.callerId}/${call.audioOnly}")

                    }) {
                        Text("Accept")
                    }

                    Button(onClick = {
                        // Reject call
                        IncomingCallManager.clearCall()
                    }) {
                        Text("Reject")
                    }

                }
            }
        }
    }
}
