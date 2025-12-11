package com.toqsoft.freechat.featureList.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toqsoft.freechat.coreModel.UserPreferencesRepository
import com.toqsoft.freechat.coreNetwork.FirestoreChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val firestoreRepo: FirestoreChatRepository, // Firestore repo
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    private val _users = MutableStateFlow<List<String>>(emptyList())
    val users: StateFlow<List<String>> = _users.asStateFlow()

    val myUsername: Flow<String> = prefs.usernameFlow.map { it ?: "" }

    init {
        viewModelScope.launch {
            // Observe Firestore users collection
            firestoreRepo.getUsersFlow().collect { userList ->
                _users.value = userList
            }
        }
    }

    fun announceSelf(name: String) {
        viewModelScope.launch {
            prefs.saveUsername(name)
            firestoreRepo.saveUser(name, name) // save in Firestore
        }
    }

    fun addDiscoveredUser(name: String) {
        if (name.isNotBlank()) {
            viewModelScope.launch {
                firestoreRepo.saveUser(name, name) // save to Firestore
            }
        }
    }
}
