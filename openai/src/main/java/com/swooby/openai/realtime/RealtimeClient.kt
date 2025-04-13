package com.swooby.openai.realtime

import android.content.Context
import android.os.SystemClock
import androidx.annotation.WorkerThread
import com.openai.apis.RealtimeApi
import com.openai.infrastructure.ApiClient
import com.openai.infrastructure.Serializer
import com.openai.models.RealtimeClientEventConversationItemCreate
import com.openai.models.RealtimeClientEventConversationItemTruncate
import com.openai.models.RealtimeClientEventInputAudioBufferClear
import com.openai.models.RealtimeClientEventInputAudioBufferCommit
import com.openai.models.RealtimeClientEventResponseCancel
import com.openai.models.RealtimeClientEventResponseCreate
import com.openai.models.RealtimeClientEventSessionUpdate
import com.openai.models.RealtimeConversationItem
import com.openai.models.RealtimeConversationItemContentInner
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
import com.swooby.Utils.extractValue
import com.swooby.Utils.quote
import com.swooby.openai.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/**
 * https://platform.openai.com/docs/guides/realtime
 * https://platform.openai.com/docs/api-reference/realtime
 * https://platform.openai.com/docs/models#gpt-4o-realtime
 * https://community.openai.com/tags/c/api/7/realtime
 *
 * https://github.com/GetStream/webrtc-android/blob/main/stream-webrtc-android/src/main/java/org/webrtc/
 */
class RealtimeClient(
    override val transportType: TransportType,
    applicationContext: Context,
    dangerousApiKey: String,
    private var sessionConfig: RealtimeSessionCreateRequest,
    httpClient: OkHttpClient = ApiClient.defaultClient,
    private val debug: Boolean = false)
    : RealtimeTransport, RealtimeProtocol
{
    companion object {
        private val log = RealtimeLog(RealtimeClient::class)

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        private val debugDataChannelText = BuildConfig.DEBUG && false

        val httpLoggingClient = OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()

        private const val AUDIO_DRAIN_MS = 200L
    }

    override fun addListener(listener: RealtimeTransportListener) {
        transport.addListener(listener)
    }

    override fun removeListener(listener: RealtimeTransportListener) {
        transport.removeListener(listener)
    }

    private val realtime = RealtimeApi(
        client = httpClient,
    )

    private val transport = RealtimeTransportBase.create(
        transportType = transportType,
        applicationContext = applicationContext,
        dangerousApiKey = dangerousApiKey,
        sessionConfig = sessionConfig,
        httpClient = httpClient,
        realtime = realtime,
        debug = debug,
    )

    init {
        transport.addListener(object : RealtimeTransportListener {
            override fun onConnecting() {
                //...?
            }

            override fun onConnected() {
                this@RealtimeClient.onConnected()
            }

            override fun onDisconnected() {
                //...?
            }

            override fun onError(error: Exception) {
                //...?
            }

            override fun onDataChannelOpened() {
                this@RealtimeClient.onDataChannelOpened()
            }

            override fun onBinaryMessageReceived(data: ByteArray): Boolean {
                return this@RealtimeClient.onBinaryMessageReceived(data)
            }

            override fun onTextMessageReceived(message: String): Boolean {
                return this@RealtimeClient.onTextMessageReceived(message)
            }
        })
    }

    override val isConnectingOrConnected: Boolean
        get() = transport.isConnectingOrConnected

    override val isConnected: Boolean
        get() = transport.isConnected

    var isCancelingResponse: Boolean = false
        private set

    private var connectStartMillis: Long = 0L

    @WorkerThread
    override fun connect(): String? {
        connectStartMillis = SystemClock.uptimeMillis()
        return transport.connect()
    }

    override fun disconnect(error: Exception?) {
        isCancelingResponse = false
        transport.disconnect(error)
    }

    private fun onConnected() {
        log.i("onConnected(); starting off muting mic and speaker")
        setLocalAudioTrackMicrophoneEnabled(false)
        setLocalAudioTrackSpeakerEnabled(false)
    }

    override fun setLocalAudioTrackSpeakerEnabled(enabled: Boolean) {
        transport.setLocalAudioTrackSpeakerEnabled(enabled)
    }

    override fun setLocalAudioTrackMicrophoneEnabled(enabled: Boolean) {
        transport.setLocalAudioTrackMicrophoneEnabled(enabled)
    }

    override fun dataSendText(message: String): Boolean {
        return transport.dataSendText(message)
    }

    override fun dataSendBinary(data: ByteArray): Boolean {
        return transport.dataSendBinary(data)
    }

    private fun onDataChannelOpened() {
        // Whether it is intentional or a bug, some settings like...
        // * `input_audio_transcription`
        // * `turn_detection`
        // ...don't take affect when creating a session:
        // https://platform.openai.com/docs/api-reference/realtime-sessions/create
        // https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-input_audio_transcription
        // Proof is to enable okhttp logging and see a non-default value getting sent
        // in the Request but the default value coming back in the Response. :/
        // Forum post explaining:
        // https://community.openai.com/t/issues-with-transcription-in-realtime-model-using-webrtc/1068762/4
        //
        // So, this code re-applies the sessionConfig after the session and data channel are open.
        // There is a mention that specifying `model` during a session.update causes issues.
        //
        // https://platform.openai.com/docs/api-reference/realtime-client-events/session/update
        //
        dataSendSessionUpdate(sessionConfig)
    }

    override fun dataSendSessionUpdate(sessionConfig: RealtimeSessionCreateRequest): Boolean {
        log.d("dataSendSessionUpdate($sessionConfig)")
        this.sessionConfig = sessionConfig
        return dataSend(RealtimeClientEventSessionUpdate(
            session = sessionConfig,
            eventId = RealtimeUtils.generateId()
        ))
    }

    // https://platform.openai.com/docs/guides/realtime-model-capabilities#voice-activity-detection-vad
    // "When VAD is disabled, the client will have to manually emit some additional client events to trigger audio responses:
    //
    //  * Manually send `input_audio_buffer.commit`, which will create a new user input item for the conversation.
    //  * Manually send `response.create` to trigger an audio response from the model.
    //  * Send `input_audio_buffer.clear` before beginning a new user input.
    // "

    override fun dataSendInputAudioBufferClear(): Boolean {
        log.d("dataSendInputAudioBufferClear()")
        return dataSend(RealtimeClientEventInputAudioBufferClear(
            eventId = RealtimeUtils.generateId(),
        ))
    }

    override fun dataSendInputAudioBufferCommit(): Boolean {
        log.d("dataSendInputAudioBufferCommit()")
        return dataSend(RealtimeClientEventInputAudioBufferCommit(
            eventId = RealtimeUtils.generateId(),
        ))
    }

    override fun dataSendResponseCreate(): Boolean {
        log.d("dataSendResponseCreate()")
        return dataSend(RealtimeClientEventResponseCreate(
            eventId = RealtimeUtils.generateId(),
        ))
    }

    override fun dataSendResponseCancel(responseId: String?): Boolean {
        log.d("dataSendResponseCancel(responseId=${quote(responseId)})")
        val sending = dataSend(RealtimeClientEventResponseCancel(
            eventId = RealtimeUtils.generateId(),
            responseId = responseId,
        ))
        if (sending) {
            setLocalAudioTrackSpeakerEnabled(false)
            isCancelingResponse = true
        }
        return sending
    }
    /**
     * https://platform.openai.com/docs/api-reference/realtime-client-events/conversation/item/truncate
     * conversation.item.truncate
     * "Send this event to truncate a previous assistant message’s audio."
     * ...
     * "If successful, the server will respond with a conversation.item.truncated event."
     * To see it in use:
     * https://youtu.be/mM8KhTxwPgs?t=965
     */
    override fun dataSendConversationItemTruncate(itemId: String, audioEndMs: Int): Boolean {
        log.d("dataSendConversationItemTruncate(itemId=${quote(itemId)}, audioEndMs=$audioEndMs)")
        val sending = dataSend(RealtimeClientEventConversationItemTruncate(
            itemId = itemId,
            contentIndex = 0,
            audioEndMs = audioEndMs,
            eventId = RealtimeUtils.generateId(),
        ))
        return sending
    }

    // TODO:(pv implement audio inputs and outputs
    // https://platform.openai.com/docs/guides/realtime-model-capabilities#audio-inputs-and-outputs
    // https://platform.openai.com/docs/guides/realtime-model-capabilities#handling-audio-with-websockets

    /**
     * https://platform.openai.com/docs/guides/realtime-model-capabilities#text-inputs-and-outputs
     */
    override fun dataSendConversationItemCreateMessageInputText(
        text: String,
        role: RealtimeConversationItem.Role,
    ): Boolean {
        return dataSend(
            RealtimeClientEventConversationItemCreate(
                eventId = RealtimeUtils.generateId(),
                item = RealtimeConversationItem(
                    type = RealtimeConversationItem.Type.message,
                    role = role,
                    content = listOf(
                        RealtimeConversationItemContentInner(
                            type = RealtimeConversationItemContentInner.Type.input_text,
                            text = text,
                        )
                    ),
                ),
            )
        )
    }

    override fun dataSendConversationItemCreateFunctionCallOutput(
        callId: String,
        output: String,
    ): Boolean {
        return dataSend(
            RealtimeClientEventConversationItemCreate(
                eventId = RealtimeUtils.generateId(),
                item = RealtimeConversationItem(
                    type = RealtimeConversationItem.Type.function_call_output,
                    callId = callId,
                    output = output,
                ),
            )
        )
    }

    //
    //region Event Listener
    //

    private val protocolListeners = mutableListOf<RealtimeProtocolListener>()

    @Suppress("MemberVisibilityCanBePrivate")
    fun addListener(listener: RealtimeProtocolListener) {
        protocolListeners.add(listener)
    }

    @Suppress("unused")
    fun removeListener(listener: RealtimeProtocolListener) {
        protocolListeners.remove(listener)
    }

    interface RealtimeClientListener : RealtimeTransportListener, RealtimeProtocolListener

    fun addListener(listener: RealtimeClientListener) {
        addListener(listener as RealtimeTransportListener)
        addListener(listener as RealtimeProtocolListener)
    }

    private fun onBinaryMessageReceived(data: ByteArray):Boolean = false

    private fun onTextMessageReceived(message: String): Boolean {
        val type = extractValue("type", message)
        if (debug || debugDataChannelText) {
            log.d("onTextMessageReceived: type=${quote(type)}")
        }
        // TODO: Consider using reflection to auto-populate the equivalent of the below code...
        // @formatter:off
        try {
            when (type) {
                RealtimeServerEventConversationCreated.Type.conversationPeriodCreated.value -> {
                    Serializer.deserialize<RealtimeServerEventConversationCreated>(message)?.also {
                        notifyServerEventConversationCreated(it)
                    }
                }

                RealtimeServerEventConversationItemCreated.Type.conversationPeriodItemPeriodCreated.value -> {
                    Serializer.deserialize<RealtimeServerEventConversationItemCreated>(message)?.also {
                        notifyServerEventConversationItemCreated(it)
                    }
                }

                RealtimeServerEventConversationItemDeleted.Type.conversationPeriodItemPeriodDeleted.value -> {
                    Serializer.deserialize<RealtimeServerEventConversationItemDeleted>(message)?.also {
                        notifyServerEventConversationItemDeleted(it)
                    }
                }

                RealtimeServerEventConversationItemInputAudioTranscriptionCompleted.Type.conversationPeriodItemPeriodInput_audio_transcriptionPeriodCompleted.value -> {
                    Serializer.deserialize<RealtimeServerEventConversationItemInputAudioTranscriptionCompleted>(message)?.also {
                        notifyServerEventConversationItemInputAudioTranscriptionCompleted(it)
                    }
                }

                RealtimeServerEventConversationItemInputAudioTranscriptionFailed.Type.conversationPeriodItemPeriodInput_audio_transcriptionPeriodFailed.value -> {
                    Serializer.deserialize<RealtimeServerEventConversationItemInputAudioTranscriptionFailed>(message)?.also {
                        notifyServerEventConversationItemInputAudioTranscriptionFailed(it)
                    }
                }

                RealtimeServerEventConversationItemTruncated.Type.conversationPeriodItemPeriodTruncated.value -> {
                    Serializer.deserialize<RealtimeServerEventConversationItemTruncated>(message)?.also {
                        notifyServerEventConversationItemTruncated(it)
                    }
                }

                RealtimeServerEventError.Type.error.value -> {
                    Serializer.deserialize<RealtimeServerEventError>(message)?.also {
                        notifyServerEventError(it)
                    }
                }

                RealtimeServerEventInputAudioBufferCleared.Type.input_audio_bufferPeriodCleared.value -> {
                    Serializer.deserialize<RealtimeServerEventInputAudioBufferCleared>(message)?.also {
                        notifyServerEventInputAudioBufferCleared(it)
                    }
                }

                RealtimeServerEventInputAudioBufferCommitted.Type.input_audio_bufferPeriodCommitted.value -> {
                    Serializer.deserialize<RealtimeServerEventInputAudioBufferCommitted>(message)?.also {
                        notifyServerEventInputAudioBufferCommitted(it)
                    }
                }

                RealtimeServerEventInputAudioBufferSpeechStarted.Type.input_audio_bufferPeriodSpeech_started.value -> {
                    Serializer.deserialize<RealtimeServerEventInputAudioBufferSpeechStarted>(message)?.also {
                        notifyServerEventInputAudioBufferSpeechStarted(it)
                    }
                }

                RealtimeServerEventInputAudioBufferSpeechStopped.Type.input_audio_bufferPeriodSpeech_stopped.value -> {
                    Serializer.deserialize<RealtimeServerEventInputAudioBufferSpeechStopped>(message)?.also {
                        notifyServerEventInputAudioBufferSpeechStopped(it)
                    }
                }

                RealtimeServerEventRateLimitsUpdated.Type.rate_limitsPeriodUpdated.value -> {
                    Serializer.deserialize<RealtimeServerEventRateLimitsUpdated>(message)?.also {
                        notifyServerEventRateLimitsUpdated(it)
                    }
                }

                RealtimeServerEventResponseAudioDelta.Type.responsePeriodAudioPeriodDelta.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseAudioDelta>(message)?.also {
                        notifyServerEventResponseAudioDelta(it)
                    }
                }

                RealtimeServerEventResponseAudioDone.Type.responsePeriodAudioPeriodDone.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseAudioDone>(message)?.also {
                        notifyServerEventResponseAudioDone(it)
                    }
                }

                RealtimeServerEventResponseAudioTranscriptDelta.Type.responsePeriodAudio_transcriptPeriodDelta.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseAudioTranscriptDelta>(message)?.also {
                        notifyServerEventResponseAudioTranscriptDelta(it)
                    }
                }

                RealtimeServerEventResponseAudioTranscriptDone.Type.responsePeriodAudio_transcriptPeriodDone.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseAudioTranscriptDone>(message)?.also {
                        notifyServerEventResponseAudioTranscriptDone(it)
                    }
                }

                RealtimeServerEventResponseContentPartAdded.Type.responsePeriodContent_partPeriodAdded.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseContentPartAdded>(message)?.also {
                        notifyServerEventResponseContentPartAdded(it)
                    }
                }

                RealtimeServerEventResponseContentPartDone.Type.responsePeriodContent_partPeriodDone.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseContentPartDone>(message)?.also {
                        notifyServerEventResponseContentPartDone(it)
                    }
                }

                RealtimeServerEventResponseCreated.Type.responsePeriodCreated.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseCreated>(message)?.also {
                        notifyServerEventResponseCreated(it)
                    }
                }

                RealtimeServerEventResponseDone.Type.responsePeriodDone.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseDone>(message)?.also {
                        notifyServerEventResponseDone(it)
                    }
                }

                RealtimeServerEventResponseFunctionCallArgumentsDelta.Type.responsePeriodFunction_call_argumentsPeriodDelta.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseFunctionCallArgumentsDelta>(message)?.also {
                        notifyServerEventResponseFunctionCallArgumentsDelta(it)
                    }
                }

                RealtimeServerEventResponseFunctionCallArgumentsDone.Type.responsePeriodFunction_call_argumentsPeriodDone.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseFunctionCallArgumentsDone>(message)?.also {
                        notifyServerEventResponseFunctionCallArgumentsDone(it)
                    }
                }

                RealtimeServerEventResponseOutputItemAdded.Type.responsePeriodOutput_itemPeriodAdded.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseOutputItemAdded>(message)?.also {
                        notifyServerEventResponseOutputItemAdded(it)
                    }
                }

                RealtimeServerEventResponseOutputItemDone.Type.responsePeriodOutput_itemPeriodDone.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseOutputItemDone>(message)?.also {
                        notifyServerEventResponseOutputItemDone(it)
                    }
                }

                RealtimeServerEventResponseTextDelta.Type.responsePeriodTextPeriodDelta.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseTextDelta>(message)?.also {
                        notifyServerEventResponseTextDelta(it)
                    }
                }

                RealtimeServerEventResponseTextDone.Type.responsePeriodTextPeriodDone.value -> {
                    Serializer.deserialize<RealtimeServerEventResponseTextDone>(message)?.also {
                        notifyServerEventResponseTextDone(it)
                    }
                }

                RealtimeServerEventSessionCreated.Type.sessionPeriodCreated.value -> {
                    Serializer.deserialize<RealtimeServerEventSessionCreated>(message)?.also {
                        notifyServerEventSessionCreated(it)
                    }
                }

                RealtimeServerEventSessionUpdated.Type.sessionPeriodUpdated.value -> {
                    Serializer.deserialize<RealtimeServerEventSessionUpdated>(message)?.also {
                        notifyServerEventSessionUpdated(it)
                    }
                }

                // https://platform.openai.com/docs/api-reference/realtime-server-events/input_audio_buffer
                "output_audio_buffer.audio_started", // circa ??
                "output_audio_buffer.started", // circa 2025/02/05
                     -> {
                    log.w("onDataChannelText: undocumented `${type}`")
                    Serializer.deserialize<ServerEventOutputAudioBufferStarted>(message)?.also {
                        notifyServerEventOutputAudioBufferStarted(it)
                    }
                }

                // https://platform.openai.com/docs/api-reference/realtime-server-events/input_audio_buffer
                "output_audio_buffer.audio_stopped", // circa ??
                "output_audio_buffer.stopped", // circa 2025/02/05
                    -> {
                    log.w("onDataChannelText: undocumented `${type}`")
                    Serializer.deserialize<ServerEventOutputAudioBufferStopped>(message)?.also {
                        notifyServerEventOutputAudioBufferStopped(it)
                    }
                }

                else ->
                    log.e("onDataChannelText: unknown/undocumented type=${quote(type)}; message(${message.length} chars TEXT)=${quote(message)}")
            }
        } catch (e: Exception) {
            log.e("onDataChannelText: exception=$e")
            throw e
        }
        // @formatter:on
        return false
    }

    private fun notifyServerEventConversationCreated(message: RealtimeServerEventConversationCreated) {
        protocolListeners.forEach {
            it.onServerEventConversationCreated(message)
        }
    }

    private fun notifyServerEventConversationItemCreated(message: RealtimeServerEventConversationItemCreated) {
        protocolListeners.forEach {
            it.onServerEventConversationItemCreated(message)
        }
    }

    private fun notifyServerEventConversationItemDeleted(message: RealtimeServerEventConversationItemDeleted) {
        protocolListeners.forEach {
            it.onServerEventConversationItemDeleted(message)
        }
    }

    private fun notifyServerEventConversationItemInputAudioTranscriptionCompleted(message: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted) {
        protocolListeners.forEach {
            it.onServerEventConversationItemInputAudioTranscriptionCompleted(message)
        }
    }

    private fun notifyServerEventConversationItemInputAudioTranscriptionFailed(message: RealtimeServerEventConversationItemInputAudioTranscriptionFailed) {
        protocolListeners.forEach {
            it.onServerEventConversationItemInputAudioTranscriptionFailed(message)
        }
    }

    private fun notifyServerEventConversationItemTruncated(message: RealtimeServerEventConversationItemTruncated) {
        log.d("notifyServerEventConversationItemTruncated($message)")
        protocolListeners.forEach {
            it.onServerEventConversationItemTruncated(message)
        }
    }

    private fun notifyServerEventError(message: RealtimeServerEventError) {
        val error = message.error
        if (error.code == "session_expired") {
            /*
            log output:
            onServerEventError(
                RealtimeServerEventError(
                    eventId=event_Au2On9AZVDzkdjCMStXVm,
                    type=error,
                    error=RealtimeServerEventErrorError(
                        type=invalid_request_error,
                        message=Your session hit the maximum duration of 30 minutes.,
                        code=session_expired,
                        param=null,
                        eventId=null
                    )
                )
            )
            */
            protocolListeners.forEach {
                it.onServerEventSessionExpired(message)
            }
        } else {
            protocolListeners.forEach {
                it.onServerEventError(message)
            }
        }
    }

    private fun notifyServerEventInputAudioBufferCleared(message: RealtimeServerEventInputAudioBufferCleared) {
        protocolListeners.forEach {
            it.onServerEventInputAudioBufferCleared(message)
        }
    }

    private fun notifyServerEventInputAudioBufferCommitted(message: RealtimeServerEventInputAudioBufferCommitted) {
        protocolListeners.forEach {
            it.onServerEventInputAudioBufferCommitted(message)
        }
    }

    private fun notifyServerEventInputAudioBufferSpeechStarted(message: RealtimeServerEventInputAudioBufferSpeechStarted) {
        protocolListeners.forEach {
            it.onServerEventInputAudioBufferSpeechStarted(message)
        }
    }

    private fun notifyServerEventInputAudioBufferSpeechStopped(message: RealtimeServerEventInputAudioBufferSpeechStopped) {
        protocolListeners.forEach {
            it.onServerEventInputAudioBufferSpeechStopped(message)
        }
    }

    /**
     * Example:
     * ```
     * {
     *   "type":"output_audio_buffer.started",
     *   "event_id":"event_51bb3e4b2b9a45b5",
     *   "response_id":"resp_AvyUM8tYkYjvWaXhkqcBJ"
     * }
     * ```
     */
    @Suppress("PropertyName")
    data class ServerEventOutputAudioBufferStarted(
        /**
         * The event type, must be "output_audio_buffer.started".
         */
        val type: String,
        /**
         * The unique ID of the server event.
         */
        val event_id: String,
        /**
         * The ID of the response.
         */
        val response_id: String,
    )

    private fun notifyServerEventOutputAudioBufferStarted(message: ServerEventOutputAudioBufferStarted) {
        log.d("notifyServerEventOutputAudioBufferStarted($message)")
        protocolListeners.forEach {
            it.onServerEventOutputAudioBufferStarted(message)
        }
    }

    /**
     * Example:
     * ```
     * {
     *   "type":"output_audio_buffer.stopped",
     *   "event_id":"event_e69be18ad4f34b01",
     *   "response_id":"resp_Asg4GBgYXDJXSLfXu6cWO"
     * }
     * ```
     */
    @Suppress("PropertyName")
    data class ServerEventOutputAudioBufferStopped(
        /**
         * The event type, must be "output_audio_buffer.stopped".
         */
        val type: String,
        /**
         * The unique ID of the server event.
         */
        val event_id: String,
        /**
         * The ID of the response.
         */
        val response_id: String,
    )

    private fun notifyServerEventOutputAudioBufferStopped(message: ServerEventOutputAudioBufferStopped) {
        log.d("notifyServerEventOutputAudioBufferStopped($message)")
        if (isCancelingResponse) {
            isCancelingResponse = false
            CoroutineScope(Dispatchers.IO).launch {
                delay(AUDIO_DRAIN_MS) // delay a bit to allow any already buffered audio to finish playing before unmuting
                setLocalAudioTrackSpeakerEnabled(true)
                protocolListeners.forEach {
                    it.onServerEventOutputAudioBufferStopped(message)
                }
            }
        } else {
            protocolListeners.forEach {
                it.onServerEventOutputAudioBufferStopped(message)
            }
        }
    }

    private fun notifyServerEventRateLimitsUpdated(message: RealtimeServerEventRateLimitsUpdated) {
        protocolListeners.forEach {
            it.onServerEventRateLimitsUpdated(message)
        }
    }

    private fun notifyServerEventResponseAudioDelta(message: RealtimeServerEventResponseAudioDelta) {
        protocolListeners.forEach {
            it.onServerEventResponseAudioDelta(message)
        }
    }

    private fun notifyServerEventResponseAudioDone(message: RealtimeServerEventResponseAudioDone) {
        log.d("notifyServerEventResponseAudioDone($message)")
        protocolListeners.forEach {
            it.onServerEventResponseAudioDone(message)
        }
    }

    private fun notifyServerEventResponseAudioTranscriptDelta(message: RealtimeServerEventResponseAudioTranscriptDelta) {
        protocolListeners.forEach {
            it.onServerEventResponseAudioTranscriptDelta(message)
        }
    }

    private fun notifyServerEventResponseAudioTranscriptDone(message: RealtimeServerEventResponseAudioTranscriptDone) {
        protocolListeners.forEach {
            it.onServerEventResponseAudioTranscriptDone(message)
        }
    }

    private fun notifyServerEventResponseContentPartAdded(message: RealtimeServerEventResponseContentPartAdded) {
        protocolListeners.forEach {
            it.onServerEventResponseContentPartAdded(message)
        }
    }

    private fun notifyServerEventResponseContentPartDone(message: RealtimeServerEventResponseContentPartDone) {
        protocolListeners.forEach {
            it.onServerEventResponseContentPartDone(message)
        }
    }

    private fun notifyServerEventResponseCreated(message: RealtimeServerEventResponseCreated) {
        protocolListeners.forEach {
            it.onServerEventResponseCreated(message)
        }
    }

    private fun notifyServerEventResponseDone(message: RealtimeServerEventResponseDone) {
        protocolListeners.forEach {
            it.onServerEventResponseDone(message)
        }
    }

    private fun notifyServerEventResponseFunctionCallArgumentsDelta(message: RealtimeServerEventResponseFunctionCallArgumentsDelta) {
        protocolListeners.forEach {
            it.onServerEventResponseFunctionCallArgumentsDelta(message)
        }
    }

    private fun notifyServerEventResponseFunctionCallArgumentsDone(message: RealtimeServerEventResponseFunctionCallArgumentsDone) {
        protocolListeners.forEach {
            it.onServerEventResponseFunctionCallArgumentsDone(message)
        }
    }

    private fun notifyServerEventResponseOutputItemAdded(message: RealtimeServerEventResponseOutputItemAdded) {
        protocolListeners.forEach {
            it.onServerEventResponseOutputItemAdded(message)
        }
    }

    private fun notifyServerEventResponseOutputItemDone(message: RealtimeServerEventResponseOutputItemDone) {
        protocolListeners.forEach {
            it.onServerEventResponseOutputItemDone(message)
        }
    }

    private fun notifyServerEventResponseTextDelta(message: RealtimeServerEventResponseTextDelta) {
        protocolListeners.forEach {
            it.onServerEventResponseTextDelta(message)
        }
    }

    private fun notifyServerEventResponseTextDone(message: RealtimeServerEventResponseTextDone) {
        protocolListeners.forEach {
            it.onServerEventResponseTextDone(message)
        }
    }

    private fun notifyServerEventSessionCreated(message: RealtimeServerEventSessionCreated) {
        val elapsedMillis = SystemClock.uptimeMillis() - connectStartMillis
        log.i("notifyServerEventSessionCreated: CONNECTED! TIMING_CONNECT Connected in elapsedMillis=${elapsedMillis}ms")
        transport.isConnected = true

        protocolListeners.forEach {
            it.onServerEventSessionCreated(message)
        }
    }

    private fun notifyServerEventSessionUpdated(message: RealtimeServerEventSessionUpdated) {
        protocolListeners.forEach {
            it.onServerEventSessionUpdated(message)
        }
    }

    //
    //endregion
    //
}
