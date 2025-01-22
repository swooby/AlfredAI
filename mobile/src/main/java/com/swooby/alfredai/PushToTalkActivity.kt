package com.swooby.alfredai

import android.Manifest.permission.RECORD_AUDIO
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.ViewModelProvider
import com.openai.infrastructure.ClientError
import com.openai.infrastructure.ClientException
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
import com.swooby.alfred.common.Utils.quote
import com.swooby.alfredai.AppUtils.showToast
import com.swooby.alfred.common.openai.realtime.RealtimeClient
import com.swooby.alfredai.AppUtils.playAudioResourceOnce
import com.swooby.alfredai.ui.theme.AlfredAITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.UnknownHostException

class PushToTalkActivity : ComponentActivity() {
    private lateinit var pushToTalkViewModel: PushToTalkViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AlfredAiApp
        val factory = ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        pushToTalkViewModel = ViewModelProvider(app.appViewModelStore, factory)[PushToTalkViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            PushToTalkScreen(pushToTalkViewModel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        pushToTalkViewModel.realtimeClient?.disconnect()
    }
}

enum class ConversationSpeaker {
    Local,
    Remote,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushToTalkScreen(pushToTalkViewModel: PushToTalkViewModel? = null) {
    @Suppress("LocalVariableName")
    val TAG = "PushToTalkScreen"

    @Suppress(
        "SimplifyBooleanWithConstants",
        "KotlinConstantConditions",
    )
    val debugForceShowPreferences = BuildConfig.DEBUG && false
    @Suppress(
        "SimplifyBooleanWithConstants",
        "KotlinConstantConditions",
    )
    val debugForceDontAutoConnect = BuildConfig.DEBUG && false
    @Suppress(
        "SimplifyBooleanWithConstants",
        "KotlinConstantConditions",
    )
    val debugLogConversation = BuildConfig.DEBUG && true
    @Suppress(
        "SimplifyBooleanWithConstants",
        "KotlinConstantConditions",
    )
    val debugToastVerbose = BuildConfig.DEBUG && false

    //
    //region Conversation
    //

    data class ConversationItem(
        val id: String?,
        val speaker: ConversationSpeaker,
        val initialText: String
    ) {
        var text by mutableStateOf(initialText)
    }

    val conversationItems = remember { mutableStateListOf<ConversationItem>() }
    @Suppress(
        "SimplifyBooleanWithConstants",
        "KotlinConstantConditions",
    )
    if (BuildConfig.DEBUG && false) {
        fun generateRandomSentence(): String {
            val subjects = listOf("The cat", "The dog", "The bird", "The fish")
            val verbs = listOf("jumps", "runs", "flies", "swims")
            val objects = listOf("over the fence", "in the park", "through the air", "in the water")
            return "${subjects.random()} ${verbs.random()} ${objects.random()}."
        }

        for (i in 0..20) {
            conversationItems.add(ConversationItem(
                id = "$i",
                ConversationSpeaker.entries.random(),
                initialText = generateRandomSentence()
            ))
        }
    }
    val conversationListState = rememberLazyListState()
    LaunchedEffect(Unit) {
        snapshotFlow { conversationItems.lastOrNull()?.text }
            .collect {
                if (conversationItems.isNotEmpty()) {
                    conversationListState.animateScrollToItem(conversationItems.size - 1)
                }
            }
    }

    //
    //endregion
    //

    var showPreferences by remember {
        mutableStateOf(debugForceShowPreferences || !(pushToTalkViewModel?.isConfigured ?: true))
    }
    var onSaveButtonClick: (() -> Job?)? by remember { mutableStateOf(null) }

    //
    //region Connect/Disconnect
    //

    var isConnectingOrConnected by remember {
        mutableStateOf(pushToTalkViewModel?.isConnectingOrConnected ?: false)
    }
    var isConnected by remember {
        mutableStateOf(pushToTalkViewModel?.isConnected ?: false)
    }

    var isConnectSwitchOn by remember { mutableStateOf(false) }
    var isConnectSwitchManualOff by remember { mutableStateOf(false) }
    var jobConnect by remember { mutableStateOf<Job?>(null) }

    val context = LocalContext.current

    fun connect() {
        Log.d(TAG, "connect()")
        if (pushToTalkViewModel?.isConfigured == true) {
            pushToTalkViewModel.realtimeClient?.also { realtimeClient ->
                isConnectSwitchOn = true
                if (!isConnectingOrConnected) {
                    isConnectingOrConnected = true
                    conversationItems.clear()
                    jobConnect = CoroutineScope(Dispatchers.IO).launch {
                        @Suppress("SimplifyBooleanWithConstants", "KotlinConstantConditions")
                        val debugDelay = BuildConfig.DEBUG && false
                        @Suppress("KotlinConstantConditions")
                        if (debugDelay) {
                            delay(10_000)
                        }
                        val ephemeralApiKey = realtimeClient.connect()
                        Log.d(TAG, "ephemeralApiKey: $ephemeralApiKey")
                        if (ephemeralApiKey != null) {
                            realtimeClient.setLocalAudioTrackMicrophoneEnabled(false)
                        }
                    }
                }
            }
        } else {
            showToast(context, "Not configured", Toast.LENGTH_SHORT)
            showPreferences = true
        }
    }

    fun connectIfAutoConnectAndNotManualOff() {
        if (!debugForceDontAutoConnect && pushToTalkViewModel?.autoConnect?.value == true) {
            if (!isConnectSwitchManualOff) {
                connect()
            }
        } else {
            showToast(context, "Auto-connect is disabled", Toast.LENGTH_SHORT)
        }
    }

    fun disconnect(
        isManual: Boolean = false,
        isClient: Boolean = false,
    ) {
        Log.d(TAG, "disconnect(isManual=$isManual, isClient=$isClient)")
        isConnectSwitchOn = false
        if (isManual) {
            isConnectSwitchManualOff = true
        }
        isConnectingOrConnected = false
        isConnected = false
        jobConnect?.also {
            Log.d(TAG, "Canceling jobConnect...")
            it.cancel()
            jobConnect = null
            Log.d(TAG, "...jobConnect canceled.")
        }
        if (!isClient) {
            pushToTalkViewModel?.realtimeClient?.also { realtimeClient ->
                Log.d(TAG, "Disconnecting RealtimeClient...")
                realtimeClient.disconnect()
                Log.d(TAG, "...RealtimeClient disconnected.")
            }
        }
    }

    //
    //endregion
    //

    //
    //region Permissions
    //

    var hasAllRequiredPermissions by remember { mutableStateOf(
        checkSelfPermission(context, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var requestPermissionLauncher: ActivityResultLauncher<String>? = null
    requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasAllRequiredPermissions = isGranted
        if (isGranted) {
            Log.d(TAG, "All required permissions granted")
            connectIfAutoConnectAndNotManualOff()
        } else {
            Log.d(TAG, "All required permissions NOT granted")
            val activity = (context as? Activity)
            if (activity != null) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, RECORD_AUDIO)) {
                    // Show rationale dialog and re-request permission
                    AlertDialog.Builder(activity)
                        .setTitle("Permission Required")
                        .setMessage("This app needs microphone access to provide the feature. Please grant the permission.")
                        .setPositiveButton("Grant") { _, _ ->
                            requestPermissionLauncher?.launch(RECORD_AUDIO)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    // "Don't ask again" selected. Guide user to settings.
                    AlertDialog.Builder(activity)
                        .setTitle("Permission Required")
                        .setMessage("Microphone permission has been permanently denied. Please enable it in app settings.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", activity.packageName, null)
                            }
                            activity.startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }
    }

    //
    //endregion
    //

    //
    //region RealtimeClientListener
    //
    DisposableEffect(Unit) {
        val realtimeClientListener = object : RealtimeClient.RealtimeClientListener {
            override fun onConnecting() {
                Log.d(TAG, "onConnecting()")
                isConnectingOrConnected = true
                if (debugToastVerbose) {
                    showToast(context = context, text = "Connecting...", forceInvokeOnMain = true)
                }
                playAudioResourceOnce(context, R.raw.connecting)
            }

            override fun onError(error: Exception) {
                Log.d(TAG, "onError($error)")
                disconnect(isClient = true)
                val text = when (error) {
                    is UnknownHostException -> {
                        /*
2025-01-17 17:43:21.190 27419-27475 okhttp.OkHttpClient com.swooby.alfredai I  <-- HTTP FAILED: java.net.UnknownHostException: Unable to resolve host "api.openai.com": No address associated with hostname
2025-01-17 17:43:21.190 27419-27475 RealtimeClient      com.swooby.alfredai E  connect: exception=java.net.UnknownHostException: Unable to resolve host "api.openai.com": No address associated with hostname
2025-01-17 17:43:21.190 27419-27475 PushToTalkActivity  com.swooby.alfredai D  onError(java.net.UnknownHostException: Unable to resolve host "api.openai.com": No address associated with hostname)
2025-01-17 17:43:21.191 27419-27475 RealtimeClient      com.swooby.alfredai D  +disconnect()
2025-01-17 17:43:21.191 27419-27475 RealtimeClient      com.swooby.alfredai D  -disconnect()
2025-01-17 17:43:21.191 27419-27475 RealtimeClient      com.swooby.alfredai D  connect: ephemeralApiKey=null
2025-01-17 17:43:21.191 27419-27475 PushToTalkActivity  com.swooby.alfredai D  onError(com.openai.infrastructure.ClientException: No Ephemeral API Key In Response)
                        */
                        "Mysterious \"Unable to resolve host `api.openai.com`\" error; Try again."
                    }
                    is ClientException -> {
                        var message: String? = error.message ?: error.toString()
                        val response = error.response as? ClientError<*>
                        if (response != null) {
                            var expectedJsonBody = response.body
                            if (expectedJsonBody == null) {
                                message = response.message
                            }
                            if (expectedJsonBody == null && message == null) {
                                expectedJsonBody = "{ \"error\": { \"code\": \"-1\" }"
                            }
                            if (expectedJsonBody != null) {
                                val jsonObject = JSONObject(expectedJsonBody.toString())
                                val jsonError = jsonObject.getJSONObject("error")
                                val errorCode = jsonError.getString("code")
                                message = errorCode
                            }
                        }
                        "ClientException: ${quote(message)}"
                    }
                    else -> "Error: ${error.message}"
                }
                showToast(context = context, text = text, duration = Toast.LENGTH_LONG, forceInvokeOnMain = true)
            }

            override fun onConnected() {
                Log.d(TAG, "onConnected()")
                isConnected = true
                jobConnect = null
                if (debugToastVerbose) {
                    showToast(context = context, text = "Connected", forceInvokeOnMain = true)
                }
                playAudioResourceOnce(context, R.raw.connected)
            }

            override fun onDisconnected() {
                Log.d(TAG, "onDisconnected()")
                disconnect(isClient = true)
                if (debugToastVerbose) {
                    showToast(context = context, text = "Disconnected", forceInvokeOnMain = true)
                }
                playAudioResourceOnce(context, R.raw.disconnected)
            }

            override fun onBinaryMessageReceived(data: ByteArray): Boolean {
                //Log.d(TAG, "onBinaryMessageReceived(): data(${data.size})=...")
                //...
                return false
            }

            override fun onTextMessageReceived(message: String): Boolean {
                //Log.d(TAG, "onTextMessageReceived(): message=${quote(message)}")
                //...
                return false
            }

            override fun onServerEventConversationCreated(
                realtimeServerEventConversationCreated: RealtimeServerEventConversationCreated
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventConversationCreated($realtimeServerEventConversationCreated)")
                }
            }

            override fun onServerEventConversationItemCreated(
                realtimeServerEventConversationItemCreated: RealtimeServerEventConversationItemCreated
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventConversationItemCreated($realtimeServerEventConversationItemCreated)")
                }
            }

            override fun onServerEventConversationItemDeleted(
                realtimeServerEventConversationItemDeleted: RealtimeServerEventConversationItemDeleted
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventConversationItemDeleted($realtimeServerEventConversationItemDeleted)")
                }
            }

            override fun onServerEventConversationItemInputAudioTranscriptionCompleted(
                realtimeServerEventConversationItemInputAudioTranscriptionCompleted: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventConversationItemInputAudioTranscriptionCompleted($realtimeServerEventConversationItemInputAudioTranscriptionCompleted)")
                }
                val id = realtimeServerEventConversationItemInputAudioTranscriptionCompleted.itemId
                val transcript = realtimeServerEventConversationItemInputAudioTranscriptionCompleted.transcript.trim() // DO TRIM!
                if (debugLogConversation) {
                    Log.w(TAG, "onServerEventConversationItemInputAudioTranscriptionCompleted: conversationItems.add(ConversationItem(id=${quote(id)}, initialText=${quote(transcript)}")
                }
                if (transcript.isNotBlank()) {
                    conversationItems.add(
                        ConversationItem(
                            id = id,
                            speaker = ConversationSpeaker.Local,
                            initialText = transcript
                        )
                    )
                }
            }

            override fun onServerEventConversationItemInputAudioTranscriptionFailed(
                realtimeServerEventConversationItemInputAudioTranscriptionFailed: RealtimeServerEventConversationItemInputAudioTranscriptionFailed
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventConversationItemInputAudioTranscriptionFailed($realtimeServerEventConversationItemInputAudioTranscriptionFailed)")
                }
            }

            override fun onServerEventConversationItemTruncated(
                realtimeServerEventConversationItemTruncated: RealtimeServerEventConversationItemTruncated
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventConversationItemTruncated($realtimeServerEventConversationItemTruncated)")
                }
            }

            override fun onServerEventError(
                realtimeServerEventError: RealtimeServerEventError
            ) {
                Log.d(TAG, "onServerEventError($realtimeServerEventError)")
                val error = realtimeServerEventError.error
                val text = error.message
                showToast(context = context, text = text, duration = Toast.LENGTH_LONG, forceInvokeOnMain = true)
            }

            override fun onServerEventInputAudioBufferCleared(
                realtimeServerEventInputAudioBufferCleared: RealtimeServerEventInputAudioBufferCleared
            ) {
                Log.d(TAG, "onServerEventInputAudioBufferCleared($realtimeServerEventInputAudioBufferCleared)")
            }

            override fun onServerEventInputAudioBufferCommitted(
                realtimeServerEventInputAudioBufferCommitted: RealtimeServerEventInputAudioBufferCommitted
            ) {
                Log.d(TAG, "onServerEventInputAudioBufferCommitted($realtimeServerEventInputAudioBufferCommitted)")
            }

            override fun onServerEventInputAudioBufferSpeechStarted(
                realtimeServerEventInputAudioBufferSpeechStarted: RealtimeServerEventInputAudioBufferSpeechStarted
            ) {
                Log.d(TAG, "onServerEventInputAudioBufferSpeechStarted($realtimeServerEventInputAudioBufferSpeechStarted)")
            }

            override fun onServerEventInputAudioBufferSpeechStopped(
                realtimeServerEventInputAudioBufferSpeechStopped: RealtimeServerEventInputAudioBufferSpeechStopped
            ) {
                Log.d(TAG, "onServerEventInputAudioBufferSpeechStopped($realtimeServerEventInputAudioBufferSpeechStopped)")
            }

            override fun onServerEventRateLimitsUpdated(
                realtimeServerEventRateLimitsUpdated: RealtimeServerEventRateLimitsUpdated
            ) {
                Log.d(TAG, "onServerEventRateLimitsUpdated($realtimeServerEventRateLimitsUpdated)")
            }

            override fun onServerEventResponseAudioDelta(
                realtimeServerEventResponseAudioDelta: RealtimeServerEventResponseAudioDelta
            ) {
                Log.d(TAG, "onServerEventResponseAudioDelta($realtimeServerEventResponseAudioDelta)")
            }

            override fun onServerEventResponseAudioDone(
                realtimeServerEventResponseAudioDone: RealtimeServerEventResponseAudioDone
            ) {
                Log.d(TAG, "onServerEventResponseAudioDone($realtimeServerEventResponseAudioDone)")
            }

            override fun onServerEventResponseAudioTranscriptDelta(
                realtimeServerEventResponseAudioTranscriptDelta: RealtimeServerEventResponseAudioTranscriptDelta
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventResponseAudioTranscriptDelta($realtimeServerEventResponseAudioTranscriptDelta)")
                }
                val id = realtimeServerEventResponseAudioTranscriptDelta.itemId
                val delta = realtimeServerEventResponseAudioTranscriptDelta.delta // DO **NOT** TRIM!

                // Append this delta to any current in progress conversation,
                // or create a new conversation
                val index = conversationItems.indexOfFirst { it.id == id }
                if (debugLogConversation) {
                    Log.w(TAG, "onServerEventResponseAudioTranscriptDelta: id=$id, delta=$delta, index=$index")
                }
                if (index == -1) {
                    val conversationItem = ConversationItem(id=id, speaker = ConversationSpeaker.Remote, initialText = delta)
                    if (debugLogConversation) {
                        Log.w(TAG, "conversationItems.add($conversationItem)")
                    }
                    conversationItems.add(conversationItem)
                } else {
                    val conversationItem = conversationItems[index]
                    conversationItem.text += delta
                    if (debugLogConversation) {
                        Log.w(TAG, "conversationItems.set($index, $conversationItem)")
                    }
                    conversationItems[index] = conversationItem
                }
            }

            override fun onServerEventResponseAudioTranscriptDone(
                realtimeServerEventResponseAudioTranscriptDone: RealtimeServerEventResponseAudioTranscriptDone
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventResponseAudioTranscriptDone($realtimeServerEventResponseAudioTranscriptDone)")
                }
            }

            override fun onServerEventResponseContentPartAdded(
                realtimeServerEventResponseContentPartAdded: RealtimeServerEventResponseContentPartAdded
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventResponseContentPartAdded($realtimeServerEventResponseContentPartAdded)")
                }
            }

            override fun onServerEventResponseContentPartDone(
                realtimeServerEventResponseContentPartDone: RealtimeServerEventResponseContentPartDone
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventResponseContentPartDone($realtimeServerEventResponseContentPartDone)")
                }
            }

            override fun onServerEventResponseCreated(
                realtimeServerEventResponseCreated: RealtimeServerEventResponseCreated
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventResponseCreated($realtimeServerEventResponseCreated)")
                }
            }

            override fun onServerEventResponseDone(
                realtimeServerEventResponseDone: RealtimeServerEventResponseDone
            ) {
                if (debugLogConversation) {
                    Log.d(TAG, "onServerEventResponseDone($realtimeServerEventResponseDone)")
                }
                realtimeServerEventResponseDone.response.output?.forEach { outputConversationItem ->
                    if (debugLogConversation) {
                        Log.w(TAG, "onServerEventResponseDone: outputConversationItem=$outputConversationItem")
                    }
                    val id = outputConversationItem.id ?: return@forEach
                    val index = conversationItems.indexOfFirst { it.id == id }
                    if (index != -1) {
                        val conversationItem = conversationItems[index]
                        if (debugLogConversation) {
                            Log.w(TAG, "onServerEventResponseDone: removing $conversationItem at index=$index")
                        }
                        conversationItems.removeAt(index)
                        if (debugLogConversation) {
                            Log.w(TAG, "onServerEventResponseDone: adding $conversationItem at end")
                        }
                        conversationItems.add(conversationItem)
                    }
                }
            }

            override fun onServerEventResponseFunctionCallArgumentsDelta(
                realtimeServerEventResponseFunctionCallArgumentsDelta: RealtimeServerEventResponseFunctionCallArgumentsDelta
            ) {
                Log.d(TAG, "onServerEventResponseFunctionCallArgumentsDelta($realtimeServerEventResponseFunctionCallArgumentsDelta)")
            }

            override fun onServerEventResponseFunctionCallArgumentsDone(
                realtimeServerEventResponseFunctionCallArgumentsDone: RealtimeServerEventResponseFunctionCallArgumentsDone
            ) {
                Log.d(TAG, "onServerEventResponseFunctionCallArgumentsDone($realtimeServerEventResponseFunctionCallArgumentsDone)")
            }

            override fun onServerEventResponseOutputItemAdded(
                realtimeServerEventResponseOutputItemAdded: RealtimeServerEventResponseOutputItemAdded
            ) {
                Log.d(TAG, "onServerEventResponseOutputItemAdded($realtimeServerEventResponseOutputItemAdded)")
            }

            override fun onServerEventResponseOutputItemDone(
                realtimeServerEventResponseOutputItemDone: RealtimeServerEventResponseOutputItemDone
            ) {
                Log.d(TAG, "onServerEventResponseOutputItemDone($realtimeServerEventResponseOutputItemDone)")
            }

            override fun onServerEventResponseTextDelta(
                realtimeServerEventResponseTextDelta: RealtimeServerEventResponseTextDelta
            ) {
                Log.d(TAG, "onServerEventResponseTextDelta($realtimeServerEventResponseTextDelta)")
            }

            override fun onServerEventResponseTextDone(
                realtimeServerEventResponseTextDone: RealtimeServerEventResponseTextDone
            ) {
                Log.d(TAG, "onServerEventResponseTextDone($realtimeServerEventResponseTextDone)")
            }

            override fun onServerEventSessionCreated(
                realtimeServerEventSessionCreated: RealtimeServerEventSessionCreated
            ) {
                Log.d(TAG, "onServerEventSessionCreated($realtimeServerEventSessionCreated)")
                if (debugToastVerbose) {
                    showToast(context = context, text = "Session Created", forceInvokeOnMain = true)
                }
            }

            override fun onServerEventSessionUpdated(
                realtimeServerEventSessionUpdated: RealtimeServerEventSessionUpdated
            ) {
                Log.d(TAG, "onServerEventSessionUpdated($realtimeServerEventSessionUpdated)")
                if (debugToastVerbose) {
                    showToast(context = context, text = "Session Updated", forceInvokeOnMain = true)
                }
            }
        }

        pushToTalkViewModel?.addListener(realtimeClientListener)

        onDispose {
            pushToTalkViewModel?.removeListener(realtimeClientListener)
        }
    }
    //
    //endregion
    //

    //
    //region UI
    //

    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)

    AlfredAITheme(dynamicColor = false) {
        Scaffold(modifier = Modifier
            .fillMaxSize(),
            topBar = {
                TopAppBar(
                    modifier = Modifier.background(MaterialTheme.colorScheme.primary),
                    title = { Text(if (showPreferences) "Preferences" else "PushToTalk") },
                    navigationIcon = {
                        if (showPreferences) {
                            IconButton(onClick = { showPreferences = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    actions = {
                        if (showPreferences) {
                            TextButton(onClick = {
                                jobConnect = onSaveButtonClick?.invoke()
                            }) {
                                Text("Save")
                            }
                        } else {
                            Switch(
                                checked = isConnectSwitchOn,
                                onCheckedChange = { newValue ->
                                    if (newValue) {
                                        // Re-check here to handle case of coming back from Settings app
                                        hasAllRequiredPermissions = checkSelfPermission(context, RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                                        if (hasAllRequiredPermissions) {
                                            connect()
                                        } else {
                                            requestPermissionLauncher.launch(RECORD_AUDIO)
                                        }
                                    } else {
                                        disconnect(isManual = true)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(
                                onClick = {
                                    showPreferences = true
                                }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_settings_24),
                                    contentDescription = "Settings"
                                )
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (showPreferences) {
                    var interceptBack by remember { mutableStateOf(true) }
                    BackHandler(enabled = interceptBack) {
                        showPreferences = false
                        interceptBack = false
                    }
                    PushToTalkPreferenceScreen(
                        pushToTalkViewModel = pushToTalkViewModel,
                        onSaveSuccess = {
                            showPreferences = !(pushToTalkViewModel?.isConfigured ?: false)
                        },
                        setSaveButtonCallback = {
                            onSaveButtonClick = it
                        },
                    )
                } else {
                    BackHandler(enabled = jobConnect != null) {
                        disconnect(isManual = true)
                    }
                    LaunchedEffect(Unit) {
                        if (hasAllRequiredPermissions) {
                            connectIfAutoConnectAndNotManualOff()
                        } else {
                            requestPermissionLauncher.launch(RECORD_AUDIO)
                        }
                    }

                    //
                    //region Conversation
                    //
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .border(1.dp, Color.LightGray, shape = RoundedCornerShape(8.dp))
                            .fillMaxWidth()
                            .padding(8.dp)
                        ,
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Column {
                            Row {
                                Text(text = "Conversation:")
                            }
                            Row {
                                LazyColumn(
                                    modifier = Modifier
                                        .border(1.dp, Color.LightGray, shape = RoundedCornerShape(8.dp))
                                        .fillMaxSize()
                                    ,
                                    contentPadding = PaddingValues(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    state = conversationListState,
                                ) {
                                    items(
                                        count = conversationItems.size,
                                    ) { index ->
                                        val item = conversationItems[index]
                                        val paddingAmount = 60.dp
                                        val modifier: Modifier
                                        val textAlign: TextAlign
                                        when (item.speaker) {
                                            ConversationSpeaker.Local -> {
                                                modifier = Modifier
                                                    .padding(start = paddingAmount)
                                                textAlign = TextAlign.End
                                            }
                                            ConversationSpeaker.Remote -> {
                                                modifier = Modifier
                                                    .padding(end = paddingAmount)
                                                textAlign = TextAlign.Start
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .then(modifier)
                                            ,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .border(1.dp, Color.Gray, shape = RoundedCornerShape(8.dp))
                                                    .padding(8.dp)
                                                ,
                                            ) {
                                                Text(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                    ,
                                                    text = item.text,
                                                    textAlign = textAlign,

                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    //
                    //endregion
                    //

                    //
                    //region Reset, PushToTalk, Stop
                    //
                    Row(
                        modifier = Modifier
                            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
                    ) {
                        //
                        //region Reset
                        //
                        Box(
                            modifier = Modifier
                                .border(
                                    4.dp,
                                    if (isConnected) MaterialTheme.colorScheme.primary else disabledColor,
                                    shape = CircleShape
                                )
                            ,
                        ) {
                            IconButton(
                                enabled = isConnected,
                                onClick = {
                                    if (true) {
                                        showToast(context, "Reset Not Yet Implemented", Toast.LENGTH_SHORT)
                                    } else {
                                        pushToTalkViewModel?.realtimeClient?.also { realtimeClient ->
                                            realtimeClient.dataSendInputAudioBufferClear()
                                        }
                                        conversationItems.clear()
                                    }
                                },
                                modifier = Modifier
                                    .size(66.dp)
                                    // Whoever designed this `restart_alt` icon did it wrong.
                                    // They should have centered it in its border instead of having an asymmetric protrusion on the top.
                                    .offset(y = (-2).dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_restart_alt_24),
                                    contentDescription = "Reset",
                                    modifier = Modifier
                                        .size(50.dp)
                                )
                            }
                        }

                        //
                        //endregion
                        //

                        Spacer(modifier = Modifier.weight(1f))

                        //
                        //region PushToTalk
                        //
                        Box(
                            modifier = Modifier
                                .size(150.dp)
                            ,
                            contentAlignment = Alignment.Center
                        ) {
                            Box {
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
                                            progress = { 0f },
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
                                    pushToTalkViewModel?.realtimeClient?.also { realtimeClient ->
                                        Log.d(TAG, "")
                                        Log.d(TAG, "+onPushToTalkStart: pttState=$pttState")
                                        // 1. Play the start sound
                                        Log.d(TAG, "onPushToTalkStart: playing start sound")
                                        playAudioResourceOnce(
                                            context = pushToTalkViewModel.getApplication(),
                                            audioResourceId = R.raw.quindar_nasa_apollo_intro,
                                            volume = 0.2f,
                                        ) {
                                            // 2. Wait for the start sound to finish
                                            Log.d(TAG, "onPushToTalkStart: start sound finished")
                                            // 3. Open the mic
                                            Log.d(TAG, "onPushToTalkStart: opening mic")
                                            realtimeClient.setLocalAudioTrackMicrophoneEnabled(true)
                                            Log.d(TAG, "onPushToTalkStart: mic opened")
                                            // 4. Wait for the mic to open successfully
                                            //...
                                            Log.d(TAG, "-onPushToTalkStart")
                                            Log.d(TAG, "")
                                        }
                                    }
                                    true
                                },
                                onPushToTalkStop = { pttState ->
                                    pushToTalkViewModel?.realtimeClient?.also { realtimeClient ->
                                        Log.d(TAG, "")
                                        Log.d(TAG, "+onPushToTalkStop: pttState=$pttState")
                                        // 1. Close the mic
                                        Log.d(TAG, "onPushToTalkStop: closing mic")
                                        realtimeClient.setLocalAudioTrackMicrophoneEnabled(false)
                                        Log.d(TAG, "onPushToTalkStop: mic closed")
                                        // 2. Wait for the mic to close successfully
                                        //...
                                        // 3. Send input_audio_buffer.commit
                                        Log.d(TAG, "onPushToTalkStop: sending input_audio_buffer.commit")
                                        realtimeClient.dataSendInputAudioBufferCommit()
                                        Log.d(TAG, "onPushToTalkStop: input_audio_buffer.commit sent")
                                        // 4. Send response.create
                                        Log.d(TAG, "onPushToTalkStop: sending response.create")
                                        realtimeClient.dataSendResponseCreate()
                                        Log.d(TAG, "onPushToTalkStop: response.create sent")
                                        // 5. Play the stop sound
                                        Log.d(TAG, "onPushToTalkStop: playing stop sound")
                                        playAudioResourceOnce(
                                            context = pushToTalkViewModel.getApplication(),
                                            audioResourceId = R.raw.quindar_nasa_apollo_outro,
                                            volume = 0.2f,
                                        ) {
                                            // 6. Wait for the stop sound to finish
                                            Log.d(TAG, "onPushToTalkStop: stop sound finished")
                                            //...
                                            Log.d(TAG, "-onPushToTalkStop")
                                            Log.d(TAG, "")
                                        }
                                    }
                                    true
                                },
                            )
                        }

                        //
                        //endregion
                        //

                        Spacer(modifier = Modifier.weight(1f))

                        //
                        //region Stop
                        //
                        Box(
                            modifier = Modifier
                                .border(
                                    4.dp,
                                    if (isConnected) MaterialTheme.colorScheme.primary else disabledColor,
                                    shape = CircleShape
                                )
                            ,
                        ) {
                            IconButton(
                                enabled = isConnected,
                                onClick = {
                                    if (true) {
                                        showToast(context, "Stop Not Yet Implemented", Toast.LENGTH_SHORT)
                                    } else {
                                        pushToTalkViewModel?.realtimeClient?.also { realtimeClient ->
                                            realtimeClient.dataSendResponseCancel()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(66.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.baseline_stop_24),
                                    contentDescription = "Stop",
                                    modifier = Modifier
                                        .size(50.dp)
                                )
                            }
                        }

                        //
                        //endregion
                        //
                    }
                }
            }
        }
    }

    //
    //endregion
    //
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true
)
@Composable
fun PushToTalkButtonActivityPreviewLight() {
    PushToTalkScreen()
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun PushToTalkButtonActivityPreviewDark() {
    PushToTalkScreen()
}
