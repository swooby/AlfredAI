package com.swooby.alfredai

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.lifecycle.viewModelScope
import com.openai.infrastructure.ApiClient
import com.openai.infrastructure.ClientError
import com.openai.infrastructure.ClientException
import com.openai.models.RealtimeConversationItem
import com.openai.models.RealtimeResponse
import com.openai.models.RealtimeResponseStatusDetails
import com.openai.models.RealtimeServerEventConversationCreated
import com.openai.models.RealtimeServerEventConversationItemCreated
import com.openai.models.RealtimeServerEventConversationItemDeleted
import com.openai.models.RealtimeServerEventConversationItemInputAudioTranscriptionCompleted
import com.openai.models.RealtimeServerEventConversationItemInputAudioTranscriptionFailed
import com.openai.models.RealtimeServerEventConversationItemTruncated
import com.openai.models.RealtimeServerEventError
import com.openai.models.RealtimeServerEventErrorError
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
import com.openai.models.RealtimeSessionCreateRequest
import com.openai.models.RealtimeSessionInputAudioTranscription
import com.openai.models.RealtimeSessionModel
import com.openai.models.RealtimeSessionTools
import com.openai.models.RealtimeSessionVoice
import com.swooby.alfredai.AppUtils.showToast
import com.swooby.alfredai.PushToTalkPreferences.Companion.getMaxResponseOutputTokens
import com.swooby.alfredai.Utils.audioManagerScoStateToString
import com.swooby.alfredai.Utils.bluetoothHeadsetStateToString
import com.swooby.alfredai.Utils.playAudioResourceOnce
import com.swooby.alfredai.Utils.quote
import com.swooby.alfredai.Utils.redact
import com.swooby.alfredai.openai.realtime.RealtimeClient
import com.swooby.alfredai.openai.realtime.RealtimeClient.RealtimeClientListener
import com.swooby.alfredai.openai.realtime.RealtimeClient.ServerEventOutputAudioBufferAudioStopped
import com.swooby.alfredai.openai.realtime.TransportType
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioDevice.BluetoothHeadset
import com.twilio.audioswitch.AudioDevice.Earpiece
import com.twilio.audioswitch.AudioDevice.Speakerphone
import com.twilio.audioswitch.AudioDevice.WiredHeadset
import com.twilio.audioswitch.AudioSwitch
import com.twilio.audioswitch.bluetooth.BluetoothHeadsetConnectionListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal
import java.net.UnknownHostException
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

class MobileViewModel(application: Application) :
    SharedViewModel(application), MobileViewModelInterface {
    companion object {
        const val DEBUG = AlfredAiApp.DEBUG

        private const val CHANNEL_ID = "SESSION_STATUS_CHANNEL"
        private const val CHANNEL_NAME = "Session Status Channel"
        private const val CHANNEL_DESCRIPTION = "Dismissible notifications for notable session status events"
        private const val NOTIFICATION_ID_SESSION = 1001

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        val debugToastVerbose = BuildConfig.DEBUG && false

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        val debugForceNotConfigured = BuildConfig.DEBUG && false

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        val debugSimulateSessionExpiredMillis = if (BuildConfig.DEBUG && false) 20_000L else 0L

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        val debugForceDoNotAutoConnect = BuildConfig.DEBUG && false

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        val debugConnectDelayMillis = if (BuildConfig.DEBUG && false) 10_000L else 0L

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        val debugLogConversation = BuildConfig.DEBUG && false

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        val debugFakeConversationCount = if (BuildConfig.DEBUG && false) 20 else 0

        internal fun generateRandomConversationItems(count: Int = 0): List<ConversationItem> {
            require(count > 0) {
                "count must be greater than 0"
            }

            val conversationItems = mutableListOf<ConversationItem>()

            fun generateRandomSentence(maxWords: Int): String {
                require(maxWords > 0) {
                    "maxWords must be greater than 0"
                }

                val wordCount = Random.nextInt(1, maxWords + 1)
                val words = List(wordCount) {
                    val length = Random.nextInt(1, 11)
                    (1..length)
                        .map { ('a'..'z').random() }
                        .joinToString("")
                }
                val sentence = words.joinToString(" ")
                return sentence.replaceFirstChar { it.uppercase() } + "."
            }

            for (i in 0..20) {
                conversationItems.add(
                    ConversationItem(
                        id = "$i",
                        type = MobileViewModel.ConversationItemType.entries.random(),
                        initialText = generateRandomSentence((5..30).random()),
                    )
                )
            }
            return conversationItems
        }
    }

    override val TAG: String
        get() = "MobileViewModel"
    override val remoteTypeName: String
        get() = "MOBILE"
    override val remoteCapabilityName: String
        get() = "verify_remote_alfredai_wear_app"

    private var jobDebugFakeSessionExpired: Job? = null

    override fun init() {
        super.init()

        observeActivityLifecycles()

        createNotificationChannel()

        observeConnectionState()

        updatePermissionsState()
    }

    override fun close() {
        super.close()
        disconnect()
        onDisconnecting()
        onDisconnected()
        audioSwitchStop()
    }

    //
    //region In-Use-Activities Detection
    //

    private val inUseActivities = mutableSetOf<Activity>()

    /**
     * Returns true if no Activities have reported onStart or onResume.
     * Often used to determine if a Notification or Toast should be shown or not.
     */
    private val hasNoInUseActivities: Boolean
        get() = inUseActivities.isEmpty()

    private fun observeActivityLifecycles() {
        val application: Application = getApplication()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                Log.d(TAG, "observeActivityLifecycles: Activity ${activity.localClassName} - Created")
            }

            override fun onActivityStarted(activity: Activity) {
                Log.d(TAG, "observeActivityLifecycles: Activity ${activity.localClassName} - Started")
                inUseActivities.add(activity)
            }

            override fun onActivityResumed(activity: Activity) {
                Log.d(TAG, "observeActivityLifecycles: Activity ${activity.localClassName} - Resumed")
            }

            override fun onActivityPaused(activity: Activity) {
                Log.d(TAG, "observeActivityLifecycles: Activity ${activity.localClassName} - Paused")
            }

            override fun onActivityStopped(activity: Activity) {
                Log.d(TAG, "observeActivityLifecycles: Activity ${activity.localClassName} - Stopped")
                inUseActivities.remove(activity)
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                Log.d(TAG, "observeActivityLifecycles: Activity ${activity.localClassName} - SaveInstanceState")
            }

            override fun onActivityDestroyed(activity: Activity) {
                Log.d(TAG, "observeActivityLifecycles: Activity ${activity.localClassName} - Destroyed")
            }
        })
    }

    //
    //endregion
    //

    //
    //region Permissions
    //

    override val requiredPermissions = arrayOf(
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

    private fun checkHasAllRequiredPermissions(): Boolean {
        return requiredPermissions.all {
            checkSelfPermission(getApplication(), it) == PackageManager.PERMISSION_GRANTED
        }
    }
    private val _hasAllRequiredPermissions = MutableStateFlow(checkHasAllRequiredPermissions())
    override val hasAllRequiredPermissions = _hasAllRequiredPermissions.asStateFlow()
    override fun updatePermissionsState() {
        _hasAllRequiredPermissions.value = checkHasAllRequiredPermissions()
        maybeAutoConnect()
    }

    //
    //endregion
    //

    //
    //region Preferences
    //

    private val prefs = PushToTalkPreferences(application)

    private var _autoConnect = MutableStateFlow(prefs.autoConnect)
    override val autoConnect = _autoConnect.asStateFlow()

    private var _apiKey = MutableStateFlow(prefs.apiKey)
    override val apiKey = _apiKey.asStateFlow()

    private var _model = MutableStateFlow(prefs.model)
    override val model = _model.asStateFlow()

    private var _instructions = MutableStateFlow(prefs.instructions)
    override val instructions = _instructions.asStateFlow()

    private var _voice = MutableStateFlow(prefs.voice)
    override val voice = _voice.asStateFlow()

    private var _inputAudioTranscription = MutableStateFlow(prefs.inputAudioTranscription)
    override val inputAudioTranscription = _inputAudioTranscription.asStateFlow()

    private var _temperature = MutableStateFlow(prefs.temperature)
    override val temperature = _temperature.asStateFlow()

    private var _maxResponseOutputTokens = MutableStateFlow(prefs.maxResponseOutputTokens)
    override val maxResponseOutputTokens = _maxResponseOutputTokens.asStateFlow()

    private fun checkIsConfigured(): Boolean {
        return !debugForceNotConfigured && apiKey.value.isNotBlank()
    }
    private var _isConfigured = MutableStateFlow(checkIsConfigured())
    override val isConfigured = _isConfigured.asStateFlow()
    private fun updateIsConfiguredState() {
        _isConfigured.value = checkIsConfigured()
    }

    private val sessionConfig: RealtimeSessionCreateRequest
        get() {
            return RealtimeSessionCreateRequest(
                modalities = null,
                model = model.value,
                instructions = instructions.value,
                voice = voice.value,
                inputAudioFormat = null,
                outputAudioFormat = null,
                inputAudioTranscription = inputAudioTranscription.value,
                turnDetection = PushToTalkPreferences.turnDetectionDefault,
                tools = functions,
                toolChoice = null,
                temperature = BigDecimal(temperature.value.toDouble()),
                maxResponseOutputTokens = getMaxResponseOutputTokens(maxResponseOutputTokens.value),
            )
        }

    override fun updatePreferences(
        autoConnect: Boolean,
        apiKey: String,
        model: RealtimeSessionModel,
        instructions: String,
        voice: RealtimeSessionVoice,
        inputAudioTranscription: RealtimeSessionInputAudioTranscription?,
        temperature: Float,
        maxResponseOutputTokens: Int,
    ) {
        prefs.autoConnect = autoConnect
        _autoConnect.value = autoConnect

        var reinitialize = realtimeClient == null
        var updateSession = false
        var reconnectSession = false

        if (apiKey != this.apiKey.value) {
            reinitialize = true
            prefs.apiKey = apiKey
            _apiKey.value = apiKey
        }

        if (model != prefs.model) {
            updateSession = true
            prefs.model = model
            _model.value = model
        }

        if (instructions != prefs.instructions) {
            updateSession = true
            prefs.instructions = instructions
            _instructions.value = instructions
        }

        /**
         * https://platform.openai.com/docs/api-reference/realtime-client-events/session
         * "session.update
         * ... The client may send this event at any time to update the session configuration,
         * and any field may be updated at any time, except for "voice"."
         */
        if (voice != prefs.voice) {
            reconnectSession = true
            prefs.voice = voice
            _voice.value = voice
        }

        if (inputAudioTranscription != prefs.inputAudioTranscription) {
            updateSession = true
            prefs.inputAudioTranscription = inputAudioTranscription
            _inputAudioTranscription.value = inputAudioTranscription
        }

        if (temperature != prefs.temperature) {
            updateSession = true
            prefs.temperature = temperature
            _temperature.value = temperature
        }

        if (maxResponseOutputTokens != prefs.maxResponseOutputTokens) {
            updateSession = true
            prefs.maxResponseOutputTokens = maxResponseOutputTokens
            _maxResponseOutputTokens.value = maxResponseOutputTokens
        }

        updateIsConfiguredState()

        if (reinitialize) {
            tryInitializeRealtimeClient()
        } else {
            if (isConnectingOrConnected.value) {
                if (isConnecting.value || reconnectSession) {
                    reconnect()
                } else {
                    if (isConnected.value && updateSession) {
                        realtimeClient?.dataSendSessionUpdate(sessionConfig)
                    }
                }
            }
        }
    }

    //
    //endregion
    //

    //
    //region Connection
    //

    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    private val connectionStateFlowProvider = ConnectionStateFlowProvider(
        connectionState = _connectionState,
        scope = viewModelScope,
    )
    override val connectionState = connectionStateFlowProvider.connectionState
    override val isConnecting = connectionStateFlowProvider.isConnecting
    override val isConnected = connectionStateFlowProvider.isConnected
    override val isConnectingOrConnected = connectionStateFlowProvider.isConnectingOrConnected
    override val isDisconnecting = connectionStateFlowProvider.isDisconnecting
    override val isDisconnected = connectionStateFlowProvider.isDisconnected
    override val isDisconnectingOrDisconnected = connectionStateFlowProvider.isDisconnectingOrDisconnected

    private var isDisconnectManual = false

    private var realtimeClient: RealtimeClient? = null

    private fun tryInitializeRealtimeClient(): Boolean {
        if (!hasAllRequiredPermissions.value) {
            Log.w(TAG, "tryInitializeRealtimeClient: Not all permissions not granted; not initializing realtimeClient")
            return false
        }

        audioSwitchStart()

        if (!isConfigured.value) {
            Log.w(TAG, "tryInitializeRealtimeClient: Not configured; not initializing realtimeClient")
            return false
        }

        disconnectInternal()

        val httpClient = if (DEBUG)
            RealtimeClient.httpLoggingClient
        else
            ApiClient.defaultClient

        realtimeClient = RealtimeClient(
            transportType = TransportType.WEBRTC,
            applicationContext = getApplication(),
            dangerousApiKey = prefs.apiKey,
            sessionConfig = sessionConfig,
            httpClient = httpClient,
            debug = DEBUG
        )
        realtimeClient?.addListener(listener)

        Log.d(TAG, "tryInitializeRealtimeClient: realtimeClient initialized successfully")
        return true
    }

    private fun maybeAutoConnect() {
        Log.d(TAG, "maybeAutoConnect()")

        if (debugForceDoNotAutoConnect) {
            Log.w(TAG, "maybeAutoConnect: Auto-connect is [debug] forced disabled")
            return
        }

        if (!autoConnect.value) {
            Log.d(TAG, "maybeAutoConnect: Auto-connect is disabled")
            return
        }

        if (isDisconnectManual) {
            Log.d(TAG, "maybeAutoConnect: Manually disconnected; Ignore auto-connect")
            return
        }

        connect()
    }

    private var jobConnect: Job? = null

    override fun connect() {
        try {
            Log.d(TAG, "+connect()")

            if (isConnectingOrConnected.value) {
                Log.d(TAG, "connect: Already connecting or connected")
                return
            }

            if (realtimeClient == null && !tryInitializeRealtimeClient()) {
                Log.w(TAG, "connect: tryInitializeRealtimeClient() return false; not connecting")
                return
            }

            isDisconnectManual = false

            _connectionState.value = ConnectionState.Connecting

            jobConnect?.cancel()
            jobConnect = viewModelScope.launch(Dispatchers.IO) {
                var ephemeralApiKey: String? = null
                try {
                    if (debugConnectDelayMillis > 0) {
                        delay(debugConnectDelayMillis)
                    }
                    ephemeralApiKey = realtimeClient?.connect()
                    Log.d(TAG, "ephemeralApiKey=${quote(redact(ephemeralApiKey, dangerousNullOK = true))}")
                    if (ephemeralApiKey == null) {
                        Log.e(TAG, "Failed to obtain ephemeral API key")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Connection failed", e)
                }
                if (ephemeralApiKey != null) {
                    realtimeClient?.setLocalAudioTrackMicrophoneEnabled(false)
                } else {
                    disconnect()
                }
            }
        } finally {
            Log.d(TAG, "-connect()")
        }
    }

    override fun disconnect(isManual: Boolean) {
        disconnect(isManual = isManual, isClient = false)
    }

    /**
     * @param isManual true if the user stopped; will prevent auto-reconnecting
     * @param isClient Prevents a realtimeClient disconnect from calling `realtimeClient.disconnect()`
     */
    private fun disconnect(isManual: Boolean = false, isClient: Boolean = false) {
        try {
            Log.d(TAG, "+disconnect(isClient=$isClient, isManual=$isManual)")

            if (_connectionState.value == ConnectionState.Disconnected) {
                Log.d(TAG, "disconnect: Already disconnected")
                return
            }

            if (isManual) {
                isDisconnectManual = true
            }

            jobConnect?.also {
                Log.d(TAG, "disconnect: Canceling jobConnect...")
                it.cancel()
                jobConnect = null
                Log.d(TAG, "disconnect: ...jobConnect canceled")
            }

            disconnectInternal(isClient)
        } finally {
            Log.d(TAG, "-disconnect(isClient=$isClient, isManual=$isManual)")
        }
    }

    /**
     * Common to both `tryInitializeRealtimeClient()` and `disconnect(...)`
     * @param isClient Prevents a realtimeClient disconnect from calling `realtimeClient.disconnect()`
     */
    private fun disconnectInternal(isClient: Boolean = false) {
        try {
            Log.d(TAG, "+disconnectInternal(isClient=$isClient)")
            _connectionState.value = ConnectionState.Disconnecting
            if (isClient) {
                Log.d(
                    TAG,
                    "disconnectInternal: Disconnect request **FROM RealtimeClient**; Intentionally **NOT** calling `realtimeClient.disconnect()`"
                )
            } else {
                realtimeClient?.also {
                    Log.d(TAG, "disconnectRealtimeClient: Disconnecting RealtimeClient...")
                    try {
                        it.disconnect()
                    } catch (e: Exception) {
                        Log.e(TAG, "disconnectRealtimeClient: Disconnect failed", e)
                    }
                    Log.d(TAG, "disconnectRealtimeClient: ...RealtimeClient disconnected")
                }
            }
            _connectionState.value = ConnectionState.Disconnected
        } finally {
            Log.d(TAG, "-disconnectInternal(isClient=$isClient)")
        }
    }

    override fun reconnect() {
        Log.d(TAG, "+reconnect()")
        disconnect()
        connect()
        Log.d(TAG, "-reconnect()")
    }

    /**
     * Called by listener.onDisconnected()
     */
    private fun onDisconnecting() {
        Log.d(TAG, "+onDisconnecting()")
        jobDebugFakeSessionExpired?.also {
            Log.d(TAG, "onDisconnecting: Canceling jobDebugFakeSessionExpired...")
            it.cancel()
            jobDebugFakeSessionExpired = null
            Log.d(TAG, "onDisconnecting: ...jobDebugFakeSessionExpired canceled")
        }
        Log.d(TAG, "-onDisconnecting()")
    }

    /**
     * Called by listener.onDisconnected()
     */
    private fun onDisconnected() {
        Log.d(TAG, "+onDisconnected()")
        conversationCurrentItemSet("onDisconnected", null)
        Log.d(TAG, "onDisconnected: audioSwitch.deactivate()")
        audioSwitch.deactivate()
        Log.d(TAG, "-onDisconnected()")
    }

    //
    //endregion
    //

    //
    //region PushToTalk
    //

    override fun pushToTalk(on: Boolean) {
        pushToTalk(on, null)
    }

    override fun pushToTalk(on: Boolean, sourceNodeId: String?) {
        Log.i(TAG, "pushToTalk(on=$on, sourceNodeId=${quote(sourceNodeId)}")
        if (on) {
            if (pushToTalkState.value != PttState.Pressed) {
                setPushToTalkState(PttState.Pressed)
                playAudioResourceOnce(getApplication(), R.raw.quindar_nasa_apollo_intro)
                //provideHapticFeedback(context)
            }
        } else {
            if (pushToTalkState.value != PttState.Idle) {
                setPushToTalkState(PttState.Idle)
                playAudioResourceOnce(getApplication(), R.raw.quindar_nasa_apollo_outro)
                //provideHapticFeedback(context)
            }
        }

        if (sourceNodeId == null) {
            // request from local/mobile
            Log.d(TAG, "pushToTalk: PTT $on **from** local/mobile...")
            val remoteAppNodeId = remoteAppNodeId.value
            if (remoteAppNodeId != null) {
                // tell remote/wear app that we are PTTing...
                sendPushToTalkCommand(remoteAppNodeId, on)
            }
            //...
        } else {
            // request from remote/wear
            //_remoteAppNodeId.value = sourceNodeId
            Log.d(TAG, "pushToTalk: PTT $on **from** remote/wear...")
            //...
        }

        pushToTalkLocal(on)
    }

    override fun pushToTalkLocal(on: Boolean) {
        super.pushToTalkLocal(on)
        if (on) {
            realtimeClient?.also { realtimeClient ->
                Log.d(TAG, "")
                Log.d(TAG, "+onPushToTalkStart: pttState=${pushToTalkState.value}")
                // 1. Play the start sound
                Log.d(TAG, "onPushToTalkStart: playing start sound")
                playAudioResourceOnce(
                    context = getApplication(),
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
        } else {
            realtimeClient?.also { realtimeClient ->
                Log.d(TAG, "")
                Log.d(TAG, "+onPushToTalkStop: pttState=${pushToTalkState.value}")
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
                    context = getApplication(),
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
        }
    }

    //
    //endregion
    //

    //
    //region Text
    //

    override fun sendText(message: String) {
        realtimeClient?.also { realtimeClient ->
            realtimeClient.dataSendConversationItemCreateMessageInputText(message.trim())
            realtimeClient.dataSendResponseCreate()
        }
    }

    //
    //endregion
    //

    //
    //region Cancel Response
    //

    private val _isCancelingResponse = MutableStateFlow(false)
    override val isCancelingResponse = _isCancelingResponse.asStateFlow()

    override fun sendCancelResponse(): Boolean {
        realtimeClient?.also { realtimeClient ->
            val result = realtimeClient.dataSendResponseCancel()
            _isCancelingResponse.value = result
            return result
        }
        return false
    }

    /**
     * https://platform.openai.com/docs/api-reference/realtime-client-events/conversation/item/truncate
     * conversation.item.truncate
     * "Send this event to truncate a previous assistant message’s audio."
     * To see it in use:
     * https://youtu.be/mM8KhTxwPgs?t=965
     */
    override fun sendConversationItemTruncate(itemId: String, audioEndMs: Int): Boolean {
        return realtimeClient?.dataSendConversationItemTruncate(itemId, audioEndMs) ?: false
    }

    override fun sendConversationItemTruncateConversationCurrentItem(): Boolean {
        val conversationItem = conversationCurrentItem
        Log.d(TAG, "sendConversationItemTruncateConversationCurrentItem: conversationCurrentItem=$conversationItem")
        if (conversationItem == null) {
            Log.w(TAG, "sendConversationItemTruncateConversationCurrentItem: conversationCurrentItem==null; ignoring")
            return false
        }
        val itemId = conversationItem.id
        if (itemId == null) {
            Log.w(TAG, "sendConversationItemTruncateConversationCurrentItem: conversationCurrentItem.id==null; ignoring")
            return false
        }
        val elapsedMs = Duration.between(conversationItem.timestamp, Instant.now()).toMillis()
        Log.d(TAG, "sendConversationItemTruncateConversationCurrentItem: itemId=$itemId, elapsedMs=$elapsedMs")
        return sendConversationItemTruncate(itemId, elapsedMs.toInt())
    }

    //
    //endregion
    //

    //
    //region Conversation
    //

    enum class ConversationItemType {
        Local,
        Remote,
        Function,
    }

    data class ConversationItem(
        val id: String?,
        val type: ConversationItemType,
        val initialText: String,
        val timestamp: Instant = Instant.now(),
        var incomplete: Boolean = false,
        val functionCallId: String = "",
        val functionName: String = "",
        var functionArguments: String = "",
        var functionOutput: String = "",
    ) {
        var text by mutableStateOf(initialText)

        override fun toString(): String {
            return "ConversationItem(id=${quote(id)}, type=$type, timestamp=$timestamp, incomplete=$incomplete, text=${quote(text)}, functionCallId=${quote(functionCallId)}, functionName=${quote(functionName)}, functionArguments=${quote(functionArguments)}, functionOutput=${quote(functionOutput)})"
        }
    }

    private fun initialConversations(): List<ConversationItem> {
        return if (debugFakeConversationCount > 0) {
            generateRandomConversationItems(debugFakeConversationCount)
        } else {
            emptyList()
        }
    }

    /**
     * Intentionally a mutableStateListOf (SnapshotStateList) and not
     * a MutableStateFlow so that [I hope] mutations are more efficient.
     * A MutableStateFlow would [I think] require replacing the whole list
     * when any item mutation is made. :/
     */
    private val _conversationItems = mutableStateListOf<ConversationItem>().apply {
        addAll(initialConversations())
    }

    /**
     * Intentionally a List<ConversationItem> and not a StateFlow...
     * ...for the same reasons as _conversationItems.
     */
    override val conversationItems: List<ConversationItem>
        get() = _conversationItems

    private fun conversationItemGet(index: Int): ConversationItem? {
        val conversationItem = _conversationItems.getOrNull(index)
        if (debugLogConversation) {
            Log.w(TAG, "conversationItemGet($index)=$conversationItem")
        }
        return conversationItem
    }

    private fun conversationItemFindById(id: String, debugLog: Boolean = true): Pair<Int, ConversationItem?> {
        val index = _conversationItems.indexOfFirst { it.id == id }
        val pair = if (index == -1) {
            Pair(-1, null)
        } else {
            Pair(index, _conversationItems[index])
        }
        if (debugLog && debugLogConversation) {
            Log.w(TAG, "conversationItemFindById(${quote(id)})=(index=${pair.first}, conversationItem=${pair.second})")
        }
        return pair
    }

    private fun conversationItemAdd(caller: String, conversationItem: ConversationItem) {
        if (debugLogConversation) {
            Log.e(TAG, "conversationItemAdd(caller=${quote(caller)}, $conversationItem)")
        }
        _conversationItems.add(conversationItem)
        if (conversationItem.type == ConversationItemType.Remote) {
            conversationCurrentItemSet(caller, conversationItem)
        }
    }

    private fun conversationItemUpdate(index: Int, conversationItem: ConversationItem) {
        if (debugLogConversation) {
            Log.e(TAG, "conversationItemUpdate($index, $conversationItem)")
        }
        _conversationItems[index] = conversationItem
    }

    private fun conversationItemDelete(index: Int) {
        if (debugLogConversation) {
            Log.e(TAG, "conversationItemDelete($index)")
        }
        _conversationItems.removeAt(index)
    }

    override fun conversationItemsClear() {
        if (debugLogConversation) {
            Log.e(TAG, "conversationItemsClear()")
        }
        _conversationItems.clear()
    }

    private var conversationCurrentItem: ConversationItem? = null

    private fun conversationCurrentItemSet(caller: String, conversationItem: ConversationItem?) {
        if (debugLogConversation) {
            Log.e(TAG, "conversationCurrentItemSet(${quote(caller)}, conversationItem=$conversationItem)")
        }
        conversationCurrentItem = conversationItem
    }

    //
    //endregion
    //

    //
    //region Notifications & Service Start/Stop
    //

    private val shouldShowNotification: Boolean
        get() = hasNoInUseActivities

    private fun observeConnectionState() {
        viewModelScope.launch {
            connectionState.collect { connectionState ->
                Log.i(TAG, "observeConnectionState: connectionState=$connectionState")
                when (connectionState) {
                    ConnectionState.Connecting -> {
                        conversationItemsClear()
                        MobileForegroundService.start(getApplication())
                    }
                    ConnectionState.Disconnecting -> {
                        onDisconnecting()
                    }
                    ConnectionState.Disconnected -> {
                        onDisconnected()
                        MobileForegroundService.stop(getApplication())
                    }
                    else -> {}
                }
            }
        }
    }

    private val notificationManager by lazy { getApplication<Application>().getSystemService(NotificationManager::class.java) }

    private fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = CHANNEL_DESCRIPTION
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(contentTitle: String, contentText: String): Notification {
        val context = getApplication<Application>()

        val notificationPendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MobileActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.alfredai_24)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true) // Makes the notification dismissible
            .setOnlyAlertOnce(true) // Prevents multiple alerts if the notification is updated
            .setContentIntent(notificationPendingIntent)
            .build()
    }

    private fun updateNotification(contentTitle: String, contentText: String) {
        if (shouldShowNotification) {
            val notification = createNotification(contentTitle, contentText)
            notificationManager.notify(NOTIFICATION_ID_SESSION, notification)
        } else {
            showToast(context = getApplication(), text = contentText, forceInvokeOnMain = true)
        }
    }

    private fun showNotificationSessionExpired(contentText: String) {
        updateNotification("AlfredAI: Session Expired", contentText)
    }

    private fun showNotificationMysteriousUnknownHostException() {
        val message = "Mysterious \"Unable to resolve host `api.openai.com`\" error; Try again."
        updateNotification("AlfredAI: Client Error", quote(message))
    }

    private fun showNotificationSessionClientError(message: String) {
        updateNotification("AlfredAI: Client Error", quote(message))
    }

    //
    //endregion
    //

    //
    //region persistent RealtimeClientListener
    //

    private val listeners = mutableListOf<RealtimeClientListener>()

    @Suppress("unused")
    fun addListener(listener: RealtimeClientListener) {
        listeners.add(listener)
    }

    @Suppress("unused")
    fun removeListener(listener: RealtimeClientListener) {
        listeners.remove(listener)
    }

    private val listener = object : RealtimeClientListener {
        override fun onConnecting() {
            Log.d(TAG, "onConnecting()")
            _connectionState.value = ConnectionState.Connecting
            val context = getApplication<Application>()
            if (debugToastVerbose) {
                showToast(context = context, text = "Connecting...", forceInvokeOnMain = true)
            }
            playAudioResourceOnce(context, R.raw.connecting)

            listeners.forEach {
                it.onConnecting()
            }
        }

        override fun onError(error: Exception) {
            Log.d(TAG, "onError($error)")
            when (error) {
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
                    showNotificationMysteriousUnknownHostException()
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
                    showNotificationSessionClientError(message!!)
                }

                else -> {
                    val message = "Error: ${error.message}"
                    showNotificationSessionClientError(message)
                }
            }
            listeners.forEach {
                it.onError(error)
            }
            disconnect(isClient = true)
        }

        override fun onDataChannelOpened() {
            Log.d(TAG, "onDataChannelOpened()")
            listeners.forEach {
                it.onDataChannelOpened()
            }
        }

        override fun onConnected() {
            Log.d(TAG, "onConnected()")
            _connectionState.value = ConnectionState.Connected
            if (debugToastVerbose) {
                showToast(context = getApplication(), text = "Connected", forceInvokeOnMain = true)
            }
            playAudioResourceOnce(getApplication(), R.raw.connected)

            Log.d(TAG, "onConnected: audioSwitch.activate()")
            audioSwitch.activate()

            if (debugSimulateSessionExpiredMillis > 0) {
                jobDebugFakeSessionExpired = viewModelScope.launch {
                    delay(debugSimulateSessionExpiredMillis)
                    // Create a fake RealtimeServerEventError
                    val fakeError = RealtimeServerEventError(
                        eventId = "fake-event-id",
                        type = RealtimeServerEventError.Type.error,
                        error = RealtimeServerEventErrorError(
                            type = "invalid_request_error",
                            message = "Your session hit the DEBUG SIMULATED maximum duration of ${debugSimulateSessionExpiredMillis / 1000} seconds.",
                            code = "session_expired",
                        )
                    )
                    onServerEventSessionExpired(fakeError)
                    Log.d(TAG, "Debug: Fake session expired event triggered")
                    disconnect()
                }
            }

            listeners.forEach {
                it.onConnected()
            }
        }

        override fun onDisconnected() {
            Log.d(TAG, "onDisconnected()")
            this@MobileViewModel.onDisconnecting()

            if (debugToastVerbose) {
                showToast(context = getApplication(), text = "Disconnected", forceInvokeOnMain = true)
            }
            playAudioResourceOnce(getApplication(), R.raw.disconnected)

            disconnect(isClient = true)
            listeners.forEach {
                it.onDisconnected()
            }

            this@MobileViewModel.onDisconnected()
        }

        override fun onBinaryMessageReceived(data: ByteArray): Boolean {
            //Log.d(TAG, "onBinaryMessageReceived(): data(${data.size})=...")
            listeners.forEach {
                if (it.onBinaryMessageReceived(data)) return true
            }
            return false
        }

        override fun onTextMessageReceived(message: String): Boolean {
            //Log.d(TAG, "onTextMessageReceived(): message=${quote(message)}")
            listeners.forEach {
                if (it.onTextMessageReceived(message)) return true
            }
            return false
        }

        override fun onServerEventConversationCreated(message: RealtimeServerEventConversationCreated) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationCreated($message)")
            }
            listeners.forEach {
                it.onServerEventConversationCreated(message)
            }
        }

        override fun onServerEventConversationItemCreated(message: RealtimeServerEventConversationItemCreated) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemCreated($message)")
            }
            val item = message.item
            val itemId = item.id
            when (item.type) {
                RealtimeConversationItem.Type.function_call -> {
                    val functionCallId = item.callId!!
                    val functionName = item.name!!
                    val functionArguments = item.arguments!!
                    Log.i(TAG, "onServerEventConversationItemCreated: id=${quote(itemId)} function_call callId=${quote(functionCallId)}, name=${quote(functionName)}, arguments=${quote(functionArguments)}")
                    val conversationItem = ConversationItem(
                        id = itemId,
                        type = ConversationItemType.Function,
                        initialText = "Function: `$functionName`",
                        functionCallId = functionCallId,
                        functionName = functionName,
                        functionArguments = functionArguments,
                    )
                    conversationItemAdd(
                        "onServerEventConversationItemCreated",
                        conversationItem
                    )
                }
                RealtimeConversationItem.Type.message -> {
                    val content = item.content
                    val text = content?.joinToString(separator = "") { it.text ?: "" } ?: ""
                    if (text.isNotBlank()) {
                        conversationItemAdd(
                            "onServerEventConversationItemCreated",
                            ConversationItem(
                                id = itemId,
                                type = ConversationItemType.Local,
                                initialText = text,
                            )
                        )
                    }
                }
                else -> { /* ignore */ }
            }
            listeners.forEach {
                it.onServerEventConversationItemCreated(message)
            }
        }

        override fun onServerEventConversationItemDeleted(message: RealtimeServerEventConversationItemDeleted) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemDeleted($message)")
            }
            listeners.forEach {
                it.onServerEventConversationItemDeleted(message)
            }
        }

        override fun onServerEventConversationItemInputAudioTranscriptionCompleted(message: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemInputAudioTranscriptionCompleted($message)")
            }
            val id = message.itemId
            val transcript = message.transcript.trim() // **DO** TRIM!
            if (transcript.isNotBlank()) {
                conversationItemAdd(
                    "onServerEventConversationItemInputAudioTranscriptionCompleted",
                    ConversationItem(
                        id = id,
                        type = ConversationItemType.Local,
                        initialText = transcript,
                    )
                )
            }
            listeners.forEach {
                it.onServerEventConversationItemInputAudioTranscriptionCompleted(message)
            }
        }

        override fun onServerEventConversationItemInputAudioTranscriptionFailed(message: RealtimeServerEventConversationItemInputAudioTranscriptionFailed) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemInputAudioTranscriptionFailed($message)")
            }
            listeners.forEach {
                it.onServerEventConversationItemInputAudioTranscriptionFailed(message)
            }
        }

        override fun onServerEventConversationItemTruncated(message: RealtimeServerEventConversationItemTruncated) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemTruncated($message)")
            }
            listeners.forEach {
                it.onServerEventConversationItemTruncated(message)
            }
        }

        override fun onServerEventError(message: RealtimeServerEventError) {
            Log.d(TAG, "onServerEventError($message)")
            val error = message.error
            val errorMessage = error.message
            showToast(
                context = getApplication(),
                text = errorMessage,
                duration = Toast.LENGTH_LONG,
                forceInvokeOnMain = true,
            )
            listeners.forEach {
                it.onServerEventError(message)
            }
        }

        override fun onServerEventInputAudioBufferCleared(message: RealtimeServerEventInputAudioBufferCleared) {
            Log.d(TAG, "onServerEventInputAudioBufferCleared($message)")
            listeners.forEach {
                it.onServerEventInputAudioBufferCleared(message)
            }
        }

        override fun onServerEventInputAudioBufferCommitted(message: RealtimeServerEventInputAudioBufferCommitted) {
            Log.d(TAG, "onServerEventInputAudioBufferCommitted($message)")
            listeners.forEach {
                it.onServerEventInputAudioBufferCommitted(message)
            }
        }

        override fun onServerEventInputAudioBufferSpeechStarted(message: RealtimeServerEventInputAudioBufferSpeechStarted) {
            Log.d(TAG, "onServerEventInputAudioBufferSpeechStarted($message)")
            listeners.forEach {
                it.onServerEventInputAudioBufferSpeechStarted(message)
            }
        }

        override fun onServerEventInputAudioBufferSpeechStopped(message: RealtimeServerEventInputAudioBufferSpeechStopped) {
            Log.d(TAG, "onServerEventInputAudioBufferSpeechStopped($message)")
            listeners.forEach {
                it.onServerEventInputAudioBufferSpeechStopped(message)
            }
        }

        override fun onServerEventOutputAudioBufferAudioStarted(message: RealtimeClient.ServerEventOutputAudioBufferAudioStarted) {
            Log.d(TAG, "onServerEventOutputAudioBufferAudioStarted($message)")
            listeners.forEach {
                it.onServerEventOutputAudioBufferAudioStarted(message)
            }
        }

        override fun onServerEventOutputAudioBufferAudioStopped(message: ServerEventOutputAudioBufferAudioStopped) {
            Log.d(TAG, "onServerEventOutputAudioBufferAudioStopped($message)")
            if (_isCancelingResponse.value) {
                _isCancelingResponse.value = false
                showToast(
                    context = getApplication(),
                    text = "Response canceled",
                    forceInvokeOnMain = true,
                )
            }
            listeners.forEach {
                it.onServerEventOutputAudioBufferAudioStopped(message)
            }
            conversationCurrentItemSet("onServerEventOutputAudioBufferAudioStopped", null)
        }

        override fun onServerEventRateLimitsUpdated(message: RealtimeServerEventRateLimitsUpdated) {
            Log.d(TAG, "onServerEventRateLimitsUpdated($message)")
            listeners.forEach {
                it.onServerEventRateLimitsUpdated(message)
            }
        }

        override fun onServerEventResponseAudioDelta(message: RealtimeServerEventResponseAudioDelta) {
            Log.d(TAG, "onServerEventResponseAudioDelta($message)")
            listeners.forEach {
                it.onServerEventResponseAudioDelta(message)
            }
        }

        override fun onServerEventResponseAudioDone(message: RealtimeServerEventResponseAudioDone) {
            Log.d(TAG, "onServerEventResponseAudioDone($message)")
            listeners.forEach {
                it.onServerEventResponseAudioDone(message)
            }
        }

        override fun onServerEventResponseAudioTranscriptDelta(message: RealtimeServerEventResponseAudioTranscriptDelta) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseAudioTranscriptDelta($message)")
            }
            val itemId = message.itemId
            val delta = message.delta // DO **NOT** TRIM!

            //
            // Append this delta to any current in progress conversation,
            // or create a new conversation
            //

            var (index, conversationItem) = conversationItemFindById(itemId, debugLog = false)
            if (debugLogConversation) {
                Log.w(TAG, "onServerEventResponseAudioTranscriptDelta: itemId=${quote(itemId)}, delta=${quote(delta)}, index=$index, conversationItem=$conversationItem")
            }
            if (conversationItem == null) {
                conversationItem = ConversationItem(
                    id = itemId,
                    type = ConversationItemType.Remote,
                    initialText = delta,
                )
                conversationItemAdd("onServerEventResponseAudioTranscriptDelta", conversationItem)
            } else {
                conversationItem.text += delta
                conversationItemUpdate(index, conversationItem)
            }
            listeners.forEach {
                it.onServerEventResponseAudioTranscriptDelta(message)
            }
        }

        override fun onServerEventResponseAudioTranscriptDone(message: RealtimeServerEventResponseAudioTranscriptDone) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseAudioTranscriptDone($message)")
            }
            listeners.forEach {
                it.onServerEventResponseAudioTranscriptDone(message)
            }
        }

        override fun onServerEventResponseContentPartAdded(message: RealtimeServerEventResponseContentPartAdded) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseContentPartAdded($message)")
            }
            listeners.forEach {
                it.onServerEventResponseContentPartAdded(message)
            }
        }

        override fun onServerEventResponseContentPartDone(message: RealtimeServerEventResponseContentPartDone) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseContentPartDone($message)")
            }
            listeners.forEach {
                it.onServerEventResponseContentPartDone(message)
            }
        }

        override fun onServerEventResponseCreated(message: RealtimeServerEventResponseCreated) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseCreated($message)")
            }
            listeners.forEach {
                it.onServerEventResponseCreated(message)
            }
        }

        override fun onServerEventResponseDone(message: RealtimeServerEventResponseDone) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseDone($message)")
            }
            val response = message.response

            var incomplete = false

            when (response.status) {
                RealtimeResponse.Status.incomplete -> {
                    incomplete = true
                    val statusDetails = response.statusDetails
                    when (statusDetails?.reason) {
                        RealtimeResponseStatusDetails.Reason.max_output_tokens -> {
                            val text = "Max output tokens reached"
                            Log.w(TAG, "onServerEventResponseDone: ${text.uppercase()}")
                            showToast(
                                context = getApplication(),
                                text = text,
                                forceInvokeOnMain = true,
                            )
                        }
                        else -> { /* ignore */ }
                    }
                }
                else -> { /* ignore */ }
            }

            response.output?.forEach { outputConversationItem ->
                if (debugLogConversation) {
                    Log.w(TAG, "onServerEventResponseDone: outputConversationItem=$outputConversationItem")
                }
                val id = outputConversationItem.id ?: return@forEach
                val (index, conversationItem) = conversationItemFindById(id)
                if (conversationItem != null) {
                    conversationItemDelete(index)
                    conversationItem.incomplete = incomplete
                    conversationItemAdd("onServerEventResponseDone", conversationItem)
                }
            }
            listeners.forEach {
                it.onServerEventResponseDone(message)
            }
        }

        override fun onServerEventResponseFunctionCallArgumentsDelta(message: RealtimeServerEventResponseFunctionCallArgumentsDelta) {
            Log.d(TAG, "onServerEventResponseFunctionCallArgumentsDelta($message)")
            val itemId = message.itemId
            val delta = message.delta // DO **NOT** TRIM!

            val (index, conversationItem) = conversationItemFindById(itemId, debugLog = false)
            //Log.d(TAG, "onServerEventResponseFunctionCallArgumentsDelta: id=${quote(itemId)}, delta=${quote(delta)}, index=$index, conversationItem=$conversationItem")
            if (conversationItem != null) {
                conversationItem.functionArguments += delta
                conversationItemUpdate(index, conversationItem)
            }
            listeners.forEach {
                it.onServerEventResponseFunctionCallArgumentsDelta(message)
            }
        }

        override fun onServerEventResponseFunctionCallArgumentsDone(message: RealtimeServerEventResponseFunctionCallArgumentsDone) {
            Log.d(TAG, "onServerEventResponseFunctionCallArgumentsDone($message)")
            val itemId = message.itemId
            val callId = message.callId
            val (index, conversationItem) = conversationItemFindById(itemId, debugLog = false)
            Log.i(TAG, "onServerEventResponseFunctionCallArgumentsDone: itemId=$itemId, callId=$callId, index=$index, conversationItem=$conversationItem")
            if (conversationItem != null) {
                val functionName = conversationItem.functionName
                val functionArguments = JSONObject(conversationItem.functionArguments)
                val functionOutput = runFunction(functionName, functionArguments)
                conversationItem.functionOutput = functionOutput
                realtimeClient?.dataSendConversationItemCreateFunctionCallOutput(
                    callId = conversationItem.functionCallId,
                    output = conversationItem.functionOutput,
                )
            }
            listeners.forEach {
                it.onServerEventResponseFunctionCallArgumentsDone(message)
            }
        }

        override fun onServerEventResponseOutputItemAdded(message: RealtimeServerEventResponseOutputItemAdded) {
            Log.d(TAG, "onServerEventResponseOutputItemAdded($message)")
            listeners.forEach {
                it.onServerEventResponseOutputItemAdded(message)
            }
        }

        override fun onServerEventResponseOutputItemDone(message: RealtimeServerEventResponseOutputItemDone) {
            Log.d(TAG, "onServerEventResponseOutputItemDone($message)")
            listeners.forEach {
                it.onServerEventResponseOutputItemDone(message)
            }
        }

        override fun onServerEventResponseTextDelta(message: RealtimeServerEventResponseTextDelta) {
            Log.d(TAG, "onServerEventResponseTextDelta($message)")
            listeners.forEach {
                it.onServerEventResponseTextDelta(message)
            }
        }

        override fun onServerEventResponseTextDone(message: RealtimeServerEventResponseTextDone) {
            Log.d(TAG, "onServerEventResponseTextDone($message)")
            listeners.forEach {
                it.onServerEventResponseTextDone(message)
            }
        }

        override fun onServerEventSessionCreated(message: RealtimeServerEventSessionCreated) {
            Log.d(TAG, "onServerEventSessionCreated($message)")
            if (debugToastVerbose) {
                showToast(context = getApplication(), text = "Session Created", forceInvokeOnMain = true)
            }
            listeners.forEach {
                it.onServerEventSessionCreated(message)
            }
        }

        override fun onServerEventSessionUpdated(message: RealtimeServerEventSessionUpdated) {
            Log.d(TAG, "onServerEventSessionUpdated($message)")
            if (debugToastVerbose) {
                showToast(
                    context = getApplication(),
                    text = "Session Updated",
                    forceInvokeOnMain = true,
                )
            }
            listeners.forEach {
                it.onServerEventSessionUpdated(message)
            }
        }

        override fun onServerEventSessionExpired(message: RealtimeServerEventError) {
            Log.d(TAG, "onServerEventSessionExpired($message)")
            val errorMessage = message.error.message
            showNotificationSessionExpired(errorMessage)
            listeners.forEach {
                it.onServerEventSessionExpired(message)
            }
        }
    }

    //
    //endregion
    //

    //
    //region AudioSwitch
    //

    private var _audioDevices = MutableStateFlow(emptyList<AudioDevice>())
    override val audioDevices = _audioDevices.asStateFlow()

    private var _selectedAudioDevice = MutableStateFlow<AudioDevice?>(null)
    override val selectedAudioDevice = _selectedAudioDevice.asStateFlow()

    override fun selectAudioDevice(device: AudioDevice) {
        // AudioSwitch will also update our callback with the new "selectedDevice"
        // so we typically *don't* set '_selectedAudioDevice' manually here.
        audioSwitch.selectDevice(device)
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG , "onAudioFocusChange($focusChange)")
    }

    private val bluetoothHeadsetConnectionListener = object : BluetoothHeadsetConnectionListener {
        override fun onBluetoothHeadsetActivationError() {
            Log.d(TAG, "onBluetoothHeadsetActivationError()")
        }

        override fun onBluetoothHeadsetStateChanged(headsetName: String?, state: Int) {
            Log.d(TAG, "onBluetoothHeadsetStateChanged(headsetName=${quote(headsetName)}, state=${bluetoothHeadsetStateToString(state)})")
        }

        override fun onBluetoothScoStateChanged(state: Int) {
            Log.d(TAG, "onBluetoothScoStateChanged(state=${audioManagerScoStateToString(state)})")
        }
    }

    private val audioSwitch = AudioSwitch(
        context = application,
        audioFocusChangeListener = audioFocusChangeListener,
        bluetoothHeadsetConnectionListener = bluetoothHeadsetConnectionListener,
        loggingEnabled = DEBUG,
        // Override their private defaultPreferredDeviceList to put Speakerphone above Earpiece
        preferredDeviceList = listOf(
            BluetoothHeadset::class.java,
            WiredHeadset::class.java,
            Speakerphone::class.java,
            Earpiece::class.java,
        )
    )

    private fun audioSwitchStart() {
        audioSwitchStop()
        Log.d(TAG, "audioSwitchStart: audioSwitch.start { ... }")
        audioSwitch.start { audioDevices, selectedAudioDevice ->
            Log.d(TAG, "audioSwitchStart.listener: selectedAudioDevice=$selectedAudioDevice, audioDevices=$audioDevices")
            _audioDevices.value = audioDevices
            _selectedAudioDevice.value = selectedAudioDevice
        }
    }

    private fun audioSwitchStop() {
        Log.d(TAG, "audioSwitchStop: audioSwitch.stop()")
        audioSwitch.stop()
    }

    //
    //endregion
    //

    //
    //region Functions
    //

    //
    // https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-tools
    // https://platform.openai.com/docs/guides/function-calling
    //

    private data class FunctionInfo(
        val function: (JSONObject) -> String,
        val tool: RealtimeSessionTools,
    )

    private val functionsMap = mapOf(
        "taskCreate" to FunctionInfo(
            function = ::functionTaskCreate,
            tool = RealtimeSessionTools(
                type = RealtimeSessionTools.Type.function,
                name = "taskCreate",
                description = "Create a task",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf(
                            "type" to "string",
                            "description" to "The task name"
                        ),
                        "time" to mapOf(
                            "type" to "string",
                            "description" to "The task time"
                        ),
                    ),
                    "required" to listOf("name"),
                )
            )
        ),
    )

    private val functions = functionsMap.values.map { it.tool }

    private fun runFunction(name: String, arguments: JSONObject): String {
        val functionInfo = functionsMap[name]
        if (functionInfo == null) {
            Log.e(TAG, "onServerEventResponseFunctionCallArgumentsDone: Unknown function name=${quote(name)}")
            return ""
        }
        val result = functionInfo.function(arguments)
        return result
    }

    private fun functionTaskCreate(arguments: JSONObject): String {
        Log.d(TAG, "+functionTaskCreate(arguments=$arguments)")
        //...
        val context: Context = getApplication()
        showToast(context, "TODO: Create task with arguments=$arguments", forceInvokeOnMain = true)
        //...
        val output = "success"
        //...
        Log.d(TAG, "-functionTaskCreate(arguments=$arguments); output=$output")
        return output
    }

    //
    //
    //
}
