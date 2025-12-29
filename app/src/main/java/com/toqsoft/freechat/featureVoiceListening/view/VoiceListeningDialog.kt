package com.toqsoft.freechat.featureVoiceListening.view


import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.times
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.toqsoft.freechat.R
import kotlinx.coroutines.delay

@Composable
fun VoiceListeningDialog(
    onDismiss: () -> Unit,
    liveText: String
) {
    val infiniteTransition = rememberInfiniteTransition()

    // Dots animation
    var dotCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            dotCount = (dotCount + 1) % 4
            delay(500)
        }
    }

    val circlePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Text(
                    text = "Listening" + ".".repeat(dotCount),
                    fontSize = 28.sp,
                    color = Color.White
                )

                Spacer(Modifier.height(32.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    repeat(3) { index ->
                        Box(
                            modifier = Modifier
                                .size(60.dp + index * 20.dp)
                                .scale(circlePulse)
                                .background(
                                    color = Color.White.copy(alpha = 0.1f + index * 0.05f),
                                    shape = CircleShape
                                )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .scale(1f + circlePulse * 0.2f)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xFF0D88FF), Color(0xFF42A5F5))
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.mic),
                            contentDescription = "Listening",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))

                Text(
                    text = if (liveText.isEmpty()) "Speak now…" else liveText,
                    fontSize = 18.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
