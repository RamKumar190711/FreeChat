// GroupChatScreen.kt
package com.toqsoft.freechat.featureChat.view

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreModel.GroupMessage
import com.toqsoft.freechat.featureChat.viewModel.GroupChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// Define your color scheme
val primaryBlue = Color(0xFF0D88FF)
val lightBlue = Color(0xFFE3F2FD) // Light variant
val lighterBlue = Color(0xFFF5FAFF) // Even lighter variant
val gradientBlueStart = Color(0xFF0D88FF)
val gradientBlueEnd = Color(0xFF64B5F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    groupId: String,
    onBack: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    // Initialize viewModel with groupId
    LaunchedEffect(groupId) {
        viewModel.initialize(groupId)
    }

    val group by viewModel.group.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val myUsername by viewModel.myUsername.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    var showOptionsMenu by remember { mutableStateOf(false) }
    var showMembersDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Handle errors
    LaunchedEffect(error) {
        error?.let { errorMessage ->
            scope.launch {
                snackbarHostState.showSnackbar(errorMessage)
                viewModel.clearError()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Group avatar with gradient
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(gradientBlueStart, gradientBlueEnd)
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.group_user),
                                contentDescription = "Group",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column {
                            Text(
                                text = group?.name ?: "Group",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${group?.members?.size ?: 0} members",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(id = R.drawable.back),
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showOptionsMenu = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.more_vertical),
                            contentDescription = "Options",
                            tint = primaryBlue
                        )
                    }

                    DropdownMenu(
                        expanded = showOptionsMenu,
                        onDismissRequest = { showOptionsMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("View Members") },
                            onClick = {
                                showOptionsMenu = false
                                showMembersDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.view_member),
                                    contentDescription = "View Members",
                                    modifier = Modifier.size(24.dp),
                                    tint = primaryBlue
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename Group") },
                            onClick = {
                                showOptionsMenu = false
                                showRenameDialog = true
                                newGroupName = group?.name ?: ""
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.edit),
                                    contentDescription = "Rename Group",
                                    tint = primaryBlue
                                )
                            }
                        )
                        Divider()
                        DropdownMenuItem(
                            text = { Text("Leave Group", color = Color.Red) },
                            onClick = {
                                showOptionsMenu = false
                                // Handle leave group
                            },
                            leadingIcon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.exit_to_app),
                                    contentDescription = "Leave Group",
                                    tint = Color.Red
                                )
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryBlue)
                }
            } else {
                // Messages list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .background(lighterBlue),
                    reverseLayout = true,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(messages.reversed()) { message ->
                        GroupMessageItem(
                            message = message,
                            isMyMessage = message.senderId == myUsername,
                            onReactionClick = { emoji ->
                                viewModel.addReaction(message.id, emoji)
                            }
                        )
                    }
                }

                // Message input
                MessageInputSection(
                    onSendMessage = { text -> viewModel.sendMessage(text) },
                    onSendImage = { imageUrl -> viewModel.sendImageMessage(imageUrl) },
                    onSendAudio = { audioUrl, duration -> viewModel.sendAudioMessage(audioUrl, duration) },
                    onSendVideo = { videoUrl, duration -> viewModel.sendVideoMessage(videoUrl, duration) },
                    onSendFile = { fileUrl, fileName, fileSize ->
                        viewModel.sendFileMessage(fileUrl, fileName, fileSize)
                    }
                )
            }
        }
    }

    // Members Dialog
    if (showMembersDialog) {
        AlertDialog(
            onDismissRequest = { showMembersDialog = false },
            title = {
                Text("Group Members", color = primaryBlue, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    group?.members?.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(lightBlue, Color.White)
                                        ),
                                        shape = CircleShape
                                    )
                                    .border(1.dp, primaryBlue.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = member.first().uppercaseChar().toString(),
                                    color = primaryBlue,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = member,
                                modifier = Modifier.weight(1f),
                                color = Color.Black
                            )
                            if (member == group?.createdBy) {
                                Box(
                                    modifier = Modifier
                                        .background(lightBlue, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "Group Admin",
                                        fontSize = 10.sp,
                                        color = primaryBlue
                                    )
                                }


                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showMembersDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = primaryBlue)
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text("Rename Group", color = primaryBlue, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newGroupName,
                    onValueChange = { newGroupName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Group Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primaryBlue,
                        focusedLabelColor = primaryBlue,
                        cursorColor = primaryBlue
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newGroupName.isNotBlank()) {
                            viewModel.updateGroupName(newGroupName)
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    Text("Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = primaryBlue)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun GroupMessageItem(
    message: GroupMessage,
    isMyMessage: Boolean,
    onReactionClick: (String) -> Unit
) {
    val alignment = if (isMyMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isMyMessage) {
        // My message with blue gradient
        Brush.linearGradient(
            colors = listOf(primaryBlue.copy(alpha = 0.1f), primaryBlue.copy(alpha = 0.05f))
        )
    } else {
        // Other's message with light background
        Brush.linearGradient(
            colors = listOf(Color.White, lighterBlue)
        )
    }
    val borderColor = if (isMyMessage) primaryBlue.copy(alpha = 0.3f) else Color(0xFFE0E0E0)
    val textColor = if (isMyMessage) Color.Black else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalAlignment = alignment
    ) {
        if (!isMyMessage) {
            Text(
                text = message.senderName,
                style = MaterialTheme.typography.bodySmall,
                color = primaryBlue.copy(alpha = 0.7f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isMyMessage) 12.dp else 4.dp,
                        bottomEnd = if (isMyMessage) 4.dp else 12.dp
                    )
                )
                .background(bubbleColor)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (isMyMessage) 12.dp else 4.dp,
                        bottomEnd = if (isMyMessage) 4.dp else 12.dp
                    )
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (message.type) {
                    "image" -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.fileUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Image",
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (message.content.isNotEmpty() && message.content != "Sent an image") {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = message.content,
                                color = textColor
                            )
                        }
                    }
                    "audio" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(gradientBlueStart, gradientBlueEnd)
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.audio),
                                    contentDescription = "Audio",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.content,
                                color = textColor
                            )
                        }
                    }
                    "video" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(gradientBlueStart, gradientBlueEnd)
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.video),
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = message.content,
                                color = textColor
                            )
                        }
                    }
                    "file" -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(gradientBlueStart, gradientBlueEnd)
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_file),
                                    contentDescription = "File",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.content,
                                    color = textColor
                                )
                                if (message.fileSize > 0) {
                                    Text(
                                        text = formatFileSize(message.fileSize),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = message.content,
                            color = textColor
                        )
                    }
                }

                // Reactions
                if (message.reactions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.Start,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        message.reactions.forEach { (userId, emoji) ->
                            Box(
                                modifier = Modifier
                                    .background(lightBlue, RoundedCornerShape(12.dp))
                                    .border(1.dp, primaryBlue.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .clickable { onReactionClick(emoji) }
                            ) {
                                Text(emoji, fontSize = 12.sp)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                    }
                }
            }
        }

        // Time and read status
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatMessageTime(message.timestamp),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            if (isMyMessage) {
                Spacer(modifier = Modifier.width(4.dp))
                if (message.isRead) {
                    Icon(
                        painter = painterResource(id = R.drawable.read),
                        contentDescription = "Read",
                        tint = primaryBlue,
                        modifier = Modifier.size(12.dp)
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_single_tick),
                        contentDescription = "Sent",
                        tint = primaryBlue.copy(alpha = 0.5f),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MessageInputSection(
    onSendMessage: (String) -> Unit,
    onSendImage: (String) -> Unit,
    onSendAudio: (String, Long) -> Unit,
    onSendVideo: (String, Long) -> Unit,
    onSendFile: (String, String, Long) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    var showAttachmentOptions by remember { mutableStateOf(false) }

    Column {
        // Attachment options
        if (showAttachmentOptions) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(lighterBlue)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                AttachmentOption(
                    iconDrawable = R.drawable.ic_image,
                    label = "Photo",
                    onClick = {
                        showAttachmentOptions = false
                        // Launch image picker
                    }
                )
                AttachmentOption(
                    iconDrawable = R.drawable.audio,
                    label = "Audio",
                    onClick = {
                        showAttachmentOptions = false
                        // Launch audio recorder
                    }
                )
                AttachmentOption(
                    iconDrawable = R.drawable.video,
                    label = "Video",
                    onClick = {
                        showAttachmentOptions = false
                        // Launch video picker
                    }
                )
                AttachmentOption(
                    iconDrawable = R.drawable.ic_file,
                    label = "File",
                    onClick = {
                        showAttachmentOptions = false
                        // Launch file picker
                    }
                )
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .background(Color.White)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attachment button
            IconButton(
                onClick = { showAttachmentOptions = !showAttachmentOptions }
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_attach),
                    contentDescription = "Attach",
                    tint = primaryBlue
                )
            }

            // Message input
            OutlinedTextField(
                value = messageText,
                onValueChange = { messageText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...") },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = primaryBlue,
                    unfocusedBorderColor = Color(0xFFE0E0E0),
                    focusedContainerColor = lighterBlue,
                    unfocusedContainerColor = Color(0xFFF5F5F5),
                    focusedLabelColor = primaryBlue,
                    cursorColor = primaryBlue
                ),
                trailingIcon = {
                    if (messageText.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                onSendMessage(messageText)
                                messageText = ""
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(gradientBlueStart, gradientBlueEnd)
                                        ),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.send),
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Voice message button
            IconButton(
                onClick = {
                    // Launch voice recorder
                }
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(gradientBlueStart, gradientBlueEnd)
                            ),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.mic),
                        contentDescription = "Voice message",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AttachmentOption(
    iconDrawable: Int,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(lightBlue, Color.White)
                    ),
                    CircleShape
                )
                .border(1.dp, primaryBlue.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconDrawable),
                contentDescription = label,
                tint = primaryBlue,
                modifier = Modifier.size(18.dp)

            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Black)
    }
}

private fun formatMessageTime(timestamp: Long): String {
    val date = Date(timestamp)
    val now = Date()
    val diff = now.time - timestamp

    return when {
        diff < 60000 -> "Just now"
        diff < 3600000 -> "${diff / 60000}m ago"
        diff < 86400000 -> SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}