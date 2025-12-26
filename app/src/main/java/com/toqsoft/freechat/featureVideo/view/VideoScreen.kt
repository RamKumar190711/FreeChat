package com.toqsoft.freechat.featureVideo.view

import android.util.Log
import android.view.SurfaceView
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreNetwork.AgoraManager
import io.agora.rtc2.video.VideoCanvas
import kotlinx.coroutines.delay

@Composable
fun VideoCallScreen(
    localUid: Int,
    onEndCall: () -> Unit
) {
    val remoteUid by VideoCallState.remoteUid
    var controlsVisible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        AgoraManager.rtcEngine?.enableVideo()
        AgoraManager.rtcEngine?.startPreview()
    }

    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { controlsVisible = true }
    ) {
        if (remoteUid != null) {
            RemoteVideoView(remoteUid!!)
        } else {
            Text(
                "Connecting…",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

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

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            VideoControls(onEndCall)
        }
    }
}

@Composable
private fun VideoControls(onEndCall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(R.drawable.mic) {
            AgoraManager.rtcEngine?.muteLocalAudioStream(false)
        }
        ControlButton(R.drawable.switch_camera) {
            AgoraManager.rtcEngine?.switchCamera()
        }
        ControlButton(R.drawable.video) {
            AgoraManager.rtcEngine?.muteLocalVideoStream(false)
        }
        ControlButton(
            R.drawable.end,
            background = Color.Red,
            size = 64.dp
        ) {
            AgoraManager.leaveChannel()
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
        modifier = Modifier
            .size(size)
            .background(background, CircleShape)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun LocalVideoView() {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                setZOrderMediaOverlay(true)
                AgoraManager.rtcEngine?.setupLocalVideo(
                    VideoCanvas(this, VideoCanvas.RENDER_MODE_HIDDEN, 0)
                )
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}


@Composable
fun RemoteVideoView(uid: Int) {
    AndroidView(
        factory = { context -> SurfaceView(context) },
        update = { view ->
            AgoraManager.rtcEngine?.setupRemoteVideo(
                VideoCanvas(view, VideoCanvas.RENDER_MODE_HIDDEN, uid)
            )
        },
        modifier = Modifier.fillMaxSize()
    )
}
