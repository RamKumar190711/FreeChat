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

    private val _usernameUpdateResult = MutableSharedFlow<String>()
    val usernameUpdateResult: SharedFlow<String> = _usernameUpdateResult.asSharedFlow()

    private val _isUpdatingUsername = MutableStateFlow(false)
    val isUpdatingUsername: StateFlow<Boolean> = _isUpdatingUsername.asStateFlow()

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
            if (myId.isNotEmpty()) {
                firestoreRepo.markChatAsRead(myId, user)
            }
        }
    }

    /**
     * Update the username both locally and in Firestore
     * @param newName The new username to set
     */
    fun updateUsername(newName: String) {
        viewModelScope.launch {
            _isUpdatingUsername.value = true
            try {
                val oldName = myUsername.value

                if (oldName.isEmpty()) {
                    // If no previous username, just announce as new user
                    prefs.saveUsername(newName)
                    firestoreRepo.saveUser(newName, newName)
                    _usernameUpdateResult.emit("Username set to: $newName")
                } else {
                    // Update username in preferences
                    prefs.saveUsername(newName)

                    // Update username in Firestore (this should handle updating user document)
                    firestoreRepo.saveUser(newName, newName)

                    // If we need to update chat references, we might need additional logic here
                    // For now, we'll just update the user document

                    _usernameUpdateResult.emit("Username updated from '$oldName' to '$newName'")
                }
            } catch (e: Exception) {
                _usernameUpdateResult.emit("Failed to update username: ${e.message}")
            } finally {
                _isUpdatingUsername.value = false
            }
        }
    }



    /**
     * Validate if a username is available
     * @param username The username to check
     * @return Boolean indicating if username is available
     */
    fun isUsernameAvailable(username: String): Boolean {
        return !users.value.contains(username) || username == myUsername.value
    }

    /**
     * Validate username rules
     * @param username The username to validate
     * @return Pair of isValid and error message
     */
    fun validateUsername(username: String): Pair<Boolean, String> {
        return when {
            username.isEmpty() -> Pair(false, "Username cannot be empty")
            username.length < 3 -> Pair(false, "Username must be at least 3 characters")
            username.length > 20 -> Pair(false, "Username must be less than 20 characters")
            username.contains(Regex("[^a-zA-Z0-9_]")) ->
                Pair(false, "Username can only contain letters, numbers, and underscores")
            username == myUsername.value ->
                Pair(true, "This is your current username")
            users.value.contains(username) ->
                Pair(false, "Username '$username' is already taken")
            else -> Pair(true, "Username is available")
        }
    }
}