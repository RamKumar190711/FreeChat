package com.toqsoft.freechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toqsoft.freechat.featureChat.ui.ChatScreen
import com.toqsoft.freechat.featureList.view.UserListScreen
import com.toqsoft.freechat.ui.theme.FreeChatTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FreeChatTheme {
                Scaffold { AppNavHost() }
            }
        }
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "users") {

        composable("users") {
            UserListScreen(onOpenChat = { otherUser ->
                navController.navigate("chat/$otherUser")
            })
        }

        composable("chat/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
            ChatScreen(otherUserId = otherUserId, onBack = { navController.popBackStack() })
        }
    }
}

