package com.swooby.alfredai.openai.realtime

import android.content.Context
import android.media.AudioManager
import com.openai.apis.RealtimeApi
import com.openai.infrastructure.ApiClient
import com.openai.infrastructure.MultiValueMap
import com.openai.infrastructure.RequestConfig
import com.openai.infrastructure.RequestMethod
import com.openai.infrastructure.Serializer
import com.openai.infrastructure.Success
import com.openai.models.RealtimeClientEventInputAudioBufferClear
import com.openai.models.RealtimeClientEventInputAudioBufferCommit
import com.openai.models.RealtimeClientEventResponseCreate
import com.openai.models.RealtimeClientEventSessionUpdate
import com.openai.models.RealtimeServerEventInputAudioBufferCleared
import com.openai.models.RealtimeSessionCreateRequest
import com.openai.models.RealtimeSessionModel
import com.swooby.alfredai.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.webrtc.AudioTrack
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpSender
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

/**
 * https://platform.openai.com/docs/guides/realtime
 * https://platform.openai.com/docs/api-reference/realtime
 * https://platform.openai.com/docs/models#gpt-4o-realtime
 * https://community.openai.com/tags/c/api/7/realtime
 *
 * https://github.com/GetStream/webrtc-android/blob/main/stream-webrtc-android/src/main/java/org/webrtc/
 */
class RealtimeClient(private val dangerousApiKey: String,
                     private var sessionConfig: RealtimeSessionCreateRequest,
                     private val debug:Boolean = false) {
    companion object {
        private val log = RealtimeLog(RealtimeClient::class)
    }

    private val httpClient = if (debug) {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .build()
    } else {
        ApiClient.defaultClient
    }

    private val realtime = RealtimeApi(client = httpClient)

    private var peerConnection: PeerConnection? = null
    private var localAudioTrackMicrophone: AudioTrack? = null
    private var localAudioTrackMicrophoneSender: RtpSender? = null
    private var dataChannel: DataChannel? = null
    private var dataChannelOpened = false

    val isConnected: Boolean get() = peerConnection != null

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

    fun connect(context: Context): String? {
        if (isConnected) {
            throw IllegalStateException("RealtimeAPI is already connected")
        }

        // Set accessToken to our real [ergo, "dangerous"] API key
        ApiClient.accessToken = dangerousApiKey

        /*
         * https://platform.openai.com/docs/guides/realtime-webrtc#creating-an-ephemeral-token
         * https://platform.openai.com/docs/api-reference/realtime-sessions/create
         */
        val realtimeSessionCreateResponse = realtime.createRealtimeSession(sessionConfig)
        val ephemeralApiKey = realtimeSessionCreateResponse.client_secret?.value ?: return null
        log.d("connect: ephemeralApiKey=$ephemeralApiKey")

        // Set accessToken to the response's safer (1 minute TTL) ephemeralApiKey;
        // clear it after requestOpenAiRealtimeSdp (success or fail)
        ApiClient.accessToken = ephemeralApiKey

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions
            .builder(context)
            .also { builder ->
                if (debug) {
                    builder
                        .setEnableInternalTracer(true)
                        .setFieldTrials("WebRTC-LogLevel/Warning/")
                } else {
                    builder
                        .setEnableInternalTracer(false)
                }
            }
            .createInitializationOptions())
        val peerConnectionFactory = PeerConnectionFactory
            .builder()
            .setOptions(PeerConnectionFactory.Options())
            .createPeerConnectionFactory()
        // ICE/STUN is not needed to talk to *server* (only needed for peer-to-peer)
        val iceServers = listOf<PeerConnection.IceServer>()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        val logPc = RealtimeLog(PeerConnection::class)
        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                    logPc.d("onSignalingChange($newState)")
                }
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    logPc.d("onIceConnectionChange($newState)")
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    logPc.d("onIceConnectionReceivingChange($receiving)")
                }
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    logPc.d("onIceGatheringChange($newState)")
                }
                override fun onIceCandidate(candidate: IceCandidate) {
                    logPc.d("onIceCandidate($candidate)")
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
                    logPc.d("onAddTrack(${receiver.track()?.kind()}, mediaStreams(${mediaStreams.size})=[...])")
                    // Remote audio track arrives here
                    // If you need to play it directly, you can attach it to an AudioTrack sink
                    //...
                }
            }
        ) ?: throw IllegalStateException("Failed to create PeerConnection")

        //
        //region Audio
        //
        // TODO: abstract this out and allow routing of audio to different audio devices,
        // especially bluetooth headset/earplugs/headphones
        // TODO: For debugging purposes, is there a way to hear/echo the captured microphone audio?
        //  Closest thing I can find is to enable session input_audio_transcription,
        //  but that costs more money! :/
        setLocalAudioMicrophone(peerConnectionFactory)
        setLocalAudioSpeaker(context)
        //
        //endregion Audio
        //

        val dataChannelInit = DataChannel.Init()
        val logDc = RealtimeLog(DataChannel::class)
        dataChannel = peerConnection?.createDataChannel("oai-events", dataChannelInit)
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                logDc.d("onBufferedAmountChange($previousAmount)")
            }
            override fun onStateChange() {
                val dataChannel = dataChannel
                val state = dataChannel?.state()
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
                    logDc.d("onMessage: buffer(${bytes.size} bytes BINARY)=[...]")
                } else {
                    val messageText = String(bytes, Charsets.UTF_8)
                    logDc.d("onMessage: buffer(${bytes.size} bytes TEXT)=${Utils.quote(messageText)}")
                }
            }
        })

        val offerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
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
                                ephemeralApiKey = ephemeralApiKey,
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

        return ephemeralApiKey
    }

    fun disconnect() {
        peerConnection?.also {
            it.close()
            peerConnection = null
        }
        localAudioTrackMicrophoneSender = null
        localAudioTrackMicrophone = null
        dataChannel?.also {
            it.close()
            dataChannel = null
        }
        dataChannelOpened = false
    }

    private inline fun <reified T> dataSend(content: T, mediaType: String? = ApiClient.JsonMediaType): Boolean {
        when {
            mediaType == null || (mediaType.startsWith("application/") && mediaType.endsWith("json")) ->
                return dataSend(Serializer.moshi.adapter(T::class.java).toJson(content))
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
        if (!isConnected) {
            throw IllegalStateException("not connected")
        }
        return dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), binary)) ?: false
    }

    fun dataSendSessionUpdate(sessionConfig: RealtimeSessionCreateRequest): Boolean {
        val content = RealtimeClientEventSessionUpdate(
            type = RealtimeClientEventSessionUpdate.Type.sessionUpdate,
            session = sessionConfig,
            event_id = RealtimeUtils.generateId())
        return dataSend(content)
    }

//    fun startRtcEventLog() {
//        peerConnection?.startRtcEventLog(...)
//    }
//
//    fun stopRtcEventLog() {
//        peerConnection?.stopRtcEventLog()
//    }

    //receive(eventName, event)

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

    private fun setLocalAudioSpeaker(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // deprecated...
        audioManager.isSpeakerphoneOn = true
    }

    /**
     * Essentially unmute or mute the speaker
     */
    fun setLocalAudioTrackSpeakerEnabled(enabled: Boolean) {
        TODO("Not yet implemented")
    }

    /**
     * Essentially unmute or mute the microphone
     */
    fun setLocalAudioTrackMicrophoneEnabled(enabled: Boolean) {
        localAudioTrackMicrophone?.setEnabled(enabled)
    }

    // https://platform.openai.com/docs/guides/realtime-model-capabilities#voice-activity-detection-vad
    // "When VAD is disabled, the client will have to manually emit some additional client events to trigger audio responses:
    //
    //  * Manually send `input_audio_buffer.commit`, which will create a new user input item for the conversation.
    //  * Manually send `response.create` to trigger an audio response from the model.
    //  * Send `input_audio_buffer.clear` before beginning a new user input.
    // "

    fun dataSendInputAudioBufferClear(): Boolean {
        return dataSend(RealtimeClientEventInputAudioBufferClear(
            type = RealtimeClientEventInputAudioBufferClear.Type.input_audio_bufferClear,
            event_id = RealtimeUtils.generateId()
        ))
    }

    fun dataSendInputAudioBufferCommit(): Boolean {
        return dataSend(RealtimeClientEventInputAudioBufferCommit(
            type = RealtimeClientEventInputAudioBufferCommit.Type.input_audio_bufferCommit,
            event_id = RealtimeUtils.generateId()
        ))
    }

    fun dataSendResponseCreate(): Boolean {
        return dataSend(RealtimeClientEventResponseCreate(
            type = RealtimeClientEventResponseCreate.Type.responseCreate,
            event_id = RealtimeUtils.generateId()
        ))
    }

    // TODO:(pv implement audio inputs and outputs
    // https://platform.openai.com/docs/guides/realtime-model-capabilities#audio-inputs-and-outputs
    // https://platform.openai.com/docs/guides/realtime-model-capabilities#handling-audio-with-websockets
    //

    //
    //endregion
    //

    // TODO:(pv) implement text inputs and outputs
    //  https://platform.openai.com/docs/guides/realtime-model-capabilities#text-inputs-and-outputs
}