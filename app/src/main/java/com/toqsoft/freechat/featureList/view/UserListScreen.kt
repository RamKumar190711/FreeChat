package com.toqsoft.freechat.featureList.view

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.hilt.navigation.compose.hiltViewModel
import com.toqsoft.freechat.featureList.viewModel.UserListViewModel

@Composable
fun UserListScreen(
    onOpenChat: (otherUserId: String) -> Unit,
    viewModel: UserListViewModel = hiltViewModel()
) {
    val users by viewModel.users.collectAsState()
    val myUsername by viewModel.myUsername.collectAsState(initial = "")

    var inputName by remember { mutableStateOf(myUsername) }
    var discoverName by remember { mutableStateOf("") }

    LaunchedEffect(myUsername) { inputName = myUsername }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Your Name
        Text("Your name:")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = inputName,
            onValueChange = { inputName = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter your display name") }
        )
        Spacer(Modifier.height(8.dp))
        if (myUsername.isEmpty()) {
            Button(onClick = {
                val nameTrimmed = inputName.trim()
                if (nameTrimmed.isNotBlank()) viewModel.announceSelf(nameTrimmed)
            }) {
                Text("Save & Announce")
            }
            Spacer(Modifier.height(16.dp))
        }

        // Discover Name
        Text("Discover name:")
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = discoverName,
            onValueChange = { discoverName = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter a name to add") }
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = {
            val nameTrimmed = discoverName.trim()
            if (nameTrimmed.isNotEmpty() && nameTrimmed != inputName) {
                viewModel.addDiscoveredUser(nameTrimmed)
                discoverName = "" // clear field after saving
            }
        }) { Text("Add User") }

        Spacer(Modifier.height(24.dp))
        Text("Users:")
        Spacer(Modifier.height(8.dp))
        LazyColumn {
            items(users.filter { it != myUsername }) { user ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpenChat(user) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(user)
                }
            }
        }
    }
}
