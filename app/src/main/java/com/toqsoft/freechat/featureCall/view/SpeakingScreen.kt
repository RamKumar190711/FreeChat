package com.toqsoft.freechat.featureCall.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.toqsoft.freechat.coreNetwork.AgoraManager
import kotlinx.coroutines.delay

@Composable
fun SpeakingScreen(
    otherUserId: String,
    audioOnly: Boolean,
    onHangUp: () -> Unit
) {
    var isMuted by remember { mutableStateOf(false) }
    var seconds by remember { mutableStateOf(0) }

    // ⏱ Call timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 👤 Name
            Text(
                text = otherUserId,
                color = Color.White,
                fontSize = 26.sp
            )

            Spacer(Modifier.height(8.dp))

            // ⏱ Timer
            Text(
                text = String.format("%02d:%02d", minutes, remainingSeconds),
                color = Color.Gray,
                fontSize = 18.sp
            )

            Spacer(Modifier.height(40.dp))

            // 🎛 Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {

                // 🔇 Mute
                Button(
                    onClick = {
                        isMuted = !isMuted
                        AgoraManager.rtcEngine?.muteLocalAudioStream(isMuted)
                    }
                ) {
                    Text(if (isMuted) "Unmute" else "Mute")
                }

                // 🔴 Hang Up
                Button(
                    onClick = {
                        AgoraManager.rtcEngine?.leaveChannel()
                        onHangUp()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    )
                ) {
                    Text("Hang Up")
                }
            }
        }
    }
}
