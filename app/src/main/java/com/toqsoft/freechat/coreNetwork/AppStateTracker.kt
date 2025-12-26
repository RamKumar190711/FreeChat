package com.toqsoft.freechat.coreNetwork

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object AppStateTracker {
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground

    fun onResume() { _isForeground.value = true }
    fun onPause() { _isForeground.value = false }
}
