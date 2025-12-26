package com.toqsoft.freechat.featureVideo.view

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.toqsoft.freechat.coreNetwork.AgoraManager
import io.agora.rtc2.RtcEngine
import io.agora.rtc2.video.VideoCanvas

@Composable
fun VideoView(uid: Int, isLocal: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    AndroidView(
        factory = { RtcEngine.CreateRendererView(context) },
        update = { view ->
            view.setZOrderMediaOverlay(isLocal)

            val canvas = VideoCanvas(
                view,
                VideoCanvas.RENDER_MODE_HIDDEN,
                uid
            )

            if (isLocal) {
                AgoraManager.rtcEngine?.setupLocalVideo(canvas)
            } else {
                AgoraManager.rtcEngine?.setupRemoteVideo(canvas)
            }
        },
        modifier = modifier
    )
}
