package com.toqsoft.freechat.coreNetwork

import android.content.Context
import io.agora.rtc2.IRtcEngineEventHandler
import io.agora.rtc2.RtcEngine

object AgoraManager {
    var rtcEngine: RtcEngine? = null
        private set

    fun init(context: Context, eventHandler: IRtcEngineEventHandler) {
        if (rtcEngine == null) {
            rtcEngine = RtcEngine.create(context, AgoraConfig.APP_ID, eventHandler)
        }
    }

    fun destroy() {
        rtcEngine?.let {
            RtcEngine.destroy()
            rtcEngine = null
        }
    }
}
