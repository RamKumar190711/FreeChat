// ------------------------- CallState.kt -------------------------
package com.toqsoft.freechat.coreNetwork

object CallState {
    var onRemoteLeft: (() -> Unit)? = null
}
