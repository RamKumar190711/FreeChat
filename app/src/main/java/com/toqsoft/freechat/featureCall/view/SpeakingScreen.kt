package com.toqsoft.freechat.featureCall.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreNetwork.AgoraManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeakingScreen(
    navController: NavController,
    callId: String,
    callerId: String,
    receiverId: String,
    otherUserId: String,
    audioOnly: Boolean,
    onHangUp: () -> Unit,
    onAddUser: () -> Unit,
    users: List<String>,       // ✅ all users
    myUsername: String         // ✅ your username
) {
    var isMuted by remember { mutableStateOf(false) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var seconds by remember { mutableStateOf(0) }
    var callActive by remember { mutableStateOf(true) }
    var showSheet by remember { mutableStateOf(false) }


    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val addedUsers = remember { mutableStateListOf<String>() }
    val selectedUsers = remember { mutableStateListOf<String>() }

    val connectedUsers = addedUsers.filter { it != otherUserId }
    val notConnectedUsers = users.filter { it !in addedUsers && it != myUsername }
    LaunchedEffect(callActive) {
        while (callActive) {
            delay(1000)
            seconds++
        }
    }

    DisposableEffect(callId) {
        val chatId = listOf(callerId, receiverId).sorted().joinToString("_")

        val listener = FirebaseFirestore.getInstance()
            .collection("chats")
            .document(chatId)
            .collection("messages")
            .document(callId)
            .addSnapshotListener { snapshot, _ ->

                val participants =
                    snapshot?.get("participants") as? List<String> ?: emptyList()

                addedUsers.clear()
                addedUsers.addAll(participants.filter {
                    it != callerId && it != receiverId
                })
            }

        onDispose { listener.remove() }
    }


    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry) {
        val newUsers =
            navBackStackEntry
                ?.savedStateHandle
                ?.get<List<String>>("addedUsers")

        if (!newUsers.isNullOrEmpty()) {
            addedUsers.clear()
            addedUsers.addAll(newUsers)

            // important: clear after reading
            navBackStackEntry
                ?.savedStateHandle
                ?.remove<List<String>>("addedUsers")
        }
    }


    val minutes = seconds / 60
    val remainingSeconds = seconds % 60

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        IconButton(
            onClick = {
                if (addedUsers.isNotEmpty()) {
                    showSheet = true
                } else {
                    onAddUser()
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 20.dp)
                .size(48.dp)
                .background(Color.DarkGray, CircleShape)
        ) {
            Icon(
                painter = painterResource(
                    id = if (addedUsers.size > 0)
                        R.drawable.group_user   // 👈 your drawable
                    else
                        R.drawable.user_add     // 👈 your drawable
                ),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = otherUserId, color = Color.White, fontSize = 26.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format("%02d:%02d", minutes, remainingSeconds),
                color = Color.Gray,
                fontSize = 18.sp
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val newState = !isSpeakerOn
                    AgoraManager.rtcEngine?.setEnableSpeakerphone(newState)
                    isSpeakerOn = newState
                },
                modifier = Modifier.size(64.dp).background(Color.DarkGray, CircleShape)
            ) {
                Icon(
                    painter = if (isSpeakerOn) painterResource(id = R.drawable.speaker)
                    else painterResource(id = R.drawable.speaker_off),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    val newState = !isMuted
                    AgoraManager.rtcEngine?.muteLocalAudioStream(newState)
                    isMuted = newState
                },
                modifier = Modifier.size(64.dp).background(Color.DarkGray, CircleShape)
            ) {
                Icon(
                    painter = if (isMuted) painterResource(id = R.drawable.mic_off)
                    else painterResource(id = R.drawable.mic),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(
                onClick = {
                    endCall(callId, callerId, receiverId)
                    callActive = false
                    AgoraManager.leaveChannel()
                    onHangUp()
                },
                modifier = Modifier.size(64.dp).background(Color.Red, CircleShape)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.end),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (showSheet) {
            ModalBottomSheet(
                sheetState = sheetState,
                onDismissRequest = { showSheet = false },
                containerColor = Color(0xFF1C1C1E),
                contentColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .padding(bottom = 32.dp)
                ) {

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Added Users",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Add User Icon Button
                        IconButton(
                            onClick = {
                                // Navigate to your user list screen to pick more users
                                navController.navigate(
                                    "userListFromCall/$callId/$callerId/$receiverId"
                                )
                                showSheet = false
                            },
                            modifier = Modifier
                                .size(50.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.user_add),
                                contentDescription = "Add User",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (connectedUsers.isNotEmpty()) {
                        Text("Connected", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(connectedUsers) { user ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.LightGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(user.first().uppercaseChar().toString(), color = Color.Black)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(user, fontSize = 16.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (notConnectedUsers.isNotEmpty()) {
                        Text("Not connected", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(notConnectedUsers) { user ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        selectedUsers.add(user)
                                    }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.DarkGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(user.first().uppercaseChar().toString(), color = Color.White)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(user, fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { showSheet = false }) {
                        Text("Done")
                    }
                }
            }
        }


    }
}

private fun endCall(callId: String, callerId: String, receiverId: String) {
    FirebaseFirestore.getInstance()
        .collection("chats")
        .document(listOf(callerId, receiverId).sorted().joinToString("_"))
        .collection("messages")
        .document(callId)
        .update("status", "ended")
}