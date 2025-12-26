package com.toqsoft.freechat.featureChat.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreModel.ChatMessage
import com.toqsoft.freechat.coreModel.MessageStatus
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
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

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val callId = viewModel.startCall(viewModel.myUserId, otherUserId, true, navController)
            navController.navigate("calling/$callId/${viewModel.myUserId}/$otherUserId/true")
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

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        TopAppBar(
            title = {
                Column {
                    Text(otherUserId)
                    Text(userStatus, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                IconButton(onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        val callId = viewModel.startCall(viewModel.myUserId, otherUserId, true, navController)
                        navController.navigate("calling/$callId/${viewModel.myUserId}/$otherUserId/true")
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }) {
                    Icon(painterResource(id = R.drawable.audio), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }

                IconButton(onClick = {
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
                }) {
                    Icon(painterResource(id = R.drawable.video), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        )

        LazyColumn(state = listState, modifier = Modifier.weight(1f), reverseLayout = true) {
            items(messages.reversed()) { msg ->
                MessageBubble(msg, msg.senderId == viewModel.myUserId)
            }
        }

        Row(
            modifier = Modifier.padding(8.dp).imePadding().navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Type a message") },
                singleLine = true
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (input.isNotBlank()) {
                        viewModel.sendMessage(input.trim(), otherUserId)
                        input = ""
                    }
                },
                shape = RoundedCornerShape(16.dp)
            ) { Text("Send") }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isMe: Boolean) {
    val isCallLog = message.status in listOf(
        MessageStatus.declined, MessageStatus.accepted,
        MessageStatus.rejected, MessageStatus.ended, MessageStatus.missed,
        MessageStatus.ringing
    )

    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Text(if (isMe) "You" else message.senderId, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        color = when {
                            isCallLog -> Color(0xFFF1F1F1)
                            isMe -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.secondaryContainer
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (isCallLog) {
                    val callIcon = when (message.status) {
                        MessageStatus.missed ->
                            painterResource(id = R.drawable.missed)
                        MessageStatus.rejected, MessageStatus.declined ->
                            painterResource(id = R.drawable.decline)

                        else ->
                            painterResource(id = R.drawable.audio)
                    }

                    val iconTint = when (message.status) {
                        MessageStatus.missed -> Color.Red
                        MessageStatus.rejected -> Color.Red
                        MessageStatus.declined -> Color.Gray
                        MessageStatus.accepted -> Color(0xFF2E7D32)
                        else -> Color.DarkGray
                    }

                    Icon(
                        painter = callIcon,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = iconTint
                    )
                    Spacer(Modifier.width(6.dp))
                }

                Text(
                    text = message.text,
                    color = if (isMe && !isCallLog) Color.White else Color.Black,
                    fontSize = 14.sp
                )
                MessageStatusIcon(message.status)
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus) {
    val (label, color) = when (status) {
        MessageStatus.SENT -> "✓" to Color.LightGray
        MessageStatus.DELIVERED -> "✓✓" to Color.LightGray
        MessageStatus.SEEN -> "✓✓" to Color(0xFF0D88FF)
        MessageStatus.ringing -> "" to Color.Transparent
        MessageStatus.accepted -> "Answered" to Color(0xFF2E7D32)
        MessageStatus.rejected -> "Rejected" to Color(0xFFC62828)
        MessageStatus.ended -> "Ended" to Color.DarkGray
        MessageStatus.missed -> "Missed Call" to Color(0xFFFF1744)
        MessageStatus.declined -> "Declined" to Color(0xFF757575)
    }
    if (label.isNotEmpty()) {
        Text(text = label, color = color, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 6.dp))
    }
}