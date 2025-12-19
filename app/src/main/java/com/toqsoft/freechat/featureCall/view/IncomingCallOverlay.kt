package com.toqsoft.freechat.featureCall.view

import androidx.compose.animation.core.Animatable
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
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.toqsoft.freechat.coreModel.IncomingCallData
import com.toqsoft.freechat.coreNetwork.AgoraManager
import com.toqsoft.freechat.coreNetwork.IncomingCallManager
import kotlinx.coroutines.launch

@Composable
fun IncomingCallOverlay(navController: NavController) {

    val incomingCall by IncomingCallManager.incomingCall.collectAsState()
    val scope = rememberCoroutineScope()

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

            // 👉👈 Swipe Handle
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
                                        acceptCall(call, navController)
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

/** ✅ ACCEPT CALL */
private fun acceptCall(
    call: IncomingCallData,
    navController: NavController
) {
    AgoraManager.rtcEngine?.joinChannel(
        call.token,
        call.channel,
        null,
        0
    )

    FirebaseFirestore.getInstance()
        .collection("chats")
        .document(listOf(call.callerId, call.receiverId).sorted().joinToString("_"))
        .collection("messages")
        .document(call.callId)
        .update("status", "accepted")

    IncomingCallManager.clearCall()
    navController.navigate(
        "speak/${call.callId}/${call.callerId}/${call.receiverId}/${call.audioOnly}"
    )

}

/** ❌ REJECT CALL */
private fun rejectCall(call: IncomingCallData) {

    FirebaseFirestore.getInstance()
        .collection("chats")
        .document(listOf(call.callerId, call.receiverId).sorted().joinToString("_"))
        .collection("messages")
        .document(call.callId)
        .update("status", "rejected")

    IncomingCallManager.clearCall()
}
