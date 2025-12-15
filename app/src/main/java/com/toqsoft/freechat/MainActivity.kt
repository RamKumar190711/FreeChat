package com.toqsoft.freechat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.toqsoft.freechat.app.BadgeManager
import com.toqsoft.freechat.coreNetwork.AgoraManager
import com.toqsoft.freechat.featureCall.view.CallingScreen
import com.toqsoft.freechat.featureCall.view.IncomingCallOverlay
import com.toqsoft.freechat.featureCall.view.SpeakingScreen
import com.toqsoft.freechat.featureChat.ui.ChatScreen
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import com.toqsoft.freechat.featureList.view.UserListScreen
import com.toqsoft.freechat.ui.theme.FreeChatTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import io.agora.rtc2.IRtcEngineEventHandler

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Agora
        AgoraManager.init(this, object : IRtcEngineEventHandler() {})

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

    override fun onDestroy() {
        super.onDestroy()
        AgoraManager.destroy()
    }
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = "users") {

        // User list screen
        composable("users") {
            UserListScreen(onOpenChat = { otherUser ->
                navController.navigate("chat/$otherUser")
            })
        }

        composable("calling/{otherUserId}/{audioOnly}") { backStack ->
            val otherUserId = backStack.arguments?.getString("otherUserId")!!
            CallingScreen(
                otherUserId = otherUserId,
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        composable("speak/{otherUserId}/{audioOnly}") { backStack ->
            val otherUserId = backStack.arguments?.getString("otherUserId")!!
            val audioOnly = backStack.arguments?.getString("audioOnly")!!.toBoolean()

            SpeakingScreen(
                otherUserId = otherUserId,
                audioOnly = audioOnly,
                onHangUp = {
                    navController.popBackStack("chat/$otherUserId", false)
                }
            )
        }



        // Chat screen with overlay support
        composable("chat/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable
            val viewModel: ChatViewModel = hiltViewModel(backStackEntry)

            Box {
                // Chat UI
                ChatScreen(
                    otherUserId = otherUserId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    navController = navController
                )

                // Incoming call overlay (shows if a call is active)
                AgoraManager.rtcEngine?.let { engine ->
                    IncomingCallOverlay(rtcEngine = engine, navController = navController)
                }
            }
        }
    }
}
