package com.toqsoft.freechat.featureList.view

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreModel.Group
import com.toqsoft.freechat.coreNetwork.VoiceFeedback
import com.toqsoft.freechat.featureChat.view.gradientBlueEnd
import com.toqsoft.freechat.featureChat.view.gradientBlueStart
import com.toqsoft.freechat.featureChat.view.lightBlue
import com.toqsoft.freechat.featureChat.view.lighterBlue
import com.toqsoft.freechat.featureChat.view.primaryBlue
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import com.toqsoft.freechat.featureList.viewModel.UserListViewModel
import com.toqsoft.freechat.featureVoiceListening.VoiceRecognitionHelper
import com.toqsoft.freechat.featureVoiceListening.view.VoiceListeningDialog
import kotlinx.coroutines.launch
import okhttp3.*



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserListScreen(
    onOpenChat: (otherUserId: String) -> Unit,
    onOpenGroupChat: (groupId: String) -> Unit,
    navController: NavController,
    viewModel: UserListViewModel = hiltViewModel(),
    chatViewModel: ChatViewModel = hiltViewModel(),
    isGroupSelect: Boolean = false,
    callId: String? = null,
    callerId: String = "",
    receiverId: String = ""
) {
    val context = LocalContext.current
    val users by viewModel.users.collectAsState()
    val groups by viewModel.groups.collectAsState()
    val myUsername by viewModel.myUsername.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()
    val lastMessages by viewModel.lastMessages.collectAsState()
    val liveText = remember { mutableStateOf("") }
    val groupCreationResult by viewModel.groupCreationResult.collectAsState(initial = "")

    var inputName by remember { mutableStateOf(myUsername) }
    LaunchedEffect(myUsername) { inputName = myUsername }

    var showAddUserDialog by remember { mutableStateOf(false) }
    var showListening by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }

    // Group chat creation states
    var isCreatingGroup by remember { mutableStateOf(false) }
    var selectedUsersForGroup by remember { mutableStateOf<List<String>>(emptyList()) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Existing selected users for group call
    val selectedUsers = remember { mutableStateListOf<String>() }

    // Show snackbar when group is created
    LaunchedEffect(groupCreationResult) {
        if (groupCreationResult.isNotEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar(groupCreationResult)
            }
        }
    }

    // ------------------ EXISTING PERMISSION & VOICE CODE ------------------

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showListening = true
    }

    val voiceFeedback = remember { VoiceFeedback(context) }

    DisposableEffect(Unit) {
        onDispose { voiceFeedback.release() }
    }

    // ------------------ UI ------------------

    Scaffold(
        floatingActionButton = {
            Box(modifier = Modifier.fillMaxSize()) {
                // First FAB: Group creation
                if (!isCreatingGroup) {
                    FloatingActionButton(
                        onClick = {
                            isCreatingGroup = true
                            selectedUsersForGroup = emptyList()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 184.dp, end = 16.dp), // Position for top FAB
                        containerColor = Color.Transparent,
                        shape = CircleShape
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp) // Same size for all FABs
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
                    }
                }

                // Second FAB: AI Assistant
                AIAssistantButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 100.dp, end = 16.dp), // Position for middle FAB
                    onClick = {
                        when {
                            ContextCompat.checkSelfPermission(
                                context, Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED -> showListening = true
                            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    size = 56.dp // Same size
                )

                // Third FAB: Add User
                FloatingActionButton(
                    onClick = { showAddUserDialog = true },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp), // Position for bottom FAB
                    containerColor = Color.Transparent,
                    shape = CircleShape
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp) // Same size for all FABs
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(gradientBlueStart, gradientBlueEnd)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add User",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        },

        // Top App Bar for group creation mode
        topBar = {
            if (isCreatingGroup) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Create Group Chat",
                            color = Color.White
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isCreatingGroup = false
                            selectedUsersForGroup = emptyList()
                        }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = gradientBlueEnd
                    ),
                    actions = {
                        if (selectedUsersForGroup.isNotEmpty()) {
                            TextButton(
                                onClick = { showGroupNameDialog = true }
                            ) {
                                Text(
                                    "Create (${selectedUsersForGroup.size})",
                                    color = Color.White
                                )
                            }
                        }
                    }
                )
            }
        },

        bottomBar = {
            if (isGroupSelect && selectedUsers.isNotEmpty()) {
                Button(
                    onClick = {
                        navController.previousBackStackEntry
                            ?.savedStateHandle
                            ?.set(
                                "addedUsers",
                                ArrayList(selectedUsers)
                            )
                        val chatId = listOf(callerId, receiverId)
                            .sorted()
                            .joinToString("_")

                        callId?.let {
                            chatViewModel.addUsersToCall(
                                chatId = chatId,
                                callId = callId,
                                users = selectedUsers,
                                callerId = callerId,
                                inviterId = myUsername
                            )
                        }

                        navController.popBackStack()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("Add ${selectedUsers.size} users to call")
                }
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {

            if (!isCreatingGroup) {
                // ------------------ EXISTING USER LIST UI ------------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text("Your name:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputName,
                        onValueChange = { inputName = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isSaved
                    )

                    Spacer(Modifier.height(8.dp))

                    if (myUsername.isEmpty()) {
                        Button(onClick = {
                            val nameTrimmed = inputName.trim()
                            if (nameTrimmed.isNotBlank()) {
                                viewModel.announceSelf(nameTrimmed)
                                isSaved = true
                            }
                        }) { Text("Save & Announce") }

                        Spacer(Modifier.height(16.dp))
                    }

                    // Groups Section
                    if (groups.isNotEmpty()) {
                        Text(
                            text = "Your Groups",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(8.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 200.dp)
                        ) {
                            items(groups) { group ->
                                GroupItem(
                                    group = group,
                                    onClick = { onOpenGroupChat(group.id) }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }

                    SelectedUsersRow(selectedUsers = selectedUsers)

                    Text("Users:")
                    Spacer(Modifier.height(8.dp))

                    UserListContent(
                        users = users.filter { it != myUsername },
                        myUsername = myUsername,
                        unreadCounts = unreadCounts,
                        lastMessages = lastMessages,
                        isGroupSelect = isGroupSelect,
                        selectedUsers = selectedUsers,
                        onUserClick = { user ->
                            if (isGroupSelect) {
                                if (selectedUsers.contains(user)) selectedUsers.remove(user)
                                else selectedUsers.add(user)
                            } else {
                                onOpenChat(user)
                                viewModel.clearUnreadCount(user)
                            }
                        }
                    )
                }
            } else {
                // ------------------ GROUP CREATION MODE UI ------------------
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Selected users preview
                    if (selectedUsersForGroup.isNotEmpty()) {
                        Text(
                            text = "Selected users:",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.Gray
                        )
                        Spacer(Modifier.height(8.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(selectedUsersForGroup) { user ->
                                Card(
                                    modifier = Modifier.width(100.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xFFE8F5E8)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Profile circle
                                        val profileBgColor = remember(user) {
                                            val colors = listOf(
                                                Color(0xFFFFCDD2),
                                                Color(0xFFC8E6C9),
                                                Color(0xFFBBDEFB)
                                            )
                                            colors[kotlin.math.abs(user.hashCode()) % colors.size]
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(profileBgColor, shape = CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = user.first().uppercaseChar().toString(),
                                                color = Color(0xFF212121),
                                                style = MaterialTheme.typography.titleMedium
                                            )
                                        }

                                        Spacer(Modifier.height(4.dp))

                                        Text(
                                            text = user,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = "Select users for group:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(8.dp))

                    UserListContent(
                        users = users.filter { it != myUsername },
                        myUsername = myUsername,
                        unreadCounts = emptyMap(),
                        lastMessages = emptyMap(),
                        isGroupSelect = true,
                        selectedUsers = selectedUsersForGroup,
                        onUserClick = { user ->
                            if (selectedUsersForGroup.contains(user)) {
                                selectedUsersForGroup = selectedUsersForGroup - user
                            } else {
                                selectedUsersForGroup = selectedUsersForGroup + user
                            }
                        }
                    )
                }
            }
        }
    }

    // ------------------ EXISTING ADD USER DIALOG ------------------
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Add User") },
            text = {
                Column {
                    var newUserName by remember { mutableStateOf("") }

                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter the username of another user on the same network",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // This needs to be extracted from the dialog
                    // For now, we'll handle it differently
                    showAddUserDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddUserDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    // NEW: Group Name Dialog
    if (showGroupNameDialog) {
        AlertDialog(
            onDismissRequest = { showGroupNameDialog = false },
            title = { Text("Create Group Chat") },
            text = {
                Column {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Group Name") },
                        placeholder = { Text("Enter group name") }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Selected users: ${selectedUsersForGroup.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Total members: ${selectedUsersForGroup.size + 1} (including you)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmedName = groupName.trim()
                    if (trimmedName.isNotBlank() && selectedUsersForGroup.isNotEmpty()) {
                        // Create group chat
                        viewModel.createGroupChat(
                            groupName = trimmedName,
                            members = selectedUsersForGroup + myUsername,
                            createdBy = myUsername
                        )

                        // Reset states
                        groupName = ""
                        selectedUsersForGroup = emptyList()
                        showGroupNameDialog = false
                        isCreatingGroup = false
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (trimmedName.isBlank()) "Please enter a group name"
                                else "Please select at least one user"
                            )
                        }
                    }
                }) {
                    val isEnabled =
                        groupName.trim().isNotBlank() && selectedUsersForGroup.isNotEmpty()

                    Text(
                        text = "Create",
                        style = TextStyle(
                            brush = if (isEnabled) {
                                Brush.linearGradient(
                                    colors = listOf(gradientBlueStart, gradientBlueEnd)
                                )
                            } else null
                        ),
                        color = if (isEnabled) Color.Unspecified else Color.Gray
                    )

                }
            },
            dismissButton = {
                TextButton(onClick = {
                    groupName = ""
                    showGroupNameDialog = false
                }) { Text("Cancel") }
            }
        )
    }

    // Voice listening dialog
    if (showListening) {
        val voiceHelper = remember(context) {
            VoiceRecognitionHelper(
                context = context,
                onPartialResult = { liveText.value = it },
                onFinalResult = { final ->
                    liveText.value = final
                    Log.d("SPEECH", "You said: $final")

                    val cmd = final.lowercase().trim()
                    val myName = myUsername.ifEmpty { "friend" }

                    val spokenHandled = when {
                        cmd.startsWith("open chat") -> {
                            val parts = cmd.split(" ")
                            if (parts.size >= 3) {
                                onOpenChat(parts.drop(2).joinToString(" "))
                                true
                            } else {
                                voiceFeedback.speak(
                                    "Hey $myName. Wrong command. Please say: open chat followed by username."
                                )
                                true
                            }
                        }

                        // Voice command for creating group
                        cmd.startsWith("create group") -> {
                            isCreatingGroup = true
                            selectedUsersForGroup = emptyList()
                            voiceFeedback.speak("Group creation mode activated. Please select users.")
                            true
                        }

                        cmd.startsWith("audio call") -> {
                            val parts = cmd.split(" ")
                            if (parts.size >= 3) {
                                val user = parts.drop(2).joinToString(" ")
                                val callId = chatViewModel.startCall(
                                    chatViewModel.myUserId,
                                    user,
                                    true,
                                    navController
                                )
                                navController.navigate(
                                    "calling/$callId/${chatViewModel.myUserId}/$user/true"
                                )
                                true
                            } else {
                                voiceFeedback.speak(
                                    "Hey $myName. Wrong command. Please say: audio call followed by username."
                                )
                                true
                            }
                        }

                        cmd.startsWith("video call") -> {
                            val parts = cmd.split(" ")
                            if (parts.size >= 3) {
                                val user = parts.drop(2).joinToString(" ")
                                val callId = chatViewModel.startCall(
                                    chatViewModel.myUserId,
                                    user,
                                    false,
                                    navController
                                )
                                navController.navigate(
                                    "calling/$callId/${chatViewModel.myUserId}/$user/false"
                                )
                                true
                            } else {
                                voiceFeedback.speak(
                                    "Hey $myName. Wrong command. Please say: video call followed by username."
                                )
                                true
                            }
                        }

                        else -> {
                            voiceFeedback.speak(
                                "Hey $myName. Wrong command. You can say: open chat, create group, audio call, or video call followed by username."
                            )
                            true
                        }
                    }

                    showListening = false
                }
            )
        }

        DisposableEffect(Unit) {
            voiceHelper.startListening()
            onDispose { voiceHelper.destroy() }
        }

        VoiceListeningDialog(
            onDismiss = { showListening = false; voiceHelper.stopListening() },
            liveText = liveText.value
        )
    }

    // Snackbar host
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.padding(16.dp)
    )
}

// New: Group Item Composable
@Composable
fun GroupItem(
    group: Group,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = lighterBlue // Using the lighter blue variant
        ),
        border = BorderStroke(1.dp, primaryBlue.copy(alpha = 0.1f)) // Subtle blue border
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group icon with gradient
            Box(
                modifier = Modifier
                    .size(40.dp)
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

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Black
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${group.members.size} members",
                    style = MaterialTheme.typography.bodySmall,
                    color = primaryBlue.copy(alpha = 0.7f) // Blue tint for member count
                )

                if (group.lastMessage.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = group.lastMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun SelectedUsersRow(selectedUsers: List<String>) {
    if (selectedUsers.isEmpty()) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        selectedUsers.forEach { user ->
            // Light colored circle with first letter
            val profileBgColor = remember(user) {
                val colors = listOf(
                    Color(0xFFFFCDD2),
                    Color(0xFFC8E6C9),
                    Color(0xFFBBDEFB),
                    Color(0xFFFFF9C4),
                    Color(0xFFD1C4E9),
                    Color(0xFFFFE0B2)
                )
                colors[kotlin.math.abs(user.hashCode()) % colors.size]
            }

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(profileBgColor, shape = CircleShape)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.first().uppercaseChar().toString(),
                    color = Color(0xFF212121),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
fun AIAssistantButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = modifier
            .size(size)
            .scale(pulse)
            .background(
                brush = Brush.verticalGradient(listOf(Color(0xFF0D88FF), Color(0xFF42A5F5))),
                shape = CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.voice_ai),
            contentDescription = "AI Assistant",
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
}
@Composable
fun UserListContent(
    users: List<String>,
    myUsername: String,
    unreadCounts: Map<String, Int>,
    lastMessages: Map<String, String>,
    isGroupSelect: Boolean,
    selectedUsers: List<String>,
    onUserClick: (String) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(users) { user ->
            val unread = unreadCounts[user] ?: 0
            val lastMsg = lastMessages[user] ?: ""
            val isSelected = selectedUsers.contains(user)

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUserClick(user) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected)
                        Color(0xFFE3F2FD)
                    else
                        MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    if (isGroupSelect) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null // Handle click on card instead
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Profile color based on username
                    val profileBgColor = remember(user) {
                        val colors = listOf(
                            Color(0xFFFFCDD2),
                            Color(0xFFC8E6C9),
                            Color(0xFFBBDEFB),
                            Color(0xFFFFF9C4),
                            Color(0xFFD1C4E9),
                            Color(0xFFFFE0B2)
                        )
                        colors[kotlin.math.abs(user.hashCode()) % colors.size]
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = profileBgColor,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.first().uppercaseChar().toString(),
                            color = Color(0xFF212121),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = user,
                            style = MaterialTheme.typography.bodyLarge
                        )

                        if (!isGroupSelect && lastMsg.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                lastMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.Gray,
                                maxLines = 1
                            )
                        }
                    }

                    if (!isGroupSelect && unread > 0) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Color(0xFF0D88FF),
                                    RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                unread.toString(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 72.dp),
                    thickness = 0.5.dp,
                    color = Color(0xFFE0E0E0)
                )
            }
        }
    }
}