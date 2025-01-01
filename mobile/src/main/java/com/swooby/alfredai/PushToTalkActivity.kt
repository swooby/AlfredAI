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
import com.swooby.alfredai.openai.realtime.RealtimeAPI
import com.swooby.alfredai.openai.realtime.SessionConfig
import com.swooby.alfredai.openai.realtime.SessionModel
import com.swooby.alfredai.openai.realtime.SessionTurnDetection
import com.swooby.alfredai.openai.realtime.SessionVoice
import com.swooby.alfredai.ui.theme.AlfredAITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PushToTalkActivity : ComponentActivity() {

    private var realtime: RealtimeAPI? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
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

                    val dangerousOpenAiApiKey = BuildConfig.DANGEROUS_OPENAI_API_KEY
                    Log.d(MainActivity.TAG, "dangerousOpenAiApiKey: $dangerousOpenAiApiKey")

                    var sessionState by remember { mutableStateOf("Creating session...") }

                    val applicationContext = applicationContext

                    LaunchedEffect(Unit) {
                        CoroutineScope(Dispatchers.IO).launch {

                            //
                            // TODO: Experiment with changing vad to manual and try to get PTT to work good
                            //
                            val sessionConfig = SessionConfig(
                                turn_detection = SessionTurnDetection(type = null)
                            )

                            val ephemeralKey = RealtimeAPI.requestOpenAiRealtimeEphemeralApiKey(
                                dangerousOpenAiApiKey,
                                SessionModel.`gpt-4o-realtime-preview-2024-12-17`,
                                SessionVoice.verse,
                                sessionConfig
                            )
                            Log.d(MainActivity.TAG, "ephemeralKey: $ephemeralKey")
                            sessionState = if (ephemeralKey != null) {
                                realtime = RealtimeAPI(ephemeralKey)
                                realtime?.connect(applicationContext, sessionConfig)
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
                        )
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PushToTalkButtonActivityPreview() {
    AlfredAITheme {
        PushToTalkButton()
    }
}
