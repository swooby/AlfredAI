package com.swooby.alfredai

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.lifecycle.viewModelScope
import com.openai.infrastructure.ApiClient
import com.openai.infrastructure.ClientError
import com.openai.infrastructure.ClientException
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
import com.openai.models.RealtimeSessionVoice
import com.swooby.alfredai.PushToTalkPreferences.Companion.getMaxResponseOutputTokens
import com.swooby.alfredai.Utils.playAudioResourceOnce
import com.swooby.alfredai.Utils.quote
import com.swooby.alfredai.openai.realtime.RealtimeClient
import com.swooby.alfredai.openai.realtime.RealtimeClient.RealtimeClientListener
import com.swooby.alfredai.openai.realtime.RealtimeClient.ServerEventOutputAudioBufferAudioStopped
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.math.BigDecimal
import java.net.UnknownHostException

class MobileViewModel(application: Application) :
    SharedViewModel(application)
{
    companion object {
        const val DEBUG = false

        private const val CHANNEL_ID = "SESSION_STATUS_CHANNEL"
        private const val CHANNEL_NAME = "Session Status Channel"
        private const val CHANNEL_DESCRIPTION = "Dismissible notifications for notable session status events"
        private const val NOTIFICATION_ID_SESSION = 1001

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        val debugSimulateSessionExpired = BuildConfig.DEBUG && false
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

        createNotificationChannel()

        audioSwitchStart()

        observeConnectionForServiceStartStop()

        if (debugSimulateSessionExpired) {
            jobDebugFakeSessionExpired = viewModelScope.launch {
                delay(20_000L) // Delay for 20 seconds...
                // Create a fake RealtimeServerEventError
                val fakeError = RealtimeServerEventError(
                    eventId = "fake-event-id",
                    type = RealtimeServerEventError.Type.error,
                    error = RealtimeServerEventErrorError(
                        type = "invalid_request_error",
                        message = "Your session hit the maximum duration of 30 minutes.",
                        code = "session_expired",
                    )
                )
                listener.onServerEventSessionExpired(fakeError)
                Log.d(TAG, "Debug: Fake session expired event triggered.")
                disconnect()
            }
        }
    }

    override fun close() {
        super.close()
        disconnect()
        onDisconnecting()
        onDisconnected()
        audioSwitchStop()
    }

    fun disconnect() {
        if (realtimeClient?.isConnected == true) {
            realtimeClient?.disconnect()
        }
    }

    /**
     * Called by listener.onDisconnected()
     */
    private fun onDisconnecting() {
        if (jobDebugFakeSessionExpired?.isActive == true) {
            jobDebugFakeSessionExpired?.cancel()
        }
        _connectionStateFlow.value = ConnectionState.Disconnected
    }

    /**
     * Called by listener.onDisconnected()
     */
    private fun onDisconnected() {
        audioSwitch.deactivate()
    }

    //
    //region In-Use-Activities Detection
    //

    private val inUseActivities = mutableSetOf<Activity>()

    private val hasNoInUseActivities: Boolean
        get() = inUseActivities.isEmpty()

    fun onStartOrResume(activity: Activity) {
        inUseActivities.add(activity)
    }

    fun onPauseOrStop(activity: Activity) {
        inUseActivities.remove(activity)
    }

    //
    //endregion
    //

    //
    //region Preferences
    //

    private val prefs = PushToTalkPreferences(application)

    private var _autoConnect = MutableStateFlow(prefs.autoConnect)
    var autoConnect = _autoConnect.asStateFlow()

    private var _apiKey = MutableStateFlow(prefs.apiKey)
    val apiKey = _apiKey.asStateFlow()

    private var _model = MutableStateFlow(prefs.model)
    val model = _model.asStateFlow()

    private var _instructions = MutableStateFlow(prefs.instructions)
    val instructions = _instructions.asStateFlow()

    private var _voice = MutableStateFlow(prefs.voice)
    val voice = _voice.asStateFlow()

    private var _inputAudioTranscription = MutableStateFlow(prefs.inputAudioTranscription)
    val inputAudioTranscription = _inputAudioTranscription.asStateFlow()

    private var _temperature = MutableStateFlow(prefs.temperature)
    val temperature = _temperature.asStateFlow()

    private var _maxResponseOutputTokens = MutableStateFlow(prefs.maxResponseOutputTokens)
    val maxResponseOutputTokens = _maxResponseOutputTokens.asStateFlow()

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
                tools = null,
                toolChoice = null,
                temperature = BigDecimal(temperature.value.toDouble()),
                maxResponseOutputTokens = getMaxResponseOutputTokens(maxResponseOutputTokens.value),
            )
        }

    fun updatePreferences(
        autoConnect: Boolean,
        apiKey: String,
        model: RealtimeSessionModel,
        instructions: String,
        voice: RealtimeSessionVoice,
        inputAudioTranscription: RealtimeSessionInputAudioTranscription?,
        temperature: Float,
        maxResponseOutputTokens: Int,
    ): Job? {
        prefs.autoConnect = autoConnect
        _autoConnect.value = autoConnect

        var reconnectSession = false
        var updateSession = false

        if (apiKey != this.apiKey.value) {
            reconnectSession = true
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

        var jobReconnect: Job? = null

        if (realtimeClient == null) {
            tryInitializeRealtimeClient()
        } else {
            if (isConnectingOrConnected) {
                if (isConnecting || reconnectSession) {
                    _realtimeClient?.disconnect()
                    jobReconnect = CoroutineScope(Dispatchers.IO).launch {
                        _realtimeClient?.connect()
                    }
                } else {
                    if (isConnected && updateSession) {
                        realtimeClient?.dataSendSessionUpdate(sessionConfig)
                    }
                }
            }
        }

        return jobReconnect
    }

    val isConfigured: Boolean
        get() = apiKey.value.isNotBlank()

    //
    //endregion
    //

    private var _realtimeClient: RealtimeClient? = null
    val realtimeClient: RealtimeClient?
        get() {
            if (_realtimeClient == null) {
                tryInitializeRealtimeClient()
            }
            return _realtimeClient
        }

    val isConnectingOrConnected: Boolean
        get() = realtimeClient?.isConnectingOrConnected ?: false

    val isConnected: Boolean
        get() = realtimeClient?.isConnected ?: false

    private val isConnecting: Boolean
        get() = realtimeClient?.isConnecting ?: false

    private fun tryInitializeRealtimeClient(): Boolean {
        if (!isConfigured) {
            return false
        }

        _realtimeClient?.disconnect()

        val httpClient = if (DEBUG)
            RealtimeClient.httpLoggingClient
        else
            ApiClient.defaultClient

        _realtimeClient = RealtimeClient(
            applicationContext = getApplication(),
            dangerousApiKey = prefs.apiKey,
            sessionConfig = sessionConfig,
            httpClient = httpClient,
            debug = DEBUG
        )
        _realtimeClient?.addListener(listener)

        return true
    }

    override fun pushToTalk(on: Boolean, sourceNodeId: String?) {
        Log.i(TAG, "pushToTalk(on=$on)")
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
    //region Conversation
    //

    @Suppress(
        "SimplifyBooleanWithConstants",
        //"KotlinConstantConditions",
    )
    val debugLogConversation = BuildConfig.DEBUG && true

    enum class ConversationSpeaker {
        Local,
        Remote,
    }

    data class ConversationItem(
        val id: String?,
        val speaker: ConversationSpeaker,
        val initialText: String
    ) {
        var text by mutableStateOf(initialText)
    }

    private val _conversationItems = mutableStateListOf<ConversationItem>()
    val conversationItems: List<ConversationItem>
        get() = _conversationItems

    fun conversationItemsClear() {
        _conversationItems.clear()
    }

    //
    //endregion
    //

    //
    //region Service Start/Stop & Notifications
    //

    enum class ConnectionState {
        Connecting,
        Connected,
        Disconnected,
    }

    private val _connectionStateFlow = MutableStateFlow(ConnectionState.Disconnected)
    val connectionStateFlow = _connectionStateFlow.asStateFlow()

    private fun observeConnectionForServiceStartStop() {
        viewModelScope.launch {
            connectionStateFlow.collect { connectionState ->
                Log.i(TAG, "connectionStateFlow: connectionState=$connectionState")
                when (connectionState) {
                    ConnectionState.Connecting -> {
                        MobileForegroundService.start(getApplication())
                    }
                    ConnectionState.Disconnected -> {
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
        val notification = createNotification(contentTitle, contentText)
        notificationManager.notify(NOTIFICATION_ID_SESSION, notification)
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
    private val listener = object : RealtimeClientListener {
        override fun onConnecting() {
            _connectionStateFlow.value = ConnectionState.Connecting

            conversationItemsClear()

            listeners.forEach {
                it.onConnecting()
            }
        }

        override fun onError(error: Exception) {
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
            }
            listeners.forEach {
                it.onError(error)
            }
        }

        override fun onConnected() {
            _connectionStateFlow.value = ConnectionState.Connected

            audioSwitch.activate()

            listeners.forEach {
                it.onConnected()
            }
        }

        override fun onDisconnected() {
            this@MobileViewModel.onDisconnecting()

            listeners.forEach {
                it.onDisconnected()
            }

            this@MobileViewModel.onDisconnected()
        }

        override fun onBinaryMessageReceived(data: ByteArray): Boolean {
            listeners.forEach {
                if (it.onBinaryMessageReceived(data)) return true
            }
            return false
        }

        override fun onTextMessageReceived(message: String): Boolean {
            listeners.forEach {
                if (it.onTextMessageReceived(message)) return true
            }
            return false
        }

        override fun onServerEventConversationCreated(realtimeServerEventConversationCreated: RealtimeServerEventConversationCreated) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationCreated($realtimeServerEventConversationCreated)")
            }
            listeners.forEach {
                it.onServerEventConversationCreated(realtimeServerEventConversationCreated)
            }
        }

        override fun onServerEventConversationItemCreated(realtimeServerEventConversationItemCreated: RealtimeServerEventConversationItemCreated) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemCreated($realtimeServerEventConversationItemCreated)")
            }
            val item = realtimeServerEventConversationItemCreated.item
            val id = item.id
            val content = item.content
            val text = content?.joinToString(separator = "") { it.text ?: "" } ?: ""
            if (text.isNotBlank()) {
                if (debugLogConversation) {
                    Log.w(TAG, "onServerEventConversationItemCreated: conversationItems.add(ConversationItem(id=${quote(id)}, initialText=${quote(text)}")
                }
                _conversationItems.add(
                    ConversationItem(
                        id = id,
                        speaker = ConversationSpeaker.Local,
                        initialText = text
                    )
                )
            }
            listeners.forEach {
                it.onServerEventConversationItemCreated(realtimeServerEventConversationItemCreated)
            }
        }

        override fun onServerEventConversationItemDeleted(realtimeServerEventConversationItemDeleted: RealtimeServerEventConversationItemDeleted) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemDeleted($realtimeServerEventConversationItemDeleted)")
            }
            listeners.forEach {
                it.onServerEventConversationItemDeleted(realtimeServerEventConversationItemDeleted)
            }
        }

        override fun onServerEventConversationItemInputAudioTranscriptionCompleted(realtimeServerEventConversationItemInputAudioTranscriptionCompleted: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemInputAudioTranscriptionCompleted($realtimeServerEventConversationItemInputAudioTranscriptionCompleted)")
            }
            val id = realtimeServerEventConversationItemInputAudioTranscriptionCompleted.itemId
            val transcript =
                realtimeServerEventConversationItemInputAudioTranscriptionCompleted.transcript.trim() // DO TRIM!
            if (transcript.isNotBlank()) {
                if (debugLogConversation) {
                    Log.w(TAG, "onServerEventConversationItemInputAudioTranscriptionCompleted: conversationItems.add(ConversationItem(id=${quote(id)}, initialText=${quote(transcript)}")
                }
                _conversationItems.add(
                    ConversationItem(
                        id = id,
                        speaker = ConversationSpeaker.Local,
                        initialText = transcript
                    )
                )
            }
            listeners.forEach {
                it.onServerEventConversationItemInputAudioTranscriptionCompleted(realtimeServerEventConversationItemInputAudioTranscriptionCompleted)
            }
        }

        override fun onServerEventConversationItemInputAudioTranscriptionFailed(realtimeServerEventConversationItemInputAudioTranscriptionFailed: RealtimeServerEventConversationItemInputAudioTranscriptionFailed) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemInputAudioTranscriptionFailed($realtimeServerEventConversationItemInputAudioTranscriptionFailed)")
            }
            listeners.forEach {
                it.onServerEventConversationItemInputAudioTranscriptionFailed(realtimeServerEventConversationItemInputAudioTranscriptionFailed)
            }
        }

        override fun onServerEventConversationItemTruncated(realtimeServerEventConversationItemTruncated: RealtimeServerEventConversationItemTruncated) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventConversationItemTruncated($realtimeServerEventConversationItemTruncated)")
            }
            listeners.forEach {
                it.onServerEventConversationItemTruncated(realtimeServerEventConversationItemTruncated)
            }
        }

        override fun onServerEventError(realtimeServerEventError: RealtimeServerEventError) {
            listeners.forEach {
                it.onServerEventError(realtimeServerEventError)
            }
        }

        override fun onServerEventInputAudioBufferCleared(realtimeServerEventInputAudioBufferCleared: RealtimeServerEventInputAudioBufferCleared) {
            listeners.forEach {
                it.onServerEventInputAudioBufferCleared(realtimeServerEventInputAudioBufferCleared)
            }
        }

        override fun onServerEventInputAudioBufferCommitted(realtimeServerEventInputAudioBufferCommitted: RealtimeServerEventInputAudioBufferCommitted) {
            listeners.forEach {
                it.onServerEventInputAudioBufferCommitted(realtimeServerEventInputAudioBufferCommitted)
            }
        }

        override fun onServerEventInputAudioBufferSpeechStarted(realtimeServerEventInputAudioBufferSpeechStarted: RealtimeServerEventInputAudioBufferSpeechStarted) {
            listeners.forEach {
                it.onServerEventInputAudioBufferSpeechStarted(realtimeServerEventInputAudioBufferSpeechStarted)
            }
        }

        override fun onServerEventInputAudioBufferSpeechStopped(realtimeServerEventInputAudioBufferSpeechStopped: RealtimeServerEventInputAudioBufferSpeechStopped) {
            listeners.forEach {
                it.onServerEventInputAudioBufferSpeechStopped(realtimeServerEventInputAudioBufferSpeechStopped)
            }
        }

        override fun onServerEventOutputAudioBufferAudioStopped(realtimeServerEventOutputAudioBufferAudioStopped: ServerEventOutputAudioBufferAudioStopped) {
            listeners.forEach {
                it.onServerEventOutputAudioBufferAudioStopped(realtimeServerEventOutputAudioBufferAudioStopped)
            }
        }

        override fun onServerEventRateLimitsUpdated(realtimeServerEventRateLimitsUpdated: RealtimeServerEventRateLimitsUpdated) {
            listeners.forEach {
                it.onServerEventRateLimitsUpdated(realtimeServerEventRateLimitsUpdated)
            }
        }

        override fun onServerEventResponseAudioDelta(realtimeServerEventResponseAudioDelta: RealtimeServerEventResponseAudioDelta) {
            listeners.forEach {
                it.onServerEventResponseAudioDelta(realtimeServerEventResponseAudioDelta)
            }
        }

        override fun onServerEventResponseAudioDone(realtimeServerEventResponseAudioDone: RealtimeServerEventResponseAudioDone) {
            listeners.forEach {
                it.onServerEventResponseAudioDone(realtimeServerEventResponseAudioDone)
            }
        }

        override fun onServerEventResponseAudioTranscriptDelta(realtimeServerEventResponseAudioTranscriptDelta: RealtimeServerEventResponseAudioTranscriptDelta) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseAudioTranscriptDelta($realtimeServerEventResponseAudioTranscriptDelta)")
            }
            val id = realtimeServerEventResponseAudioTranscriptDelta.itemId
            val delta =
                realtimeServerEventResponseAudioTranscriptDelta.delta // DO **NOT** TRIM!

            // Append this delta to any current in progress conversation,
            // or create a new conversation
            val index = _conversationItems.indexOfFirst { it.id == id }
            if (debugLogConversation) {
                Log.w(TAG, "onServerEventResponseAudioTranscriptDelta: id=$id, delta=$delta, index=$index")
            }
            if (index == -1) {
                val conversationItem = ConversationItem(
                    id = id,
                    speaker = ConversationSpeaker.Remote,
                    initialText = delta,
                )
                if (debugLogConversation) {
                    Log.w(TAG, "conversationItems.add($conversationItem)")
                }
                _conversationItems.add(conversationItem)
            } else {
                val conversationItem = _conversationItems[index]
                conversationItem.text += delta
                if (debugLogConversation) {
                    Log.w(TAG, "conversationItems.set($index, $conversationItem)")
                }
                _conversationItems[index] = conversationItem
            }
            listeners.forEach {
                it.onServerEventResponseAudioTranscriptDelta(realtimeServerEventResponseAudioTranscriptDelta)
            }
        }

        override fun onServerEventResponseAudioTranscriptDone(realtimeServerEventResponseAudioTranscriptDone: RealtimeServerEventResponseAudioTranscriptDone) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseAudioTranscriptDone($realtimeServerEventResponseAudioTranscriptDone)")
            }
            listeners.forEach {
                it.onServerEventResponseAudioTranscriptDone(realtimeServerEventResponseAudioTranscriptDone)
            }
        }

        override fun onServerEventResponseContentPartAdded(realtimeServerEventResponseContentPartAdded: RealtimeServerEventResponseContentPartAdded) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseContentPartAdded($realtimeServerEventResponseContentPartAdded)")
            }
            listeners.forEach {
                it.onServerEventResponseContentPartAdded(realtimeServerEventResponseContentPartAdded)
            }
        }

        override fun onServerEventResponseContentPartDone(realtimeServerEventResponseContentPartDone: RealtimeServerEventResponseContentPartDone) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseContentPartDone($realtimeServerEventResponseContentPartDone)")
            }
            listeners.forEach {
                it.onServerEventResponseContentPartDone(realtimeServerEventResponseContentPartDone)
            }
        }

        override fun onServerEventResponseCreated(realtimeServerEventResponseCreated: RealtimeServerEventResponseCreated) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseCreated($realtimeServerEventResponseCreated)")
            }
            listeners.forEach {
                it.onServerEventResponseCreated(realtimeServerEventResponseCreated)
            }
        }

        override fun onServerEventResponseDone(realtimeServerEventResponseDone: RealtimeServerEventResponseDone) {
            if (debugLogConversation) {
                Log.d(TAG, "onServerEventResponseDone($realtimeServerEventResponseDone)")
            }
            realtimeServerEventResponseDone.response.output?.forEach { outputConversationItem ->
                if (debugLogConversation) {
                    Log.w(TAG, "onServerEventResponseDone: outputConversationItem=$outputConversationItem")
                }
                val id = outputConversationItem.id ?: return@forEach
                val index = _conversationItems.indexOfFirst { it.id == id }
                if (index != -1) {
                    val conversationItem = _conversationItems[index]
                    if (debugLogConversation) {
                        Log.w(TAG, "onServerEventResponseDone: removing $conversationItem at index=$index")
                    }
                    _conversationItems.removeAt(index)
                    if (debugLogConversation) {
                        Log.w(TAG, "onServerEventResponseDone: adding $conversationItem at end")
                    }
                    _conversationItems.add(conversationItem)
                }
            }
            listeners.forEach {
                it.onServerEventResponseDone(realtimeServerEventResponseDone)
            }
        }

        override fun onServerEventResponseFunctionCallArgumentsDelta(
            realtimeServerEventResponseFunctionCallArgumentsDelta: RealtimeServerEventResponseFunctionCallArgumentsDelta
        ) {
            listeners.forEach {
                it.onServerEventResponseFunctionCallArgumentsDelta(realtimeServerEventResponseFunctionCallArgumentsDelta)
            }
        }

        override fun onServerEventResponseFunctionCallArgumentsDone(
            realtimeServerEventResponseFunctionCallArgumentsDone: RealtimeServerEventResponseFunctionCallArgumentsDone
        ) {
            listeners.forEach {
                it.onServerEventResponseFunctionCallArgumentsDone(realtimeServerEventResponseFunctionCallArgumentsDone)
            }
        }

        override fun onServerEventResponseOutputItemAdded(realtimeServerEventResponseOutputItemAdded: RealtimeServerEventResponseOutputItemAdded) {
            listeners.forEach {
                it.onServerEventResponseOutputItemAdded(realtimeServerEventResponseOutputItemAdded)
            }
        }

        override fun onServerEventResponseOutputItemDone(realtimeServerEventResponseOutputItemDone: RealtimeServerEventResponseOutputItemDone) {
            listeners.forEach {
                it.onServerEventResponseOutputItemDone(realtimeServerEventResponseOutputItemDone)
            }
        }

        override fun onServerEventResponseTextDelta(realtimeServerEventResponseTextDelta: RealtimeServerEventResponseTextDelta) {
            listeners.forEach {
                it.onServerEventResponseTextDelta(realtimeServerEventResponseTextDelta)
            }
        }

        override fun onServerEventResponseTextDone(realtimeServerEventResponseTextDone: RealtimeServerEventResponseTextDone) {
            listeners.forEach {
                it.onServerEventResponseTextDone(realtimeServerEventResponseTextDone)
            }
        }

        override fun onServerEventSessionCreated(realtimeServerEventSessionCreated: RealtimeServerEventSessionCreated) {
            listeners.forEach {
                it.onServerEventSessionCreated(realtimeServerEventSessionCreated)
            }
        }

        override fun onServerEventSessionUpdated(realtimeServerEventSessionUpdated: RealtimeServerEventSessionUpdated) {
            listeners.forEach {
                it.onServerEventSessionUpdated(realtimeServerEventSessionUpdated)
            }
        }

        override fun onServerEventSessionExpired(realtimeServerEventError: RealtimeServerEventError) {
            if (hasNoInUseActivities) {
                val message = realtimeServerEventError.error.message
                showNotificationSessionExpired(message)
            }

            listeners.forEach {
                it.onServerEventSessionExpired(realtimeServerEventError)
            }
        }
    }

    fun addListener(listener: RealtimeClientListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: RealtimeClientListener) {
        listeners.remove(listener)
    }

    //
    //endregion
    //

    //
    //region AudioSwitch
    //

    var audioDevices by mutableStateOf<List<AudioDevice>>(emptyList())
        private set

    var selectedAudioDevice by mutableStateOf<AudioDevice?>(null)
        private set

    fun selectAudioDevice(device: AudioDevice) {
        // AudioSwitch will also update our callback with the new "selectedDevice"
        // so we typically *don't* set 'selectedAudioDevice' manually here.
        audioSwitch.selectDevice(device)
    }

    private val audioSwitch = AudioSwitch(
        context = application,
        preferredDeviceList = listOf(
            AudioDevice.BluetoothHeadset::class.java,
            AudioDevice.WiredHeadset::class.java,
            AudioDevice.Speakerphone::class.java,
            AudioDevice.Earpiece::class.java
        )
    )

    fun audioSwitchStart() {
        audioSwitchStop()
        audioSwitch.start { audioDevices, selectedAudioDevice ->
            Log.d(TAG, "audioSwitchStart: audioDevices=$audioDevices, selectedAudioDevice=$selectedAudioDevice")
            this.audioDevices = audioDevices
            this.selectedAudioDevice = selectedAudioDevice
        }
    }

    private fun audioSwitchStop() {
        audioSwitch.stop()
    }

    //
    //endregion
    //
}
