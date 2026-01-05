package com.toqsoft.freechat.featureCall.view

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.coreNetwork.AgoraManager
import kotlinx.coroutines.delay

@Composable
fun CallInvitationScreen(
    navController: NavController,
    callId: String,
    callerId: String,
    chatId: String,
    myUsername: String
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(true) }
    var callInfo by remember { mutableStateOf("Loading call info...") }
    var actualInviter by remember { mutableStateOf(callerId) } // This will store who actually invited
    var currentParticipants by remember { mutableStateOf<List<String>>(emptyList()) }
    var originalCallerId by remember { mutableStateOf("") }
    var originalReceiverId by remember { mutableStateOf("") }

    // Fetch call details including who sent the invitation
    LaunchedEffect(Unit) {
        FirebaseFirestore.getInstance()
            .collection("notifications")
            .document(myUsername)
            .collection("call_invitations")
            .document("$callId-$myUsername")
            .get()
            .addOnSuccessListener { notification ->
                // Try to get the actual inviter from notification
                val inviterFromNotification = notification.getString("inviterId") ?: callerId
                actualInviter = inviterFromNotification

                // Now fetch call details
                FirebaseFirestore.getInstance()
                    .collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(callId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val audioOnly = snapshot.getBoolean("audioOnly") ?: true
                        val status = snapshot.getString("status") ?: ""
                        val channel = snapshot.getString("channel") ?: ""
                        val participants = snapshot.get("participants") as? List<String> ?: emptyList()
                        val dbCallerId = snapshot.getString("callerId") ?: callerId
                        val dbReceiverId = snapshot.getString("receiverId") ?: ""

                        originalCallerId = dbCallerId
                        originalReceiverId = dbReceiverId

                        // Get ALL participants including original call participants
                        val allParticipants = mutableSetOf<String>()
                        allParticipants.add(dbCallerId)
                        allParticipants.add(dbReceiverId)
                        allParticipants.addAll(participants)
                        allParticipants.removeIf { it.isBlank() }

                        currentParticipants = allParticipants.toList()

                        if (status == "ended" || status == "missed") {
                            callInfo = "This call has ended"
                        } else {
                            val isGroupCall = allParticipants.size >= 2
                            callInfo = if (audioOnly) {
                                if (isGroupCall) "Group Audio Call (${allParticipants.size} people)" else "Audio Call Invitation"
                            } else {
                                if (isGroupCall) "Group Video Call (${allParticipants.size} people)" else "Video Call Invitation"
                            }
                        }
                    }
            }
            .addOnFailureListener {
                // If notification not found, use callerId as fallback
                actualInviter = callerId

                // Fetch call details anyway
                FirebaseFirestore.getInstance()
                    .collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(callId)
                    .get()
                    .addOnSuccessListener { snapshot ->
                        val audioOnly = snapshot.getBoolean("audioOnly") ?: true
                        val status = snapshot.getString("status") ?: ""
                        val channel = snapshot.getString("channel") ?: ""
                        val participants = snapshot.get("participants") as? List<String> ?: emptyList()
                        val dbCallerId = snapshot.getString("callerId") ?: callerId
                        val dbReceiverId = snapshot.getString("receiverId") ?: ""

                        originalCallerId = dbCallerId
                        originalReceiverId = dbReceiverId

                        val allParticipants = mutableSetOf<String>()
                        allParticipants.add(dbCallerId)
                        allParticipants.add(dbReceiverId)
                        allParticipants.addAll(participants)
                        allParticipants.removeIf { it.isBlank() }

                        currentParticipants = allParticipants.toList()

                        if (status == "ended" || status == "missed") {
                            callInfo = "This call has ended"
                        } else {
                            val isGroupCall = allParticipants.size >= 2
                            callInfo = if (audioOnly) {
                                if (isGroupCall) "Group Audio Call (${allParticipants.size} people)" else "Audio Call Invitation"
                            } else {
                                if (isGroupCall) "Group Video Call (${allParticipants.size} people)" else "Video Call Invitation"
                            }
                        }
                    }
            }
    }

    if (showDialog) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { /* Prevent dismiss on background click */ }
        ) {
            Card(
                modifier = Modifier
                    .width(300.dp)
                    .align(Alignment.Center),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📞 Call Invitation",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D88FF)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // FIX: Show actual inviter, not original caller
                    Text(
                        text = "$actualInviter",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "invited you to join a call",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = callInfo,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    // Show current participants count
                    if (currentParticipants.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Already in call: ${currentParticipants.size} people",
                            fontSize = 12.sp,
                            color = Color.LightGray
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = {
                                declineCallInvitation(callId, myUsername, chatId)
                                showDialog = false
                                navController.popBackStack()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Red
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Decline")
                        }

                        Button(
                            onClick = {
                                // IMPORTANT: Pass original caller and receiver IDs
                                acceptCallInvitation(
                                    callId = callId,
                                    invitedUser = myUsername,
                                    chatId = chatId,
                                    navController = navController,
                                    originalCallerId = originalCallerId,
                                    originalReceiverId = originalReceiverId,
                                    context = context,
                                    isGroupCall = currentParticipants.size >= 2
                                )
                                showDialog = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Join Call")
                        }
                    }
                }
            }
        }
    }
}

// FIXED FUNCTION: Use correct parameters
private fun acceptCallInvitation(
    callId: String,
    invitedUser: String,
    chatId: String,
    navController: NavController,
    originalCallerId: String,
    originalReceiverId: String,
    context: Context,
    isGroupCall: Boolean
) {
    val db = FirebaseFirestore.getInstance()

    // 1. First get the current call document
    db.collection("chats")
        .document(chatId)
        .collection("messages")
        .document(callId)
        .get()
        .addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val currentParticipants = snapshot.get("participants") as? List<String> ?: emptyList()
                val channel = snapshot.getString("channel") ?: ""
                val audioOnly = snapshot.getBoolean("audioOnly") ?: true

                // Create updated participants list
                val updatedParticipants = mutableListOf<String>()
                updatedParticipants.addAll(currentParticipants)

                // Add original call participants if not already there
                if (originalCallerId !in updatedParticipants) {
                    updatedParticipants.add(originalCallerId)
                }
                if (originalReceiverId.isNotBlank() && originalReceiverId !in updatedParticipants) {
                    updatedParticipants.add(originalReceiverId)
                }

                // Add the invited user
                if (invitedUser !in updatedParticipants) {
                    updatedParticipants.add(invitedUser)
                }

                // 2. Update Firestore with ALL participants
                db.collection("chats")
                    .document(chatId)
                    .collection("messages")
                    .document(callId)
                    .update("participants", updatedParticipants.distinct())
                    .addOnSuccessListener {
                        // 3. Remove from invitations
                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(callId)
                            .update(
                                "invitations",
                                com.google.firebase.firestore.FieldValue.arrayRemove(invitedUser)
                            )

                        // 4. Clear notification
                        db.collection("notifications")
                            .document(invitedUser)
                            .collection("call_invitations")
                            .document("$callId-$invitedUser")
                            .delete()

                        // 5. Initialize Agora and join
                        AgoraManager.init(context)
                        val token = "" // Your token

                        AgoraManager.joinChannel(
                            context = context,
                            token = token,
                            channelName = channel,
                            userId = invitedUser
                        )

                        // 6. CRITICAL FIX: Navigate with CORRECT parameters
                        val totalParticipants = updatedParticipants.distinct().size
                        val shouldShowGroupRoute = totalParticipants >= 3 || isGroupCall

                        val targetRoute = if (shouldShowGroupRoute) {
                            // Use ORIGINAL caller and receiver IDs, not the invited user
                            "groupCall/$callId/$originalCallerId/$originalReceiverId/$audioOnly/$originalCallerId"
                        } else {
                            "speak/$callId/$originalCallerId/$invitedUser/$audioOnly/$originalCallerId"
                        }

                        println("DEBUG: CORRECT Navigation to $targetRoute")
                        println("DEBUG: Original caller: $originalCallerId, receiver: $originalReceiverId")
                        println("DEBUG: Invited user: $invitedUser")
                        println("DEBUG: Total participants: $totalParticipants")

                        navController.navigate(targetRoute) {
                            popUpTo("callInvitation") { inclusive = true }
                        }
                    }
            }
        }
}

private fun declineCallInvitation(
    callId: String,
    invitedUser: String,
    chatId: String
) {
    val db = FirebaseFirestore.getInstance()

    db.collection("chats")
        .document(chatId)
        .collection("messages")
        .document(callId)
        .update(
            "invitations",
            com.google.firebase.firestore.FieldValue.arrayRemove(invitedUser)
        )

    db.collection("notifications")
        .document(invitedUser)
        .collection("call_invitations")
        .document("$callId-$invitedUser")
        .delete()
}