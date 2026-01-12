package com.toqsoft.freechat.featureList.view

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.toqsoft.freechat.R
import com.toqsoft.freechat.coreModel.Group
import com.toqsoft.freechat.coreNetwork.VoiceFeedback
import com.toqsoft.freechat.featureChat.view.primaryBlue
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import com.toqsoft.freechat.featureList.viewModel.UserListViewModel
import com.toqsoft.freechat.featureVoiceListening.VoiceRecognitionHelper
import com.toqsoft.freechat.featureVoiceListening.view.VoiceListeningDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var newUserName by remember { mutableStateOf("") }
    var originalUsername by remember { mutableStateOf("") }

    // Search state
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredUsers = remember(users, searchQuery) {
        users.filter {
            it != myUsername &&
                    (searchQuery.isEmpty() || it.contains(searchQuery, ignoreCase = true))
        }
    }
    val filteredGroups = remember(groups, searchQuery) {
        groups.filter {
            searchQuery.isEmpty() ||
                    it.name.contains(searchQuery, ignoreCase = true) ||
                    it.members.any { member -> member.contains(searchQuery, ignoreCase = true) }
        }
    }

    // Profile editing states
    var showProfileDialog by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var profileStatus by remember { mutableStateOf("") }

    LaunchedEffect(myUsername) {
        profileName = myUsername
        originalUsername = myUsername
    }

    var showAddUserDialog by remember { mutableStateOf(false) }
    var showListening by remember { mutableStateOf(false) }

    // Group creation states
    var isCreatingGroup by remember { mutableStateOf(false) }
    var selectedUsersForGroup by remember { mutableStateOf<List<String>>(emptyList()) }
    var showGroupNameDialog by remember { mutableStateOf(false) }
    var groupName by remember { mutableStateOf("") }

    // Selected users for group call
    val selectedUsers = remember { mutableStateListOf<String>() }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) showListening = true
    }

    val voiceFeedback = remember { VoiceFeedback(context) }

    DisposableEffect(Unit) {
        onDispose { voiceFeedback.release() }
    }

    // Show snackbar when group is created
    LaunchedEffect(groupCreationResult) {
        if (groupCreationResult.isNotEmpty()) {
            scope.launch {
                snackbarHostState.showSnackbar(groupCreationResult)
            }
        }
    }

    Scaffold(
        topBar = {
            if (isCreatingGroup) {
                // Group Creation Top Bar
                TopAppBar(
                    title = {
                        Text(
                            "New Group",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                isCreatingGroup = false
                                selectedUsersForGroup = emptyList()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Cancel", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = primaryBlue
                    )
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(primaryBlue)
                ) {
                    // Main Top Bar (always visible)
                    TopAppBar(
                        title = {
                            if (showSearchBar) {
                                // In search mode, show back button instead of title
                                Box(modifier = Modifier.fillMaxWidth())
                            } else {
                                Text(
                                    "FreeChat",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 20.sp
                                )
                            }
                        },
                        navigationIcon = {
                            if (showSearchBar) {
                                IconButton(
                                    onClick = {
                                        showSearchBar = false
                                        searchQuery = ""
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                                }
                            } else {
                                IconButton(
                                    onClick = { showProfileDialog = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = Color.White)
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent
                        ),
                        actions = {
                            if (showSearchBar) {
                                // Empty actions during search
                            } else {
                                IconButton(
                                    onClick = { showSearchBar = true },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                                }
                                IconButton(
                                    onClick = { /* Handle more options */ },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                                }
                            }
                        }
                    )

                    // Search Bar (only visible when showSearchBar is true)
                    if (showSearchBar) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White
                            ),
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.weight(1f),
                                    placeholder = { Text("Search contacts...") },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    )
                                )
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (isGroupSelect && selectedUsers.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    color = primaryBlue
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${selectedUsers.size} selected",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )

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
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = primaryBlue
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Add to Call", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (isCreatingGroup && selectedUsersForGroup.isNotEmpty()) {
                // Show FAB when creating group with selected users
                FloatingActionButton(
                    onClick = { showGroupNameDialog = true },
                    containerColor = primaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Create Group")
                }
            } else if (!isCreatingGroup && !isGroupSelect && !showSearchBar) {
                FloatingActionButton(
                    onClick = { showAddUserDialog = true },
                    containerColor = primaryBlue,
                    contentColor = Color.White,
                    modifier = Modifier
                        .size(56.dp)
                        .padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Chat")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White)
            ) {
                if (isCreatingGroup) {
                    // Group Creation Mode
                    GroupCreationContent(
                        users = users.filter { it != myUsername },
                        myUsername = myUsername,
                        selectedUsers = selectedUsersForGroup,
                        onUserClick = { user ->
                            if (selectedUsersForGroup.contains(user)) {
                                selectedUsersForGroup = selectedUsersForGroup - user
                            } else {
                                selectedUsersForGroup = selectedUsersForGroup + user
                            }
                        },
                        onCreateGroup = {
                            showGroupNameDialog = true
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Regular User List Mode
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // Profile Section - Only show when not in group select
                        if (!isGroupSelect && !showSearchBar) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color.White
                                ),
                                elevation = CardDefaults.cardElevation(2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .clickable { showProfileDialog = true },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE8E8E8))
                                    ) {
                                        Icon(
                                            Icons.Default.Person,
                                            contentDescription = "Profile",
                                            tint = Color.Gray,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .align(Alignment.Center)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = myUsername.ifEmpty { "Set your name" },
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = Color.Black
                                        )
                                        Text(
                                            text = "Tap to view and edit profile",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }

                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // Quick Actions - Only show when not in search mode
                        if (!isGroupSelect && !showSearchBar) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                QuickActionButton(
                                    icon = R.drawable.group_user,
                                    label = "New Group",
                                    onClick = {
                                        isCreatingGroup = true
                                        selectedUsersForGroup = emptyList()
                                    }
                                )
                                QuickActionButton(
                                    icon = null,
                                    iconVector = Icons.Default.Person,
                                    label = "New Contact",
                                    onClick = { showAddUserDialog = true }
                                )
                                QuickActionButton(
                                    icon = R.drawable.voice_ai,
                                    label = "Voice AI",
                                    onClick = {
                                        when {
                                            ContextCompat.checkSelfPermission(
                                                context, Manifest.permission.RECORD_AUDIO
                                            ) == PackageManager.PERMISSION_GRANTED -> showListening = true
                                            else -> micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                )
                            }
                        }

                        // User List Content
                        UserListContent(
                            users = if (showSearchBar) filteredUsers else users.filter { it != myUsername },
                            groups = if (showSearchBar) filteredGroups else groups,
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
                            },
                            onGroupClick = onOpenGroupChat,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    // Profile Dialog
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = {
                Text(
                    "Edit Profile",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    // Profile Picture
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE8E8E8))
                            .align(Alignment.CenterHorizontally)
                            .clickable {
                                // TODO: Add image picker
                            }
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.Gray,
                            modifier = Modifier
                                .size(40.dp)
                                .align(Alignment.Center)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(primaryBlue)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Change photo",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(16.dp)
                                    .align(Alignment.Center)
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Your Name") },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryBlue,
                            focusedLabelColor = primaryBlue
                        )
                    )

                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = profileStatus,
                        onValueChange = { profileStatus = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Status") },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryBlue,
                            focusedLabelColor = primaryBlue
                        ),
                        placeholder = { Text("Hey there! I'm using FreeChat") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = profileName.trim()
                        if (trimmed.isNotBlank() && trimmed != myUsername) {
                            viewModel.updateUsername(trimmed)
                        }
                        showProfileDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Changes")
                }

            },
            dismissButton = {
                TextButton(
                    onClick = {
                        profileName = originalUsername   // ✅ restore previous name
                        showProfileDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }

            },
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Add User Dialog
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Add New Contact", fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newUserName,
                        onValueChange = { newUserName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Username") },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryBlue,
                            focusedLabelColor = primaryBlue
                        )
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Enter the username to add as contact",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameTrimmed = newUserName.trim()
                        if (nameTrimmed.isNotBlank()) {
                            viewModel.addDiscoveredUser(nameTrimmed)
                            newUserName = ""        // ✅ clear after add
                            showAddUserDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    Text("Add Contact")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    newUserName = ""
                    showAddUserDialog = false
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Group Name Dialog
    if (showGroupNameDialog) {
        AlertDialog(
            onDismissRequest = { showGroupNameDialog = false },
            title = {
                Text(
                    "New Group",
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Group name") },
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primaryBlue,
                            focusedLabelColor = primaryBlue
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Members: ${selectedUsersForGroup.size + 1} (including you)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            },
            confirmButton = {
                val isEnabled = groupName.trim().isNotBlank() && selectedUsersForGroup.isNotEmpty()

                Button(
                    onClick = {
                        val trimmedName = groupName.trim()
                        if (trimmedName.isNotBlank() && selectedUsersForGroup.isNotEmpty()) {
                            viewModel.createGroupChat(
                                groupName = trimmedName,
                                members = selectedUsersForGroup + myUsername,
                                createdBy = myUsername
                            )

                            groupName = ""
                            selectedUsersForGroup = emptyList()
                            showGroupNameDialog = false
                            isCreatingGroup = false
                        }
                    },
                    enabled = isEnabled,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primaryBlue
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Create Group")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        groupName = ""
                        showGroupNameDialog = false
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(12.dp)
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

@Composable
fun GroupCreationContent(
    users: List<String>,
    myUsername: String,
    selectedUsers: List<String>,
    onUserClick: (String) -> Unit,
    onCreateGroup: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Selected Users Preview
        if (selectedUsers.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F2F5)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Selected (${selectedUsers.size})",
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )

                        // Create Group Button
                        Button(
                            onClick = onCreateGroup,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryBlue
                            ),
                            shape = RoundedCornerShape(8.dp),
                            enabled = selectedUsers.isNotEmpty()
                        ) {
                            Text("Create Group")
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedUsers.forEach { user ->
                            SelectedUserChip(
                                user = user,
                                onRemove = { onUserClick(user) }
                            )
                        }
                    }
                }
            }
        } else {
            // Show message when no users selected
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFF0F2F5)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Select users to create a group",
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray
                    )

                    Text(
                        "0 selected",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // User List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (users.isEmpty()) {
                item {
                    EmptyState(
                        message = "No contacts to add",
                        modifier = Modifier.fillParentMaxSize()
                    )
                }
            } else {
                items(users) { user ->
                    UserSelectionItem(
                        user = user,
                        isSelected = selectedUsers.contains(user),
                        onClick = { onUserClick(user) }
                    )
                    Divider(color = Color(0xFFF0F2F5), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: Int? = null,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clickable { onClick() }
    ) {
        Card(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF0F2F5)
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Icon(
                        painter = painterResource(id = icon),
                        contentDescription = label,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = label,
                        tint = Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray,
            maxLines = 1
        )
    }
}

@Composable
fun UserListContent(
    users: List<String>,
    groups: List<Group>,
    myUsername: String,
    unreadCounts: Map<String, Int>,
    lastMessages: Map<String, String>,
    isGroupSelect: Boolean,
    selectedUsers: List<String>,
    onUserClick: (String) -> Unit,
    onGroupClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Groups Section
        if (groups.isNotEmpty() && !isGroupSelect) {
            items(groups) { group ->
                GroupListItem(
                    group = group,
                    onClick = { onGroupClick(group.id) }
                )
                Divider(color = Color(0xFFF0F2F5), thickness = 0.5.dp)
            }
        }

        // Users Section
        if (users.isEmpty() && groups.isEmpty()) {
            item {
                EmptyState(
                    message = "No contacts available",
                    modifier = Modifier.fillParentMaxSize()
                )
            }
        } else {
            items(users) { user ->
                UserListItem(
                    user = user,
                    isSelected = selectedUsers.contains(user),
                    isGroupSelect = isGroupSelect,
                    unreadCount = unreadCounts[user] ?: 0,
                    lastMessage = lastMessages[user] ?: "",
                    onClick = { onUserClick(user) }
                )
                Divider(color = Color(0xFFF0F2F5), thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun GroupListItem(
    group: Group,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Group Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8E8E8))
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.group_user),
                    contentDescription = "Group",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center)
                )
            }

            Spacer(Modifier.width(16.dp))

            // Group Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = group.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "${group.members.size} members",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            // Last Message Preview
            if (group.lastMessage.isNotEmpty()) {
                Text(
                    text = group.lastMessage,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    modifier = Modifier
                        .widthIn(max = 140.dp)
                        .padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun UserListItem(
    user: String,
    isSelected: Boolean,
    isGroupSelect: Boolean,
    unreadCount: Int,
    lastMessage: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) Color(0xFFE8F5E8) else Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isGroupSelect) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) primaryBlue else Color.LightGray)
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.Center)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
            }

            // User Avatar
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8E8E8))
            ) {
                Text(
                    text = user.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.width(16.dp))

            // User Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = user,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color.Black
                )

                if (lastMessage.isNotEmpty() && !isGroupSelect) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = lastMessage,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
            }

            // Unread Badge
            if (!isGroupSelect && unreadCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(primaryBlue)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun UserSelectionItem(
    user: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) primaryBlue else Color.LightGray)
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.Center)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            // User Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8E8E8))
            ) {
                Text(
                    text = user.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            Spacer(Modifier.width(16.dp))

            Text(
                text = user,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SelectedUserChip(
    user: String,
    onRemove: () -> Unit
) {
    Surface(
        onClick = onRemove,
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                user,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(id = R.drawable.voice_ai),
                contentDescription = null,
                tint = Color(0xFFCCCCCC),
                modifier = Modifier.size(60.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}