package com.swooby.alfredai

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swooby.alfredai.ui.theme.AlfredAITheme

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true
)
@Composable
fun PushToTalkButtonPreviewLight() {
    AlfredAITheme {
        PushToTalkButton(MobileViewModelPreview())
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun PushToTalkButtonPreviewDark() {
    AlfredAITheme {
        PushToTalkButton(MobileViewModelPreview())
    }
}

@Composable
fun PushToTalkButton(
    mobileViewModel: MobileViewModelInterface,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    iconIdle: Int = R.drawable.baseline_mic_24,
    iconPressed: Int = R.drawable.baseline_mic_24,
    iconDisabled: Int = R.drawable.baseline_mic_off_24,
    onPushToTalkStart: () -> Unit = {},
    onPushToTalkStop: () -> Unit = {}
) {
    val pttState by mobileViewModel.pushToTalkState.collectAsState()

    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)
    val boxAlpha = if (enabled) 1.0f else 0.38f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(120.dp)
            .border(4.dp, if (enabled) MaterialTheme.colorScheme.primary else disabledColor, shape = CircleShape)
            .background(
                color = if (pttState == SharedViewModel.PttState.Pressed) Color.Green else Color.Transparent,
                shape = CircleShape
            )
            .let { baseModifier ->
                if (enabled) {
                    baseModifier.pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            onPushToTalkStart()
                            do {
                                val event = awaitPointerEvent()
                            } while (event.changes.any { !it.changedToUp() })
                            onPushToTalkStop()
                        }
                    }
                } else {
                    baseModifier.pointerInput(Unit) {
                        awaitEachGesture {
                            while (true) {
                                val event = awaitPointerEvent()
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            }
            .then(Modifier.background(Color.Transparent))
            .let {
                it.background(Color.Transparent).graphicsLayer {
                    this.alpha = boxAlpha
                }
            }
    ) {
        val iconRes = if (enabled) {
            if (pttState == SharedViewModel.PttState.Pressed) {
                iconPressed
            } else {
                iconIdle
            }
        } else {
            iconDisabled
        }
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = "Microphone",
            modifier = Modifier
                .size(90.dp)
        )
    }
}
