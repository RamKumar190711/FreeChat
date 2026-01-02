package com.toqsoft.freechat.featureCall.view

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
    val participants = remember { mutableStateListOf<String>() }

    val connectedUsers = participants.filter { it != otherUserId && it != myUsername }
    val notConnectedUsers = users.filter { it !in participants && it != myUsername }

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
                val firestoreParticipants = snapshot?.get("participants") as? List<String> ?: emptyList()

                // Always rebuild participants including yourself and other user
                participants.clear()
                participants.add(myUsername) // your device
                participants.add(otherUserId) // the person you are talking to
                participants.addAll(firestoreParticipants.filter { it != myUsername && it != otherUserId }.distinct())
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
            val chatId = listOf(callerId, receiverId)
                .sorted()
                .joinToString("_")

            FirebaseFirestore.getInstance()
                .collection("chats")
                .document(chatId)
                .collection("messages")
                .document(callId)
                .update(
                    "participants",
                    com.google.firebase.firestore.FieldValue.arrayUnion(*newUsers.toTypedArray())
                )

            addedUsers.addAll(newUsers.distinct())
            navBackStackEntry?.savedStateHandle?.remove<List<String>>("addedUsers")
        }




    }

//    LaunchedEffect(addedUsers) {
//        participants.clear()
//        participants.add(myUsername)    // ✅ ADD YOURSELF FIRST
//        participants.add(otherUserId)
//        participants.addAll(addedUsers)
//    }

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
                    id = if (participants.size > 2)
                        R.drawable.group_user   // 👈 your drawable
                    else
                        R.drawable.user_add     // 👈 your drawable
                ),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )

        }

        /* ---------------- GROUP CALL (3+) ---------------- */
        if (participants.size > 2) {

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "$otherUserId & ${participants.size - 1} others",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format("%02d:%02d", minutes, remainingSeconds),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 120.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(participants) { user ->
                    ParticipantTile(
                        username = user,
                        isMe = user == myUsername
                    )
                }
            }

        }
        else {

            // 🔹 TOP NAME + TIMER (UNCHANGED)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = otherUserId,
                    color = Color.White,
                    fontSize = 26.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = String.format("%02d:%02d", minutes, remainingSeconds),
                    color = Color.Gray,
                    fontSize = 18.sp
                )
            }

            // 🔥 CENTER AVATAR ONLY
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(userColor(otherUserId)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = otherUserId.first().uppercaseChar().toString(),
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
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

@Composable
fun ParticipantTile(
    username: String,
    isMe: Boolean
) {
    val color = userColor(username)

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E1E))
            .border(2.dp, color, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.first().uppercaseChar().toString(),
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isMe) "You" else username,
                color = if (isMe) Color.White else color,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}


fun userColor(username: String): Color {
    val colors = listOf(
        Color(0xFFEF5350), // Red
        Color(0xFFAB47BC), // Purple
        Color(0xFF5C6BC0), // Indigo
        Color(0xFF29B6F6), // Blue
        Color(0xFF26A69A), // Teal
        Color(0xFF66BB6A), // Green
        Color(0xFFFFCA28), // Amber
        Color(0xFFFF7043)  // Orange
    )
    return colors[kotlin.math.abs(username.hashCode()) % colors.size]
}
fun updateParticipants(
    participants: MutableList<String>,
    myUsername: String,
    otherUserId: String,
    addedUsers: List<String>
) {
    participants.clear()
    participants.add(myUsername)   // yourself
    participants.add(otherUserId)  // the other person
    participants.addAll(addedUsers.distinct()) // added users, no duplicates
}
