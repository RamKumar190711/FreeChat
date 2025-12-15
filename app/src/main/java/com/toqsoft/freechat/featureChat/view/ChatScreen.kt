package com.toqsoft.freechat.featureChat.ui

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.toqsoft.freechat.coreModel.ChatMessage
import com.toqsoft.freechat.coreModel.MessageStatus
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.toqsoft.freechat.R

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

    // Observe chat after myUserId is ready
    LaunchedEffect(viewModel.myUserId) {
        if (viewModel.myUserId != "unknown") {
            viewModel.observeChatWithUser(otherUserId)
        }
    }

    // Mark messages as seen
    LaunchedEffect(messages) {
        val firstVisibleIndex = listState.firstVisibleItemIndex
        val visibleMessages = messages.asReversed().drop(firstVisibleIndex)
        visibleMessages.forEach { msg ->
            if (msg.senderId != viewModel.myUserId && msg.status != MessageStatus.SEEN) {
                viewModel.markMessageSeen(msg)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White) // Chat screen background
    ) {

        TopAppBar(
            title = {
                Column {
                    Text(otherUserId)
                    Text(userStatus, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },

            actions = {
                IconButton(onClick = {

                    viewModel.startCall(
                        otherUserId = otherUserId,
                        audioOnly = true,
                        onAccepted = {
                            navController.navigate("speak/$otherUserId/true") {
                                popUpTo("calling/$otherUserId/true") { inclusive = true }
                            }
                        },
                        onRejected = {
                            navController.popBackStack() // back to chat
                        }
                    )

                    // 🔥 IMMEDIATE navigation
                    navController.navigate("calling/$otherUserId/true")

                }){
                    Icon(
                        painter = painterResource(id = R.drawable.audio),
                        contentDescription = "Audio Call",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
                            .size(24.dp)// Use primary color
                    )
                }
                IconButton(onClick = {

                    viewModel.startCall(
                        otherUserId = otherUserId,
                        audioOnly = false,
                        onAccepted = {
                            navController.navigate("speak/$otherUserId/true") {
                                popUpTo("calling/$otherUserId/true") { inclusive = true }
                            }
                        },
                        onRejected = {
                            navController.popBackStack() // back to chat
                        }
                    )

                    // 🔥 IMMEDIATE navigation
                    navController.navigate("calling/$otherUserId/true")

                }) {
                    Icon(
                        painter = painterResource(id = R.drawable.video),
                        contentDescription = "Video Call",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp)
                            .size(24.dp)// Use primary color
                    )
                }
            }

        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            reverseLayout = true
        ) {
            items(messages.reversed()) { msg ->
                MessageBubble(msg, msg.senderId == viewModel.myUserId)
            }
        }

        Row(
            modifier = Modifier
                .padding(8.dp)
                .imePadding()
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
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
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Send")
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage, isMe: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            Text(
                if (isMe) "You" else message.senderId,
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(
                        if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(message.text, color = if (isMe) Color.White else Color.Black)
                if (isMe) {
                    Spacer(Modifier.width(4.dp))
                    MessageStatusIcon(message.status)
                }
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus) {
    val (text, color) = when (status) {
        MessageStatus.SENT -> "✓" to Color.LightGray
        MessageStatus.DELIVERED -> "✓✓" to Color.LightGray
        MessageStatus.SEEN -> "✓✓" to Color(0xFF0D88FF)
        MessageStatus.accepted -> "accepted" to Color.Green
        MessageStatus.rejected -> "rejected" to Color.Red
        MessageStatus.ended -> "ended" to Color.Red
        MessageStatus.ringing -> "ringing" to Color.Yellow

    }
    Text(text = text, color = color, style = MaterialTheme.typography.labelSmall)
}
