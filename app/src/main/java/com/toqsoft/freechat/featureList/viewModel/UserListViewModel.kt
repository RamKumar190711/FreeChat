// UserListViewModel.kt
package com.toqsoft.freechat.featureList.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toqsoft.freechat.coreModel.Group
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

    private val _groups = MutableStateFlow<List<Group>>(emptyList())
    val groups: StateFlow<List<Group>> = _groups.asStateFlow()

    private val _groupCreationResult = MutableSharedFlow<String>()
    val groupCreationResult: SharedFlow<String> = _groupCreationResult.asSharedFlow()

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

        // Load groups for current user
        viewModelScope.launch {
            myUsername.filter { it.isNotEmpty() }.collect { myId ->
                firestoreRepo.getGroupsFlow(myId).collect { groupList ->
                    _groups.value = groupList
                }
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

    fun createGroupChat(
        groupName: String,
        members: List<String>,
        createdBy: String
    ) {
        viewModelScope.launch {
            try {
                val group = Group(
                    name = groupName,
                    members = members,
                    createdBy = createdBy,
                    createdAt = System.currentTimeMillis()
                )

                val groupId = firestoreRepo.createGroup(group)
                _groupCreationResult.emit("Group '$groupName' created successfully!")

                // Navigate to the group chat (you'll handle this in the UI)
                // The group ID can be used to navigate to the group chat screen
            } catch (e: Exception) {
                _groupCreationResult.emit("Failed to create group: ${e.message}")
            }
        }
    }

    fun clearUnreadCount(user: String) {
        viewModelScope.launch {
            val myId = myUsername.value
            firestoreRepo.markChatAsRead(myId, user)
        }
    }
}