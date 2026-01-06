package com.toqsoft.freechat.featureCall.view

import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path as ComposePath
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
import com.toqsoft.freechat.coreNetwork.SpeakingIndicatorManager
import kotlinx.coroutines.delay
import okhttp3.internal.platform.android.AndroidLogHandler.close
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.moveTo
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

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
    var seconds by remember { mutableIntStateOf(0) }
    var callActive by remember { mutableStateOf(true) }
    var showSheet by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val addedUsers = remember { mutableStateListOf<String>() }
    val allParticipants = remember { mutableStateListOf<String>() }
    val activeParticipants = remember { mutableStateListOf<String>() }
    val invitedUsers = remember { mutableStateListOf<String>() }
    val connectedUsers = remember { mutableStateListOf<String>() }

    // Track who to show in 1:1 mode
    val userToShowIn1on1 = remember {
        mutableStateOf(otherUserId)
    }

    // Track speaking users
    var speakingUsers by remember { mutableStateOf(emptySet<String>()) }

    // Track volume levels
    var volumeLevels by remember { mutableStateOf(emptyMap<String, Int>()) }

    // Set up callbacks
    LaunchedEffect(Unit) {
        SpeakingIndicatorManager.onSpeakingStateChanged = { speakingState ->
            val newSpeakingUsers = speakingState.filter { it.value }.keys
            Log.d("UI_CALLBACK", "Speaking callback: $newSpeakingUsers")
            speakingUsers = newSpeakingUsers
        }

        SpeakingIndicatorManager.onVolumeLevelsChanged = { levels ->
            Log.d("UI_CALLBACK", "Volume callback: $levels")
            volumeLevels = levels
        }
    }

    LaunchedEffect(Unit) {
        SpeakingIndicatorManager.initialize(callId, myUsername)

        // Register local user
        val localUid = AgoraManager.localUid
        SpeakingIndicatorManager.registerUser(localUid, myUsername)
        Log.d("SPEAKING_SCREEN", "Registered local user: $myUsername with UID=$localUid")

        // Register other users immediately
        allParticipants.forEach { user ->
            if (user != myUsername) {
                val userUid = (user.hashCode() and 0x7FFFFFFF)
                SpeakingIndicatorManager.registerUser(userUid, user)
                Log.d("SPEAKING_SCREEN", "Registered user: $user with UID=$userUid")
            }
        }

        // Enable audio volume indication
        AgoraManager.rtcEngine?.enableAudioVolumeIndication(
            200,
            3,
            true  // reportVad
        )

        // Connect to Agora volume callback
        AgoraManager.setOnAudioVolumeCallback { speakers, totalVolume ->
            Log.d("AGORA_VOLUME", "Received volume: speakers=${speakers?.size}, total=$totalVolume")

            // Process volume through SpeakingIndicatorManager
            SpeakingIndicatorManager.processAudioVolume(
                speakers = speakers,
                totalVolume = totalVolume,
                localUsername = myUsername,
                isMuted = isMuted
            )

            // Debug log
            speakers?.forEach { speaker ->
                val username = SpeakingIndicatorManager.getUsernameFromUid(speaker.uid)
                Log.d("VOLUME_CALLBACK", "UID ${speaker.uid} ($username): volume=${speaker.volume}")
            }
        }
    }

    // Register new users when participants change
    LaunchedEffect(allParticipants) {
        // Register all participants
        allParticipants.forEach { user ->
            if (user != myUsername) {
                // Use the same UID generation logic as AgoraManager
                val userUid = (user.hashCode() and 0x7FFFFFFF)
                SpeakingIndicatorManager.registerUser(userUid, user)
                Log.d("SPEAKING_SCREEN", "Registered user: $user with UID=$userUid")
            }
        }
    }

    // Cleanup when leaving
    DisposableEffect(Unit) {
        onDispose {
            SpeakingIndicatorManager.clearAll()
        }
    }

    // Debug log
    LaunchedEffect(allParticipants) {
        val active = allParticipants.filter { it != myUsername }
        activeParticipants.clear()
        activeParticipants.addAll(active)

        userToShowIn1on1.value = when {
            activeParticipants.isNotEmpty() -> activeParticipants.first()
            else -> otherUserId
        }

        println("DEBUG: Updated userToShowIn1on1 to: ${userToShowIn1on1.value}")
        println("DEBUG: Active participants: $activeParticipants")
        println("DEBUG: All participants: $allParticipants")
        println("DEBUG: Speaking users: $speakingUsers")
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
                    val firestoreParticipants = snapshot.get("participants") as? List<*> ?: emptyList<String>()
                    val firestoreInvitations = snapshot.get("invitations") as? List<*> ?: emptyList<String>()
                    val dbCallerId = snapshot.getString("callerId") ?: callerId
                    val dbReceiverId = snapshot.getString("receiverId") ?: receiverId
                    val callStatus = snapshot.getString("status") ?: ""

                    val stringParticipants = firestoreParticipants.filterIsInstance<String>()
                    val stringInvitations = firestoreInvitations.filterIsInstance<String>()

                    if (callStatus == "ended") {
                        callActive = false
                        AgoraManager.leaveChannel()
                        onHangUp()
                        return@addSnapshotListener
                    }

                    println("DEBUG Firestore Data for $myUsername:")
                    println("  - CallerId: $dbCallerId")
                    println("  - ReceiverId: $dbReceiverId")
                    println("  - Firestore participants: $stringParticipants")

                    val allUsersInThisCall = mutableSetOf<String>()
                    allUsersInThisCall.add(dbCallerId)
                    allUsersInThisCall.add(dbReceiverId)
                    allUsersInThisCall.addAll(stringParticipants)
                    allUsersInThisCall.removeIf { it.isBlank() }

                    println("  - All users in call: $allUsersInThisCall")

                    allParticipants.clear()
                    allParticipants.add(myUsername)

                    allUsersInThisCall.forEach { user ->
                        if (user != myUsername && user.isNotBlank()) {
                            allParticipants.add(user)
                        }
                    }

                    val uniqueParticipants = allParticipants.distinct().toMutableList()
                    allParticipants.clear()
                    allParticipants.addAll(uniqueParticipants)

                    val active = allParticipants.filter { it != myUsername }
                    if (active.isNotEmpty()) {
                        userToShowIn1on1.value = active.first()
                    }

                    invitedUsers.clear()
                    invitedUsers.addAll(stringInvitations.filter {
                        it != myUsername && it !in allParticipants && it.isNotBlank()
                    }.distinct())

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

            sendCallInvitations(
                chatId = chatId,
                callId = callId,
                callerId = callerId,
                invitedUsers = newUsers,
                inviterId = myUsername
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
            Log.d("DEBUG", "SHOWING GROUP UI FOR $myUsername (Total: ${allParticipants.size}, Speaking: ${speakingUsers.size})")

            // Top info section
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val otherUsers = allParticipants.filter { it != myUsername }

                Text(
                    text = when {
                        otherUsers.size == 1 -> otherUsers.first()
                        otherUsers.size == 2 -> "${otherUsers[0]} & ${otherUsers[1]}"
                        else -> "${otherUsers.firstOrNull() ?: "Group"} & ${otherUsers.size - 1} others"
                    },
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds),
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
                    ParticipantTileWithSpeakingIndicator(
                        username = user,
                        isMe = user == myUsername,
                        isSpeaking = speakingUsers.contains(user),
                        volumeLevel = volumeLevels[user] ?: 0
                    )
                }
            }
        } else {
            Log.d("DEBUG", "SHOWING 1:1 UI FOR $myUsername (Total: ${allParticipants.size})")

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
                    text = String.format(Locale.getDefault(), "%02d:%02d", minutes, remainingSeconds),
                    color = Color.Gray,
                    fontSize = 18.sp
                )
            }

            // Center avatar with speaking indicator
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val isOtherSpeaking = speakingUsers.contains(userToShowIn1on1.value)

                if (isOtherSpeaking) {
                    // WhatsApp-style pulsing ring
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val ringScale by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "ringScale"
                    )

                    Box(
                        modifier = Modifier
                            .size((130 * ringScale).dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .drawWithContent {
                                drawContent()
                                drawCircle(
                                    color = Color.Green.copy(alpha = 0.3f),
                                    radius = size.minDimension / 2,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(CircleShape)
                        .background(userColor(userToShowIn1on1.value))
                        .then(
                            if (isOtherSpeaking) {
                                Modifier.border(
                                    width = 3.dp,
                                    color = Color.Green,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val avatarLetter =
                        userToShowIn1on1.value
                            .firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "?"

                    Text(
                        text = avatarLetter,
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold
                    )


                    // Small mic indicator in corner
                    if (isOtherSpeaking) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(Color.Green),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.mic),
                                contentDescription = "Speaking",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
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

            // Hang up button
            IconButton(
                onClick = {
                    handleUserHangup(
                        callId = callId,
                        callerId = callerId,
                        receiverId = receiverId,
                        myUsername = myUsername,
                        onComplete = {
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

                    // Connected Users with speaking indicators
                    if (connectedUsers.isNotEmpty()) {
                        Text("Connected", fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(connectedUsers) { user ->
                                ConnectedUserItem(
                                    user = user,
                                    isSpeaking = speakingUsers.contains(user)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Invited Users
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



@Composable
fun ParticipantTileWithSpeakingIndicator(
    username: String,
    isMe: Boolean,
    isSpeaking: Boolean = false,
    volumeLevel: Int = 0
) {
    val userColor = userColor(username)
    val speakingColor = userColor

    // Animation values
    val infiniteTransition = rememberInfiniteTransition(label = "speakingAnimations")

    // Choose animation style based on user hash (to get different styles per user)
    val animationStyle = remember(username) {
        // This will give consistent but different animation for each user
        when (abs(username.hashCode()) % 6) {
            0 -> "PULSING_RINGS"
            1 -> "RADIAL_LINES"
            2 -> "PARTICLE_ORBIT"
            3 -> "WAVY_BORDER"
            4 -> "GLOW_PULSE"
            else -> "GEOMETRIC_EXPANSION"
        }
    }

    // Common animation values
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing)
        ),
        label = "rotation"
    )

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        // Modern speaking animation background
        if (isSpeaking) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.Center)
            ) {
                val centerX = size.width / 2
                val centerY = size.height / 2
                val maxRadius = minOf(centerX, centerY) * 0.9f

                when (animationStyle) {
                    "PULSING_RINGS" -> {
                        // Multiple concentric rings pulsing outward
                        for (i in 0..2) {
                            val ringRadius = maxRadius * 0.3f + (maxRadius * 0.6f * pulse) - (i * 40)
                            val alpha = 0.3f - (i * 0.1f)

                            drawCircle(
                                color = speakingColor.copy(alpha = alpha * (1 - pulse)),
                                radius = ringRadius.coerceAtLeast(0f),
                                style = Stroke(
                                    width = 3.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }

                    "RADIAL_LINES" -> {
                        // Rotating radial lines
                        val lineCount = 12
                        val lineLength = maxRadius * 0.8f

                        for (i in 0 until lineCount) {
                            val angle = (i * (360f / lineCount) + rotation) * (Math.PI / 180).toFloat()
                            val endX = centerX + lineLength * cos(angle)
                            val endY = centerY + lineLength * sin(angle)

                            val linePulse = (pulse + (i * 0.1f)) % 1f

                            drawLine(
                                color = speakingColor.copy(alpha = 0.7f * linePulse),
                                start = Offset(centerX, centerY),
                                end = Offset(endX, endY),
                                strokeWidth = 2.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    "PARTICLE_ORBIT" -> {
                        // Orbiting particles
                        val orbitCount = 8
                        val orbitRadius = maxRadius * 0.7f

                        for (i in 0 until orbitCount) {
                            val angle = (i * (360f / orbitCount) + rotation * 2) * (Math.PI / 180).toFloat()
                            val particleX = centerX + orbitRadius * cos(angle)
                            val particleY = centerY + orbitRadius * sin(angle)
                            val particleSize = 8.dp.toPx() * pulse

                            drawCircle(
                                color = speakingColor,
                                center = Offset(particleX, particleY),
                                radius = particleSize
                            )
                        }
                    }

                    "WAVY_BORDER" -> {
                        // Animated wavy border
                        val points = 30
                        val amplitude = 15f * pulse
                        val path = ComposePath().apply {
                            for (i in 0..points) {
                                val angle = (i * (360f / points) + rotation) * (Math.PI / 180).toFloat()
                                val waveRadius = maxRadius + amplitude * sin(angle * 3 + rotation * 2)
                                val x = centerX + waveRadius * cos(angle)
                                val y = centerY + waveRadius * sin(angle)

                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }

                        drawPath(
                            path = path,
                            color = speakingColor.copy(alpha = 0.4f),
                            style = Stroke(
                                width = 3.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }

                    "GLOW_PULSE" -> {
                        // Glowing aura effect
                        val glowRadius = maxRadius * (0.8f + pulse * 0.4f)

                        // Draw multiple layers for glow effect
                        for (i in 0..3) {
                            val alpha = 0.2f / (i + 1)
                            val radius = glowRadius + i * 10

                            drawCircle(
                                color = speakingColor.copy(alpha = alpha),
                                radius = radius,
                                style = Stroke(
                                    width = 8.dp.toPx() / (i + 1)
                                )
                            )
                        }
                    }

                    "GEOMETRIC_EXPANSION" -> {
                        // Expanding geometric pattern
                        val sides = 6
                        val shapeRadius = maxRadius * (0.6f + pulse * 0.4f)

                        val path = ComposePath().apply {
                            for (i in 0 until sides) {
                                val angle = (i * (360f / sides) + rotation) * (Math.PI / 180).toFloat()
                                val x = centerX + shapeRadius * cos(angle)
                                val y = centerY + shapeRadius * sin(angle)

                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            close()
                        }

                        drawPath(
                            path = path,
                            color = speakingColor.copy(alpha = 0.5f * pulse),
                            style = Stroke(
                                width = 4.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(12.dp)
        ) {
            // Animated border container with modern design
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSpeaking) {
                            Modifier.drawWithContent {
                                drawContent()
                                // Modern border effect
                                drawCircle(
                                    color = speakingColor.copy(alpha = 0.8f),
                                    radius = size.minDimension / 2,
                                    style = Stroke(
                                        width = 4.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )

                                // Inner glow
                                drawCircle(
                                    color = speakingColor.copy(alpha = 0.3f),
                                    radius = size.minDimension / 2 - 2.dp.toPx(),
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Modern profile picture with shadow effect
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    userColor,
                                    userColor.copy(alpha = 0.8f),
                                    userColor.copy(alpha = 0.6f)
                                )
                            )
                        )
                        .shadow(
                            elevation = if (isSpeaking) 8.dp else 4.dp,
                            shape = CircleShape,
                            ambientColor = speakingColor,
                            spotColor = speakingColor
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = username.first().uppercaseChar().toString(),
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .shadow(2.dp, shape = CircleShape)
                    )
                }

                // Modern floating indicator
                if (isSpeaking) {
                    val floatAnim by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 10f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "floatAnim"
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(speakingColor)
                            .shadow(4.dp, shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Animated sound waves inside indicator
                        Canvas(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val centerX = size.width / 2
                            val centerY = size.height / 2

                            for (i in 0..2) {
                                val waveRadius = 4f + i * 2f * pulse
                                val alpha = 0.8f - i * 0.3f

                                drawCircle(
                                    color = Color.White.copy(alpha = alpha),
                                    center = Offset(centerX, centerY),
                                    radius = waveRadius,
                                    style = Stroke(
                                        width = 1.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Modern status indicator
                if (isSpeaking) {
                    // Triple-dot pulsing indicator
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.height(12.dp)
                    ) {
                        repeat(3) { index ->
                            val dotPulse by infiniteTransition.animateFloat(
                                initialValue = 0.3f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = 600,
                                        delayMillis = index * 200,
                                        easing = FastOutSlowInEasing
                                    ),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dotPulse$index"
                            )

                            Box(
                                modifier = Modifier
                                    .size(6.dp * dotPulse)
                                    .clip(CircleShape)
                                    .background(speakingColor)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = if (isMe) "You" else username,
                    color = if (isMe) Color.White else userColor,
                    fontSize = 14.sp,
                    fontWeight = if (isSpeaking) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .shadow(
                            elevation = if (isSpeaking) 2.dp else 0.dp,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }

            // Modern volume visualization
            if (isSpeaking && volumeLevel > 0) {
                Spacer(modifier = Modifier.height(6.dp))

                val activeBars = (volumeLevel / 100f * 8).toInt()

                Canvas(
                    modifier = Modifier
                        .width(40.dp)
                        .height(12.dp)
                ) {
                    val barWidth = size.width / 8

                    for (i in 0 until 8) {
                        val isActive = i < activeBars
                        val height = if (isActive) {
                            size.height * (0.3f + (i / 7f) * 0.7f)
                        } else {
                            size.height * 0.2f
                        }

                        val yOffset = (size.height - height) / 2
                        val colorAlpha = if (isActive) {
                            0.3f + (i / 7f) * 0.7f
                        } else {
                            0.1f
                        }

                        drawRoundRect(
                            color = speakingColor.copy(alpha = colorAlpha),
                            topLeft = Offset(i * barWidth, yOffset),
                            size = Size(barWidth - 1, height),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ConnectedUserItem(
    user: String,
    isSpeaking: Boolean = false
) {

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Green.copy(alpha = 0.3f))
                .then(
                    if (isSpeaking) {
                        Modifier.border(
                            width = 2.dp,
                            color = Color.Green,
                            shape = CircleShape
                        )
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSpeaking) {
                Icon(
                    painter = painterResource(id = R.drawable.mic),
                    contentDescription = "Speaking",
                    tint = Color.Green,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.audio),
                    contentDescription = "Connected",
                    tint = Color.Green,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(user, fontSize = 16.sp, color = Color.White)

        if (isSpeaking) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.Green)
            )
        }
    }
}

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

    db.collection("chats")
        .document(chatId)
        .collection("messages")
        .document(callId)
        .update("participants", FieldValue.arrayRemove(myUsername))
        .addOnSuccessListener {
            println("DEBUG: Successfully removed $myUsername from participants")

            db.collection("chats")
                .document(chatId)
                .collection("messages")
                .document(callId)
                .get()
                .addOnSuccessListener { snapshot ->
                    val participants = snapshot.get("participants") as? List<*> ?: emptyList<String>()
                    val originalCallerId = snapshot.getString("callerId") ?: callerId
                    val originalReceiverId = snapshot.getString("receiverId") ?: receiverId

                    val stringParticipants = participants.filterIsInstance<String>()
                    println("DEBUG: Remaining participants: $stringParticipants")

                    val updates = mutableMapOf<String, Any>()

                    if (myUsername == originalCallerId) {
                        val newCallerId = stringParticipants.firstOrNull() ?: ""
                        updates["callerId"] = newCallerId
                        println("DEBUG: Updating callerId from $myUsername to $newCallerId")
                    }

                    if (myUsername == originalReceiverId) {
                        val newReceiverId = stringParticipants.getOrNull(1) ?: stringParticipants.firstOrNull() ?: ""
                        updates["receiverId"] = newReceiverId
                        println("DEBUG: Updating receiverId from $myUsername to $newReceiverId")
                    }

                    if (updates.isNotEmpty()) {
                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(callId)
                            .update(updates)
                    }

                    if (stringParticipants.isEmpty()) {
                        println("DEBUG: Last user left, ending call")
                        db.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(callId)
                            .update("status", "ended")
                    }

                    onComplete()
                }
        }
        .addOnFailureListener { e ->
            println("DEBUG: Failed to remove user from participants: $e")
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

    db.collection("chats")
        .document(chatId)
        .collection("messages")
        .document(callId)
        .get()
        .addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val currentInvitations = snapshot.get("invitations") as? List<*> ?: emptyList<String>()
                val currentParticipants = snapshot.get("participants") as? List<*> ?: emptyList<String>()

                val stringInvitations = currentInvitations.filterIsInstance<String>()
                val stringParticipants = currentParticipants.filterIsInstance<String>()

                val newInvitations = invitedUsers.filter {
                    it !in stringInvitations && it !in stringParticipants
                }

                if (newInvitations.isNotEmpty()) {
                    db.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .document(callId)
                        .update(
                            "invitations",
                            FieldValue.arrayUnion(*newInvitations.toTypedArray())
                        )

                    newInvitations.forEach { invitedUser ->
                        sendCallInvitationNotification(
                            callId = callId,
                            callerId = callerId,
                            inviterId = inviterId,
                            invitedUser = invitedUser,
                            chatId = chatId
                        )
                    }
                }
            }
        }
}

private fun sendCallInvitationNotification(
    callId: String,
    callerId: String,
    inviterId: String,
    invitedUser: String,
    chatId: String
) {
    val notificationId = "$callId-$invitedUser"
    val notificationData = hashMapOf(
        "type" to "call_invitation",
        "callId" to callId,
        "callerId" to callerId,
        "inviterId" to inviterId,
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

fun userColor(username: String): Color {
    val colors = listOf(
        Color(0xFFEF5350),
        Color(0xFFAB47BC),
        Color(0xFF5C6BC0),
        Color(0xFF29B6F6),
        Color(0xFF26A69A),
        Color(0xFF66BB6A),
        Color(0xFFFFCA28),
        Color(0xFFFF7043)
    )
    return colors[kotlin.math.abs(username.hashCode()) % colors.size]
}