package com.toqsoft.freechat.coreNetwork

import com.toqsoft.freechat.coreModel.IncomingCallData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object IncomingCallManager {
    private val _incomingCall = MutableStateFlow<IncomingCallData?>(null)
    val incomingCall: StateFlow<IncomingCallData?> = _incomingCall

    fun showIncomingCall(
        callerId: String,
        receiverId: String,
        channel: String,
        token: String,
        callId: String,
        audioOnly: Boolean
    ) {
        _incomingCall.value = IncomingCallData(
            callerId, receiverId, channel, token, callId, audioOnly
        )
    }

    fun clearCall() {
        _incomingCall.value = null
    }
}
