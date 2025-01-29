package com.swooby.alfredai

import android.Manifest
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
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
//noinspection UsingMaterialAndMaterial3Libraries
import androidx.compose.material.ContentAlpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat.checkSelfPermission
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
import com.swooby.alfredai.Utils.playAudioResourceOnce
import com.swooby.alfredai.Utils.quote
import com.swooby.alfredai.AppUtils.showToast
import com.swooby.alfredai.Utils.getFriendlyPermissionName
import com.swooby.alfredai.openai.realtime.RealtimeClient
import com.swooby.alfredai.openai.realtime.RealtimeClient.ServerEventOutputAudioBufferAudioStopped
import com.swooby.alfredai.ui.theme.AlfredAITheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.UnknownHostException

class MobileActivity : ComponentActivity() {
    companion object {
        private const val TAG = "PushToTalkActivity"
    }

    private val mobileViewModel by lazy { (application as AlfredAiApp).mobileViewModel }

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate(savedInstanceState=$savedInstanceState)")
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileApp(mobileViewModel)
        }
    }

    override fun onStart() {
        super.onStart()
        mobileViewModel.onStartOrResume(this)
    }

    override fun onStop() {
        super.onStop()
        mobileViewModel.onPauseOrStop(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MobileApp(mobileViewModel: MobileViewModel? = null) {
    @Suppress("LocalVariableName")
    val TAG = "MobileApp"

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
    val debugConnectDelayMillis = if (BuildConfig.DEBUG && false) 10_000L else 0L

    @Suppress(
        "SimplifyBooleanWithConstants",
        "KotlinConstantConditions",
    )
    val debugToastVerbose = BuildConfig.DEBUG && false

    //
    //region Conversation
    //

    fun mockConversationItems(): List<MobileViewModel.ConversationItem> {
        val conversationItems = mutableListOf<MobileViewModel.ConversationItem>()
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
                conversationItems.add(
                    MobileViewModel.ConversationItem(
                        id = "$i",
                        speaker = MobileViewModel.ConversationSpeaker.entries.random(),
                        initialText = generateRandomSentence()
                    )
                )
            }
        }
        return conversationItems
    }

    val conversationItems = mobileViewModel?.conversationItems ?: mockConversationItems()

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
        mutableStateOf(debugForceShowPreferences || !(mobileViewModel?.isConfigured ?: true))
    }
    var onSaveButtonClick: (() -> Job?)? by remember { mutableStateOf(null) }

    //
    //region Connect/Disconnect
    //

    var isConnectingOrConnected by remember {
        mutableStateOf(mobileViewModel?.isConnectingOrConnected ?: false)
    }
    var isConnected by remember {
        mutableStateOf(mobileViewModel?.isConnected ?: false)
    }

    var isCancelingResponse by remember {
        mutableStateOf(
            mobileViewModel?.realtimeClient?.isCancelingResponse ?: false
        )
    }

    var isConnectSwitchOn by remember { mutableStateOf(false) }
    var isConnectSwitchManualOff by remember { mutableStateOf(false) }
    var jobConnect by remember { mutableStateOf<Job?>(null) }

    val context = LocalContext.current

    fun connect() {
        Log.d(TAG, "connect()")
        if (mobileViewModel?.isConfigured == true) {
            mobileViewModel.realtimeClient?.also { realtimeClient ->
                isConnectSwitchOn = true
                if (!isConnectingOrConnected) {
                    isConnectingOrConnected = true
                    mobileViewModel.conversationItemsClear()
                    jobConnect = CoroutineScope(Dispatchers.IO).launch {
                        if (debugConnectDelayMillis > 0) {
                            delay(debugConnectDelayMillis)
                        }
                        val ephemeralApiKey = realtimeClient.connect()
                        //Log.d(TAG, "ephemeralApiKey: $ephemeralApiKey")
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
        if (!debugForceDontAutoConnect && mobileViewModel?.autoConnect?.value == true) {
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
            mobileViewModel?.realtimeClient?.also { realtimeClient ->
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

    val requiredPermissions = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        /*
        NOTE: As of Android 12 (API 31), BLUETOOTH* no longer absolutely requires LOCATION permission per:
        https://developer.android.com/about/versions/12/summary
        https://developer.android.com/about/versions/12/behavior-changes-12
        https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
        https://developer.android.com/develop/connectivity/bluetooth/bt-permissions#declare-android12-or-higher
         */
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.POST_NOTIFICATIONS,
        Manifest.permission.FOREGROUND_SERVICE,
        MobileForegroundService.FOREGROUND_SERVICE_PERMISSION,
    )

    var hasAllRequiredPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    var requestPermissionsLauncher: ActivityResultLauncher<Array<String>>? = null
    requestPermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsResult: Map<String, Boolean> ->
        val allGranted = permissionsResult.values.all { it }
        hasAllRequiredPermissions = allGranted
        if (allGranted) {
            Log.d(TAG, "All required permissions granted")
            mobileViewModel?.audioSwitchStart()
            connectIfAutoConnectAndNotManualOff()
        } else {
            Log.d(TAG, "All required permissions NOT granted")
            (context as? Activity)?.let { activity ->
                var anyDenied = false
                val shouldShowRequestPermissionRationale = mutableSetOf<String>()
                requiredPermissions.forEach { permission ->
                    // If this particular permission was *not* granted, handle it:
                    if (permissionsResult[permission] == false) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(
                                activity,
                                permission
                            )
                        ) {
                            shouldShowRequestPermissionRationale.add(getFriendlyPermissionName(permission))
                        } else {
                            anyDenied = true
                        }
                    }
                }
                if (shouldShowRequestPermissionRationale.isNotEmpty()) {
                    // Show rationale dialog and allow the user to try again
                    val permissionsText = shouldShowRequestPermissionRationale.joinToString("\", \"")
                    AlertDialog.Builder(activity)
                        .setTitle("Permission Required")
                        .setMessage("This app needs “${permissionsText}” permissions for full functionality.\nPlease grant the permission.")
                        .setPositiveButton("Grant") { _, _ ->
                            requestPermissionsLauncher!!.launch(requiredPermissions)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    if (anyDenied) {
                        // User selected “don’t ask again” or system can’t show rationale.
                        // Guide the user to app settings.
                        AlertDialog.Builder(activity)
                            .setTitle("Permission Required")
                            .setMessage("Some required permissions have been denied.\nPlease enable them in app settings.")
                            .setPositiveButton("Open Settings") { _, _ ->
                                val intent =
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", activity.packageName, null)
                                    }
                                activity.startActivity(intent)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        // ignore; weird that this says it is required :/
                    }
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
                showToast(
                    context = context,
                    text = text,
                    duration = Toast.LENGTH_LONG,
                    forceInvokeOnMain = true
                )
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

            override fun onServerEventConversationCreated(realtimeServerEventConversationCreated: RealtimeServerEventConversationCreated) {
            }

            override fun onServerEventConversationItemCreated(realtimeServerEventConversationItemCreated: RealtimeServerEventConversationItemCreated) {
            }

            override fun onServerEventConversationItemDeleted(realtimeServerEventConversationItemDeleted: RealtimeServerEventConversationItemDeleted) {
            }

            override fun onServerEventConversationItemInputAudioTranscriptionCompleted(realtimeServerEventConversationItemInputAudioTranscriptionCompleted: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted) {
            }

            override fun onServerEventConversationItemInputAudioTranscriptionFailed(realtimeServerEventConversationItemInputAudioTranscriptionFailed: RealtimeServerEventConversationItemInputAudioTranscriptionFailed) {
            }

            override fun onServerEventConversationItemTruncated(realtimeServerEventConversationItemTruncated: RealtimeServerEventConversationItemTruncated) {
            }

            override fun onServerEventError(realtimeServerEventError: RealtimeServerEventError) {
                Log.d(TAG, "onServerEventError($realtimeServerEventError)")
                val error = realtimeServerEventError.error
                val text = error.message
                showToast(
                    context = context,
                    text = text,
                    duration = Toast.LENGTH_LONG,
                    forceInvokeOnMain = true,
                )
            }

            override fun onServerEventInputAudioBufferCleared(realtimeServerEventInputAudioBufferCleared: RealtimeServerEventInputAudioBufferCleared) {
                Log.d(TAG, "onServerEventInputAudioBufferCleared($realtimeServerEventInputAudioBufferCleared)")
            }

            override fun onServerEventInputAudioBufferCommitted(realtimeServerEventInputAudioBufferCommitted: RealtimeServerEventInputAudioBufferCommitted) {
                Log.d(TAG, "onServerEventInputAudioBufferCommitted($realtimeServerEventInputAudioBufferCommitted)")
            }

            override fun onServerEventInputAudioBufferSpeechStarted(realtimeServerEventInputAudioBufferSpeechStarted: RealtimeServerEventInputAudioBufferSpeechStarted) {
                Log.d(TAG, "onServerEventInputAudioBufferSpeechStarted($realtimeServerEventInputAudioBufferSpeechStarted)")
            }

            override fun onServerEventInputAudioBufferSpeechStopped(realtimeServerEventInputAudioBufferSpeechStopped: RealtimeServerEventInputAudioBufferSpeechStopped) {
                Log.d(TAG, "onServerEventInputAudioBufferSpeechStopped($realtimeServerEventInputAudioBufferSpeechStopped)")
            }

            override fun onServerEventOutputAudioBufferAudioStopped(realtimeServerEventOutputAudioBufferAudioStopped: ServerEventOutputAudioBufferAudioStopped) {
                Log.d(TAG, "onServerEventOutputAudioBufferAudioStopped($realtimeServerEventOutputAudioBufferAudioStopped)")
                if (isCancelingResponse) {
                    isCancelingResponse = false
                    showToast(
                        context = context,
                        text = "Response canceled",
                        duration = Toast.LENGTH_SHORT,
                        forceInvokeOnMain = true,
                    )
                }
            }

            override fun onServerEventRateLimitsUpdated(realtimeServerEventRateLimitsUpdated: RealtimeServerEventRateLimitsUpdated) {
                Log.d(TAG, "onServerEventRateLimitsUpdated($realtimeServerEventRateLimitsUpdated)")
            }

            override fun onServerEventResponseAudioDelta(realtimeServerEventResponseAudioDelta: RealtimeServerEventResponseAudioDelta) {
                Log.d(TAG, "onServerEventResponseAudioDelta($realtimeServerEventResponseAudioDelta)")
            }

            override fun onServerEventResponseAudioDone(realtimeServerEventResponseAudioDone: RealtimeServerEventResponseAudioDone) {
                Log.d(TAG, "onServerEventResponseAudioDone($realtimeServerEventResponseAudioDone)")
            }

            override fun onServerEventResponseAudioTranscriptDelta(realtimeServerEventResponseAudioTranscriptDelta: RealtimeServerEventResponseAudioTranscriptDelta) {
            }

            override fun onServerEventResponseAudioTranscriptDone(realtimeServerEventResponseAudioTranscriptDone: RealtimeServerEventResponseAudioTranscriptDone) {
            }

            override fun onServerEventResponseContentPartAdded(realtimeServerEventResponseContentPartAdded: RealtimeServerEventResponseContentPartAdded) {
            }

            override fun onServerEventResponseContentPartDone(realtimeServerEventResponseContentPartDone: RealtimeServerEventResponseContentPartDone) {
            }

            override fun onServerEventResponseCreated(realtimeServerEventResponseCreated: RealtimeServerEventResponseCreated) {
            }

            override fun onServerEventResponseDone(realtimeServerEventResponseDone: RealtimeServerEventResponseDone) {
            }

            override fun onServerEventResponseFunctionCallArgumentsDelta(realtimeServerEventResponseFunctionCallArgumentsDelta: RealtimeServerEventResponseFunctionCallArgumentsDelta) {
                Log.d(TAG, "onServerEventResponseFunctionCallArgumentsDelta($realtimeServerEventResponseFunctionCallArgumentsDelta)")
            }

            override fun onServerEventResponseFunctionCallArgumentsDone(realtimeServerEventResponseFunctionCallArgumentsDone: RealtimeServerEventResponseFunctionCallArgumentsDone) {
                Log.d(TAG, "onServerEventResponseFunctionCallArgumentsDone($realtimeServerEventResponseFunctionCallArgumentsDone)")
            }

            override fun onServerEventResponseOutputItemAdded(realtimeServerEventResponseOutputItemAdded: RealtimeServerEventResponseOutputItemAdded) {
                Log.d(TAG, "onServerEventResponseOutputItemAdded($realtimeServerEventResponseOutputItemAdded)")
            }

            override fun onServerEventResponseOutputItemDone(realtimeServerEventResponseOutputItemDone: RealtimeServerEventResponseOutputItemDone) {
                Log.d(TAG, "onServerEventResponseOutputItemDone($realtimeServerEventResponseOutputItemDone)")
            }

            override fun onServerEventResponseTextDelta(realtimeServerEventResponseTextDelta: RealtimeServerEventResponseTextDelta) {
                Log.d(TAG, "onServerEventResponseTextDelta($realtimeServerEventResponseTextDelta)")
            }

            override fun onServerEventResponseTextDone(realtimeServerEventResponseTextDone: RealtimeServerEventResponseTextDone) {
                Log.d(TAG, "onServerEventResponseTextDone($realtimeServerEventResponseTextDone)")
            }

            override fun onServerEventSessionCreated(realtimeServerEventSessionCreated: RealtimeServerEventSessionCreated) {
                Log.d(TAG, "onServerEventSessionCreated($realtimeServerEventSessionCreated)")
                if (debugToastVerbose) {
                    showToast(context = context, text = "Session Created", forceInvokeOnMain = true)
                }
            }

            override fun onServerEventSessionUpdated(realtimeServerEventSessionUpdated: RealtimeServerEventSessionUpdated) {
                Log.d(TAG, "onServerEventSessionUpdated($realtimeServerEventSessionUpdated)")
                if (debugToastVerbose) {
                    showToast(context = context, text = "Session Updated", forceInvokeOnMain = true)
                }
            }

            override fun onServerEventSessionExpired(realtimeServerEventError: RealtimeServerEventError) {
                Log.d(TAG, "onServerEventSessionExpired($realtimeServerEventError)")
                val text = "Session Expired: ${realtimeServerEventError.error.message}"
                showToast(context = context, text = text, forceInvokeOnMain = true)
            }
        }

        mobileViewModel?.addListener(realtimeClientListener)

        onDispose {
            mobileViewModel?.removeListener(realtimeClientListener)
        }
    }
    //
    //endregion
    //

    //
    //region UI
    //

    val disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = ContentAlpha.disabled)

    AlfredAITheme(
        dynamicColor = false,
        darkTheme = true,
    ) {
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
                                        hasAllRequiredPermissions = requiredPermissions.all {
                                            checkSelfPermission(
                                                context,
                                                it
                                            ) == PackageManager.PERMISSION_GRANTED
                                        }
                                        if (hasAllRequiredPermissions) {
                                            connect()
                                        } else {
                                            requestPermissionsLauncher.launch(requiredPermissions)
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
                        mobileViewModel = mobileViewModel,
                        onSaveSuccess = {
                            showPreferences = !(mobileViewModel?.isConfigured ?: false)
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
                            requestPermissionsLauncher.launch(requiredPermissions)
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
                            .padding(start = 8.dp, end = 8.dp, top = 0.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    modifier = Modifier.padding(start = 4.dp),
                                    text = "Conversation:",
                                )
                                IconButton(onClick = { mobileViewModel?.conversationItemsClear() }) {
                                    Icon(
                                        painterResource(id = R.drawable.baseline_clear_all_24),
                                        contentDescription = "Clear All",
                                    )
                                }
                            }
                            if (conversationItems.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            Color.LightGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No conversation items",
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            Color.LightGray,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .fillMaxWidth()
                                        .weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(8.dp),
                                    state = conversationListState,
                                ) {
                                    items(
                                        count = conversationItems.size,
                                    ) { index ->
                                        val item = conversationItems[index]
                                        val avatarResId: Int
                                        val horizontalAlignment: Arrangement.Horizontal
                                        val contentAlignment: Alignment
                                        val textAlign: TextAlign
                                        when (item.speaker) {
                                            MobileViewModel.ConversationSpeaker.Local -> {
                                                avatarResId = R.drawable.baseline_person_24
                                                horizontalAlignment = Arrangement.Start
                                                contentAlignment = Alignment.CenterStart
                                                textAlign = TextAlign.Start
                                            }

                                            MobileViewModel.ConversationSpeaker.Remote -> {
                                                avatarResId = R.drawable.baseline_memory_24
                                                horizontalAlignment = Arrangement.End
                                                contentAlignment = Alignment.CenterEnd
                                                textAlign = TextAlign.End
                                            }
                                        }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = horizontalAlignment
                                        ) {
                                            if (item.speaker == MobileViewModel.ConversationSpeaker.Local) {
                                                Icon(
                                                    painterResource(id = avatarResId),
                                                    contentDescription = "Local",
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f),
                                                contentAlignment = contentAlignment
                                            ) {
                                                SelectionContainer {
                                                    Text(
                                                        modifier = Modifier
                                                            .border(
                                                                1.dp,
                                                                Color.Gray,
                                                                shape = RoundedCornerShape(8.dp)
                                                            )
                                                            .padding(8.dp),
                                                        text = item.text,
                                                        textAlign = textAlign,
                                                    )
                                                }
                                            }
                                            if (item.speaker == MobileViewModel.ConversationSpeaker.Remote) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    painterResource(id = avatarResId),
                                                    contentDescription = "Remote",
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                var inputText by remember { mutableStateOf("") }

                                fun doSendInputText() {
                                    mobileViewModel?.realtimeClient?.also { realtimeClient ->
                                        realtimeClient.dataSendConversationItemCreate(inputText.trim())
                                        realtimeClient.dataSendResponseCreate()
                                        inputText = ""
                                    }
                                }

                                TextField(
                                    modifier = Modifier
                                        .weight(1f)
                                        .onKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                                if (event.isCtrlPressed) {
                                                    doSendInputText()
                                                    true
                                                } else {
                                                    false
                                                }
                                            } else {
                                                false
                                            }
                                        },
                                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Default),
                                    singleLine = false,
                                    enabled = isConnected,
                                    label = { Text("Text Input") },
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    trailingIcon = {
                                        IconButton(
                                            enabled = isConnected && inputText.trim().isNotBlank(),
                                            onClick = { doSendInputText() }
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send"
                                            )
                                        }
                                    },
                                )
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
                            .padding(horizontal = 24.dp, vertical = 16.dp)
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
                                ),
                        ) {
                            IconButton(
                                enabled = isConnected && !isCancelingResponse,
                                onClick = {
                                    disconnect()
                                    connect()
                                    showToast(
                                        context = context,
                                        text = "Reconnecting",
                                        forceInvokeOnMain = true,
                                    )
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
                                .size(150.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isConnected) {
                                KeepScreenOnComposable()
                            }
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
                                mobileViewModel = mobileViewModel,
                                enabled = isConnected && !isCancelingResponse,
                                onPushToTalkStart = {
                                    mobileViewModel?.pushToTalk(true)
                                },
                                onPushToTalkStop = {
                                    mobileViewModel?.pushToTalk(false)
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
                                ),
                        ) {
                            IconButton(
                                enabled = isConnected && !isCancelingResponse,
                                onClick = {
                                    mobileViewModel?.realtimeClient?.also { realtimeClient ->
                                        // If true, will be set back to false in onServerEventOutputAudioBufferAudioStopped
                                        isCancelingResponse =
                                            realtimeClient.dataSendResponseCancel()
                                        /*
                                        It takes a REALLY long time for `output_audio_buffer.audio_stopped` to be received.
                                        I have seen it take up to 20 seconds (proportional to the amount of speech in response)
2025-01-22 16:38:45.287 17703-17703 RealtimeClient          com.swooby.alfredai                  D  dataSend: text="{"type":"response.cancel","event_id":"evt_SbrzipkcoeecHwabk"}"
...
2025-01-22 16:38:45.590 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.audio.done"
2025-01-22 16:38:45.601 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseAudioDone(RealtimeServerEventResponseAudioDone(eventId=event_AsfX3PzYdfufTHPO6EgGQ, type=responsePeriodAudioPeriodDone, responseId=resp_AsfX2yF5teyOCOyVSTCow, itemId=item_AsfX2leMQadkxBACGBdgr, outputIndex=0, contentIndex=0))
2025-01-22 16:38:45.601 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.audio_transcript.done"
2025-01-22 16:38:45.606 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseAudioTranscriptDone(RealtimeServerEventResponseAudioTranscriptDone(eventId=event_AsfX3A3s59KvfvVMZI0ws, type=responsePeriodAudio_transcriptPeriodDone, responseId=resp_AsfX2yF5teyOCOyVSTCow, itemId=item_AsfX2leMQadkxBACGBdgr, outputIndex=0, contentIndex=0, transcript=Once upon a time in a bustling little town lived a man named Fred. Fred was known for his curiosity and his adventurous spirit. Every morning, he would set out with))
2025-01-22 16:38:45.606 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.content_part.done"
2025-01-22 16:38:45.619 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseContentPartDone(RealtimeServerEventResponseContentPartDone(eventId=event_AsfX3kZXNbclgSThE1QMJ, type=responsePeriodContent_partPeriodDone, responseId=resp_AsfX2yF5teyOCOyVSTCow, itemId=item_AsfX2leMQadkxBACGBdgr, outputIndex=0, contentIndex=0, part=RealtimeServerEventResponseContentPartDonePart(type=audio, text=null, audio=null, transcript=Once upon a time in a bustling little town lived a man named Fred. Fred was known for his curiosity and his adventurous spirit. Every morning, he would set out with)))
2025-01-22 16:38:45.620 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.output_item.done"
2025-01-22 16:38:45.624 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseOutputItemDone(RealtimeServerEventResponseOutputItemDone(eventId=event_AsfX3US6OdHBIghibc0j4, type=responsePeriodOutput_itemPeriodDone, responseId=resp_AsfX2yF5teyOCOyVSTCow, outputIndex=0, item=RealtimeConversationItem(id=item_AsfX2leMQadkxBACGBdgr, type=message, object=realtimePeriodItem, status=incomplete, role=assistant, content=[RealtimeConversationItemContent(type=audio, text=null, id=null, audio=null, transcript=Once upon a time in a bustling little town lived a man named Fred. Fred was known for his curiosity and his adventurous spirit. Every morning, he would set out with)], callId=null, name=null, arguments=null, output=null)))
2025-01-22 16:38:45.624 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="response.done"
2025-01-22 16:38:45.631 17703-17785 PushToTalkScreen        com.swooby.alfredai                  D  onServerEventResponseDone(RealtimeServerEventResponseDone(eventId=event_AsfX3UkPktMYSvQYvrS4h, type=responsePeriodDone, response=RealtimeResponse(id=resp_AsfX2yF5teyOCOyVSTCow, object=realtimePeriodResponse, status=cancelled, statusDetails=RealtimeResponseStatusDetails(type=cancelled, reason=client_cancelled, error=null), output=[RealtimeConversationItem(id=item_AsfX2leMQadkxBACGBdgr, type=message, object=realtimePeriodItem, status=incomplete, role=assistant, content=[RealtimeConversationItemContent(type=audio, text=null, id=null, audio=null, transcript=Once upon a time in a bustling little town lived a man named Fred. Fred was known for his curiosity and his adventurous spirit. Every morning, he would set out with)], callId=null, name=null, arguments=null, output=null)], metadata=null, usage=RealtimeResponseUsage(totalTokens=400, inputTokens=167, outputTokens=233, inputTokenDetails=RealtimeResponseUsageInputTokenDetails(cachedTokens=0, textTokens=130, audioTokens=37), outputTokenDetails=RealtimeResponseUsageOutputTokenDetails(textTokens=50, audioTokens=183)))))
...
2025-01-22 16:38:51.975 17703-17785 RealtimeClient          com.swooby.alfredai                  D  onDataChannelText: type="output_audio_buffer.audio_stopped"
2025-01-22 16:38:51.976 17703-17785 RealtimeClient          com.swooby.alfredai                  W  onDataChannelText: unknown type=output_audio_buffer.audio_stopped
                                         */
                                        if (isCancelingResponse) {
                                            showToast(
                                                context = context,
                                                text = "Cancelling Response; this can take a long time to complete.",
                                                duration = Toast.LENGTH_LONG,
                                            )
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

                    val audioDevices = mobileViewModel?.audioDevices
                    val selectedAudioDevice = mobileViewModel?.selectedAudioDevice
                    if (audioDevices?.isEmpty() == false && selectedAudioDevice != null) {
                        var expanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            TextField(
                                readOnly = true,
                                value = selectedAudioDevice.name,
                                onValueChange = { /* read-only; ignore */ },
                                label = { Text("Audio Device") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(
                                        type = MenuAnchorType.PrimaryNotEditable,
                                        enabled = true
                                    )
                            )
                            ExposedDropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                audioDevices.forEach { audioDevice ->
                                    DropdownMenuItem(
                                        text = { Text(audioDevice.name) },
                                        onClick = {
                                            mobileViewModel.selectAudioDevice(audioDevice)
                                            expanded = false
                                        }
                                    )
                                }
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
}

@Composable
fun KeepScreenOnComposable() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
        }
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true
)
@Composable
fun PushToTalkButtonActivityPreviewLight() {
    MobileApp()
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun PushToTalkButtonActivityPreviewDark() {
    MobileApp()
}
