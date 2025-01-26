package com.swooby.alfredai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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
import com.openai.models.RealtimeSessionCreateRequest
import com.openai.models.RealtimeSessionInputAudioTranscription
import com.openai.models.RealtimeSessionModel
import com.openai.models.RealtimeSessionVoice
import com.swooby.alfredai.openai.realtime.RealtimeClient
import com.swooby.alfredai.openai.realtime.RealtimeClient.RealtimeClientListener
import com.swooby.alfredai.openai.realtime.RealtimeClient.ServerEventOutputAudioBufferAudioStopped
import com.swooby.alfredai.PushToTalkPreferences.Companion.getMaxResponseOutputTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigDecimal

class PushToTalkViewModel(private val application: Application) :
    AndroidViewModel(application)
{
    companion object {
        const val DEBUG = true
    }

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

        _realtimeClient = RealtimeClient(
            application,
            prefs.apiKey,
            sessionConfig,
            debug = DEBUG
        )
        _realtimeClient?.addListener(listener)

        return true
    }

    //
    //region persistent RealtimeClientListener
    //

    private val listeners = mutableListOf<RealtimeClientListener>()
    private val listener = object : RealtimeClientListener {
        override fun onConnecting() {
            listeners.forEach {
                it.onConnecting()
            }
        }

        override fun onError(error: Exception) {
            listeners.forEach {
                it.onError(error)
            }
        }

        override fun onConnected() {
            listeners.forEach {
                it.onConnected()
            }
        }

        override fun onDisconnected() {
            listeners.forEach {
                it.onDisconnected()
            }
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
            listeners.forEach {
                it.onServerEventConversationCreated(realtimeServerEventConversationCreated)
            }
        }

        override fun onServerEventConversationItemCreated(realtimeServerEventConversationItemCreated: RealtimeServerEventConversationItemCreated) {
            listeners.forEach {
                it.onServerEventConversationItemCreated(realtimeServerEventConversationItemCreated)
            }
        }

        override fun onServerEventConversationItemDeleted(realtimeServerEventConversationItemDeleted: RealtimeServerEventConversationItemDeleted) {
            listeners.forEach {
                it.onServerEventConversationItemDeleted(realtimeServerEventConversationItemDeleted)
            }
        }

        override fun onServerEventConversationItemInputAudioTranscriptionCompleted(
            realtimeServerEventConversationItemInputAudioTranscriptionCompleted: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted
        ) {
            listeners.forEach {
                it.onServerEventConversationItemInputAudioTranscriptionCompleted(realtimeServerEventConversationItemInputAudioTranscriptionCompleted)
            }
        }

        override fun onServerEventConversationItemInputAudioTranscriptionFailed(
            realtimeServerEventConversationItemInputAudioTranscriptionFailed: RealtimeServerEventConversationItemInputAudioTranscriptionFailed
        ) {
            listeners.forEach {
                it.onServerEventConversationItemInputAudioTranscriptionFailed(realtimeServerEventConversationItemInputAudioTranscriptionFailed)
            }
        }

        override fun onServerEventConversationItemTruncated(
            realtimeServerEventConversationItemTruncated: RealtimeServerEventConversationItemTruncated
        ) {
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

        override fun onServerEventInputAudioBufferCommitted(
            realtimeServerEventInputAudioBufferCommitted: RealtimeServerEventInputAudioBufferCommitted
        ) {
            listeners.forEach {
                it.onServerEventInputAudioBufferCommitted(realtimeServerEventInputAudioBufferCommitted)
            }
        }

        override fun onServerEventInputAudioBufferSpeechStarted(
            realtimeServerEventInputAudioBufferSpeechStarted: RealtimeServerEventInputAudioBufferSpeechStarted
        ) {
            listeners.forEach {
                it.onServerEventInputAudioBufferSpeechStarted(realtimeServerEventInputAudioBufferSpeechStarted)
            }
        }

        override fun onServerEventInputAudioBufferSpeechStopped(
            realtimeServerEventInputAudioBufferSpeechStopped: RealtimeServerEventInputAudioBufferSpeechStopped
        ) {
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

        override fun onServerEventResponseAudioTranscriptDelta(
            realtimeServerEventResponseAudioTranscriptDelta: RealtimeServerEventResponseAudioTranscriptDelta
        ) {
            listeners.forEach {
                it.onServerEventResponseAudioTranscriptDelta(realtimeServerEventResponseAudioTranscriptDelta)
            }
        }

        override fun onServerEventResponseAudioTranscriptDone(
            realtimeServerEventResponseAudioTranscriptDone: RealtimeServerEventResponseAudioTranscriptDone
        ) {
            listeners.forEach {
                it.onServerEventResponseAudioTranscriptDone(realtimeServerEventResponseAudioTranscriptDone)
            }
        }

        override fun onServerEventResponseContentPartAdded(
            realtimeServerEventResponseContentPartAdded: RealtimeServerEventResponseContentPartAdded
        ) {
            listeners.forEach {
                it.onServerEventResponseContentPartAdded(realtimeServerEventResponseContentPartAdded)
            }
        }

        override fun onServerEventResponseContentPartDone(realtimeServerEventResponseContentPartDone: RealtimeServerEventResponseContentPartDone) {
            listeners.forEach {
                it.onServerEventResponseContentPartDone(realtimeServerEventResponseContentPartDone)
            }
        }

        override fun onServerEventResponseCreated(realtimeServerEventResponseCreated: RealtimeServerEventResponseCreated) {
            listeners.forEach {
                it.onServerEventResponseCreated(realtimeServerEventResponseCreated)
            }
        }

        override fun onServerEventResponseDone(realtimeServerEventResponseDone: RealtimeServerEventResponseDone) {
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
}
