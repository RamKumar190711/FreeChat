package com.toqsoft.freechat.featureCall.view

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.toqsoft.freechat.coreModel.IncomingCallData
import com.toqsoft.freechat.coreNetwork.AgoraManager
import com.toqsoft.freechat.coreNetwork.IncomingCallManager
import com.toqsoft.freechat.featureChat.viewModel.ChatViewModel
import kotlinx.coroutines.launch

@Composable
fun IncomingCallOverlay(navController: NavController,viewModel: ChatViewModel= hiltViewModel()) {

    val incomingCall by IncomingCallManager.incomingCall.collectAsState()
    val scope = rememberCoroutineScope()
    val myId = viewModel.myUserId
    incomingCall?.let { call ->

        val swipeOffsetX = remember { Animatable(0f) }
        val acceptThreshold = 200f
        val rejectThreshold = -200f

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0A1A24),
                            Color.Black
                        )
                    )
                )
        ) {

            // 🔝 Caller Name
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = call.callerId,
                    color = Color.White,
                    fontSize = 35.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = "Incoming call",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .offset(x = swipeOffsetX.value.dp)
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    swipeOffsetX.snapTo(
                                        (swipeOffsetX.value + dragAmount.x)
                                            .coerceIn(-300f, 300f)
                                    )
                                }
                            },
                            onDragEnd = {
                                when {
                                    swipeOffsetX.value > acceptThreshold -> {
                                        acceptCall(call, navController,myId)
                                    }

                                    swipeOffsetX.value < rejectThreshold -> {
                                        rejectCall(call)
                                    }

                                    else -> {
                                        scope.launch {
                                            swipeOffsetX.animateTo(0f)
                                        }
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            // ⬅️➡️ Hint
            Text(
                text = "Swipe → to answer    |    ← to reject",
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                fontSize = 14.sp
            )
        }
    }
}
private fun acceptCall(call: IncomingCallData, navController: NavController, myUserId:String) {
    val TAG = "CALL_FLOW_DEBUG"

    val myId = myUserId
    val callerId = call.callerId
    val chatId = listOf(myId, callerId).sorted().joinToString("_")

    Log.d(TAG, "Receiver: Corrected Target chatId: $chatId  $myId")

    val docRef = FirebaseFirestore.getInstance()
        .collection("chats").document(chatId)
        .collection("messages").document(call.callId)

    val updateData = mapOf(
        "status" to "accepted",
        "channel" to call.channel,
        "token" to call.token
    )

    docRef.set(updateData, SetOptions.merge())
        .addOnSuccessListener {
            Log.d(TAG, "Receiver: Successfully updated path: chats/$chatId/messages/${call.callId}")
            IncomingCallManager.clearCall()

            // Fix the navigation path (ensure all params are present)
            navController.navigate("speak/${call.callId}/$callerId/$myId/${call.audioOnly}/$callerId") {
                launchSingleTop = true
            }
        }
}
private fun rejectCall(call: IncomingCallData) {
    val chatId = listOf(call.callerId, call.receiverId).sorted().joinToString("_")
    FirebaseFirestore.getInstance()
        .collection("chats").document(chatId)
        .collection("messages").document(call.callId)
        .set(mapOf("status" to "rejected"), SetOptions.merge())

    IncomingCallManager.clearCall()
}
