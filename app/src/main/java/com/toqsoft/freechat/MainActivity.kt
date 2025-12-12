package com.toqsoft.freechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toqsoft.freechat.app.BadgeManager
import com.toqsoft.freechat.featureChat.ui.ChatScreen
import com.toqsoft.freechat.featureList.view.UserListScreen
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
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
    override fun onResume() {
        super.onResume()
        BadgeManager.clear(this)
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

            // Scope ViewModel to NavBackStackEntry to survive navigation
            val viewModel: ChatViewModel = hiltViewModel(backStackEntry)

            ChatScreen(
                otherUserId = otherUserId,
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
