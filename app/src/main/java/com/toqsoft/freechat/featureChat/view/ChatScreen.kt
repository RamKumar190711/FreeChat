package com.toqsoft.freechat.featureChat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreModel.ChatMessage
import com.toqsoft.freechat.coreModel.MessageStatus
import com.toqsoft.freechat.coreNetwork.VoiceFeedback
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import com.toqsoft.freechat.featureVoiceListening.VoiceRecognitionHelper
import com.toqsoft.freechat.featureVoiceListening.view.VoiceListeningDialog
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.text.style.TextOverflow

// Updated color palette
val PrimaryBlue = Color(0xFF2196F3)      // Primary blue
val LightBlue = Color(0xFF64B5F6)        // Light blue
val DarkBlue = Color(0xFF1976D2)         // Dark blue
val WhiteBackground = Color(0xFFFFFFFF)  // White background
val CardWhite = Color(0xFFF8F9FA)        // Slightly off-white for cards
val TextPrimary = Color(0xFF212121)      // Dark text
val TextSecondary = Color(0xFF757575)    // Medium text
val TextTertiary = Color(0xFFBDBDBD)     // Light text
val DividerColor = Color(0xFFE0E0E0)     // Divider color
val ErrorRed = Color(0xFFF44336)         // For missed calls/errors
val SuccessGreen = Color(0xFF4CAF50)     // For success states

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    otherUserId: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit = {},
    navController: NavController
) {
    val messagesMap by viewModel.messagesMap.collectAsState()
    val messages = messagesMap[otherUserId] ?: emptyList()
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val userStatus by viewModel.combinedUserStatus.collectAsState()
    val context = LocalContext.current

    // 🎤 Voice states
    var showVoiceListening by remember { mutableStateOf(false) }
    val liveVoiceText = remember { mutableStateOf("") }

    // Typing indicator
    var showTypingIndicator by remember { mutableStateOf(false) }

    // Message options menu
    var showMessageOptions by remember { mutableStateOf(false) }
    var selectedMessage by remember { mutableStateOf<ChatMessage?>(null) }

    // Permission launchers
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val callId = viewModel.startCall(viewModel.myUserId, otherUserId, true, navController)
            navController.navigate("calling/$callId/${viewModel.myUserId}/$otherUserId/true")
            showVoiceListening = true
        } else {
            Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }

    val videoPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] == true
        val camGranted = permissions[Manifest.permission.CAMERA] == true

        if (micGranted && camGranted) {
            val callId = viewModel.startCall(
                viewModel.myUserId,
                otherUserId,
                audioOnly = false,
                navController = navController
            )
            navController.navigate(
                "calling/$callId/${viewModel.myUserId}/$otherUserId/false"
            )
        } else {
            Toast.makeText(context, "Camera & microphone required", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(otherUserId) {
        viewModel.observeChatWithUser(otherUserId)
    }

    // Voice feedback
    val voiceFeedback = remember {
        VoiceFeedback(context)
    }

    DisposableEffect(Unit) {
        onDispose {
            voiceFeedback.release()
        }
    }

    // Background with subtle pattern
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WhiteBackground)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // Subtle grid pattern in background
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            // Draw subtle grid lines
            for (x in 0 until size.width.toInt() step 60) {
                drawLine(
                    color = DividerColor.copy(alpha = 0.3f),
                    start = Offset(x.toFloat(), 0f),
                    end = Offset(x.toFloat(), size.height),
                    strokeWidth = 0.5f
                )
            }
            for (y in 0 until size.height.toInt() step 60) {
                drawLine(
                    color = DividerColor.copy(alpha = 0.3f),
                    start = Offset(0f, y.toFloat()),
                    end = Offset(size.width, y.toFloat()),
                    strokeWidth = 0.5f
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Add spacing for status bar
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            // Clean Header with Blue Accent
            ModernHeader(
                otherUserId = otherUserId,
                userStatus = userStatus,
                onBack = onBack,
                onAudioCall = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        val callId = viewModel.startCall(viewModel.myUserId, otherUserId, true, navController)
                        navController.navigate("calling/$callId/${viewModel.myUserId}/$otherUserId/true")
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onVideoCall = {
                    val micGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    val camGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    if (micGranted && camGranted) {
                        val callId = viewModel.startCall(
                            viewModel.myUserId,
                            otherUserId,
                            audioOnly = false,
                            navController = navController
                        )
                        navController.navigate(
                            "calling/$callId/${viewModel.myUserId}/$otherUserId/false"
                        )
                    } else {
                        videoPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.RECORD_AUDIO,
                                Manifest.permission.CAMERA
                            )
                        )
                    }
                }
            )

            // Chat Messages Area
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                items(messages.reversed()) { msg ->
                    CleanMessageBubble(
                        message = msg,
                        isMe = msg.senderId == viewModel.myUserId,
                        onLongPress = {
                            selectedMessage = msg
                            showMessageOptions = true
                        }
                    )
                }

                // Add typing indicator
                if (showTypingIndicator) {
                    item {
                        CleanTypingIndicator()
                    }
                }
            }

            // Modern Input Area
            CleanInputArea(
                input = input,
                onInputChange = { input = it },
                onSend = {
                    viewModel.sendMessage(input.trim(), otherUserId)
                    input = ""
                },
                onVoiceClick = {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        showVoiceListening = true
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WhiteBackground)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .imePadding()
            )
        }

        // Message Options Menu
        if (showMessageOptions) {
            CleanMessageOptionsMenu(
                message = selectedMessage,
                onDismiss = { showMessageOptions = false },
                onCopy = {
                    // Copy text to clipboard
                    showMessageOptions = false
                },
                onDelete = {
                    // Delete message
                    showMessageOptions = false
                },
                onReply = {
                    // Reply to message
                    showMessageOptions = false
                }
            )
        }

        // 🎙️ VOICE LISTENING OVERLAY
        if (showVoiceListening) {
            CleanVoiceListeningDialog(
                onDismiss = {
                    showVoiceListening = false
                },
                liveText = liveVoiceText.value
            )
        }
    }

    // Voice recognition setup
    if (showVoiceListening) {
        val voiceHelper = remember {
            VoiceRecognitionHelper(
                context = context,
                onPartialResult = { liveVoiceText.value = it },
                onFinalResult = { finalText ->
                    val spoken = finalText.lowercase().trim()
                    val currentUserName = viewModel.myUserId

                    when {
                        spoken.startsWith("send message") -> {
                            val msg = spoken.removePrefix("send message").trim()
                            if (msg.isNotEmpty()) {
                                viewModel.sendMessage(msg, otherUserId)
                            } else {
                                voiceFeedback.speak(
                                    "Please provide a message after 'send message' command."
                                )
                            }
                        }
                        else -> {
                            voiceFeedback.speak(
                                "Please say 'send message' followed by your text."
                            )
                        }
                    }
                    showVoiceListening = false
                }
            )
        }

        DisposableEffect(Unit) {
            voiceHelper.startListening()
            onDispose {
                voiceHelper.stopListening()
                voiceHelper.destroy()
            }
        }
    }
}

@Composable
fun ModernHeader(
    otherUserId: String,
    userStatus: String,
    onBack: () -> Unit,
    onAudioCall: () -> Unit,
    onVideoCall: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = WhiteBackground,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left side: Back button + User info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Back button
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.back),
                        contentDescription = "Back",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // User info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = otherUserId,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Status indicator
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (userStatus.contains("online", ignoreCase = true))
                                        SuccessGreen
                                    else
                                        TextTertiary
                                )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = userStatus,
                            color = TextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                }
            }

            // Right side: Call buttons
            Row {
                // Audio call button
                IconButton(
                    onClick = onAudioCall,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.audio),
                        contentDescription = "Voice Call",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Video call button
                IconButton(
                    onClick = onVideoCall,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.video),
                        contentDescription = "Video Call",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CleanMessageBubble(
    message: ChatMessage,
    isMe: Boolean,
    onLongPress: () -> Unit
) {
    val isCallLog = message.status in listOf(
        MessageStatus.declined,
        MessageStatus.accepted,
        MessageStatus.rejected,
        MessageStatus.ended,
        MessageStatus.missed,
        MessageStatus.ringing
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = if (isCallLog) 320.dp else 300.dp)
                .padding(horizontal = 8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { onLongPress() })
                    },
                shape = RoundedCornerShape(16.dp),
                color = if (isMe) PrimaryBlue else CardWhite,
                shadowElevation = if (isMe) 2.dp else 1.dp
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
                ) {
                    if (isCallLog) {
                        CleanCallLogContent(message)
                    } else {
                        Text(
                            text = message.text,
                            color = if (isMe) Color.White else TextPrimary,
                            fontSize = 15.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        // Timestamp + status row
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!isMe && !isCallLog) {
                Text(
                    text = message.senderId.take(8),
                    color = TextSecondary,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Text(
                text = formatSimpleTime(message.timestamp),
                color = TextTertiary,
                fontSize = 10.sp
            )

            if (isMe && !isCallLog) {
                Spacer(modifier = Modifier.width(4.dp))
                CleanStatusIndicator(status = message.status)
            }
        }
    }
}

@Composable
fun CleanCallLogContent(message: ChatMessage) {
    val iconRes = when (message.status) {
        MessageStatus.missed -> R.drawable.missed
        MessageStatus.rejected, MessageStatus.declined -> R.drawable.decline
        MessageStatus.accepted -> R.drawable.audio
        MessageStatus.ended -> R.drawable.end
        else -> R.drawable.audio
    }

    val iconColor = when (message.status) {
        MessageStatus.missed -> ErrorRed
        MessageStatus.rejected -> ErrorRed
        MessageStatus.declined -> TextTertiary
        MessageStatus.accepted -> PrimaryBlue
        MessageStatus.ended -> DarkBlue
        else -> TextSecondary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = iconColor
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = message.text,
                color = TextPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (message.status) {
                    MessageStatus.missed -> "Missed"
                    MessageStatus.rejected -> "Rejected"
                    MessageStatus.declined -> "Declined"
                    MessageStatus.accepted -> "Answered"
                    MessageStatus.ended -> "Ended"
                    MessageStatus.ringing -> "Ringing"
                    else -> "Call"
                },
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun CleanStatusIndicator(status: MessageStatus) {
    val (iconRes, color) = when (status) {
        MessageStatus.SENT -> Pair(R.drawable.ic_single_tick, TextTertiary)
        MessageStatus.DELIVERED -> Pair(R.drawable.double_tick, TextSecondary)
        MessageStatus.SEEN -> Pair(R.drawable.double_tick, PrimaryBlue)
        else -> Pair(R.drawable.ic_single_tick, Color.Transparent)
    }

    if (status in listOf(MessageStatus.SENT, MessageStatus.DELIVERED, MessageStatus.SEEN)) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = "Status",
            modifier = Modifier.size(14.dp),
            tint = color
        )
    }
}

@Composable
fun CleanTypingIndicator() {
    Row(
        modifier = Modifier
            .padding(start = 20.dp, top = 8.dp, bottom = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(CardWhite)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
//        AnimatedTypingDots()
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "typing",
            color = TextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
fun CleanAnimatedTypingDots() {
    val infiniteTransition = rememberInfiniteTransition()
    val dot1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.3f at 0
                1f at 400
                0.3f at 800
                0.3f at 1400
            },
            repeatMode = RepeatMode.Restart
        )
    )
    val dot2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.3f at 200
                1f at 600
                0.3f at 1000
                0.3f at 1400
            },
            repeatMode = RepeatMode.Restart
        )
    )
    val dot3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1400
                0.3f at 400
                1f at 800
                0.3f at 1200
                0.3f at 1400
            },
            repeatMode = RepeatMode.Restart
        )
    )

    Row {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = dot1Alpha))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = dot2Alpha))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = dot3Alpha))
        )
    }
}

@Composable
fun CleanInputArea(
    input: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoiceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = WhiteBackground,
        shadowElevation = 4.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment button
            IconButton(
                onClick = { /* Handle attachment */ }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_attach),
                    contentDescription = "Attach",
                    tint = PrimaryBlue
                )
            }

            // Message input
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = RoundedCornerShape(20.dp),
                color = CardWhite,
                border = BorderStroke(1.dp, DividerColor)
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .padding(vertical = 10.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = TextPrimary,
                        fontSize = 15.sp
                    ),
                    decorationBox = { innerTextField ->
                        if (input.isEmpty()) {
                            Text(
                                text = "Type a message...",
                                color = TextTertiary,
                                fontSize = 15.sp
                            )
                        }
                        innerTextField()
                    }
                )
            }

            // Send/Voice button
            IconButton(
                onClick = {
                    if (input.isNotBlank()) onSend() else onVoiceClick()
                }
            ) {
                if (input.isNotBlank()) {
                    Icon(
                        painter = painterResource(id = R.drawable.send),
                        contentDescription = "Send",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.mic),
                        contentDescription = "Voice",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CleanMessageOptionsMenu(
    message: ChatMessage?,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onReply: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(200.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column {
                CleanOptionMenuItem(
                    icon = R.drawable.copy,
                    text = "Copy",
                    onClick = onCopy
                )
                Divider(color = DividerColor, thickness = 1.dp)
                CleanOptionMenuItem(
                    icon = R.drawable.reply,
                    text = "Reply",
                    onClick = onReply
                )
                Divider(color = DividerColor, thickness = 1.dp)
                CleanOptionMenuItem(
                    icon = R.drawable.delete,
                    text = "Delete",
                    onClick = onDelete,
                    textColor = ErrorRed
                )
            }
        }
    }
}

@Composable
fun CleanOptionMenuItem(
    icon: Int,
    text: String,
    onClick: () -> Unit,
    textColor: Color = TextPrimary
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = text,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 14.sp
        )
    }
}

@Composable
fun CleanVoiceListeningDialog(
    onDismiss: () -> Unit,
    liveText: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(240.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Animated voice waves
                CleanVoiceWavesAnimation()

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Listening...",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = liveText.ifEmpty { "Speak your message" },
                    color = TextPrimary.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Tap to cancel",
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun CleanVoiceWavesAnimation() {
    val infiniteTransition = rememberInfiniteTransition()
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 0
                1f at 300
                0.3f at 600
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        )
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 150
                1f at 450
                0.3f at 750
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        )
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                0.3f at 300
                1f at 600
                0.3f at 900
                0.3f at 1200
            },
            repeatMode = RepeatMode.Restart
        )
    )

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(PrimaryBlue.copy(alpha = wave1), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(70.dp)
                .background(PrimaryBlue.copy(alpha = wave2), CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(PrimaryBlue.copy(alpha = wave3), CircleShape)
        )
    }
}

// Keep the existing formatSimpleTime function as is
@Composable
fun formatSimpleTime(timestamp: Long?): String {
    if (timestamp == null || timestamp == 0L) return ""

    return try {
        val date = Date(timestamp)
        SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
    } catch (e: Exception) {
        ""
    }
}