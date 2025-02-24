package com.swooby.alfredai

import com.openai.models.RealtimeSessionCreateRequest
import com.openai.models.RealtimeSessionCreateRequestInputAudioTranscription
import com.openai.models.RealtimeSessionInputAudioTranscription
import com.twilio.audioswitch.AudioDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class ConnectionState {
    Connecting,
    Connected,
    Disconnecting,
    Disconnected,
}

interface ConnectionStateFlowInterface {
    val connectionState: StateFlow<ConnectionState>
    val isConnecting: StateFlow<Boolean>
    val isConnected: StateFlow<Boolean>
    val isConnectingOrConnected: StateFlow<Boolean>
    val isDisconnecting: StateFlow<Boolean>
    val isDisconnected: StateFlow<Boolean>
    val isDisconnectingOrDisconnected: StateFlow<Boolean>
}

interface MobileViewModelInterface : ConnectionStateFlowInterface {
    val requiredPermissions: Array<String>
    val hasAllRequiredPermissions: StateFlow<Boolean>
    fun updatePermissionsState()

    val autoConnect: StateFlow<Boolean>
    val apiKey: StateFlow<String>
    val model: StateFlow<RealtimeSessionCreateRequest.Model>
    val instructions: StateFlow<String>
    val voice: StateFlow<RealtimeSessionCreateRequest.Voice>
    val inputAudioTranscription: StateFlow<RealtimeSessionCreateRequestInputAudioTranscription?>
    val temperature: StateFlow<Float>
    val maxResponseOutputTokens: StateFlow<Int>
    val isConfigured: StateFlow<Boolean>
    fun updatePreferences(
        autoConnect: Boolean,
        apiKey: String,
        model: RealtimeSessionCreateRequest.Model,
        instructions: String,
        voice: RealtimeSessionCreateRequest.Voice,
        inputAudioTranscription: RealtimeSessionCreateRequestInputAudioTranscription?,
        temperature: Float,
        maxResponseOutputTokens: Int,
    )

    fun connect()
    fun disconnect(isManual: Boolean = false)
    fun reconnect()

    val pushToTalkState: StateFlow<SharedViewModel.PttState>
    fun pushToTalk(on: Boolean)

    fun sendText(message: String)

    val isCancelingResponse: StateFlow<Boolean>
    fun sendCancelResponse(): Boolean

    fun sendConversationItemTruncate(itemId: String, audioEndMs: Int): Boolean
    fun sendConversationItemTruncateConversationCurrentItem(): Boolean

    val conversationItems: List<MobileViewModel.ConversationItem>
    fun conversationItemsClear()

    val audioDevices: StateFlow<List<AudioDevice>>
    val selectedAudioDevice: StateFlow<AudioDevice?>
    fun selectAudioDevice(device: AudioDevice)
}

class ConnectionStateFlowProvider(
    scope: CoroutineScope,
    connectionState: MutableStateFlow<ConnectionState>
) : ConnectionStateFlowInterface {
    companion object {
        private val sharingStartedDefault = SharingStarted.WhileSubscribed(5_000)
    }
    override val connectionState = connectionState.asStateFlow()

    override val isConnecting = connectionState.map { state ->
        state == ConnectionState.Connecting
    }.stateIn(
        scope = scope,
        started = sharingStartedDefault,
        initialValue = connectionState.value == ConnectionState.Connecting
    )

    override val isConnected = connectionState.map { state ->
        state == ConnectionState.Connected
    }.stateIn(
        scope = scope,
        started = sharingStartedDefault,
        initialValue = connectionState.value == ConnectionState.Connected
    )

    override val isConnectingOrConnected = connectionState.map { state ->
        state == ConnectionState.Connecting || state == ConnectionState.Connected
    }.stateIn(
        scope = scope,
        started = sharingStartedDefault,
        initialValue = isConnecting.value || isConnected.value
    )

    override val isDisconnecting = connectionState.map { state ->
        state == ConnectionState.Disconnecting
    }.stateIn(
        scope = scope,
        started = sharingStartedDefault,
        initialValue = connectionState.value == ConnectionState.Disconnecting
    )
    override val isDisconnected = connectionState.map { state ->
        state == ConnectionState.Disconnected
    }.stateIn(
        scope = scope,
        started = sharingStartedDefault,
        initialValue = connectionState.value == ConnectionState.Disconnected
    )
    override val isDisconnectingOrDisconnected = connectionState.map { state ->
        state == ConnectionState.Disconnecting || state == ConnectionState.Disconnected
    }.stateIn(
        scope = scope,
        started = sharingStartedDefault,
        initialValue = isDisconnecting.value || isDisconnected.value
    )
}
