package com.swooby.alfredai.openai.realtime

import android.content.Context
import com.openai.apis.RealtimeApi
import com.openai.infrastructure.ApiClient
import com.openai.infrastructure.MultiValueMap
import com.openai.infrastructure.RequestConfig
import com.openai.infrastructure.RequestMethod
import com.openai.infrastructure.Success
import com.openai.models.RealtimeSessionCreateRequest
import com.swooby.alfredai.Utils.quote
import com.swooby.alfredai.Utils.redact
import com.swooby.alfredai.common.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import java.nio.ByteBuffer

class RealtimeTransportWebRTC(
    private val applicationContext: Context,
    dangerousApiKey: String,
    private var sessionConfig: RealtimeSessionCreateRequest,
    realtime: RealtimeApi = RealtimeApi(),
    private val debug: Boolean = false)
    : RealtimeTransportBase(dangerousApiKey, sessionConfig, realtime)
{
    companion object {
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
    }

    override val log = RealtimeLog(RealtimeTransportWebRTC::class)

    override val transportType = TransportType.WEBRTC

    private var peerConnection: PeerConnection? = null
    private var localAudioTrackMicrophone: AudioTrack? = null
    private var localAudioTrackMicrophoneSender: RtpSender? = null
    private val remoteAudioTracks = mutableListOf<AudioTrack>()
    private var dataChannel: DataChannel? = null
    private var dataChannelOpened = false

    /**
     * Send the offer SDP to OpenAI’s Realtime endpoint and get the answer SDP back.
     */
    private fun requestOpenAiRealtimeSdp(
        model: RealtimeSessionCreateRequest.Model?,
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

    override fun connectInternal(ephemeralApiKey: String): String? {
        @Suppress("NAME_SHADOWING")
        var ephemeralApiKey: String? = ephemeralApiKey
        try {
            log.d("+connectInternal(ephemeralApiKey=${quote(redact(ephemeralApiKey))})")

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
                        onDataChannelOpened()
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
        } finally {
            log.d("-connectInternal(ephemeralApiKey=${quote(redact(ephemeralApiKey))})")
        }
        return ephemeralApiKey
    }

    override fun disconnectInternal() {
        log.d("+disconnectInternal()")

        setLocalAudioTrackSpeakerEnabled(true)
        remoteAudioTracks.clear()

        isConnectingOrConnected = false

        peerConnection?.also {
            peerConnection = null
            it.close()
        }

        localAudioTrackMicrophoneSender = null
        localAudioTrackMicrophone = null

        dataChannel?.also {
            dataChannel = null
            it.close()
        }
        dataChannelOpened = false

        log.d("-disconnectInternal()")
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
    override fun setLocalAudioTrackSpeakerEnabled(enabled: Boolean) {
        log.w("setLocalAudioTrackSpeakerEnabled($enabled)")
        remoteAudioTracks.forEach {
            it.setEnabled(enabled)
        }
    }

    /**
     * Essentially unmute or mute the microphone
     */
    override fun setLocalAudioTrackMicrophoneEnabled(enabled: Boolean) {
        log.d("setLocalAudioTrackMicrophoneEnabled($enabled)")
        localAudioTrackMicrophone?.setEnabled(enabled)
    }

    //
    //endregion
    //

    private fun onDataChannelOpened() {
        listeners.forEach {
            it.onDataChannelOpened()
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
    }

    override fun dataSendBinary(data: ByteArray): Boolean {
        if (!dataChannelOpened) throw IllegalStateException("data channel not opened")
        if (debug || debugDataChannelBinary) {
            log.d("dataSendBinary: data(${data.size} bytes BINARY)=[...]")
        }
        return dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(data), true)) ?: false
    }

    override fun dataSendText(message: String): Boolean {
        if (!dataChannelOpened) throw IllegalStateException("data channel not opened")
        if (debug || debugDataChannelText) {
            log.d("dataSendText: message(${message.length} chars TEXT)=${quote(message)}")
        }
        val data = message.toByteArray(Charsets.UTF_8)
        return dataChannel?.send(DataChannel.Buffer(ByteBuffer.wrap(data), false)) ?: false
    }
}
