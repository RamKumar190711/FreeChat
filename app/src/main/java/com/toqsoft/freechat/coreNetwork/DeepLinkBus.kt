package com.toqsoft.freechat.coreNetwork

import androidx.navigation.NavController
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object DeepLinkBus {

    private val _events = MutableSharedFlow<(NavController) -> Unit>()
    val events = _events.asSharedFlow()

    suspend fun emit(action: (NavController) -> Unit) {
        _events.emit(action)
    }
}
