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
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import com.toqsoft.freechat.featureList.viewModel.UserListViewModel
import com.toqsoft.freechat.featureVoiceListening.VoiceRecognitionHelper
import com.toqsoft.freechat.featureVoiceListening.view.VoiceListeningDialog
import okhttp3.*


@Composable
fun UserListScreen(
    onOpenChat: (otherUserId: String) -> Unit,
    viewModel: UserListViewModel = hiltViewModel(),
    navController: NavController,
    chatViewModel: ChatViewModel = hiltViewModel()

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


    // Request microphone permission
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showListening = true
        } else {
            Log.d("Voice", "Mic permission denied")
        }
    }

    Scaffold(
        floatingActionButton = {
            Box(modifier = Modifier.fillMaxSize()) {
                // Add User FAB
                FloatingActionButton(
                    onClick = { showAddUserDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add User")
                }

                // AI Voice Assistant Button above FAB
                AIAssistantButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(y = -100.dp)
                        .padding(16.dp),
                    onClick = {
                        when {
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED -> {
                                showListening = true
                            }
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
                                when {
                                    cmd.startsWith("open chat") -> {
                                        val parts = cmd.split(" ")
                                        if (parts.size >= 3) onOpenChat(parts.drop(2).joinToString(" "))
                                    }
                                    cmd.startsWith("audio call") -> {
                                        val parts = cmd.split(" ")
                                        if (parts.size >= 3) {
                                            val user = parts.drop(2).joinToString(" ")
                                            val callId = chatViewModel.startCall(chatViewModel.myUserId, user, true, navController)
                                            navController.navigate("calling/$callId/${chatViewModel.myUserId}/$user/true")
                                        }
                                    }
                                    cmd.startsWith("video call") -> {
                                        val parts = cmd.split(" ")
                                        if (parts.size >= 3) {
                                            val user = parts.drop(2).joinToString(" ")
                                            val callId = chatViewModel.startCall(chatViewModel.myUserId, user, false, navController)
                                            navController.navigate("calling/$callId/${chatViewModel.myUserId}/$user/false")
                                        }
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
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Text("Your name:")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = inputName,
                onValueChange = { inputName = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            if (myUsername.isEmpty()) {
                Button(onClick = {
                    val nameTrimmed = inputName.trim()
                    if (nameTrimmed.isNotBlank()) viewModel.announceSelf(nameTrimmed)
                }) { Text("Save & Announce") }

                Spacer(Modifier.height(16.dp))
            }

            Text("Users:")
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(users.filter { it != myUsername }) { user ->
                    val unread = unreadCounts[user] ?: 0
                    val lastMsg = lastMessages[user] ?: ""

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenChat(user)
                                viewModel.clearUnreadCount(user)
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user, style = MaterialTheme.typography.bodyLarge)
                                if (lastMsg.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        lastMsg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (unread > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0xFF0D88FF),
                                            shape = RoundedCornerShape(20.dp)
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
                    }
                }
            }
        }
    }

    // Add User Dialog
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