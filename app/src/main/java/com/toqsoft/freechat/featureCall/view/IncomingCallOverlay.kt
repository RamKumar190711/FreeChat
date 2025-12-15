// ------------------------- IncomingCallOverlay.kt -------------------------
package com.toqsoft.freechat.featureCall.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
fun IncomingCallOverlay(rtcEngine: RtcEngine, navController: NavController) {
    val incomingCall by IncomingCallManager.incomingCall.collectAsState()

    incomingCall?.let { call ->
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xAA000000))
                .clickable { /* consume */ }
        ) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Incoming call from ${call.callerId}", color = Color.White, fontSize = 24.sp)
                Spacer(Modifier.height(16.dp))
                Row {
                    Button(onClick = {
                        // Join channel
                        val uid = 0
                        AgoraManager.rtcEngine?.joinChannel(call.token, call.channel, null, uid)

                        // Update status
                        FirebaseFirestore.getInstance()
                            .collection("chats")
                            .document(listOf(call.callerId, call.receiverId).sorted().joinToString("_"))
                            .collection("messages")
                            .document(call.callId)
                            .update("status", "accepted")

                        IncomingCallManager.clearCall()
                        navController.navigate("speak/${call.callerId}/${call.audioOnly}")
                    }) { Text("Accept") }

                    Spacer(Modifier.width(16.dp))

                    Button(onClick = {
                        IncomingCallManager.clearCall()
                    }) { Text("Reject") }
                }
            }
        }
    }
}
