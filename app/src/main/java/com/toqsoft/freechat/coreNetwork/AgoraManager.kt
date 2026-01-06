package com.toqsoft.freechat.coreNetwork

import android.content.Context
import android.util.Log
import com.toqsoft.freechat.featureVideo.view.VideoCallState
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas
import io.agora.rtc2.video.VideoEncoderConfiguration

object AgoraManager {

    var rtcEngine: RtcEngine? = null
    var localUid: Int = 0
        private set

    private var onAudioVolumeCallback: ((speakers: Array<IRtcEngineEventHandler.AudioVolumeInfo>?, totalVolume: Int) -> Unit)? = null

    fun setOnAudioVolumeCallback(callback: (speakers: Array<IRtcEngineEventHandler.AudioVolumeInfo>?, totalVolume: Int) -> Unit) {
        onAudioVolumeCallback = callback
    }

    private val rtcEventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Log.d("AGORA_DEBUG", "✅ Joined channel: $channel, uid=$uid, elapsed=$elapsed")
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d("AGORA_DEBUG", "👤 Remote user joined uid=$uid, elapsed=$elapsed")
            VideoCallState.remoteUid.value = uid
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d("AGORA_DEBUG", "❌ Remote user offline uid=$uid, reason=$reason")
            VideoCallState.remoteUid.value = null
        }

        override fun onError(err: Int) {
            Log.e("AGORA_DEBUG", "🔥 Agora error=$err")
        }

        override fun onAudioVolumeIndication(speakers: Array<AudioVolumeInfo>?, totalVolume: Int) {
            super.onAudioVolumeIndication(speakers, totalVolume)

            speakers?.forEach { speakerInfo ->
                Log.d("AGORA_VOLUME", "UID ${speakerInfo.uid}: volume=${speakerInfo.volume}")
            }

            onAudioVolumeCallback?.invoke(speakers, totalVolume)
        }
    }

    fun init(context: Context) {
        if (rtcEngine != null) return

        Log.d("AGORA_DEBUG", "Initializing RtcEngine")
        val config = RtcEngineConfig().apply {
            mContext = context.applicationContext
            mAppId = AgoraConfig.APP_ID
            mEventHandler = rtcEventHandler
        }

        rtcEngine = RtcEngine.create(config)

        rtcEngine?.apply {
            setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            enableVideo()
            setVideoEncoderConfiguration(
                VideoEncoderConfiguration(
                    VideoEncoderConfiguration.VD_640x360,
                    VideoEncoderConfiguration.FRAME_RATE.FRAME_RATE_FPS_15,
                    VideoEncoderConfiguration.STANDARD_BITRATE,
                    VideoEncoderConfiguration.ORIENTATION_MODE.ORIENTATION_MODE_FIXED_PORTRAIT
                )
            )
        }
    }

    fun joinChannel(
        context: Context,
        token: String,
        channelName: String,
        userId: String
    ) {
        if (rtcEngine == null) init(context)

        localUid = (userId.hashCode() and 0x7FFFFFFF)
        Log.d("AGORA_DEBUG", "Local UID set to $localUid")

        rtcEngine?.enableAudioVolumeIndication(200, 3, true)
        Log.d("AGORA_DEBUG", "Enabled audio volume indication")

        rtcEngine?.enableVideo()
        rtcEngine?.startPreview()

        val options = ChannelMediaOptions().apply {
            publishCameraTrack = true
            publishMicrophoneTrack = true
            autoSubscribeAudio = true
            autoSubscribeVideo = true
            clientRoleType = Constants.CLIENT_ROLE_BROADCASTER
        }

        Log.d("AGORA_DEBUG", "Joining channel $channelName with token $token")
        rtcEngine?.joinChannel(token, channelName, localUid, options)
    }

    fun setupLocalVideo(surfaceView: android.view.SurfaceView) {
        Log.d("AGORA_DEBUG", "Setting up local video for UID=$localUid")
        rtcEngine?.setupLocalVideo(
            VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, localUid)
        )
    }

    fun setupRemoteVideo(surfaceView: android.view.SurfaceView, uid: Int) {
        Log.d("AGORA_DEBUG", "Setting up remote video for UID=$uid")
        rtcEngine?.setupRemoteVideo(
            VideoCanvas(surfaceView, VideoCanvas.RENDER_MODE_FIT, uid)
        )
    }

    fun leaveChannel() {
        rtcEngine?.stopPreview()
        rtcEngine?.leaveChannel()
        VideoCallState.remoteUid.value = null
        Log.d("AGORA_DEBUG", "Left channel and stopped preview")
    }
}