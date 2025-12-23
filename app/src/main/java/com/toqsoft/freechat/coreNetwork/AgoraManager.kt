package com.toqsoft.freechat.coreNetwork

import android.content.Context
import android.util.Log
import io.agora.rtc2.*

object AgoraManager {

    var rtcEngine: RtcEngine? = null
        private set

    private val rtcEventHandler = object : IRtcEngineEventHandler() {

        override fun onJoinChannelSuccess(channel: String, uid: Int, elapsed: Int) {
            Log.d("AGORA", "✅ Joined channel=$channel uid=$uid")
        }

        override fun onUserJoined(uid: Int, elapsed: Int) {
            Log.d("AGORA", "👤 REMOTE USER JOINED uid=$uid")
        }

        override fun onUserOffline(uid: Int, reason: Int) {
            Log.e("AGORA", "❌ REMOTE USER LEFT uid=$uid reason=$reason")
            CallState.onRemoteLeft?.invoke()
        }

        override fun onAudioVolumeIndication(
            speakers: Array<AudioVolumeInfo>,
            totalVolume: Int
        ) {
            speakers.forEach { info ->
                if (info.uid == 0) {
                    Log.d("AGORA_AUDIO", "🎤 LOCAL MIC volume=${info.volume}")
                } else {
                    Log.d("AGORA_AUDIO", "🔊 REMOTE uid=${info.uid} volume=${info.volume}")
                }
            }
        }
    }

    fun init(context: Context) {
        if (rtcEngine != null) return
        rtcEngine = RtcEngine.create(context.applicationContext, AgoraConfig.APP_ID, rtcEventHandler)
        rtcEngine?.apply {
            setChannelProfile(Constants.CHANNEL_PROFILE_COMMUNICATION)
            enableAudio()
            enableLocalAudio(true)
            muteLocalAudioStream(false)
            setEnableSpeakerphone(true)
            enableAudioVolumeIndication(200, 3, true)
        }
        Log.d("AGORA", "🎧 Agora Initialized")
    }

    fun generateToken(channel: String): String {
        // For testing without certificate, return empty string
        return ""
    }

    fun joinChannel(token: String, channel: String, userId: String = "0") {
        val uid = userId.hashCode() and 0x7FFFFFFF
        rtcEngine?.joinChannel(token, channel, null, uid)
    }

    fun leaveChannel() {
        rtcEngine?.leaveChannel()
    }

    fun destroy() {
        leaveChannel()
        RtcEngine.destroy()
        rtcEngine = null
    }
}
