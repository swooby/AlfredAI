package com.swooby.alfredai

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.compose.ConstraintLayout
import com.swooby.alfredai.ui.theme.AlfredAITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PushToTalkActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PushToTalkApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushToTalkApp() {
    val applicationContext = LocalContext.current.applicationContext
    val alfredAiApp = applicationContext as? AlfredAiApp
    val realtimeClient = alfredAiApp?.realtimeClient

    AlfredAITheme {
        Scaffold(modifier = Modifier
            .border(1.dp, Color.Red)
            .fillMaxSize(),
            topBar = {
                TopAppBar(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                    title = { Text("PushToTalk") },
                )
            }
        ) { innerPadding ->

            var sessionState by remember { mutableStateOf("Creating session...") }

            LaunchedEffect(Unit) {
                CoroutineScope(Dispatchers.IO).launch {

                    val ephemeralApiKey = realtimeClient?.connect(applicationContext)
                    Log.d(MainActivity.TAG, "ephemeralApiKey: $ephemeralApiKey")
                    realtimeClient?.setLocalAudioTrackMicrophoneEnabled(false)
                    sessionState = if (ephemeralApiKey != null) {
                        "Session created"
                    } else {
                        "Error creating session"
                    }
                }
            }

            ConstraintLayout(modifier = Modifier.fillMaxSize()) {
                val (button) = createRefs()
                PushToTalkButton(
                    modifier = Modifier
                        .border(1.dp, Color.Green)
                        .constrainAs(button) {
                            centerTo(parent)
                        }
                        .padding(innerPadding),
                    onPushToTalkStart = { pttState ->
                        Log.d(MainActivity.TAG, "")
                        Log.d(MainActivity.TAG, "+onPushToTalkStart: pttState=$pttState")
                        // 1. Play the start sound
                        Log.d(MainActivity.TAG, "onPushToTalkStart: playing start sound")
                        Utils.playAudioResourceOnce(
                            context = applicationContext,
                            audioResourceId = R.raw.quindar_nasa_apollo_intro
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
                        Log.d(MainActivity.TAG, "onPushToTalkStop: sending input_audio_buffer.commit")
                        realtimeClient?.dataSendInputAudioBufferCommit()
                        Log.d(MainActivity.TAG, "onPushToTalkStop: input_audio_buffer.commit sent")
                        // 4. Send response.create
                        Log.d(MainActivity.TAG, "onPushToTalkStop: sending response.create")
                        realtimeClient?.dataSendResponseCreate()
                        Log.d(MainActivity.TAG, "onPushToTalkStop: response.create sent")
                        // 5. Play the stop sound
                        Log.d(MainActivity.TAG, "onPushToTalkStop: playing stop sound")
                        Utils.playAudioResourceOnce(
                            context = applicationContext,
                            audioResourceId = R.raw.quindar_nasa_apollo_outro
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
        }
    }
}

@Preview
@Composable
fun PushToTalkButtonActivityPreview() {
    PushToTalkApp()
}
