// GroupChatViewModel.kt
package com.toqsoft.freechat.featureChat.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.toqsoft.freechat.coreModel.Group
import com.toqsoft.freechat.coreModel.GroupMessage
import com.toqsoft.freechat.coreModel.UserPreferencesRepository
import com.toqsoft.freechat.coreNetwork.FirestoreChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val firestoreRepo: FirestoreChatRepository,
    private val prefs: UserPreferencesRepository
) : ViewModel() {

    private val _groupId = MutableStateFlow<String?>(null)
    
    private val _group = MutableStateFlow<Group?>(null)
    val group: StateFlow<Group?> = _group.asStateFlow()
    
    private val _messages = MutableStateFlow<List<GroupMessage>>(emptyList())
    val messages: StateFlow<List<GroupMessage>> = _messages.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    val myUsername: StateFlow<String> = prefs.usernameFlow.map { it ?: "" }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ""
    )

    fun initialize(groupId: String) {
        if (_groupId.value != groupId) {
            _groupId.value = groupId
            loadGroup(groupId)
            loadMessages(groupId)
        }
    }

    private fun loadGroup(groupId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val group = firestoreRepo.getGroupById(groupId)
                _group.value = group
                _error.value = null
            } catch (e: Exception) {
                _error.value = "Failed to load group: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadMessages(groupId: String) {
        viewModelScope.launch {
            firestoreRepo.getGroupMessagesFlow(groupId).collect { messageList ->
                _messages.value = messageList
                // Mark messages as read
                messageList.filter { 
                    it.senderId != myUsername.value && !it.isRead 
                }.forEach { message ->
                    markMessageAsRead(message.id)
                }
            }
        }
    }

    fun sendMessage(content: String) {
        viewModelScope.launch {
            val groupId = _groupId.value
            val senderId = myUsername.value
            
            if (groupId != null && senderId.isNotEmpty()) {
                try {
                    val message = GroupMessage(
                        groupId = groupId,
                        senderId = senderId,
                        senderName = senderId,
                        content = content,
                        timestamp = System.currentTimeMillis()
                    )
                    
                    firestoreRepo.sendGroupMessage(message)
                } catch (e: Exception) {
                    _error.value = "Failed to send message: ${e.message}"
                }
            }
        }
    }

    fun sendImageMessage(imageUrl: String, caption: String = "") {
        viewModelScope.launch {
            val groupId = _groupId.value
            val senderId = myUsername.value
            
            if (groupId != null && senderId.isNotEmpty()) {
                try {
                    val message = GroupMessage(
                        groupId = groupId,
                        senderId = senderId,
                        senderName = senderId,
                        content = caption.ifEmpty { "Sent an image" },
                        timestamp = System.currentTimeMillis(),
                        type = "image",
                        fileUrl = imageUrl
                    )
                    
                    firestoreRepo.sendGroupMessage(message)
                } catch (e: Exception) {
                    _error.value = "Failed to send image: ${e.message}"
                }
            }
        }
    }

    fun sendAudioMessage(audioUrl: String, duration: Long) {
        viewModelScope.launch {
            val groupId = _groupId.value
            val senderId = myUsername.value
            
            if (groupId != null && senderId.isNotEmpty()) {
                try {
                    val message = GroupMessage(
                        groupId = groupId,
                        senderId = senderId,
                        senderName = senderId,
                        content = "Sent an audio message",
                        timestamp = System.currentTimeMillis(),
                        type = "audio",
                        fileUrl = audioUrl,
                        duration = duration
                    )
                    
                    firestoreRepo.sendGroupMessage(message)
                } catch (e: Exception) {
                    _error.value = "Failed to send audio: ${e.message}"
                }
            }
        }
    }

    fun sendVideoMessage(videoUrl: String, duration: Long) {
        viewModelScope.launch {
            val groupId = _groupId.value
            val senderId = myUsername.value
            
            if (groupId != null && senderId.isNotEmpty()) {
                try {
                    val message = GroupMessage(
                        groupId = groupId,
                        senderId = senderId,
                        senderName = senderId,
                        content = "Sent a video",
                        timestamp = System.currentTimeMillis(),
                        type = "video",
                        fileUrl = videoUrl,
                        duration = duration
                    )
                    
                    firestoreRepo.sendGroupMessage(message)
                } catch (e: Exception) {
                    _error.value = "Failed to send video: ${e.message}"
                }
            }
        }
    }

    fun sendFileMessage(fileUrl: String, fileName: String, fileSize: Long) {
        viewModelScope.launch {
            val groupId = _groupId.value
            val senderId = myUsername.value
            
            if (groupId != null && senderId.isNotEmpty()) {
                try {
                    val message = GroupMessage(
                        groupId = groupId,
                        senderId = senderId,
                        senderName = senderId,
                        content = "Sent a file: $fileName",
                        timestamp = System.currentTimeMillis(),
                        type = "file",
                        fileUrl = fileUrl,
                        fileSize = fileSize
                    )
                    
                    firestoreRepo.sendGroupMessage(message)
                } catch (e: Exception) {
                    _error.value = "Failed to send file: ${e.message}"
                }
            }
        }
    }

    private fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            try {
                firestoreRepo.markGroupMessageAsRead(messageId, myUsername.value)
            } catch (e: Exception) {
                // Silent fail for read receipts
            }
        }
    }

    fun addReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            try {
                // This would need to be implemented in FirestoreChatRepository
                // For now, we'll just update locally
                val currentMessages = _messages.value.toMutableList()
                val messageIndex = currentMessages.indexOfFirst { it.id == messageId }
                if (messageIndex != -1) {
                    val message = currentMessages[messageIndex]
                    val updatedReactions = message.reactions.toMutableMap().apply {
                        put(myUsername.value, emoji)
                    }
                    val updatedMessage = message.copy(reactions = updatedReactions)
                    currentMessages[messageIndex] = updatedMessage
                    _messages.value = currentMessages
                }
            } catch (e: Exception) {
                _error.value = "Failed to add reaction: ${e.message}"
            }
        }
    }

    fun addMemberToGroup(userId: String) {
        viewModelScope.launch {
            val groupId = _groupId.value
            if (groupId != null) {
                try {
                    firestoreRepo.addMemberToGroup(groupId, userId, myUsername.value)
                    // Refresh group info
                    loadGroup(groupId)
                } catch (e: Exception) {
                    _error.value = "Failed to add member: ${e.message}"
                }
            }
        }
    }

    fun removeMemberFromGroup(userId: String) {
        viewModelScope.launch {
            val groupId = _groupId.value
            if (groupId != null) {
                try {
                    firestoreRepo.removeMemberFromGroup(groupId, userId, myUsername.value)
                    // Refresh group info
                    loadGroup(groupId)
                } catch (e: Exception) {
                    _error.value = "Failed to remove member: ${e.message}"
                }
            }
        }
    }

    fun updateGroupName(newName: String) {
        viewModelScope.launch {
            val groupId = _groupId.value
            if (groupId != null) {
                try {
                    firestoreRepo.updateGroupName(groupId, newName)
                    // Refresh group info
                    loadGroup(groupId)
                } catch (e: Exception) {
                    _error.value = "Failed to update group name: ${e.message}"
                }
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}