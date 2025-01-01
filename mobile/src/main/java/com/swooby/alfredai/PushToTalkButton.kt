package com.swooby.alfredai

import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swooby.alfredai.ui.theme.AlfredAITheme

enum class PTTState {
    Idle,
    Pressed
}

@Composable
fun PushToTalkButton(
    modifier: Modifier = Modifier,
    iconIdle: Int = R.drawable.outline_mic_24,
    iconPressed: Int = R.drawable.outline_mic_24,
    onPushToTalkStart: () -> Unit = { Log.d("PTT", "Push-to-Talk Start") },
    onPushToTalkStop: () -> Unit = { Log.d("PTT", "Push-to-Talk Stop") }
) {
    var pttState by remember { mutableStateOf(PTTState.Idle) }
    val context = LocalContext.current
    val vibrator = context.getSystemService(VibratorManager::class.java)
        ?.defaultVibrator ?: context.getSystemService(Vibrator::class.java)

    fun provideHapticFeedback() {
        vibrator?.vibrate(
            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }

    fun provideAudibleFeedback() {
        val resId = when (pttState) {
            PTTState.Idle -> R.raw.quindar_nasa_apollo_outro
            PTTState.Pressed -> R.raw.quindar_nasa_apollo_intro
        }
        val mediaPlayer = MediaPlayer.create(context, resId)
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener {
            it.release()
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(100.dp)
            .border(4.dp, Color.Gray, shape = CircleShape)
            .background(
                color = if (pttState == PTTState.Pressed) Color.Green else Color.Transparent,
                shape = CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    if (pttState == PTTState.Idle) {
                        pttState = PTTState.Pressed
                        onPushToTalkStart()
                        provideHapticFeedback()
                        provideAudibleFeedback()
                    }
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { !it.changedToUp() })
                    if (pttState == PTTState.Pressed) {
                        pttState = PTTState.Idle
                        onPushToTalkStop()
                        provideHapticFeedback()
                        provideAudibleFeedback()
                    }
                }
            }
    ) {
        val iconRes = if (pttState == PTTState.Pressed) iconPressed else iconIdle
        androidx.compose.foundation.Image(
            painter = painterResource(id = iconRes),
            contentDescription = "microphone",
            modifier = Modifier
                .size(60.dp)
                .border(1.dp, Color.Magenta)
        )
    }
}

@Preview
@Composable
fun PushToTalkButtonPreview() {
    AlfredAITheme {
        PushToTalkButton()
    }
}
