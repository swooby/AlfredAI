package com.swooby.alfredai

import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.ContentAlpha
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.openai.models.RealtimeServerEventConversationCreated
import com.openai.models.RealtimeServerEventConversationItemCreated
import com.openai.models.RealtimeServerEventConversationItemDeleted
import com.openai.models.RealtimeServerEventConversationItemInputAudioTranscriptionCompleted
import com.openai.models.RealtimeServerEventConversationItemInputAudioTranscriptionFailed
import com.openai.models.RealtimeServerEventConversationItemTruncated
import com.openai.models.RealtimeServerEventError
import com.openai.models.RealtimeServerEventInputAudioBufferCleared
import com.openai.models.RealtimeServerEventInputAudioBufferCommitted
import com.openai.models.RealtimeServerEventInputAudioBufferSpeechStarted
import com.openai.models.RealtimeServerEventInputAudioBufferSpeechStopped
import com.openai.models.RealtimeServerEventRateLimitsUpdated
import com.openai.models.RealtimeServerEventResponseAudioDelta
import com.openai.models.RealtimeServerEventResponseAudioDone
import com.openai.models.RealtimeServerEventResponseAudioTranscriptDelta
import com.openai.models.RealtimeServerEventResponseAudioTranscriptDone
import com.openai.models.RealtimeServerEventResponseContentPartAdded
import com.openai.models.RealtimeServerEventResponseContentPartDone
import com.openai.models.RealtimeServerEventResponseCreated
import com.openai.models.RealtimeServerEventResponseDone
import com.openai.models.RealtimeServerEventResponseFunctionCallArgumentsDelta
import com.openai.models.RealtimeServerEventResponseFunctionCallArgumentsDone
import com.openai.models.RealtimeServerEventResponseOutputItemAdded
import com.openai.models.RealtimeServerEventResponseOutputItemDone
import com.openai.models.RealtimeServerEventResponseTextDelta
import com.openai.models.RealtimeServerEventResponseTextDone
import com.openai.models.RealtimeServerEventSessionCreated
import com.openai.models.RealtimeServerEventSessionUpdated
import com.swooby.alfredai.openai.realtime.RealtimeClient
import com.swooby.alfredai.ui.theme.AlfredAITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PushToTalkActivity : ComponentActivity() {
    companion object {
        val TAG = PushToTalkActivity::class.simpleName
    }

    private lateinit var alfredAiApp: AlfredAiApp
    private lateinit var realtimeClient: RealtimeClient


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        alfredAiApp = applicationContext as AlfredAiApp
        realtimeClient = alfredAiApp.realtimeClient

        enableEdgeToEdge()
        setContent {
            PushToTalkApp()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realtimeClient.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushToTalkApp() {
    val applicationContext = LocalContext.current.applicationContext
    val alfredAiApp = applicationContext as? AlfredAiApp
    val realtimeClient = alfredAiApp?.realtimeClient

    var isConnectingOrConnected by remember {
        mutableStateOf(realtimeClient?.isConnectingOrConnected ?: false)
    }
    var isConnected by remember {
        mutableStateOf(realtimeClient?.isConnected ?: false)
    }

    fun connect() {
        CoroutineScope(Dispatchers.IO).launch {
            val ephemeralApiKey = realtimeClient?.connect()
            Log.d(MainActivity.TAG, "ephemeralApiKey: $ephemeralApiKey")
            if (ephemeralApiKey != null) {
                realtimeClient.setLocalAudioTrackMicrophoneEnabled(false)
            }
        }
    }

    DisposableEffect(Unit) {
        val realtimeClientListener = object : RealtimeClient.RealtimeClientListener {
            override fun onConnecting() {
                Log.d(PushToTalkActivity.TAG, "onConnecting()")
                isConnectingOrConnected = true
            }

            override fun onConnected() {
                Log.d(PushToTalkActivity.TAG, "onConnected()")
                isConnected = true
            }

            override fun onDisconnected() {
                Log.d(PushToTalkActivity.TAG, "onDisconnected()")
                isConnectingOrConnected = false
                isConnected = false
            }

            override fun onBinaryMessageReceived(data: ByteArray): Boolean {
                //Log.d(PushToTalkActivity.TAG, "onBinaryMessageReceived(): data(${data.size})=...")
                //...
                return false
            }

            override fun onTextMessageReceived(message: String): Boolean {
                //Log.d(PushToTalkActivity.TAG, "onTextMessageReceived(): message=${Utils.quote(message)}")
                //...
                return false
            }

            override fun onServerEventConversationCreated(
                realtimeServerEventConversationCreated: RealtimeServerEventConversationCreated
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventConversationCreated($realtimeServerEventConversationCreated)")
            }

            override fun onServerEventConversationItemCreated(
                realtimeServerEventConversationItemCreated: RealtimeServerEventConversationItemCreated
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventConversationItemCreated($realtimeServerEventConversationItemCreated)")
            }

            override fun onServerEventConversationItemDeleted(
                realtimeServerEventConversationItemDeleted: RealtimeServerEventConversationItemDeleted
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventConversationItemDeleted($realtimeServerEventConversationItemDeleted)")
            }

            override fun onServerEventConversationItemInputAudioTranscriptionCompleted(
                realtimeServerEventConversationItemInputAudioTranscriptionCompleted: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventConversationItemInputAudioTranscriptionCompleted($realtimeServerEventConversationItemInputAudioTranscriptionCompleted)")
            }

            override fun onServerEventConversationItemInputAudioTranscriptionFailed(
                realtimeServerEventConversationItemInputAudioTranscriptionFailed: RealtimeServerEventConversationItemInputAudioTranscriptionFailed
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventConversationItemInputAudioTranscriptionFailed($realtimeServerEventConversationItemInputAudioTranscriptionFailed)")
            }

            override fun onServerEventConversationItemTruncated(
                realtimeServerEventConversationItemTruncated: RealtimeServerEventConversationItemTruncated
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventConversationItemTruncated($realtimeServerEventConversationItemTruncated)")
            }

            override fun onServerEventError(
                realtimeServerEventError: RealtimeServerEventError
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventError($realtimeServerEventError)")
            }

            override fun onServerEventInputAudioBufferCleared(
                realtimeServerEventInputAudioBufferCleared: RealtimeServerEventInputAudioBufferCleared
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventInputAudioBufferCleared($realtimeServerEventInputAudioBufferCleared)")
            }

            override fun onServerEventInputAudioBufferCommitted(
                realtimeServerEventInputAudioBufferCommitted: RealtimeServerEventInputAudioBufferCommitted
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventInputAudioBufferCommitted($realtimeServerEventInputAudioBufferCommitted)")
            }

            override fun onServerEventInputAudioBufferSpeechStarted(
                realtimeServerEventInputAudioBufferSpeechStarted: RealtimeServerEventInputAudioBufferSpeechStarted
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventInputAudioBufferSpeechStarted($realtimeServerEventInputAudioBufferSpeechStarted)")
            }

            override fun onServerEventInputAudioBufferSpeechStopped(
                realtimeServerEventInputAudioBufferSpeechStopped: RealtimeServerEventInputAudioBufferSpeechStopped
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventInputAudioBufferSpeechStopped($realtimeServerEventInputAudioBufferSpeechStopped)")
            }

            override fun onServerEventRateLimitsUpdated(
                realtimeServerEventRateLimitsUpdated: RealtimeServerEventRateLimitsUpdated
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventRateLimitsUpdated($realtimeServerEventRateLimitsUpdated)")
            }

            override fun onServerEventResponseAudioDelta(
                realtimeServerEventResponseAudioDelta: RealtimeServerEventResponseAudioDelta
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseAudioDelta($realtimeServerEventResponseAudioDelta)")
            }

            override fun onServerEventResponseAudioDone(
                realtimeServerEventResponseAudioDone: RealtimeServerEventResponseAudioDone
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseAudioDone($realtimeServerEventResponseAudioDone)")
            }

            override fun onServerEventResponseAudioTranscriptDelta(
                realtimeServerEventResponseAudioTranscriptDelta: RealtimeServerEventResponseAudioTranscriptDelta
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseAudioTranscriptDelta($realtimeServerEventResponseAudioTranscriptDelta)")
            }

            override fun onServerEventResponseAudioTranscriptDone(
                realtimeServerEventResponseAudioTranscriptDone: RealtimeServerEventResponseAudioTranscriptDone
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseAudioTranscriptDone($realtimeServerEventResponseAudioTranscriptDone)")
            }

            override fun onServerEventResponseContentPartAdded(
                realtimeServerEventResponseContentPartAdded: RealtimeServerEventResponseContentPartAdded
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseContentPartAdded($realtimeServerEventResponseContentPartAdded)")
            }

            override fun onServerEventResponseContentPartDone(
                realtimeServerEventResponseContentPartDone: RealtimeServerEventResponseContentPartDone
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseContentPartDone($realtimeServerEventResponseContentPartDone)")
            }

            override fun onServerEventResponseCreated(
                realtimeServerEventResponseCreated: RealtimeServerEventResponseCreated
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseCreated($realtimeServerEventResponseCreated)")
            }

            override fun onServerEventResponseDone(
                realtimeServerEventResponseDone: RealtimeServerEventResponseDone
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseDone($realtimeServerEventResponseDone)")
            }

            override fun onServerEventResponseFunctionCallArgumentsDelta(
                realtimeServerEventResponseFunctionCallArgumentsDelta: RealtimeServerEventResponseFunctionCallArgumentsDelta
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseFunctionCallArgumentsDelta($realtimeServerEventResponseFunctionCallArgumentsDelta)")
            }

            override fun onServerEventResponseFunctionCallArgumentsDone(
                realtimeServerEventResponseFunctionCallArgumentsDone: RealtimeServerEventResponseFunctionCallArgumentsDone
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseFunctionCallArgumentsDone($realtimeServerEventResponseFunctionCallArgumentsDone)")
            }

            override fun onServerEventResponseOutputItemAdded(
                realtimeServerEventResponseOutputItemAdded: RealtimeServerEventResponseOutputItemAdded
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseOutputItemAdded($realtimeServerEventResponseOutputItemAdded)")
            }

            override fun onServerEventResponseOutputItemDone(
                realtimeServerEventResponseOutputItemDone: RealtimeServerEventResponseOutputItemDone
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseOutputItemDone($realtimeServerEventResponseOutputItemDone)")
            }

            override fun onServerEventResponseTextDelta(
                realtimeServerEventResponseTextDelta: RealtimeServerEventResponseTextDelta
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseTextDelta($realtimeServerEventResponseTextDelta)")
            }

            override fun onServerEventResponseTextDone(
                realtimeServerEventResponseTextDone: RealtimeServerEventResponseTextDone
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventResponseTextDone($realtimeServerEventResponseTextDone)")
            }

            override fun onServerEventSessionCreated(
                realtimeServerEventSessionCreated: RealtimeServerEventSessionCreated
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventSessionCreated($realtimeServerEventSessionCreated)")
            }

            override fun onServerEventSessionUpdated(
                realtimeServerEventSessionUpdated: RealtimeServerEventSessionUpdated
            ) {
                Log.d(PushToTalkActivity.TAG, "onServerEventSessionUpdated($realtimeServerEventSessionUpdated)")
            }
        }

        realtimeClient?.addListener(realtimeClientListener)

        onDispose {
            realtimeClient?.removeListener(realtimeClientListener)
        }
    }

    AlfredAITheme(dynamicColor = false) {
        val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)

        Scaffold(modifier = Modifier
            //.border(1.dp, Color.Red)
            .fillMaxSize(),
            topBar = {
                TopAppBar(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                    title = { Text("PushToTalk") },
                )
            }
        ) { innerPadding ->

            LaunchedEffect(Unit) {
                connect()
            }

            ConstraintLayout(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                val (rowConnectSettings, buttonPushToTalk, rowStopReset) = createRefs()

                Row(
                    modifier = Modifier.constrainAs(rowConnectSettings) {
                        bottom.linkTo(buttonPushToTalk.top, margin = 24.dp)
                        centerHorizontallyTo(parent)
                    },
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Switch(
                        checked = isConnectingOrConnected,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                connect()
                            } else {
                                realtimeClient?.disconnect()
                            }
                        }
                    )

                    IconButton(
                        onClick = {
                            Log.d(PushToTalkActivity.TAG, "Settings button clicked")
                            Toast.makeText(applicationContext, "Settings not yet implemented", Toast.LENGTH_SHORT).show()
                            //...
                        }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.baseline_settings_24),
                            contentDescription = "Settings"
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(150.dp)
                        //.border(1.dp, Color.Green)
                        .constrainAs(buttonPushToTalk) {
                            centerTo(parent)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                        //.border(1.dp, Color.Magenta)
                    ) {
                        when {
                            isConnected -> {
                                CircularProgressIndicator(
                                    progress = { 1f },
                                    color = Color.Green,
                                    strokeWidth = 6.dp,
                                    modifier = Modifier.size(150.dp)
                                )
                            }

                            isConnectingOrConnected -> {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 6.dp,
                                    modifier = Modifier.size(150.dp)
                                )
                            }

                            else -> {
                                CircularProgressIndicator(
                                    progress = { 1f },
                                    color = disabledColor,
                                    strokeWidth = 6.dp,
                                    modifier = Modifier.size(150.dp)
                                )
                            }
                        }
                    }

                    PushToTalkButton(
                        enabled = isConnected,
                        onPushToTalkStart = { pttState ->
                            Log.d(MainActivity.TAG, "")
                            Log.d(MainActivity.TAG, "+onPushToTalkStart: pttState=$pttState")
                            // 1. Play the start sound
                            Log.d(MainActivity.TAG, "onPushToTalkStart: playing start sound")
                            Utils.playAudioResourceOnce(
                                context = applicationContext,
                                audioResourceId = R.raw.quindar_nasa_apollo_intro,
                                volume = 0.2f,
                            ) {
                                // 2. Wait for the start sound to finish
                                Log.d(MainActivity.TAG, "onPushToTalkStart: start sound finished")
                                // 3. Open the mic
                                Log.d(MainActivity.TAG, "onPushToTalkStart: opening mic")
                                realtimeClient?.setLocalAudioTrackMicrophoneEnabled(true)
                                Log.d(MainActivity.TAG, "onPushToTalkStart: mic opened")
                                // 4. Wait for the mic to open successfully
                                //...
                                Log.d(MainActivity.TAG, "-onPushToTalkStart")
                                Log.d(MainActivity.TAG, "")
                            }
                            true
                        },
                        onPushToTalkStop = { pttState ->
                            Log.d(MainActivity.TAG, "")
                            Log.d(MainActivity.TAG, "+onPushToTalkStop: pttState=$pttState")
                            // 1. Close the mic
                            Log.d(MainActivity.TAG, "onPushToTalkStop: closing mic")
                            realtimeClient?.setLocalAudioTrackMicrophoneEnabled(false)
                            Log.d(MainActivity.TAG, "onPushToTalkStop: mic closed")
                            // 2. Wait for the mic to close successfully
                            //...
                            // 3. Send input_audio_buffer.commit
                            Log.d(
                                MainActivity.TAG,
                                "onPushToTalkStop: sending input_audio_buffer.commit"
                            )
                            realtimeClient?.dataSendInputAudioBufferCommit()
                            Log.d(
                                MainActivity.TAG,
                                "onPushToTalkStop: input_audio_buffer.commit sent"
                            )
                            // 4. Send response.create
                            Log.d(MainActivity.TAG, "onPushToTalkStop: sending response.create")
                            realtimeClient?.dataSendResponseCreate()
                            Log.d(MainActivity.TAG, "onPushToTalkStop: response.create sent")
                            // 5. Play the stop sound
                            Log.d(MainActivity.TAG, "onPushToTalkStop: playing stop sound")
                            Utils.playAudioResourceOnce(
                                context = applicationContext,
                                audioResourceId = R.raw.quindar_nasa_apollo_outro,
                                volume = 0.2f,
                            ) {
                                // 6. Wait for the stop sound to finish
                                Log.d(MainActivity.TAG, "onPushToTalkStop: stop sound finished")
                                //...
                                Log.d(MainActivity.TAG, "-onPushToTalkStop")
                                Log.d(MainActivity.TAG, "")
                            }
                            true
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .constrainAs(rowStopReset) {
                            top.linkTo(buttonPushToTalk.bottom, margin = 24.dp)
                            centerHorizontallyTo(parent)
                        },
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .border(
                                4.dp,
                                if (isConnected) MaterialTheme.colorScheme.primary else disabledColor,
                                shape = CircleShape
                            )
                    ) {
                        IconButton(
                            enabled = isConnected,
                            onClick = {
                                realtimeClient?.dataSendInputAudioBufferClear()
                            },
                            modifier = Modifier
                                .size(66.dp)
                                // Whoever designed this `restart_alt` icon did it wrong.
                                // They should have centered it in its border instead of having an asymmetric protrusion on the top.
                                .offset(y = -2.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_restart_alt_24),
                                contentDescription = "Reset",
                                modifier = Modifier
                                    .size(50.dp)
                                //.border(1.dp, Color.Magenta)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .border(
                                4.dp,
                                if (isConnected) MaterialTheme.colorScheme.primary else disabledColor,
                                shape = CircleShape
                            )
                    ) {
                        IconButton(
                            enabled = isConnected,
                            onClick = {
                                realtimeClient?.dataSendResponseCancel()
                            },
                            modifier = Modifier
                                .size(66.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_stop_24),
                                contentDescription = "Stop",
                                modifier = Modifier
                                    .size(50.dp)
                                //.border(1.dp, Color.Magenta)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true
)
@Composable
fun PushToTalkButtonActivityPreviewLight() {
    PushToTalkApp()
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun PushToTalkButtonActivityPreviewDark() {
    PushToTalkApp()
}