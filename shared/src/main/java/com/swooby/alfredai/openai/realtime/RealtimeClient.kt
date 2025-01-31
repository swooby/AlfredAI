package com.swooby.alfredai.openai.realtime

import android.content.Context
import android.os.SystemClock
import androidx.annotation.WorkerThread
import com.openai.apis.RealtimeApi
import com.openai.infrastructure.ApiClient
import com.openai.infrastructure.ClientException
import com.openai.infrastructure.MultiValueMap
import com.openai.infrastructure.RequestConfig
import com.openai.infrastructure.RequestMethod
import com.openai.infrastructure.Serializer
import com.openai.infrastructure.Success
import com.openai.models.RealtimeClientEventConversationItemCreate
import com.openai.models.RealtimeClientEventConversationItemTruncate
import com.openai.models.RealtimeClientEventInputAudioBufferClear
import com.openai.models.RealtimeClientEventInputAudioBufferCommit
import com.openai.models.RealtimeClientEventResponseCancel
import com.openai.models.RealtimeClientEventResponseCreate
import com.openai.models.RealtimeClientEventSessionUpdate
import com.openai.models.RealtimeConversationItem
import com.openai.models.RealtimeConversationItemContent
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
import com.openai.models.RealtimeSessionCreateResponse
import com.openai.models.RealtimeSessionModel
import com.swooby.alfredai.Utils.extractValue
import com.swooby.alfredai.Utils.quote
import com.swooby.alfredai.Utils.redact
import com.swooby.alfredai.common.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.Logging
import org.webrtc.Logging.Severity
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.net.UnknownHostException
import java.nio.ByteBuffer

/**
 * https://platform.openai.com/docs/guides/realtime
 * https://platform.openai.com/docs/api-reference/realtime
 * https://platform.openai.com/docs/models#gpt-4o-realtime
 * https://community.openai.com/tags/c/api/7/realtime
 *
 * https://github.com/GetStream/webrtc-android/blob/main/stream-webrtc-android/src/main/java/org/webrtc/
 */
class RealtimeClient(private val applicationContext: Context,
                     private val dangerousApiKey: String,
                     private var sessionConfig: RealtimeSessionCreateRequest,
                     httpClient: OkHttpClient = ApiClient.defaultClient,
                     private val debug: Boolean = false) {
    companion object {
        private val log = RealtimeLog(RealtimeClient::class)

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        private val debugDataChannelBinary = BuildConfig.DEBUG && false

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        private val debugDataChannelText = BuildConfig.DEBUG && false

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        private val debugInduceMysteriousUnknownHostException = BuildConfig.DEBUG && false

        private const val MYSTERIOUS_UNKNOWN_HOST_EXCEPTION_MESSAGE = "Unable to resolve host \"api.openai.com\": No address associated with hostname"

        val httpLoggingClient = OkHttpClient.Builder()
                .addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                })
                .build()
    }

    private val realtime = RealtimeApi(client = httpClient)

    private var peerConnection: PeerConnection? = null
    private var localAudioTrackMicrophone: AudioTrack? = null
    private var localAudioTrackMicrophoneSender: RtpSender? = null
    private val remoteAudioTracks = mutableListOf<AudioTrack>()
    var isCancelingResponse: Boolean = false
        private set
    private var dataChannel: DataChannel? = null
    private var dataChannelOpened = false

    private var _isConnectingOrConnected: Boolean = false
    var isConnectingOrConnected: Boolean
        get() = _isConnectingOrConnected
        private set(value) {
            if (value != _isConnectingOrConnected) {
                _isConnectingOrConnected = value
                if (value) {
                    notifyConnecting()
                } else {
                    notifyDisconnected()
                }
            }
        }

    private var _isConnected: Boolean = false
    var isConnected: Boolean
        get() = _isConnected
        private set(value) {
            if (value != _isConnected) {
                _isConnected = value
                if (value) {
                    notifyConnected()
                } else {
                    notifyDisconnected()
                }
            }
        }

    val isConnecting: Boolean
        get() = isConnectingOrConnected && !isConnected

    /**
     * Send the offer SDP to OpenAI’s Realtime endpoint and get the answer SDP back.
     */
    private fun requestOpenAiRealtimeSdp(
        model: RealtimeSessionModel,
        ephemeralApiKey: String,
        offerSdp: String): String? {

        val localVariableQuery: MultiValueMap = mutableMapOf()
        localVariableQuery["model"] = listOf(model.toString())
        val localVariableHeaders: MutableMap<String, String> = mutableMapOf()
        localVariableHeaders["Content-Type"] = ApiClient.SdpMediaType
        localVariableHeaders["Accept"] = ApiClient.TextPlainMediaType
        localVariableHeaders["Authorization"] = "Bearer $ephemeralApiKey"

        val request = RequestConfig(
            method = RequestMethod.POST,
            path = "/realtime",
            query = localVariableQuery,
            headers = localVariableHeaders,
            requiresAuthentication = true, // ?
            body = offerSdp,
        )

        when (val response = realtime.request<String, String>(request)) {
            is Success -> {
                return response.data
            }
            else -> {
                log.e("requestOpenAiRealtimeSdp: Error creating OpenAI realtime session: response=$response")
                throw IllegalStateException("Failed to get OpenAI SDP answer: ${response.statusCode}")
            }
        }
    }

    private var connectStartMillis: Long = 0L
    private var connectIntervalMillis: Long = 0L

    /**
     * @return true if still isConnectingOrConnected, false if canceled
     */
    private fun verifyStillConnectingOrConnected(stageName: String): Boolean {
        val uptimeMillis = SystemClock.uptimeMillis()
        val elapsedMillis = uptimeMillis - connectIntervalMillis
        connectIntervalMillis = uptimeMillis
        log.d("verifyStillConnectingOrConnected: isConnectingOrConnected=$isConnectingOrConnected")
        log.i("verifyStillConnectingOrConnected: TIMING_CONNECT $stageName elapsedMillis=${elapsedMillis}ms")
        if (isConnectingOrConnected) return true
        log.w("No longer connecting or connected; cleaning up...")
        disconnect()
        return false
    }

    @WorkerThread
    fun connect(): String? {
        var ephemeralApiKey: String? = null
        try {
            log.d("+connect()")
            if (isConnected) {
                throw IllegalStateException("RealtimeAPI is already connected")
            }

            connectStartMillis = SystemClock.uptimeMillis()
            connectIntervalMillis = connectStartMillis

            isConnectingOrConnected = true

            // Set accessToken to our real [ergo, "dangerous"] API key
            ApiClient.accessToken = dangerousApiKey

            /*
             * https://platform.openai.com/docs/guides/realtime-webrtc#creating-an-ephemeral-token
             * https://platform.openai.com/docs/api-reference/realtime-sessions/create
             */
            val realtimeSessionCreateResponse: RealtimeSessionCreateResponse? = try {
                if (debugInduceMysteriousUnknownHostException) {
                    throw UnknownHostException(MYSTERIOUS_UNKNOWN_HOST_EXCEPTION_MESSAGE)
                }
                realtime.createRealtimeSession(sessionConfig)
            } catch (exception: Exception) {
                log.e("connect: exception=$exception")
                notifyError(exception)
                disconnect()
                null
            }
            ephemeralApiKey = realtimeSessionCreateResponse?.clientSecret?.value
            log.d("connect: ephemeralApiKey=${quote(redact(ephemeralApiKey))}")
            if (ephemeralApiKey == null) {
                notifyError(ClientException("No Ephemeral API Key In Response"))
                disconnect()
                return null
            }

            if (!verifyStillConnectingOrConnected("createRealtimeSession")) {
                ephemeralApiKey = null
                return null
            }

            // Set accessToken to the response's safer (1 minute TTL) ephemeralApiKey;
            // clear it after requestOpenAiRealtimeSdp (success or fail)
            ApiClient.accessToken = ephemeralApiKey

            val audioDeviceModule = JavaAudioDeviceModule.builder(applicationContext)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()

            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions
                    .builder(applicationContext).apply {
                        if (debug) {
                            setEnableInternalTracer(true)
                            setFieldTrials("WebRTC-LogLevel/Warning/")
                        } else {
                            setEnableInternalTracer(false)
                        }
                    }
                    .createInitializationOptions()
            )
            if (!debug) {
                Logging.enableLogToDebugOutput(Severity.LS_NONE)
            }
            val peerConnectionFactory = PeerConnectionFactory
                .builder()
                .setOptions(PeerConnectionFactory.Options())
                .setAudioDeviceModule(audioDeviceModule)
                .createPeerConnectionFactory()
            // ICE/STUN is not needed to talk to *server* (only needed for peer-to-peer)
            val iceServers = listOf<PeerConnection.IceServer>()
            val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

            if (!verifyStillConnectingOrConnected("peerConnectionFactory")) {
                ephemeralApiKey = null
                return null
            }

            val logPc = RealtimeLog(PeerConnection::class)
            peerConnection = peerConnectionFactory.createPeerConnection(
                rtcConfig,
                object : PeerConnection.Observer {
                    override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                        logPc.d("onSignalingChange($newState)")
                    }

                    override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                        logPc.d("onIceConnectionChange($newState)")
                        when (newState) {
                            PeerConnection.IceConnectionState.CONNECTED -> {
                                if (!verifyStillConnectingOrConnected("onIceConnectionChange: CONNECTED")) {
                                    return
                                }
                            }

                            PeerConnection.IceConnectionState.DISCONNECTED,
                            PeerConnection.IceConnectionState.FAILED,
                            PeerConnection.IceConnectionState.CLOSED -> {
                                disconnect()
                            }

                            else -> {}
                        }
                    }

                    override fun onIceConnectionReceivingChange(receiving: Boolean) {
                        logPc.d("onIceConnectionReceivingChange($receiving)")
                    }

                    override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                        logPc.d("onIceGatheringChange($newState)")
                    }

                    override fun onIceCandidate(candidate: IceCandidate) {
                        logPc.d("onIceCandidate(candidate=\"$candidate\")")
                        // Typically you'd send new ICE candidates to the remote peer
                    }

                    override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
                        logPc.d("onIceCandidatesRemoved($candidates)")
                    }

                    override fun onAddStream(stream: MediaStream) {
                        logPc.d("onAddStream(${stream.id})")
                    }

                    override fun onRemoveStream(stream: MediaStream) {
                        logPc.d("onRemoveStream(${stream.id})")
                    }

                    override fun onDataChannel(dc: DataChannel) {
                        logPc.d("onDataChannel(${dc.label()})")
                        // If the remote side creates a DataChannel, handle it here...
                    }

                    override fun onRenegotiationNeeded() {
                        logPc.d("onRenegotiationNeeded()")
                    }

                    override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
                        val track = receiver.track()
                        logPc.d("onAddTrack(receiver={..., track=${track}, ...}, mediaStreams(${mediaStreams.size})=[...])")
                        val trackKind = track?.kind()
                        // Remote audio track arrives here
                        // If you need to play it directly, you can attach it to an AudioTrack sink
                        //...
                        if (trackKind == "audio") {
                            remoteAudioTracks.add(track as AudioTrack)

                            // If you want to play it automatically, attach to an AudioSink here
                            // e.g. track.addSink(myAudioSink)
                        }
                    }
                }
            ) ?: throw IllegalStateException("Failed to create PeerConnection")

            if (!verifyStillConnectingOrConnected("createPeerConnection")) {
                ephemeralApiKey = null
                return null
            }

            setLocalAudioMicrophone(peerConnectionFactory)

            val dataChannelInit = DataChannel.Init()
            val logDc = RealtimeLog(DataChannel::class)
            dataChannel = peerConnection?.createDataChannel("oai-events", dataChannelInit)
            dataChannel?.registerObserver(object : DataChannel.Observer {
                override fun onBufferedAmountChange(previousAmount: Long) {
                    logDc.d("onBufferedAmountChange($previousAmount)")
                }

                override fun onStateChange() {
                    val dataChannel = dataChannel ?: return
                    val state = dataChannel.state()
                    logDc.d("onStateChange(): dataChannel.state()=$state")
                    if (!dataChannelOpened && state == DataChannel.State.OPEN) {
                        dataChannelOpened = true
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
                }

                override fun onMessage(buffer: DataChannel.Buffer) {
                    val dataByteBuffer = buffer.data
                    val bytes = ByteArray(dataByteBuffer.remaining())
                    dataByteBuffer.get(bytes)
                    if (buffer.binary) {
                        onDataChannelBinary(bytes)
                    } else {
                        val messageText = String(bytes, Charsets.UTF_8)
                        onDataChannelText(messageText)
                    }
                }
            })

            val offerConstraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            }

            if (!verifyStillConnectingOrConnected("createDataChannel/registerObserver")) {
                ephemeralApiKey = null
                return null
            }

            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    logPc.d("createOffer onCreateSuccessLocal($desc)")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(sdp: SessionDescription?) {
                            logPc.d("localDescription onCreateSuccess($sdp)")
                        }

                        override fun onSetSuccess() {
                            logPc.d("localDescription setSuccess()")
                            CoroutineScope(Dispatchers.IO).launch {

                                val answerSdp = requestOpenAiRealtimeSdp(
                                    model = sessionConfig.model,
                                    ephemeralApiKey = ephemeralApiKey!!,
                                    offerSdp = desc.description
                                )

                                // No longer used after sdp request (pass or fail).
                                // NOTE that the current session is limited to a maximum of 30 minutes.
                                // https://platform.openai.com/docs/guides/realtime-model-capabilities#session-lifecycle-events
                                // "The maximum duration of a Realtime session is 30 minutes."
                                ApiClient.accessToken = null

                                withContext(Dispatchers.Main) {
                                    val answerDescription = SessionDescription(
                                        SessionDescription.Type.ANSWER,
                                        answerSdp
                                    )
                                    peerConnection?.setRemoteDescription(object : SdpObserver {
                                        override fun onCreateSuccess(sdp: SessionDescription?) {
                                            logPc.d("remoteDescription onCreateSuccess($sdp)")
                                        }

                                        override fun onSetSuccess() {
                                            logPc.d("remoteDescription setSuccess()")
                                        }

                                        override fun onCreateFailure(error: String?) {
                                            logPc.e("remoteDescription onCreateFailure($error)")
                                        }

                                        override fun onSetFailure(error: String) {
                                            logPc.e("remoteDescription onSetFailure($error)")
                                        }
                                    }, answerDescription)
                                }
                            }
                        }

                        override fun onCreateFailure(error: String?) {
                            logPc.e("localDescription onCreateFailure($error)")
                        }

                        override fun onSetFailure(error: String) {
                            logPc.e("localDescription onSetFailure($error)")
                        }
                    }, desc)
                }

                override fun onSetSuccess() {
                    logPc.d("createOffer setSuccess()")
                }

                override fun onCreateFailure(error: String) {
                    logPc.e("createOffer onCreateFailure($error)")
                }

                override fun onSetFailure(error: String?) {
                    logPc.e("createOffer onSetFailure($error)")
                }
            }, offerConstraints)

            if (!verifyStillConnectingOrConnected("createOffer")) {
                ephemeralApiKey = null
                return null
            }
        } finally {
            log.d("-connect(); ephemeralApiKey=${quote(redact(ephemeralApiKey))}")
        }
        return ephemeralApiKey
    }

    fun disconnect() {
        log.d("+disconnect()")

        ApiClient.accessToken = null

        isCancelingResponse = false
        setLocalAudioTrackSpeakerEnabled(true)
        remoteAudioTracks.clear()

        isConnectingOrConnected = false
        isConnected = false

        val peerConnection = this.peerConnection
        this.peerConnection = null
        peerConnection?.also {
            it.close()
        }
        localAudioTrackMicrophoneSender = null
        localAudioTrackMicrophone = null
        val dataChannel = this.dataChannel
        this.dataChannel = null
        dataChannel?.also {
            it.close()
        }
        dataChannelOpened = false

        log.d("-disconnect()")
    }

    //
    //region Audio Stuff...
    //

    /**
     * Requires RECORD_AUDIO permission
     */
    private fun setLocalAudioMicrophone(peerConnectionFactory: PeerConnectionFactory) {
        localAudioTrackMicrophoneSender?.also {
            peerConnection?.removeTrack(it)
            localAudioTrackMicrophoneSender = null
        }

        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        localAudioTrackMicrophone = peerConnectionFactory.createAudioTrack("MIC_TRACK", audioSource)
        localAudioTrackMicrophoneSender = peerConnection?.addTrack(localAudioTrackMicrophone)
    }

    /**
     * Essentially unmute or mute the speaker
     */
    private fun setLocalAudioTrackSpeakerEnabled(enabled: Boolean) {
        log.w("setLocalAudioTrackSpeakerEnabled($enabled)")
        remoteAudioTracks.forEach {
            it.setEnabled(enabled)
        }
    }

    /**
     * Essentially unmute or mute the microphone
     */
    fun setLocalAudioTrackMicrophoneEnabled(enabled: Boolean) {
        log.d("setLocalAudioTrackMicrophoneEnabled($enabled)")
        localAudioTrackMicrophone?.setEnabled(enabled)
    }

    //
    //endregion
    //

//    fun startRtcEventLog() {
//        peerConnection?.startRtcEventLog(...)
//    }
//
//    fun stopRtcEventLog() {
//        peerConnection?.stopRtcEventLog()
//    }

    private inline fun <reified T> dataSend(content: T, mediaType: String? = ApiClient.JsonMediaType): Boolean {
        when {
            mediaType == null || (mediaType.startsWith("application/") && mediaType.endsWith("json")) ->
                return dataSend(Serializer.serialize<T>(content))
            else ->
                throw UnsupportedOperationException("send currently only supports JSON body.")
        }
    }

    private fun dataSend(text: String): Boolean {
        if (debug) {
            log.d("dataSend: text=\"${text}\"")
        }
        return dataSend(text.toByteArray(), binary = false)
    }

    private fun dataSend(bytes: ByteArray,
                         @Suppress("SameParameterValue")
                         binary: Boolean = true): Boolean {
        if (!dataChannelOpened) {
            throw IllegalStateException("data channel not opened")
        }
        return dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), binary)) ?: false
    }

    fun dataSendSessionUpdate(sessionConfig: RealtimeSessionCreateRequest): Boolean {
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

    fun dataSendInputAudioBufferClear(): Boolean {
        log.d("dataSendInputAudioBufferClear()")
        return dataSend(RealtimeClientEventInputAudioBufferClear(
            eventId = RealtimeUtils.generateId(),
        ))
    }

    fun dataSendInputAudioBufferCommit(): Boolean {
        log.d("dataSendInputAudioBufferCommit()")
        return dataSend(RealtimeClientEventInputAudioBufferCommit(
            eventId = RealtimeUtils.generateId(),
        ))
    }

    fun dataSendResponseCreate(): Boolean {
        log.d("dataSendResponseCreate()")
        return dataSend(RealtimeClientEventResponseCreate(
            eventId = RealtimeUtils.generateId(),
        ))
    }

    fun dataSendResponseCancel(responseId: String? = null): Boolean {
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
     * To see it in use:
     * https://youtu.be/mM8KhTxwPgs?t=965
     */
    fun dataSendConversationItemTruncate(itemId: String, audioEndMs: Int): Boolean {
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
    fun dataSendConversationItemCreate(text: String): Boolean {
        return dataSend(
            RealtimeClientEventConversationItemCreate(
                eventId = RealtimeUtils.generateId(),
                item = RealtimeConversationItem(
                    type = RealtimeConversationItem.Type.message,
                    role = RealtimeConversationItem.Role.user,
                    content = listOf(
                        RealtimeConversationItemContent(
                            type = RealtimeConversationItemContent.Type.input_text,
                            text = text
                        )
                    )
                )
            )
        )
    }

    //
    //region Event Listener
    //

    interface RealtimeClientListener {
        /**
         * Called when the client is starting a connection.
         */
        fun onConnecting()

        /**
         * Called when the client encounters an error.
         */
        fun onError(error: Exception)

        /**
         * Called when the client has successfully established a connection.
         */
        fun onConnected()

        /**
         * Called when the client has disconnected.
         */
        fun onDisconnected()

        /**
         * Called when a binary data message is received.
         * @return true to stop further handling; false to continue further handling.
         */
        fun onBinaryMessageReceived(data: ByteArray): Boolean

        /**
         * Called when a textual data message is received over the DataChannel.
         * @return true to stop further handling; false to continue further handling.
         */
        fun onTextMessageReceived(message: String): Boolean

        fun onServerEventConversationCreated(realtimeServerEventConversationCreated: RealtimeServerEventConversationCreated)
        fun onServerEventConversationItemCreated(realtimeServerEventConversationItemCreated: RealtimeServerEventConversationItemCreated)
        fun onServerEventConversationItemDeleted(realtimeServerEventConversationItemDeleted: RealtimeServerEventConversationItemDeleted)
        fun onServerEventConversationItemInputAudioTranscriptionCompleted(realtimeServerEventConversationItemInputAudioTranscriptionCompleted: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted)
        fun onServerEventConversationItemInputAudioTranscriptionFailed(realtimeServerEventConversationItemInputAudioTranscriptionFailed: RealtimeServerEventConversationItemInputAudioTranscriptionFailed)
        fun onServerEventConversationItemTruncated(realtimeServerEventConversationItemTruncated: RealtimeServerEventConversationItemTruncated)
        fun onServerEventError(realtimeServerEventError: RealtimeServerEventError)
        fun onServerEventInputAudioBufferCleared(realtimeServerEventInputAudioBufferCleared: RealtimeServerEventInputAudioBufferCleared)
        fun onServerEventInputAudioBufferCommitted(realtimeServerEventInputAudioBufferCommitted: RealtimeServerEventInputAudioBufferCommitted)
        fun onServerEventInputAudioBufferSpeechStarted(realtimeServerEventInputAudioBufferSpeechStarted: RealtimeServerEventInputAudioBufferSpeechStarted)
        fun onServerEventInputAudioBufferSpeechStopped(realtimeServerEventInputAudioBufferSpeechStopped: RealtimeServerEventInputAudioBufferSpeechStopped)
        fun onServerEventOutputAudioBufferAudioStarted(realtimeServerEventOutputAudioBufferAudioStarted: ServerEventOutputAudioBufferAudioStarted)
        fun onServerEventOutputAudioBufferAudioStopped(realtimeServerEventOutputAudioBufferAudioStopped: ServerEventOutputAudioBufferAudioStopped,
        )
        fun onServerEventRateLimitsUpdated(realtimeServerEventRateLimitsUpdated: RealtimeServerEventRateLimitsUpdated)
        fun onServerEventResponseAudioDelta(realtimeServerEventResponseAudioDelta: RealtimeServerEventResponseAudioDelta)
        fun onServerEventResponseAudioDone(realtimeServerEventResponseAudioDone: RealtimeServerEventResponseAudioDone)
        fun onServerEventResponseAudioTranscriptDelta(realtimeServerEventResponseAudioTranscriptDelta: RealtimeServerEventResponseAudioTranscriptDelta)
        fun onServerEventResponseAudioTranscriptDone(realtimeServerEventResponseAudioTranscriptDone: RealtimeServerEventResponseAudioTranscriptDone)
        fun onServerEventResponseContentPartAdded(realtimeServerEventResponseContentPartAdded: RealtimeServerEventResponseContentPartAdded)
        fun onServerEventResponseContentPartDone(realtimeServerEventResponseContentPartDone: RealtimeServerEventResponseContentPartDone)
        fun onServerEventResponseCreated(realtimeServerEventResponseCreated: RealtimeServerEventResponseCreated)
        fun onServerEventResponseDone(realtimeServerEventResponseDone: RealtimeServerEventResponseDone)
        fun onServerEventResponseFunctionCallArgumentsDelta(realtimeServerEventResponseFunctionCallArgumentsDelta: RealtimeServerEventResponseFunctionCallArgumentsDelta)
        fun onServerEventResponseFunctionCallArgumentsDone(realtimeServerEventResponseFunctionCallArgumentsDone: RealtimeServerEventResponseFunctionCallArgumentsDone)
        fun onServerEventResponseOutputItemAdded(realtimeServerEventResponseOutputItemAdded: RealtimeServerEventResponseOutputItemAdded)
        fun onServerEventResponseOutputItemDone(realtimeServerEventResponseOutputItemDone: RealtimeServerEventResponseOutputItemDone)
        fun onServerEventResponseTextDelta(realtimeServerEventResponseTextDelta: RealtimeServerEventResponseTextDelta)
        fun onServerEventResponseTextDone(realtimeServerEventResponseTextDone: RealtimeServerEventResponseTextDone)
        fun onServerEventSessionCreated(realtimeServerEventSessionCreated: RealtimeServerEventSessionCreated)
        fun onServerEventSessionUpdated(realtimeServerEventSessionUpdated: RealtimeServerEventSessionUpdated)
        fun onServerEventSessionExpired(realtimeServerEventError: RealtimeServerEventError)
    }

    private val listeners = mutableListOf<RealtimeClientListener>()

    fun addListener(listener: RealtimeClientListener) {
        listeners.add(listener)
    }

    @Suppress("unused")
    fun removeListener(listener: RealtimeClientListener) {
        listeners.remove(listener)
    }

    private fun notifyConnecting() {
        listeners.forEach {
            it.onConnecting()
        }
    }

    private fun notifyError(error: Exception) {
        if (!isConnectingOrConnected) return
        listeners.forEach {
            it.onError(error)
        }
    }

    private fun notifyConnected() {
        listeners.forEach {
            it.onConnected()
        }
    }

    private fun notifyDisconnected() {
        listeners.forEach {
            it.onDisconnected()
        }
    }

    private fun onDataChannelBinary(data: ByteArray) {
        if (debug || debugDataChannelBinary) {
            log.d("onDataChannelBinary: data(${data.size} bytes BINARY)=[...]")
        }
        listeners.forEach {
            if (it.onBinaryMessageReceived(data)) return
        }
    }

    private fun onDataChannelText(message: String) {
        if (debug || debugDataChannelText) {
            log.d("onDataChannelText: message(${message.length} chars TEXT)=${quote(message)}")
        }
        listeners.forEach {
            if (it.onTextMessageReceived(message)) return
        }

        val type = extractValue("type", message)
        if (debug || debugDataChannelText) {
            log.d("onDataChannelText: type=${quote(type)}")
        }
        // TODO: Consider using reflection to auto-populate the equivalent of the below code...
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
            "output_audio_buffer.audio_started" -> {
                log.w("onDataChannelText: undocumented `output_audio_buffer.audio_started`")
                Serializer.deserialize<ServerEventOutputAudioBufferAudioStarted>(message)?.also {
                    notifyServerEventOutputAudioBufferAudioStarted(it)
                }
            }
            "output_audio_buffer.audio_stopped" -> {
                log.w("onDataChannelText: undocumented `output_audio_buffer.audio_stopped`")
                Serializer.deserialize<ServerEventOutputAudioBufferAudioStopped>(message)?.also {
                    notifyServerEventOutputAudioBufferAudioStopped(it)
                }
            }
            else ->
                log.e("onDataChannelText: unknown/undocumented type=${quote(type)}; message(${message.length} chars TEXT)=${quote(message)}")
        }
    }

    private fun notifyServerEventConversationCreated(realtimeServerEventConversationCreated: RealtimeServerEventConversationCreated) {
        listeners.forEach {
            it.onServerEventConversationCreated(realtimeServerEventConversationCreated)
        }
    }

    private fun notifyServerEventConversationItemCreated(realtimeServerEventConversationItemCreated: RealtimeServerEventConversationItemCreated) {
        listeners.forEach {
            it.onServerEventConversationItemCreated(realtimeServerEventConversationItemCreated)
        }
    }

    private fun notifyServerEventConversationItemDeleted(realtimeServerEventConversationItemDeleted: RealtimeServerEventConversationItemDeleted) {
        listeners.forEach {
            it.onServerEventConversationItemDeleted(realtimeServerEventConversationItemDeleted)
        }
    }

    private fun notifyServerEventConversationItemInputAudioTranscriptionCompleted(realtimeServerEventConversationItemInputAudioTranscriptionCompleted: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted) {
        listeners.forEach {
            it.onServerEventConversationItemInputAudioTranscriptionCompleted(realtimeServerEventConversationItemInputAudioTranscriptionCompleted)
        }
    }

    private fun notifyServerEventConversationItemInputAudioTranscriptionFailed(realtimeServerEventConversationItemInputAudioTranscriptionFailed: RealtimeServerEventConversationItemInputAudioTranscriptionFailed) {
        listeners.forEach {
            it.onServerEventConversationItemInputAudioTranscriptionFailed(realtimeServerEventConversationItemInputAudioTranscriptionFailed)
        }
    }

    private fun notifyServerEventConversationItemTruncated(realtimeServerEventConversationItemTruncated: RealtimeServerEventConversationItemTruncated) {
        listeners.forEach {
            it.onServerEventConversationItemTruncated(realtimeServerEventConversationItemTruncated)
        }
    }

    private fun notifyServerEventError(realtimeServerEventError: RealtimeServerEventError) {
        val error = realtimeServerEventError.error
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
            listeners.forEach {
                it.onServerEventSessionExpired(realtimeServerEventError)
            }
        } else {
            listeners.forEach {
                it.onServerEventError(realtimeServerEventError)
            }
        }
    }

    private fun notifyServerEventInputAudioBufferCleared(realtimeServerEventInputAudioBufferCleared: RealtimeServerEventInputAudioBufferCleared) {
        listeners.forEach {
            it.onServerEventInputAudioBufferCleared(realtimeServerEventInputAudioBufferCleared)
        }
    }

    private fun notifyServerEventInputAudioBufferCommitted(realtimeServerEventInputAudioBufferCommitted: RealtimeServerEventInputAudioBufferCommitted) {
        listeners.forEach {
            it.onServerEventInputAudioBufferCommitted(realtimeServerEventInputAudioBufferCommitted)
        }
    }

    private fun notifyServerEventInputAudioBufferSpeechStarted(realtimeServerEventInputAudioBufferSpeechStarted: RealtimeServerEventInputAudioBufferSpeechStarted) {
        listeners.forEach {
            it.onServerEventInputAudioBufferSpeechStarted(realtimeServerEventInputAudioBufferSpeechStarted)
        }
    }

    private fun notifyServerEventInputAudioBufferSpeechStopped(realtimeServerEventInputAudioBufferSpeechStopped: RealtimeServerEventInputAudioBufferSpeechStopped) {
        listeners.forEach {
            it.onServerEventInputAudioBufferSpeechStopped(realtimeServerEventInputAudioBufferSpeechStopped)
        }
    }

    /**
     * Example:
     * ```
     * {
     *   "type":"output_audio_buffer.audio_started",
     *   "event_id":"event_51bb3e4b2b9a45b5",
     *   "response_id":"resp_AvyUM8tYkYjvWaXhkqcBJ"
     * }
     * ```
     */
    @Suppress("PropertyName")
    data class ServerEventOutputAudioBufferAudioStarted(
        /**
         * The event type, must be "output_audio_buffer.audio_stopped".
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

    private fun notifyServerEventOutputAudioBufferAudioStarted(realtimeServerEventOutputAudioBufferAudioStarted: ServerEventOutputAudioBufferAudioStarted) {
        listeners.forEach {
            it.onServerEventOutputAudioBufferAudioStarted(realtimeServerEventOutputAudioBufferAudioStarted)
        }
    }

    /**
     * Example:
     * ```
     * {
     *   "type":"output_audio_buffer.audio_stopped",
     *   "event_id":"event_e69be18ad4f34b01",
     *   "response_id":"resp_Asg4GBgYXDJXSLfXu6cWO"
     * }
     * ```
     */
    @Suppress("PropertyName")
    data class ServerEventOutputAudioBufferAudioStopped(
        /**
         * The event type, must be "output_audio_buffer.audio_stopped".
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

    private fun notifyServerEventOutputAudioBufferAudioStopped(realtimeServerEventOutputAudioBufferAudioStopped: ServerEventOutputAudioBufferAudioStopped) {
        if (isCancelingResponse) {
            isCancelingResponse = false
            CoroutineScope(Dispatchers.IO).launch {
                delay(200) // delay a bit to allow any already buffered audio to finish playing before unmuting
                setLocalAudioTrackSpeakerEnabled(true)
                listeners.forEach {
                    it.onServerEventOutputAudioBufferAudioStopped(realtimeServerEventOutputAudioBufferAudioStopped,
                    )
                }
            }
        } else {
            listeners.forEach {
                it.onServerEventOutputAudioBufferAudioStopped(realtimeServerEventOutputAudioBufferAudioStopped,
                )
            }
        }
    }

    private fun notifyServerEventRateLimitsUpdated(realtimeServerEventRateLimitsUpdated: RealtimeServerEventRateLimitsUpdated) {
        listeners.forEach {
            it.onServerEventRateLimitsUpdated(realtimeServerEventRateLimitsUpdated)
        }
    }

    private fun notifyServerEventResponseAudioDelta(realtimeServerEventResponseAudioDelta: RealtimeServerEventResponseAudioDelta) {
        listeners.forEach {
            it.onServerEventResponseAudioDelta(realtimeServerEventResponseAudioDelta)
        }
    }

    private fun notifyServerEventResponseAudioDone(realtimeServerEventResponseAudioDone: RealtimeServerEventResponseAudioDone) {
        listeners.forEach {
            it.onServerEventResponseAudioDone(realtimeServerEventResponseAudioDone)
        }
    }

    private fun notifyServerEventResponseAudioTranscriptDelta(
        realtimeServerEventResponseAudioTranscriptDelta: RealtimeServerEventResponseAudioTranscriptDelta
    ) {
        listeners.forEach {
            it.onServerEventResponseAudioTranscriptDelta(realtimeServerEventResponseAudioTranscriptDelta)
        }
    }

    private fun notifyServerEventResponseAudioTranscriptDone(
        realtimeServerEventResponseAudioTranscriptDone: RealtimeServerEventResponseAudioTranscriptDone
    ) {
        listeners.forEach {
            it.onServerEventResponseAudioTranscriptDone(realtimeServerEventResponseAudioTranscriptDone)
        }
    }

    private fun notifyServerEventResponseContentPartAdded(
        realtimeServerEventResponseContentPartAdded: RealtimeServerEventResponseContentPartAdded
    ) {
        listeners.forEach {
            it.onServerEventResponseContentPartAdded(realtimeServerEventResponseContentPartAdded)
        }
    }

    private fun notifyServerEventResponseContentPartDone(realtimeServerEventResponseContentPartDone: RealtimeServerEventResponseContentPartDone) {
        listeners.forEach {
            it.onServerEventResponseContentPartDone(realtimeServerEventResponseContentPartDone)
        }
    }

    private fun notifyServerEventResponseCreated(realtimeServerEventResponseCreated: RealtimeServerEventResponseCreated) {
        listeners.forEach {
            it.onServerEventResponseCreated(realtimeServerEventResponseCreated)
        }
    }

    private fun notifyServerEventResponseDone(realtimeServerEventResponseDone: RealtimeServerEventResponseDone) {
        listeners.forEach {
            it.onServerEventResponseDone(realtimeServerEventResponseDone)
        }
    }

    private fun notifyServerEventResponseFunctionCallArgumentsDelta(
        realtimeServerEventResponseFunctionCallArgumentsDelta: RealtimeServerEventResponseFunctionCallArgumentsDelta
    ) {
        listeners.forEach {
            it.onServerEventResponseFunctionCallArgumentsDelta(realtimeServerEventResponseFunctionCallArgumentsDelta)
        }
    }

    private fun notifyServerEventResponseFunctionCallArgumentsDone(
        realtimeServerEventResponseFunctionCallArgumentsDone: RealtimeServerEventResponseFunctionCallArgumentsDone
    ) {
        listeners.forEach {
            it.onServerEventResponseFunctionCallArgumentsDone(realtimeServerEventResponseFunctionCallArgumentsDone)
        }
    }

    private fun notifyServerEventResponseOutputItemAdded(realtimeServerEventResponseOutputItemAdded: RealtimeServerEventResponseOutputItemAdded) {
        listeners.forEach {
            it.onServerEventResponseOutputItemAdded(realtimeServerEventResponseOutputItemAdded)
        }
    }

    private fun notifyServerEventResponseOutputItemDone(realtimeServerEventResponseOutputItemDone: RealtimeServerEventResponseOutputItemDone) {
        listeners.forEach {
            it.onServerEventResponseOutputItemDone(realtimeServerEventResponseOutputItemDone)
        }
    }

    private fun notifyServerEventResponseTextDelta(realtimeServerEventResponseTextDelta: RealtimeServerEventResponseTextDelta) {
        listeners.forEach {
            it.onServerEventResponseTextDelta(realtimeServerEventResponseTextDelta)
        }
    }

    private fun notifyServerEventResponseTextDone(realtimeServerEventResponseTextDone: RealtimeServerEventResponseTextDone) {
        listeners.forEach {
            it.onServerEventResponseTextDone(realtimeServerEventResponseTextDone)
        }
    }

    private fun notifyServerEventSessionCreated(realtimeServerEventSessionCreated: RealtimeServerEventSessionCreated) {
        val elapsedMillis = SystemClock.uptimeMillis() - connectStartMillis
        log.i("notifyServerEventSessionCreated: CONNECTED! TIMING_CONNECT Connected in elapsedMillis=${elapsedMillis}ms")
        isConnected = true

        listeners.forEach {
            it.onServerEventSessionCreated(realtimeServerEventSessionCreated)
        }
    }

    private fun notifyServerEventSessionUpdated(realtimeServerEventSessionUpdated: RealtimeServerEventSessionUpdated) {
        listeners.forEach {
            it.onServerEventSessionUpdated(realtimeServerEventSessionUpdated)
        }
    }

    //
    //endregion
    //
}