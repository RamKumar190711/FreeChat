package com.toqsoft.freechat.featureChat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toqsoft.freechat.coreModel.ChatMessage
import com.toqsoft.freechat.coreModel.MessageStatus
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    otherUserId: String,
    onBack: () -> Unit = {},
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messagesMap by viewModel.messagesMap.collectAsState()
    val typingMap by viewModel.typingMap.collectAsState()
    val lastMessageStatus by viewModel.lastMessageStatus.collectAsState()
    val lastSeen by viewModel.otherUserLastSeen.collectAsState()
    var inputText by remember { mutableStateOf("") }

    val chatMessages = messagesMap[otherUserId] ?: emptyList()
    val typing = typingMap[otherUserId] ?: false

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F8F8))) {

        TopAppBar(
            title = {
                Column {
                    Text(otherUserId, style = MaterialTheme.typography.titleMedium)
                    Text(
                        chatStatusTextFromTick(lastMessageStatus, lastSeen),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
            modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true
        ) {
            items(chatMessages.reversed()) { msg ->
                MessageBubble(msg, msg.senderId == viewModel.myUserId)
                if (msg.senderId != viewModel.myUserId && msg.status == MessageStatus.DELIVERED) {
                    LaunchedEffect(msg.id) { viewModel.markMessageSeen(msg) }
                }
            }
            if (typing) item { TypingIndicator() }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(8.dp).imePadding()
        ) {
            TextField(
                value = inputText,
                onValueChange = {
                    inputText = it
                    viewModel.sendTyping(otherUserId, it.isNotEmpty())
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message...") }
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (inputText.isNotBlank()) {
                    viewModel.sendMessage(inputText.trim(), otherUserId)
                    inputText = ""
                    viewModel.sendTyping(otherUserId, false)
                }
            }) { Text("Send") }
        }
    }
}
@Composable
fun MessageStatusIcon(status: MessageStatus) {
    val (text, color) = when (status) {
        MessageStatus.SENT -> "✓" to Color.LightGray
        MessageStatus.DELIVERED -> "✓✓" to Color.LightGray
        MessageStatus.SEEN -> "✓✓" to Color(0xFF0D88FF)
    }
    Text(text = text, color = color, style = MaterialTheme.typography.labelSmall)
}


@Composable
fun chatStatusTextFromTick(lastMessageStatus: MessageStatus?, lastSeen: Long?): String {
    return when (lastMessageStatus) {
        MessageStatus.SENT -> "offline"
        MessageStatus.DELIVERED -> lastSeen?.let { "last seen at ${formatTime(it)}" } ?: "offline"
        MessageStatus.SEEN -> "online"
        else -> "offline"
    }
}

@Composable
fun formatTime(timestamp: Long) = remember(timestamp) {
    java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
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
                        RoundedCornerShape(8.dp)
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
fun TypingIndicator() = Row(
    Modifier.fillMaxWidth().padding(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    Text("Typing...", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
}
