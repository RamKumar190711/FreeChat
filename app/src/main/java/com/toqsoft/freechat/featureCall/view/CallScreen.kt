package com.toqsoft.freechat.featureCall.view

import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.R
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

    var callLabel by remember { mutableStateOf("Calling…") }
    var navigated by remember { mutableStateOf(false) }
    var joinedAgora by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(45_000)
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
            val token = snapshot.getString("token") ?: ""
            val delivered = snapshot.getBoolean("delivered") ?: false

            callLabel = when {
                status == "accepted" -> "Connecting…"
                delivered -> "Ringing…"
                else -> "Calling…"
            }

            if (status == "accepted" && !joinedAgora && !channel.isNullOrEmpty() && token.isNotEmpty()) {
                joinedAgora = true
                AgoraManager.init(context)
                val numericUid = AgoraManager.agoraUidFromUserId(callerId)

                AgoraManager.joinChannel(
                    token = token,
                    channelName = channel,
                )
            }

            if (status == "accepted" && !navigated) {
                navigated = true
                val targetRoute = if (audioOnly) {
                    "speak/$callId/$callerId/$receiverId/true/$receiverId"
                } else {
                    "videoCall/$callerId"
                }
                navController.navigate(targetRoute) { popUpTo("calling") { inclusive = true } }
            }

            if (status in listOf("rejected", "declined", "missed", "ended") && !navigated) {
                navigated = true
                onCancel()
            }
        }
        onDispose { listener.remove() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B1014), Color.Black)
                )
            )
    ) {
        CallingAvatar(
            modifier = Modifier.align(Alignment.Center),
            initial = receiverId.take(1).uppercase()
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 90.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(receiverId, color = Color.White, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(callLabel, color = Color.White.copy(alpha = 0.6f), fontSize = 16.sp)
        }

        IconButton(
            onClick = {
                FirebaseFirestore.getInstance()
                    .collection("chats").document(chatId)
                    .collection("messages").document(callId)
                    .update("status", "declined")
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 70.dp)
                .size(72.dp)
                .background(Color.Red, CircleShape)
        ) {
            Icon(
                painter = painterResource(R.drawable.end),
                contentDescription = "Cancel",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun CallingAvatar(
    modifier: Modifier,
    initial: String
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .scale(scale)
                .background(Color.White.copy(alpha = 0.05f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(initial, color = Color.White, fontSize = 52.sp)
        }
    }
}