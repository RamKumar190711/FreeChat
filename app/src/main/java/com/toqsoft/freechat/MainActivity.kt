package com.toqsoft.freechat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val REQUEST_MIC = 101


    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                initAgora()
            } else {
                // Permission denied: show message or disable audio features
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestMicPermission()

        setContent {
            FreeChatTheme {
                Scaffold { AppNavHost() }
            }
        }
    }

    private fun requestMicPermission() {
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun initAgora() {
        AgoraManager.init(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        AgoraManager.destroy()
    }
}
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val viewModel: ChatViewModel = hiltViewModel()

    NavHost(navController, startDestination = "users") {

        // User list screen
        composable("users") {
            UserListScreen(onOpenChat = { otherUser ->
                navController.navigate("chat/$otherUser")
            })
        }

        // Calling screen (before connection)
        composable("calling/{otherUserId}/{audioOnly}") { backStack ->
            val otherUserId = backStack.arguments?.getString("otherUserId")!!
            CallingScreen(
                otherUserId = otherUserId,
                onCancel = {
                    navController.popBackStack()
                }
            )
        }

        // Speaking screen (active call)
        composable(
            route = "speak/{callId}/{callerId}/{receiverId}/{audioOnly}"
        ) { backStack ->

            val callId = backStack.arguments?.getString("callId")!!
            val callerId = backStack.arguments?.getString("callerId")!!
            val receiverId = backStack.arguments?.getString("receiverId")!!
            val audioOnly = backStack.arguments?.getString("audioOnly")!!.toBoolean()

            // 👈 Decide who is "other user"
            val otherUserId =
                if (callerId == receiverId) receiverId else callerId

            SpeakingScreen(
                callId = callId,
                callerId = callerId,
                receiverId = receiverId,
                otherUserId = otherUserId,
                audioOnly = audioOnly,
                onHangUp = {
                    navController.popBackStack(
                        "chat/$otherUserId",
                        inclusive = false
                    )
                }
            )
        }


        // Chat screen
        composable("chat/{otherUserId}") { backStackEntry ->
            val otherUserId = backStackEntry.arguments?.getString("otherUserId") ?: return@composable

            Box {
                ChatScreen(
                    otherUserId = otherUserId,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    navController = navController
                )

                // Incoming call overlay
                AgoraManager.rtcEngine?.let { engine ->
                    IncomingCallOverlay( navController = navController)
                }
            }
        }
    }
}
