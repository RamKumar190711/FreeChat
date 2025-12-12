package com.toqsoft.freechat.featureList.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toqsoft.freechat.coreModel.UserPreferencesRepository
import com.toqsoft.freechat.coreNetwork.FirestoreChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val firestoreRepo: FirestoreChatRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    private val _users = MutableStateFlow<List<String>>(emptyList())
    val users: StateFlow<List<String>> = _users.asStateFlow()

    private val _unreadCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val unreadCounts: StateFlow<Map<String, Int>> = _unreadCounts.asStateFlow()

    private val _lastMessages = MutableStateFlow<Map<String, String>>(emptyMap())
    val lastMessages: StateFlow<Map<String, String>> = _lastMessages.asStateFlow()

    val myUsername: StateFlow<String> = prefs.usernameFlow.map { it ?: "" }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ""
    )

    init {
        // Load users
        viewModelScope.launch {
            firestoreRepo.getUsersFlow().collect { list ->
                _users.value = list
            }
        }

        // Observe unread counts and last messages
        viewModelScope.launch {
            myUsername.filter { it.isNotEmpty() }.collect { myId ->
                launch {
                    firestoreRepo.getUnreadCountFlow(myId).collect { counts ->
                        _unreadCounts.value = counts
                    }
                }
                launch {
                    firestoreRepo.getLastMessageFlow(myId).collect { lastMsg ->
                        _lastMessages.value = lastMsg.mapValues { it.value.first }
                    }
                }
            }
        }
    }

    fun announceSelf(name: String) {
        viewModelScope.launch {
            prefs.saveUsername(name)
            firestoreRepo.saveUser(name, name)
        }
    }

    fun addDiscoveredUser(name: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                firestoreRepo.saveUser(name, name)
            }
        }
    }

    fun clearUnreadCount(user: String) {
        viewModelScope.launch {
            val myId = myUsername.value
            firestoreRepo.markChatAsRead(myId, user) // Firestore updated here
            // No local zeroing, Flow will update automatically
        }
    }
}
