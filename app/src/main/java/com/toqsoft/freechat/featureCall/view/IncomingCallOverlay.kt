package com.toqsoft.freechat.featureCall.view

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.coreModel.IncomingCallData
import com.toqsoft.freechat.coreNetwork.AgoraManager
import com.toqsoft.freechat.coreNetwork.IncomingCallManager
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun IncomingCallOverlay(navController: NavController, viewModel: ChatViewModel = hiltViewModel()) {
    val incomingCall by IncomingCallManager.incomingCall.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val myId = viewModel.myUserId.ifEmpty { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var vibrator by remember { mutableStateOf<Vibrator?>(null) }

    LaunchedEffect(incomingCall) {
        if (incomingCall != null) {
            if (mediaPlayer == null) {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mediaPlayer = MediaPlayer.create(context, uri)?.apply {
                    isLooping = true
                    start()
                }
            }
            if (vibrator == null) {
                vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.let {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
                        } else {
                            it.vibrate(longArrayOf(0, 500, 500), 0)
                        }
                    } catch (_: SecurityException) {}
                }
            }
        } else {
            mediaPlayer?.let { player ->
                try { if (player.isPlaying) player.stop() } catch (_: IllegalStateException) {}
                player.release()
            }
            mediaPlayer = null
            vibrator?.cancel()
            vibrator = null
        }
    }

    LaunchedEffect(incomingCall, myId) {
        val call = incomingCall ?: return@LaunchedEffect
        if (myId.isEmpty()) return@LaunchedEffect
        val chatId = listOf(myId, call.callerId).sorted().joinToString("_")
        val docRef = FirebaseFirestore.getInstance()
            .collection("chats").document(chatId)
            .collection("messages").document(call.callId)
        docRef.update("delivered", true)
        docRef.addSnapshotListener { snapshot, _ ->
            val status = snapshot?.getString("status")
            if (listOf("declined", "missed", "ended").contains(status)) {
                mediaPlayer?.let { player ->
                    try { if (player.isPlaying) player.stop() } catch (_: IllegalStateException) {}
                    player.release()
                }
                mediaPlayer = null
                vibrator?.cancel()
                vibrator = null
                NotificationManagerCompat.from(context).cancel(999)
                IncomingCallManager.clearCall()
            }
        }
    }

    incomingCall?.let { call ->
        val swipeOffsetX = remember { Animatable(0f) }
        val swipeProgress = (swipeOffsetX.value / 200f).coerceIn(-1f, 1f)
        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "scale"
        )
        val backgroundColor by animateColorAsState(
            targetValue = when {
                swipeProgress > 0.4f -> Color(0xFF1B5E20)
                swipeProgress < -0.4f -> Color(0xFFB71C1C)
                else -> Color(0xFF0A1A24)
            }, label = "bg"
        )

        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            Box(modifier = Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .scale(pulseScale + abs(swipeProgress) * 0.2f)
                        .background(Color.White.copy(alpha = 0.05f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = call.callerId.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = call.callerId,
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Light
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (call.audioOnly) "FREECHAT AUDIO CALL" else "FREECHAT VIDEO CALL",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    letterSpacing = 2.sp
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp, start = 24.dp, end = 24.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(42.dp))
                    .background(Color.LightGray.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 60.dp, vertical = 50.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Decline", color = Color.White.copy(alpha = (0.6f - swipeProgress).coerceIn(0.1f, 1f)))
                    Text("Answer", color = Color.White.copy(alpha = (0.6f + swipeProgress).coerceIn(0.1f, 1f)))
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = swipeOffsetX.value.dp)
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .pointerInput(myId) {
                            detectDragGestures(
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    scope.launch { swipeOffsetX.snapTo((swipeOffsetX.value + dragAmount.x).coerceIn(-150f, 150f)) }
                                },
                                onDragEnd = {
                                    scope.launch {
                                        when {
                                            swipeOffsetX.value > 130f -> acceptCall(call, navController, myId, context, mediaPlayer, vibrator)
                                            swipeOffsetX.value < -130f -> rejectCall(call, myId, context, mediaPlayer, vibrator)
                                            else -> swipeOffsetX.animateTo(0f, spring(Spring.DampingRatioMediumBouncy))
                                        }
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val iconColor by animateColorAsState(
                        targetValue = when {
                            swipeProgress > 0.4f -> Color(0xFF2E7D32)
                            swipeProgress < -0.4f -> Color(0xFFC62828)
                            else -> Color(0xFF0A1A24)
                        }, label = "iconColor"
                    )
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

private fun acceptCall(call: IncomingCallData, navController: NavController, myId: String, context: Context, mediaPlayer: MediaPlayer?, vibrator: Vibrator?) {
    mediaPlayer?.let { player ->
        try { if (player.isPlaying) player.stop() } catch (_: IllegalStateException) {}
        player.release()
    }
    vibrator?.cancel()

    val chatId = listOf(myId, call.callerId).sorted().joinToString("_")
    FirebaseFirestore.getInstance()
        .collection("chats").document(chatId)
        .collection("messages").document(call.callId)
        .update("status", "accepted")
        .addOnSuccessListener {
            IncomingCallManager.clearCall()
            AgoraManager.init(context)
            AgoraManager.joinChannel(
                context = context,
                token = call.token,
                channelName = call.channel,
                userId = myId
            )
            if (call.audioOnly) {
                navController.navigate("speak/${call.callId}/${call.callerId}/$myId/true/${call.callerId}")
            } else {
                navController.navigate("videoCall/${call.callId}/${call.callerId}/$myId")
            }
        }
}

private fun rejectCall(call: IncomingCallData, myId: String, context: Context, mediaPlayer: MediaPlayer?, vibrator: Vibrator?) {
    mediaPlayer?.let { player ->
        try { if (player.isPlaying) player.stop() } catch (_: IllegalStateException) {}
        player.release()
    }
    vibrator?.cancel()

    val chatId = listOf(myId, call.callerId).sorted().joinToString("_")
    FirebaseFirestore.getInstance().collection("chats").document(chatId)
        .collection("messages").document(call.callId)
        .update("status", "rejected")
        .addOnSuccessListener {
            NotificationManagerCompat.from(context).cancel(999)
            IncomingCallManager.clearCall()
        }
}
