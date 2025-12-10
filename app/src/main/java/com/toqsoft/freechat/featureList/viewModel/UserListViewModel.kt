package com.toqsoft.freechat.featureList.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toqsoft.freechat.coreModel.UserPreferencesRepository
import com.toqsoft.freechat.coreNetwork.MqttClientManager
import com.toqsoft.freechat.coreModel.UserPresence
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val mqttManager: MqttClientManager,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    private val _users = MutableStateFlow<List<String>>(emptyList())
    val users: StateFlow<List<String>> = _users.asStateFlow()

    val myUsername: Flow<String> = prefs.usernameFlow.map { it ?: "" }

    private val _userPresenceMap = MutableStateFlow<Map<String, UserPresence>>(emptyMap())
    val userPresenceMap: StateFlow<Map<String, UserPresence>> = _userPresenceMap.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.discoveredUsersFlow.collect { savedUsers ->
                _users.value = (_users.value + savedUsers).distinct()
            }
        }
    }

    private fun observeUserPresence(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            mqttManager.observeUserPresence(userId).collectLatest { presence ->
                val map = _userPresenceMap.value.toMutableMap()
                map[userId] = presence
                _userPresenceMap.value = map
            }
        }
    }

    fun announceSelf(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.saveUsername(name)
            if (!_users.value.contains(name)) _users.value = _users.value + name
            if (!mqttManager.isConnected()) mqttManager.connect(name)
            mqttManager.publishPresenceStatus(name, true, null)
        }
    }

    fun addDiscoveredUser(name: String) {
        if (name.isNotBlank() && !_users.value.contains(name)) {
            _users.value = _users.value + name
            viewModelScope.launch {
                val savedUsers = prefs.discoveredUsersFlow.first().toMutableList()
                if (!savedUsers.contains(name)) {
                    savedUsers.add(name)
                    prefs.saveDiscoveredUsers(savedUsers)
                }
            }
            observeUserPresence(name)
        }
    }

    fun goOffline() {
        viewModelScope.launch {
            myUsername.firstOrNull()?.let { name ->
                mqttManager.publishPresenceStatus(name, false, System.currentTimeMillis())
            }
        }
    }
}
