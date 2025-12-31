package com.toqsoft.freechat.featureList.view

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreNetwork.VoiceFeedback
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import com.toqsoft.freechat.featureList.viewModel.UserListViewModel
import com.toqsoft.freechat.featureVoiceListening.VoiceRecognitionHelper
import com.toqsoft.freechat.featureVoiceListening.view.VoiceListeningDialog
import okhttp3.*



@Composable
fun UserListScreen(
    onOpenChat: (otherUserId: String) -> Unit,
    navController: NavController,
    viewModel: UserListViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel(),
    isGroupSelect: Boolean = false,   // ✅ NEW
    callId: String? = null,
    // ✅ ADD THESE (safe defaults)
    callerId: String = "",
    receiverId: String = ""
) {
    val context = LocalContext.current
    val users by viewModel.users.collectAsState()
    val myUsername by viewModel.myUsername.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()
    val lastMessages by viewModel.lastMessages.collectAsState()
    val liveText = remember { mutableStateOf("") }

    var inputName by remember { mutableStateOf(myUsername) }
    LaunchedEffect(myUsername) { inputName = myUsername }

    var showAddUserDialog by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }
    var showListening by remember { mutableStateOf(false) }

    var isSaved by remember { mutableStateOf(false) }

    // ✅ NEW — selected users for group call
    val selectedUsers = remember { mutableStateListOf<String>() }

    // ------------------ EXISTING PERMISSION & VOICE CODE (UNCHANGED) ------------------

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showListening = true
    }

    val voiceFeedback = remember { VoiceFeedback(context) }

    DisposableEffect(Unit) {
        onDispose { voiceFeedback.release() }
    }

    // ------------------ UI ------------------

    Scaffold(
        floatingActionButton = {
            Box(modifier = Modifier.fillMaxSize()) {

                // FAB (unchanged)
                FloatingActionButton(
                    onClick = { showAddUserDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add User")
                }

                // AI button (unchanged)
                AIAssistantButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(y = -100.dp)
                        .padding(16.dp),
                    onClick = {
                        when {
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED -> showListening = true
                            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )


                if (showListening) {
                    val voiceHelper = remember(context) {
                        VoiceRecognitionHelper(
                            context = context,
                            onPartialResult = { liveText.value = it },
                            onFinalResult = { final ->
                                liveText.value = final
                                Log.d("SPEECH", "You said: $final")

                                val cmd = final.lowercase().trim()
                                val myName = myUsername.ifEmpty { "friend" }

                                // ✅ Only trigger ONE feedback per input
                                val spokenHandled = when {
                                    // OPEN CHAT
                                    cmd.startsWith("open chat") -> {
                                        val parts = cmd.split(" ")
                                        if (parts.size >= 3) {
                                            onOpenChat(parts.drop(2).joinToString(" "))
                                            true // handled successfully
                                        } else {
                                            voiceFeedback.speak(
                                                "Hey $myName. Wrong command. Please say: open chat followed by username."
                                            )
                                            true
                                        }
                                    }

                                    // AUDIO CALL
                                    cmd.startsWith("audio call") -> {
                                        val parts = cmd.split(" ")
                                        if (parts.size >= 3) {
                                            val user = parts.drop(2).joinToString(" ")
                                            val callId = chatViewModel.startCall(
                                                chatViewModel.myUserId,
                                                user,
                                                true,
                                                navController
                                            )
                                            navController.navigate(
                                                "calling/$callId/${chatViewModel.myUserId}/$user/true"
                                            )
                                            true
                                        } else {
                                            voiceFeedback.speak(
                                                "Hey $myName. Wrong command. Please say: audio call followed by username."
                                            )
                                            true
                                        }
                                    }

                                    // VIDEO CALL
                                    cmd.startsWith("video call") -> {
                                        val parts = cmd.split(" ")
                                        if (parts.size >= 3) {
                                            val user = parts.drop(2).joinToString(" ")
                                            val callId = chatViewModel.startCall(
                                                chatViewModel.myUserId,
                                                user,
                                                false,
                                                navController
                                            )
                                            navController.navigate(
                                                "calling/$callId/${chatViewModel.myUserId}/$user/false"
                                            )
                                            true
                                        } else {
                                            voiceFeedback.speak(
                                                "Hey $myName. Wrong command. Please say: video call followed by username."
                                            )
                                            true
                                        }
                                    }

                                    // ANY OTHER INVALID COMMAND
                                    else -> {
                                        voiceFeedback.speak(
                                            "Hey $myName. Wrong command. You can say: open chat followed by username, audio call followed by username, or video call followed by username."
                                        )
                                        true
                                    }
                                }

                                showListening = false
                            }
                        )
                    }

                    DisposableEffect(Unit) {
                        voiceHelper.startListening()
                        onDispose { voiceHelper.destroy() }
                    }

                    VoiceListeningDialog(
                        onDismiss = { showListening = false; voiceHelper.stopListening() },
                        liveText = liveText.value
                    )
                }
            }
        },

        // ✅ NEW — bottom button ONLY for group select
        bottomBar = {
            if (isGroupSelect && selectedUsers.isNotEmpty()) {
                Button(
                    onClick = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                "addedUsers",
                                ArrayList(selectedUsers) // 🔥 FIX
                            )
                        val chatId = listOf(callerId, receiverId)
                            .sorted()
                            .joinToString("_")

                        callId?.let {
                            chatViewModel.addUsersToCall(
                                chatId = chatId,
                                callId = callId,
                                users = selectedUsers
                            )
                        }

                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Add ${selectedUsers.size} users to call")
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {

            // ------------------ EXISTING HEADER UI (UNCHANGED) ------------------

            Text("Your name:")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = inputName,
                onValueChange = { inputName = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isSaved
            )

            Spacer(Modifier.height(8.dp))

            if (myUsername.isEmpty()) {
                Button(onClick = {
                    val nameTrimmed = inputName.trim()
                    if (nameTrimmed.isNotBlank()) {
                        viewModel.announceSelf(nameTrimmed)
                        isSaved = true
                    }
                }) { Text("Save & Announce") }

                Spacer(Modifier.height(16.dp))
            }
            SelectedUsersRow(selectedUsers = selectedUsers)


            Text("Users:")
            Spacer(Modifier.height(8.dp))

            // ------------------ USER LIST (MINIMAL CHANGE HERE) ------------------

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(users.filter { it != myUsername }) { user ->

                    val unread = unreadCounts[user] ?: 0
                    val lastMsg = lastMessages[user] ?: ""
                    val isSelected = selectedUsers.contains(user)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isGroupSelect) {
                                    if (isSelected) selectedUsers.remove(user)
                                    else selectedUsers.add(user)
                                } else {
                                    onOpenChat(user)
                                    viewModel.clearUnreadCount(user)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected)
                                Color(0xFFE3F2FD)
                            else
                                MaterialTheme.colorScheme.surface
                        )
                    )
                    {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        )
                        {

                            // ✅ Checkbox (group select only)
                            if (isGroupSelect) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = {
                                        if (it) selectedUsers.add(user)
                                        else selectedUsers.remove(user)
                                    }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // ✅ Profile color based on username (light colors)
                            val profileBgColor = remember(user) {
                                val colors = listOf(
                                    Color(0xFFFFCDD2),
                                    Color(0xFFC8E6C9),
                                    Color(0xFFBBDEFB),
                                    Color(0xFFFFF9C4),
                                    Color(0xFFD1C4E9),
                                    Color(0xFFFFE0B2)
                                )
                                colors[kotlin.math.abs(user.hashCode()) % colors.size]
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(
                                        color = profileBgColor,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = user.first().uppercaseChar().toString(),
                                    color = Color(0xFF212121), // dark text
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }


                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = user,
                                    style = MaterialTheme.typography.bodyLarge
                                )

                                if (!isGroupSelect && lastMsg.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        lastMsg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                            }

                            // ✅ Unread badge (unchanged)
                            if (!isGroupSelect && unread > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0xFF0D88FF),
                                            RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        unread.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }

                        Divider(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 72.dp), // aligns after avatar
                            thickness = 0.5.dp,
                            color = Color(0xFFE0E0E0)
                        )

                    }
                }
            }
        }
    }

    // ------------------ EXISTING ADD USER DIALOG (UNCHANGED) ------------------

    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Add User") },
            text = {
                OutlinedTextField(
                    value = newUserName,
                    onValueChange = { newUserName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newUserName.trim()
                    if (name.isNotBlank() && name != myUsername) {
                        viewModel.addDiscoveredUser(name)
                    }
                    newUserName = ""
                    showAddUserDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newUserName = ""
                    showAddUserDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SelectedUsersRow(selectedUsers: List<String>) {
    if (selectedUsers.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        selectedUsers.forEach { user ->
            // Light colored circle with first letter
            val profileBgColor = remember(user) {
                val colors = listOf(
                    Color(0xFFFFCDD2),
                    Color(0xFFC8E6C9),
                    Color(0xFFBBDEFB),
                    Color(0xFFFFF9C4),
                    Color(0xFFD1C4E9),
                    Color(0xFFFFE0B2)
                )
                colors[kotlin.math.abs(user.hashCode()) % colors.size]
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(profileBgColor, shape = CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.first().uppercaseChar().toString(),
                    color = Color(0xFF212121),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun AIAssistantButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .scale(pulse)
            .background(
                brush = Brush.verticalGradient(listOf(Color(0xFF0D88FF), Color(0xFF42A5F5))),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.voice_ai),
            contentDescription = "AI Assistant",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}