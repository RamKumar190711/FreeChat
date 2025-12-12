package com.toqsoft.freechat.featureList.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.toqsoft.freechat.featureList.viewModel.UserListViewModel

@Composable
fun UserListScreen(
    onOpenChat: (otherUserId: String) -> Unit,
    viewModel: UserListViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsState()
    val myUsername by viewModel.myUsername.collectAsState()
    val unreadCounts by viewModel.unreadCounts.collectAsState()
    val lastMessages by viewModel.lastMessages.collectAsState()

    var inputName by remember { mutableStateOf(myUsername) }
    LaunchedEffect(myUsername) { inputName = myUsername }

    var showAddUserDialog by remember { mutableStateOf(false) }
    var newUserName by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddUserDialog = true }) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add User")
            }
        }
    ) { paddingValues ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {

            // Username input
            Text("Your name:")
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = inputName,
                onValueChange = { inputName = it },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))
            if (myUsername.isEmpty()) {
                Button(onClick = {
                    val nameTrimmed = inputName.trim()
                    if (nameTrimmed.isNotBlank()) viewModel.announceSelf(nameTrimmed)
                }) { Text("Save & Announce") }

                Spacer(Modifier.height(16.dp))
            }

            // User list
            Text("Users:")
            Spacer(Modifier.height(8.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(users.filter { it != myUsername }) { user ->
                    val unread = unreadCounts[user] ?: 0
                    val lastMsg = lastMessages[user] ?: ""

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenChat(user)
                                viewModel.clearUnreadCount(user)
                            },
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(user, style = MaterialTheme.typography.bodyLarge)
                                if (lastMsg.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        lastMsg,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray,
                                        maxLines = 1
                                    )
                                }
                            }

                            if (unread > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Color(0xFF0D88FF),
                                            shape = RoundedCornerShape(20.dp)
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
                    }
                }
            }
        }
    }

    // Add User Dialog
    if (showAddUserDialog) {
        AlertDialog(
            onDismissRequest = { showAddUserDialog = false },
            title = { Text("Add User") },
            text = {
                OutlinedTextField(
                    value = newUserName,
                    onValueChange = { newUserName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Username") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newUserName.trim()
                    if (name.isNotBlank() && name != myUsername) {
                        viewModel.addDiscoveredUser(name)
                    }
                    newUserName = ""
                    showAddUserDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = {
                    newUserName = ""
                    showAddUserDialog = false
                }) { Text("Cancel") }
            }
        )
    }
}
