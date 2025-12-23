package com.toqsoft.freechat

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.toqsoft.freechat.app.MyFirebaseMessagingService
import com.toqsoft.freechat.coreNetwork.*
import com.toqsoft.freechat.featureCall.view.*
import com.toqsoft.freechat.featureChat.ui.ChatScreen
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import com.toqsoft.freechat.featureList.view.UserListScreen
import com.toqsoft.freechat.ui.theme.FreeChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var navController: androidx.navigation.NavHostController

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) AgoraManager.init(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        if (
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // 🔥 INIT IMMEDIATELY
            AgoraManager.init(this)
        } else {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        handleIntent(intent)

        setContent {
            FreeChatTheme {
                navController = rememberNavController()
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(navController)
                    IncomingCallOverlay(navController)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val type = intent?.getStringExtra(EXTRA_TYPE)
        if (type == TYPE_CALL) {
            val callerId = intent.getStringExtra("EXTRA_CALLER_ID") ?: "Unknown Caller"
            val callId = intent.getStringExtra("EXTRA_CALL_ID") ?: ""
            val channel = intent.getStringExtra("EXTRA_CHANNEL") ?: ""
            val token = intent.getStringExtra("EXTRA_TOKEN") ?: ""
            val receiverId = intent.getStringExtra("EXTRA_RECEIVER_ID") ?: ""
            val audioOnly = intent.getBooleanExtra("EXTRA_AUDIO_ONLY", true)

            IncomingCallManager.showIncomingCall(callerId, receiverId, channel, token, callId, audioOnly)
        }
    }
}

@Composable
fun AppNavHost(navController: androidx.navigation.NavHostController) {
    val chatViewModel: ChatViewModel = hiltViewModel()

    NavHost(navController = navController, startDestination = "users") {
        composable("users") {
            UserListScreen(onOpenChat = { id -> navController.navigate("chat/$id") })
        }

        composable(
            route = "chat/{otherUserId}",
            arguments = listOf(navArgument("otherUserId") { type = NavType.StringType })
        ) { entry ->
            ChatScreen(
                otherUserId = entry.arguments?.getString("otherUserId").orEmpty(),
                viewModel = chatViewModel,
                onBack = { navController.popBackStack() },
                navController = navController
            )
        }

        composable(
            route = "calling/{callId}/{callerId}/{receiverId}/{audioOnly}",
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("callerId") { type = NavType.StringType },
                navArgument("receiverId") { type = NavType.StringType },
                navArgument("audioOnly") { type = NavType.BoolType }
            )
        ) { entry ->
            CallingScreen(
                callId = entry.arguments!!.getString("callId")!!,
                callerId = entry.arguments!!.getString("callerId")!!,
                receiverId = entry.arguments!!.getString("receiverId")!!,
                audioOnly = entry.arguments!!.getBoolean("audioOnly"),
                navController = navController,
                onCancel = { navController.popBackStack() }
            )
        }



        composable(
            route = "speak/{callId}/{callerId}/{receiverId}/{audioOnly}/{otherUserId}",
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("callerId") { type = NavType.StringType },
                navArgument("receiverId") { type = NavType.StringType },
                navArgument("audioOnly") { type = NavType.BoolType },
                navArgument("otherUserId") { type = NavType.StringType }
            )
        ) { entry ->
            val args = entry.arguments
            SpeakingScreen(
                callId = args?.getString("callId").orEmpty(),
                callerId = args?.getString("callerId").orEmpty(),
                receiverId = args?.getString("receiverId").orEmpty(),
                otherUserId = args?.getString("otherUserId").orEmpty(),
                audioOnly = args?.getBoolean("audioOnly") ?: true,
                onHangUp = {
                    navController.navigate("users") {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}