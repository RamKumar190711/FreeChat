package com.toqsoft.freechat

import android.Manifest
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.app.MyFirebaseMessagingService
import com.toqsoft.freechat.coreNetwork.*
import com.toqsoft.freechat.featureCall.view.*
import com.toqsoft.freechat.featureChat.ui.ChatScreen
import com.toqsoft.freechat.featureChat.view.GroupChatScreen
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import com.toqsoft.freechat.featureList.view.UserListScreen
import com.toqsoft.freechat.featureList.viewModel.UserListViewModel
import com.toqsoft.freechat.featureVideo.view.VideoCallScreen
import com.toqsoft.freechat.ui.theme.FreeChatTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[Manifest.permission.CAMERA] ?: false
        val micGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false

        if (!cameraGranted || !micGranted) {
            Toast.makeText(
                this,
                "Camera and Microphone permissions are required",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(this, "Notifications permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)
        turnScreenOnAndKeyguard()

        // Status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowInsetsControllerCompat(window, window.decorView)
        window.statusBarColor = Color.White.toArgb()
        windowInsetsController.isAppearanceLightStatusBars = true

        checkNotificationPermission()
        requestCameraAndMicPermissions()

        setContent {
            FreeChatTheme {
                val navController = rememberNavController()
                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(navController)
                    IncomingCallOverlay(navController)
                }
            }
        }
    }

    private fun requestCameraAndMicPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.CAMERA)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }



    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Request the permission
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
    private fun turnScreenOnAndKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
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
    val userListViewModel: UserListViewModel = hiltViewModel()

    val myUsername by userListViewModel.myUsername.collectAsState(initial = "")
    LaunchedEffect(myUsername) {
        if (myUsername.isNotEmpty()) {
            listenForCallInvitations(myUsername, navController)
        }
    }
    NavHost(navController = navController, startDestination = "users") {
        composable("users") {
            UserListScreen(onOpenChat = { id -> navController.navigate("chat/$id") },
                onOpenGroupChat = { id -> navController.navigate("groupChat/$id")},
                navController = navController)
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
            route = "userListFromCall/{callId}/{callerId}/{receiverId}",
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("callerId") { type = NavType.StringType },
                navArgument("receiverId") { type = NavType.StringType }
            )
        ) { entry ->

            UserListScreen(
                navController = navController,
                isGroupSelect = true,
                callId = entry.arguments?.getString("callId"),
                callerId = entry.arguments?.getString("callerId").orEmpty(),
                receiverId = entry.arguments?.getString("receiverId").orEmpty(),
                onOpenChat = {},
                onOpenGroupChat = {}
            )
        }


        composable(
            route = "groupCall/{callId}/{callerId}/{receiverId}/{audioOnly}/{otherUserId}",
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("callerId") { type = NavType.StringType },
                navArgument("receiverId") { type = NavType.StringType },
                navArgument("audioOnly") { type = NavType.BoolType },
                navArgument("otherUserId") { type = NavType.StringType }
            )
        ) { entry ->
            val args = entry.arguments
            val users by userListViewModel.users.collectAsState()
            val myUsername by userListViewModel.myUsername.collectAsState()

            SpeakingScreen(
                callId = args?.getString("callId").orEmpty(),
                callerId = args?.getString("callerId").orEmpty(),
                receiverId = args?.getString("receiverId").orEmpty(),
                otherUserId = args?.getString("otherUserId").orEmpty(),
                audioOnly = args?.getBoolean("audioOnly") ?: true,
                onHangUp = {
                    navController.navigate("users") {
                        popUpTo("users") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAddUser = {
                    navController.navigate(
                        "userListFromCall/" +
                                "${args?.getString("callId")}/" +
                                "${args?.getString("callerId")}/" +
                                "${args?.getString("receiverId")}"
                    )
                },
                navController = navController,
                users = users,
                myUsername = myUsername
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
            route = "videoCall/{callId}/{callerId}/{receiverId}",
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("callerId") { type = NavType.StringType },
                navArgument("receiverId") { type = NavType.StringType }
            )
        ) { backStackEntry ->

            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val callerId = backStackEntry.arguments?.getString("callerId") ?: ""
            val receiverId = backStackEntry.arguments?.getString("receiverId") ?: ""

            VideoCallScreen(
                navController = navController,
                callId = callId,
                callerId = callerId,
                receiverId = receiverId,
            )
        }

        // Add this to your navigation graph:
        composable(
            route = "groupChat/{groupId}",
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupChatScreen(
                groupId = groupId,
                onBack = { navController.popBackStack() }
            )
        }


        composable("callInvitation/{callId}/{callerId}/{chatId}/{myUsername}") { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val callerId = backStackEntry.arguments?.getString("callerId") ?: ""
            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
            val myUsername = backStackEntry.arguments?.getString("myUsername") ?: ""

            CallInvitationScreen(
                navController = navController,
                callId = callId,
                callerId = callerId,
                chatId = chatId,
                myUsername = myUsername
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
            val users by userListViewModel.users.collectAsState()
            val myUsername by userListViewModel.myUsername.collectAsState()

            SpeakingScreen(
                callId = args?.getString("callId").orEmpty(),
                callerId = args?.getString("callerId").orEmpty(),
                receiverId = args?.getString("receiverId").orEmpty(),
                otherUserId = args?.getString("otherUserId").orEmpty(),
                audioOnly = args?.getBoolean("audioOnly") ?: true,
                onHangUp = {
                    navController.navigate("users") {
                        popUpTo("users") { inclusive = false }
                        launchSingleTop = true
                    }
                },
                onAddUser = {
                    navController.navigate(
                        "userListFromCall/" +
                                "${args?.getString("callId")}/" +
                                "${args?.getString("callerId")}/" +
                                "${args?.getString("receiverId")}"
                    )
                },
                navController = navController,
                users = users,             // ✅ pass users
                myUsername = myUsername    // ✅ pass my username
            )


        }
    }
}
fun listenForCallInvitations(myUsername: String, navController: NavController) {
    FirebaseFirestore.getInstance()
        .collection("notifications")
        .document(myUsername)
        .collection("call_invitations")
        .addSnapshotListener { snapshot, error ->
            snapshot?.documentChanges?.forEach { change ->
                if (change.type == DocumentChange.Type.ADDED) {
                    val callId = change.document.getString("callId") ?: ""
                    val callerId = change.document.getString("callerId") ?: ""
                    val chatId = change.document.getString("chatId") ?: ""

                    // Navigate to invitation screen
                    navController.navigate(
                        "callInvitation/$callId/$callerId/$chatId/$myUsername"
                    )
                }
            }
        }
}