package com.swooby.alfredai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openai.models.RealtimeSessionCreateRequest
import com.openai.models.RealtimeSessionCreateRequestInputAudioTranscription
import com.openai.models.RealtimeSessionInputAudioTranscription
import com.twilio.audioswitch.AudioDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MobileViewModelPreview : ViewModel(), MobileViewModelInterface {
    override val requiredPermissions: Array<String>
        get() = arrayOf()
    override val hasAllRequiredPermissions: StateFlow<Boolean>
        get() = MutableStateFlow(true)
    override fun updatePermissionsState() = Unit

    override val autoConnect: StateFlow<Boolean>
        get() = MutableStateFlow(PushToTalkPreferences.autoConnectDefault)
    override val apiKey: StateFlow<String>
        get() = MutableStateFlow(PushToTalkPreferences.apiKeyDefault)
    override val model: StateFlow<RealtimeSessionCreateRequest.Model>
        get() = MutableStateFlow(PushToTalkPreferences.modelDefault)
    override val instructions: StateFlow<String>
        get() = MutableStateFlow(PushToTalkPreferences.instructionsDefault)
    override val voice: StateFlow<RealtimeSessionCreateRequest.Voice>
        get() = MutableStateFlow(PushToTalkPreferences.voiceDefault)
    override val inputAudioTranscription: StateFlow<RealtimeSessionCreateRequestInputAudioTranscription?>
        get() = MutableStateFlow(PushToTalkPreferences.inputAudioTranscriptionDefault)
    override val temperature: StateFlow<Float>
        get() = MutableStateFlow(PushToTalkPreferences.temperatureDefault)
    override val maxResponseOutputTokens: StateFlow<Int>
        get() = MutableStateFlow(PushToTalkPreferences.maxResponseOutputTokensDefault)
    override val isConfigured: StateFlow<Boolean>
        get() = MutableStateFlow(true)
    override fun updatePreferences(
        autoConnect: Boolean,
        apiKey: String,
        model: RealtimeSessionCreateRequest.Model,
        instructions: String,
        voice: RealtimeSessionCreateRequest.Voice,
        inputAudioTranscription: RealtimeSessionCreateRequestInputAudioTranscription?,
        temperature: Float,
        maxResponseOutputTokens: Int
    ) = Unit

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

    override fun connect() = Unit
    override fun disconnect(isManual: Boolean) = Unit
    override fun reconnect() = Unit

    override val pushToTalkState: StateFlow<SharedViewModel.PttState>
        get() = MutableStateFlow(SharedViewModel.PttState.Idle)
    override fun pushToTalk(on: Boolean) = Unit

    override fun sendText(message: String) = Unit

    override val isCancelingResponse: StateFlow<Boolean>
        get() = MutableStateFlow(false)
    override fun sendCancelResponse(): Boolean = false
    override fun sendConversationItemTruncate(itemId: String, audioEndMs: Int): Boolean = false
    override fun sendConversationItemTruncateConversationCurrentItem(): Boolean = false

    override val conversationItems: List<MobileViewModel.ConversationItem>
        get() = MobileViewModel.generateRandomConversationItems(20)
    override fun conversationItemsClear() = Unit

    override var audioDevices: StateFlow<List<AudioDevice>> = MutableStateFlow(listOf())
    override var selectedAudioDevice: StateFlow<AudioDevice?> = MutableStateFlow(null)
    override fun selectAudioDevice(device: AudioDevice) = Unit
}