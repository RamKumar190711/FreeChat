    package com.toqsoft.freechat.featureCall.view

    import androidx.compose.foundation.background
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.res.painterResource
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import com.google.firebase.firestore.FirebaseFirestore
    import com.toqsoft.freechat.R
    import com.toqsoft.freechat.coreNetwork.AgoraManager
    import com.toqsoft.freechat.coreNetwork.IncomingCallManager
    import kotlinx.coroutines.delay

    @Composable
    fun SpeakingScreen(
        callId: String,
        callerId: String,
        receiverId: String,
        otherUserId: String,
        audioOnly: Boolean,
        onHangUp: () -> Unit
    ) {
        var isMuted by remember { mutableStateOf(false) }
        var isSpeakerOn by remember { mutableStateOf(true) }
        var seconds by remember { mutableStateOf(0) }

        LaunchedEffect(Unit) {
            while (true) {
                delay(1000)
                seconds++
            }
        }
        DisposableEffect(callId) {
            val chatId = listOf(callerId, receiverId).sorted().joinToString("_")
            val listener = FirebaseFirestore.getInstance()
                .collection("chats").document(chatId)
                .collection("messages").document(callId)
                .addSnapshotListener { snapshot, _ ->
                    if (snapshot?.getString("status") == "ended") {
                        AgoraManager.leaveChannel()
                        onHangUp()
                    }
                }
            onDispose { listener.remove() }
        }

        val minutes = seconds / 60
        val remainingSeconds = seconds % 60

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 50.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = otherUserId, color = Color.White, fontSize = 26.sp)
                Spacer(Modifier.height(8.dp))
                Text(String.format("%02d:%02d", minutes, remainingSeconds), color = Color.Gray, fontSize = 18.sp)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(40.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)
            ) {
                // Speaker
                IconButton(onClick = {
                    isSpeakerOn = !isSpeakerOn
                    AgoraManager.rtcEngine?.setEnableSpeakerphone(isSpeakerOn)
                }, modifier = Modifier.size(64.dp).background(Color.DarkGray, CircleShape)) {
                    Icon(
                        painter = painterResource(id = if (isSpeakerOn) R.drawable.speaker else R.drawable.speaker_off),
                        contentDescription = "Speaker",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Mute
                IconButton(onClick = {
                    isMuted = !isMuted
                    AgoraManager.rtcEngine?.muteLocalAudioStream(isMuted)
                }, modifier = Modifier.size(64.dp).background(Color.DarkGray, CircleShape)) {
                    Icon(
                        painter = painterResource(id = if (isMuted) R.drawable.mic_off else R.drawable.mic),
                        contentDescription = "Mute",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Hangup
                IconButton(onClick = {
                    endCall(callId, callerId, receiverId)
                    AgoraManager.leaveChannel()
                    onHangUp()
                }, modifier = Modifier.size(64.dp).background(Color.Red, CircleShape)) {
                    Icon(
                        painter = painterResource(id = R.drawable.end),
                        contentDescription = "Hang Up",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }

    private fun endCall(
        callId: String,
        callerId: String,
        receiverId: String
    ) {
        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(listOf(callerId, receiverId).sorted().joinToString("_"))
            .collection("messages")
            .document(callId)
            .update("status", "ended") // 🔥 OR "rejected"

    //    IncomingCallManager.clearCall()
    }
