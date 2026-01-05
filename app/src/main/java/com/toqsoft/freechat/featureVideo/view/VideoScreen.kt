package com.toqsoft.freechat.featureVideo.view

import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreNetwork.AgoraManager
import kotlinx.coroutines.delay

/**
 * A Composable function that displays the user interface for an ongoing video call.
 *
 * This screen manages the local and remote video feeds, call controls (mute, switch camera, end call),
 * and listens for the call status from Firebase to automatically end the call if the other user hangs up.
 *
 * @param navController The NavController used for navigating away from the screen after the call ends.
 * @param callId The unique identifier for the current call document in Firebase.
 * @param callerId The unique identifier of the user who initiated the call.
 * @param receiverId The unique identifier of the user who is receiving the call.
 */
@Composable
fun VideoCallScreen(
    navController: NavController,
    callId: String,
    callerId: String,
    receiverId: String
) {
    val remoteUid by VideoCallState.remoteUid
    var controlsVisible by remember { mutableStateOf(true) }
    var isVideoMuted by remember { mutableStateOf(false) }

    val chatId = remember {
        listOf(callerId, receiverId).sorted().joinToString("_")
    }

    // 🔴 LISTEN FOR REMOTE END
    DisposableEffect(callId) {
        val listener = FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .document(callId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot?.getString("status") == "ended") {
                    AgoraManager.leaveChannel()
                    navController.navigate("users") {
                        popUpTo("users") { inclusive = false }
                    }
                }
            }
        onDispose { listener.remove() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { controlsVisible = true }
    ) {

        // REMOTE VIDEO
        if (remoteUid != null) {
            RemoteVideoView(remoteUid!!)
        } else {
            Text(
                "Connecting…",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // LOCAL PREVIEW (ONLY WHEN VIDEO IS ON)
        if (!isVideoMuted) {
            Box(
                modifier = Modifier
                    .size(110.dp, 160.dp)
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.DarkGray)
            ) {
                LocalVideoView()
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VideoControls(
                isVideoMuted = isVideoMuted,
                onVideoToggle = { muted ->
                    isVideoMuted = muted
                    AgoraManager.rtcEngine?.muteLocalVideoStream(muted)

                    if (muted) {
                        AgoraManager.rtcEngine?.stopPreview()
                        AgoraManager.rtcEngine?.disableVideo()
                    } else {
                        AgoraManager.rtcEngine?.enableVideo()
                        AgoraManager.rtcEngine?.startPreview()
                    }
                },
                onEndCall = {
                    endVideoCall(callId, callerId, receiverId)
                    AgoraManager.leaveChannel()
                    navController.navigate("users") {
                        popUpTo("users") { inclusive = true }
                    }
                }
            )
        }

        LaunchedEffect(controlsVisible) {
            if (controlsVisible) {
                delay(3000)
                controlsVisible = false
            }
        }
    }
}

@Composable
fun LocalVideoView() {
    val context = LocalContext.current
    val surfaceView = remember {
        SurfaceView(context).apply { setZOrderMediaOverlay(true) }
    }

    DisposableEffect(surfaceView) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                AgoraManager.setupLocalVideo(surfaceView)
                AgoraManager.rtcEngine?.enableVideo()
                AgoraManager.rtcEngine?.startPreview()
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        }
        surfaceView.holder.addCallback(callback)
        onDispose { surfaceView.holder.removeCallback(callback) }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun RemoteVideoView(uid: Int) {
    val context = LocalContext.current
    val surfaceView = remember(uid) {
        SurfaceView(context).apply { setZOrderOnTop(false) }
    }

    DisposableEffect(uid) {
        val callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                AgoraManager.setupRemoteVideo(surfaceView, uid)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) {}
        }
        surfaceView.holder.addCallback(callback)
        onDispose { surfaceView.holder.removeCallback(callback) }
    }

    AndroidView(
        factory = { surfaceView },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun VideoControls(
    isVideoMuted: Boolean,
    onVideoToggle: (Boolean) -> Unit,
    onEndCall: () -> Unit
) {
    var isAudioMuted by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {

        ControlButton(
            icon = if (isAudioMuted) R.drawable.mic_off else R.drawable.mic
        ) {
            isAudioMuted = !isAudioMuted
            AgoraManager.rtcEngine?.muteLocalAudioStream(isAudioMuted)
        }

        ControlButton(R.drawable.switch_camera) {
            AgoraManager.rtcEngine?.switchCamera()
        }

        ControlButton(
            icon = if (isVideoMuted) R.drawable.video_off else R.drawable.video
        ) {
            onVideoToggle(!isVideoMuted)
        }

        ControlButton(
            R.drawable.end,
            background = Color.Red,
            size = 64.dp
        ) {
            onEndCall()
        }
    }
}

@Composable
private fun ControlButton(
    icon: Int,
    background: Color = Color(0xFF2C2C2C),
    size: Dp = 54.dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size).background(background, CircleShape)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

private fun endVideoCall(
    callId: String,
    callerId: String,
    receiverId: String
) {
    FirebaseFirestore.getInstance()
        .collection("chats")
        .document(listOf(callerId, receiverId).sorted().joinToString("_"))
        .collection("messages")
        .document(callId)
        .update("status", "ended")
}
