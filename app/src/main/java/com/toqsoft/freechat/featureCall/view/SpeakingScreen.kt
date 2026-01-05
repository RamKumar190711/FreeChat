package com.toqsoft.freechat.featureCall.view

import android.util.Log
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
import com.google.firebase.firestore.FieldValue
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
    users: List<String>,
    myUsername: String
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
    val allParticipants = remember { mutableStateListOf<String>() } // All users including myself
    val activeParticipants = remember { mutableStateListOf<String>() } // Only other active users
    val invitedUsers = remember { mutableStateListOf<String>() }
    val connectedUsers = remember { mutableStateListOf<String>() }

    // Track who to show in 1:1 mode
    val userToShowIn1on1 = remember {
        mutableStateOf(otherUserId)
    }

    // Debug log
    LaunchedEffect(allParticipants) {
        val active = allParticipants.filter { it != myUsername }
        activeParticipants.clear()
        activeParticipants.addAll(active)

        // FIX: Update who to show in 1:1 mode - use the first active participant
        if (activeParticipants.isNotEmpty()) {
            userToShowIn1on1.value = activeParticipants.first()
        } else {
            // If no other participants, still show the original other user
            // or handle as empty call
            userToShowIn1on1.value = otherUserId
        }
        println("DEBUG: Updated userToShowIn1on1 to: ${userToShowIn1on1.value}")
        println("DEBUG: Active participants: $activeParticipants")
        println("DEBUG: All participants: $allParticipants")
    }

    LaunchedEffect(callActive) {
        while (callActive) {
            delay(1000)
            seconds++
        }
    }

    // Firestore listener for call participants
    DisposableEffect(callId) {

        val chatId = listOf(callerId, receiverId).sorted().joinToString("_")

        val db = FirebaseFirestore.getInstance()
        val listener = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    println("DEBUG: Firestore error: $error")
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val firestoreParticipants = snapshot.get("participants") as? List<String> ?: emptyList()
                    val firestoreInvitations = snapshot.get("invitations") as? List<String> ?: emptyList()
                    val dbCallerId = snapshot.getString("callerId") ?: callerId
                    val dbReceiverId = snapshot.getString("receiverId") ?: receiverId
                    val callStatus = snapshot.getString("status") ?: ""

                    // If call is ended, navigate back
                    if (callStatus == "ended") {
                        callActive = false
                        AgoraManager.leaveChannel()
                        onHangUp()
                        return@addSnapshotListener
                    }

                    println("DEBUG Firestore Data for $myUsername:")
                    println("  - CallerId: $dbCallerId")
                    println("  - ReceiverId: $dbReceiverId")
                    println("  - Firestore participants: $firestoreParticipants")

                    // Build complete list of ALL users in this call
                    val allUsersInThisCall = mutableSetOf<String>()

                    // Always include original call participants
                    allUsersInThisCall.add(dbCallerId)
                    allUsersInThisCall.add(dbReceiverId)

                    // Add all participants from Firestore
                    allUsersInThisCall.addAll(firestoreParticipants)

                    // Remove empty strings
                    allUsersInThisCall.removeIf { it.isBlank() }

                    println("  - All users in call: $allUsersInThisCall")

                    // Update all participants list
                    allParticipants.clear()

                    // Add yourself first
                    allParticipants.add(myUsername)

                    // Add everyone else (except yourself)
                    allUsersInThisCall.forEach { user ->
                        if (user != myUsername && user.isNotBlank()) {
                            allParticipants.add(user)
                        }
                    }

                    // Remove duplicates
                    val uniqueParticipants = allParticipants.distinct().toMutableList()
                    allParticipants.clear()
                    allParticipants.addAll(uniqueParticipants)

                    // CRITICAL FIX: Update userToShowIn1on1 immediately when participants change
                    val active = allParticipants.filter { it != myUsername }
                    if (active.isNotEmpty()) {
                        userToShowIn1on1.value = active.first()
                    }

                    // Update invited users
                    invitedUsers.clear()
                    invitedUsers.addAll(firestoreInvitations.filter {
                        it != myUsername && it !in allParticipants && it.isNotBlank()
                    }.distinct())

                    // Update connected users for display in bottom sheet
                    connectedUsers.clear()
                    connectedUsers.addAll(allParticipants.filter {
                        it != myUsername && it.isNotBlank()
                    })

                } else {
                    println("DEBUG: No snapshot found for callId: $callId in chat: $chatId")
                }
            }

        onDispose {
            listener.remove()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()

    LaunchedEffect(navBackStackEntry) {
        val newUsers =
            navBackStackEntry
                ?.savedStateHandle
                ?.get<List<String>>("addedUsers")

        if (!newUsers.isNullOrEmpty()) {
            val chatId = listOf(callerId, receiverId).sorted().joinToString("_")

            // FIX: Pass the actual inviter (myUsername) not just the original caller
            sendCallInvitations(
                chatId = chatId,
                callId = callId,
                callerId = callerId,
                invitedUsers = newUsers,
                inviterId = myUsername // Pass who is actually inviting
            )

            addedUsers.addAll(newUsers.distinct())
            navBackStackEntry?.savedStateHandle?.remove<List<String>>("addedUsers")
        }
    }

    val minutes = seconds / 60
    val remainingSeconds = seconds % 60


    val shouldShowGroupUI = allParticipants.size > 2


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        IconButton(
            onClick = {
                if (addedUsers.isNotEmpty() || allParticipants.size > 2) {
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
                    id = if (allParticipants.size > 2)
                        R.drawable.group_user
                    else
                        R.drawable.user_add
                ),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        /* ---------------- GROUP CALL UI (Grid Layout) ---------------- */
        if (shouldShowGroupUI) {
            Log.d("DEBUG", "SHOWING GROUP UI FOR $myUsername (Total: ${allParticipants.size}, Active: ${activeParticipants.size})")

            // Top info section
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val otherUsers = allParticipants.filter { it != myUsername }

                Text(
                    text = if (otherUsers.size == 1) {
                        otherUsers.first()
                    } else if (otherUsers.size == 2) {
                        "${otherUsers[0]} & ${otherUsers[1]}"
                    } else {
                        "${otherUsers.firstOrNull() ?: "Group"} & ${otherUsers.size - 1} others"
                    },
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

            // SHOW THE GRID LAYOUT with all participants
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 120.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(allParticipants) { user ->
                    ParticipantTile(
                        username = user,
                        isMe = user == myUsername
                    )
                }
            }
        } else {
            Log.d("DEBUG", "SHOWING 1:1 UI FOR $myUsername (Total: ${allParticipants.size}, Active: ${activeParticipants.size})")

            // 1:1 CALL UI (Center Avatar)
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = userToShowIn1on1.value,
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

            // Center avatar
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(userColor(userToShowIn1on1.value)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userToShowIn1on1.value.first().uppercaseChar().toString(),
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom control buttons
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speaker button
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

            // Mic button
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

            // Hang up button using the helper function
            IconButton(
                onClick = {
                    // Call the helper function to handle user hangup properly
                    handleUserHangup(
                        callId = callId,
                        callerId = callerId,
                        receiverId = receiverId,
                        myUsername = myUsername,
                        onComplete = {
                            // This callback runs after user is removed from Firestore
                            println("DEBUG: User $myUsername successfully removed from call")
                            callActive = false
                            AgoraManager.leaveChannel()
                            onHangUp()
                        }
                    )
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

        // Bottom sheet for participants management
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
                            text = "Call Participants (${connectedUsers.size})",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        IconButton(
                            onClick = {
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

                    // Connected Users
                    if (connectedUsers.isNotEmpty()) {
                        Text("Connected", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(connectedUsers) { user ->
                                ConnectedUserItem(user = user)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Invited Users (waiting to join)
                    if (invitedUsers.isNotEmpty()) {
                        Text("Invited - Waiting to join", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(invitedUsers) { user ->
                                InvitedUserItem(user = user)
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
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

// Helper function to handle hangup for individual users
private fun handleUserHangup(
    callId: String,
    callerId: String,
    receiverId: String,
    myUsername: String,
    onComplete: () -> Unit
) {
    val chatId = listOf(callerId, receiverId).sorted().joinToString("_")
    val db = FirebaseFirestore.getInstance()

    println("DEBUG: Removing $myUsername from call participants...")

    // Remove user from participants
    db.collection("chats")
        .document(chatId)
        .collection("messages")
        .document(callId)
        .update("participants", FieldValue.arrayRemove(myUsername))
        .addOnSuccessListener {
            println("DEBUG: Successfully removed $myUsername from participants")

            // Check if any participants left
            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(callId)
                .get()
                .addOnSuccessListener { snapshot ->
                    val participants = snapshot.get("participants") as? List<String> ?: emptyList()
                    val originalCallerId = snapshot.getString("callerId") ?: callerId
                    val originalReceiverId = snapshot.getString("receiverId") ?: receiverId

                    println("DEBUG: Remaining participants: $participants")

                    // Also remove from original caller/receiver if needed
                    val updates = mutableMapOf<String, Any>()

                    // If the hanging user is the original caller, update callerId
                    if (myUsername == originalCallerId) {
                        // Set callerId to next available participant or empty
                        val newCallerId = participants.firstOrNull() ?: ""
                        updates["callerId"] = newCallerId
                        println("DEBUG: Updating callerId from $myUsername to $newCallerId")
                    }

                    // If the hanging user is the original receiver, update receiverId
                    if (myUsername == originalReceiverId) {
                        // Set receiverId to next available participant or empty
                        val newReceiverId = participants.getOrNull(1) ?: participants.firstOrNull() ?: ""
                        updates["receiverId"] = newReceiverId
                        println("DEBUG: Updating receiverId from $myUsername to $newReceiverId")
                    }

                    // Apply updates if needed
                    if (updates.isNotEmpty()) {
                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(callId)
                            .update(updates)
                    }

                    if (participants.isEmpty()) {
                        // Last user left, end the call completely
                        println("DEBUG: Last user left, ending call")
                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(callId)
                            .update("status", "ended")
                    }

                    // Call the completion callback
                    onComplete()
                }
        }
        .addOnFailureListener { e ->
            println("DEBUG: Failed to remove user from participants: $e")
            // Still complete the hangup process
            onComplete()
        }
}

fun sendCallInvitations(
    chatId: String,
    callId: String,
    callerId: String,
    invitedUsers: List<String>,
    inviterId: String
) {
    val db = FirebaseFirestore.getInstance()

    // Get the current call document
    db.collection("chats")
        .document(chatId)
        .collection("messages")
        .document(callId)
        .get()
        .addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val currentInvitations = snapshot.get("invitations") as? List<String> ?: emptyList()
                val currentParticipants = snapshot.get("participants") as? List<String> ?: emptyList()

                // Filter out users already invited or already in call
                val newInvitations = invitedUsers.filter {
                    it !in currentInvitations && it !in currentParticipants
                }

                if (newInvitations.isNotEmpty()) {
                    // Update invitations array in Firestore
                    db.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .document(callId)
                        .update(
                            "invitations",
                            com.google.firebase.firestore.FieldValue.arrayUnion(*newInvitations.toTypedArray())
                        )

                    // Send notification to each invited user WITH THE ACTUAL INVITER
                    newInvitations.forEach { invitedUser ->
                        sendCallInvitationNotification(
                            callId = callId,
                            callerId = callerId,
                            inviterId = inviterId, // Pass who actually invited
                            invitedUser = invitedUser,
                            chatId = chatId
                        )
                    }
                }
            }
        }
}

// Send push notification for call invitation
private fun sendCallInvitationNotification(
    callId: String,
    callerId: String,
    inviterId: String, // Add this parameter
    invitedUser: String,
    chatId: String
) {
    // Create a new notification document
    val notificationId = "$callId-$invitedUser"
    val notificationData = hashMapOf(
        "type" to "call_invitation",
        "callId" to callId,
        "callerId" to callerId,
        "inviterId" to inviterId, // Store who actually sent the invitation
        "invitedUser" to invitedUser,
        "chatId" to chatId,
        "timestamp" to System.currentTimeMillis(),
        "status" to "pending"
    )

    FirebaseFirestore.getInstance()
        .collection("notifications")
        .document(invitedUser)
        .collection("call_invitations")
        .document(notificationId)
        .set(notificationData)
}

// Connected user item
@Composable
fun ConnectedUserItem(user: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Green.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.audio),
                contentDescription = "Connected",
                tint = Color.Green,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(user, fontSize = 16.sp, color = Color.White)
    }
}

// Invited user item
@Composable
fun InvitedUserItem(user: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Yellow.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.group_user),
                contentDescription = "Invited",
                tint = Color.Yellow,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(user, fontSize = 16.sp, color = Color.LightGray)
    }
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