package com.toqsoft.freechat.coreNetwork

import android.content.Context
import android.util.Log
import android.view.SurfaceView
import com.toqsoft.freechat.featureVideo.view.VideoCallState
import io.agora.rtc2.*
import io.agora.rtc2.video.VideoCanvas

object AgoraManager {
    var rtcEngine: RtcEngine? = null

    private val rtcEventHandler = object : IRtcEngineEventHandler() {
        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Log.d("AGORA_DEBUG", "✅ SUCCESS: Joined channel=$channel with UID=$uid")
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d("AGORA_DEBUG", "👤 EVENT: Remote user joined with UID=$uid")
            VideoCallState.remoteUid.value = uid
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.d("AGORA_DEBUG", "❌ EVENT: Remote user $uid went offline")
            VideoCallState.remoteUid.value = null
        }

        override fun onError(err: Int) {
            Log.e("AGORA_DEBUG", "🔥 ERROR: Agora code=$err")
        }
    }

    fun agoraUidFromUserId(userId: String): Int {
        return userId.hashCode() and 0x7FFFFFFF
    }

    fun init(context: Context) {
        if (rtcEngine != null) return

        val config = RtcEngineConfig().apply {
            mContext = context.applicationContext
            mAppId = AgoraConfig.APP_ID
            mEventHandler = rtcEventHandler
        }

        rtcEngine = RtcEngine.create(config)
        rtcEngine?.apply {
            enableVideo()
            setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
        }
    }

    fun joinChannel(token: String, channelName: String) {
        val options = ChannelMediaOptions().apply {
            autoSubscribeAudio = true
            autoSubscribeVideo = true
            publishCameraTrack = true
            publishMicrophoneTrack = true
        }

        rtcEngine?.joinChannel(token, channelName, 0, options) // ✅ UID = 0
        Log.d("AGORA_DEBUG", "Join Request result:")

    }



    fun leaveChannel() {
        rtcEngine?.stopPreview()
        rtcEngine?.leaveChannel()
        VideoCallState.remoteUid.value = null
    }
}