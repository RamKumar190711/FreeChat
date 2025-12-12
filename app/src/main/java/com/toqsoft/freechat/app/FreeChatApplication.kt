package com.toqsoft.freechat.app

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import com.toqsoft.freechat.coreModel.UserPreferencesRepository

@HiltAndroidApp
class FreeChatApplication : Application(), LifecycleObserver {

    companion object {
        val isAppInForeground = MutableStateFlow(false)
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        saveFcmToken()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onEnterForeground() {
        isAppInForeground.value = true
        updateAppState("foreground")
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onEnterBackground() {
        isAppInForeground.value = false
        updateAppState("background")
    }

    private fun saveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) updateFcmToken(task.result)
        }
    }

    private fun updateAppState(state: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val userId = getCurrentUserId() ?: return@launch
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("appState", state)
        }
    }

    private fun updateFcmToken(token: String?) {
        if (token == null) return
        CoroutineScope(Dispatchers.IO).launch {
            val userId = getCurrentUserId() ?: return@launch
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .update("fcmToken", token)
        }
    }

    private suspend fun getCurrentUserId(): String? {
        return UserPreferencesRepository(this).usernameFlow.firstOrNull()
    }
}
