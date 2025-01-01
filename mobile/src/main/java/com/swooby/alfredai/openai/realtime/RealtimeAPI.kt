package com.swooby.alfredai.openai.realtime

import android.content.Context
import android.media.AudioManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import java.nio.ByteBuffer

/**
 * https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-model
 * https://platform.openai.com/docs/models#gpt-4o-realtime
 */
enum class SessionModel {
    `gpt-4o-realtime-preview-2024-12-17`,
    `gpt-4o-mini-realtime-preview-2024-12-17`,
}

/**
 * https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-voice
 */
enum class SessionVoice {
    alloy,
    ash,
    ballad,
    coral,
    echo,
    sage,
    shimmer,
    verse,
}

/**
 * https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-modalities
 */
enum class SessionModalities {
    audio,
    text,
}

/**
 * https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-input_audio_format
 * https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-output_audio_format
 */
enum class SessionAudioFormat {
    pcm16,
    g711_ulaw,
    g711_alaw,
}

/**
 * https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-turn_detection
 * "Can be set to null to turn off."
 * Set to `type=null` or `type=""` to explicitly turn off.
 */
@Serializable
data class SessionTurnDetection(
    val type: String? = "server_vad",
    val threshold: Double = 0.5,
    val prefix_padding_ms: Int = 300,
    val silence_duration_ms: Int = 500,
    val create_response: Boolean = true,
)

/**
 * https://platform.openai.com/docs/api-reference/realtime-sessions/create#realtime-sessions-create-input_audio_transcription
 */
@Serializable
data class SessionInputTurnDetection(val model: String = "whisper-1")

/**
 * https://platform.openai.com/docs/api-reference/realtime-sessions/create
 * Any field/value == null will be ignored and thus use the default value [determined by the server].
 */
@Serializable
data class SessionConfig(
    /**
     * Set to null to ignore and use server-side default of ["audio", "text"]
     */
    var modalities: List<SessionModalities>? = null,
    /**
     * Set to null to ignore and use server-side default of
     * "Your knowledge cutoff is 2023-10. You are a helpful, witty, and friendly AI. ..."
     */
    var instructions: String? = null,
    /**
     * Set to null to ignore and use server-side default of "pcm16"
     */
    var input_audio_format: SessionAudioFormat? = null,
    /**
     * Set to null to ignore and use server-side default of "pcm16"
     */
    var output_audio_format: SessionAudioFormat? = null,
    /**
     * Set to null to ignore and use server-side default of { "model": "whisper-1" }
     */
    var input_audio_transcription: SessionInputTurnDetection? = null,
    /**
     * Set to null to ignore and use server-side default of `{ "type": "server_vad", ... }`
     * Set to `{ type: null }`, or `{ type: ""}` to explicitly turn off.
     */
    var turn_detection: SessionTurnDetection? = null,

    // TODO:(pv) ...
    //var tools: List<Map<String, Any>>? = emptyList(),
    //var tool_choice: String? = "auto",

    /**
     * Set to null to ignore and use server-side default of 0.8
     */
    var temperature: Double? = null,
    /**
     * Set to null to ignore and use server-side default of "inf"
     */
    var max_response_output_tokens: Int? = null,
) {
    fun toJsonObject(): JsonObject {
        return buildJsonObject {
            modalities?.let { list ->
                put("modalities", buildJsonArray {
                    list.forEach { modality -> add(modality.name) }
                })
            }
            instructions?.let {
                put("instructions", it)
            }
            input_audio_format?.let {
                put("input_audio_format", it.name)
            }
            output_audio_format?.let {
                put("output_audio_format", it.name)
            }
            input_audio_transcription?.let {
                put("input_audio_transcription", Json.encodeToJsonElement(SessionInputTurnDetection.serializer(), it))
            }
            turn_detection?.let {
                put("turn_detection", if (it.type == null || it.type == "") JsonNull else Json.encodeToJsonElement(SessionTurnDetection.serializer(), it))
            }

            // TODO:(pv) ...
            //tools?.let { put("tools", Json.encodeToJsonElement(it)) }
            //tool_choice?.let { put("tool_choice", it) }

            temperature?.let {
                put("temperature", it)
            }
            max_response_output_tokens?.let {
                put("max_response_output_tokens", max_response_output_tokens)
            }
        }
    }
}

/**
 * Requires at least the following API permissions:
 *  Models (v1/models) Read
 *  Model capabilities (v1/audio, ...): Write
 *
 * Otherwise the following data message will be received:
 * ```
 * ..., "status_details":{"type":"failed","error":{"type":"invalid_request_error","code":"inference_not_authorized_error",
 * "message":"You have insufficient permissions for this operation. Missing scopes: model.request. Check that you have the correct role in your organization (Reader, Writer, Owner) and project (Member, Owner), and if you're using a restricted API key, that it has the necessary scopes."
 * }}, ...
 * ```
 */
class RealtimeAPI(
    private val apiKey: String,
    private val debug: Boolean = false
)
    : RealtimeEventHandler() {

    companion object {
        private const val TAG = "RealtimeAPI"

        @Suppress("MemberVisibilityCanBePrivate")
        const val URL = "https://api.openai.com/v1/realtime"

        init {
            //Logging.enableLogToDebugOutput(Logging.Severity.LS_WARNING)
            //Logging.enableLogToDebugOutput(Logging.Severity.LS_ERROR)
        }

        /**
         * https://platform.openai.com/docs/guides/realtime-webrtc#creating-an-ephemeral-token
         * https://platform.openai.com/docs/api-reference/realtime-sessions/create
         */
        fun requestOpenAiRealtimeEphemeralApiKey(
            dangerousOpenAiApiKey: String,
            sessionModel: SessionModel,
            sessionVoice: SessionVoice,
            sessionConfig: SessionConfig? = null
            ): String? {

            var saferEphemeralApiKey: String? = null

            val url = "$URL/sessions"
            val contentTypeApplicationJson = "application/json"

            val requestSessionConfig = buildJsonObject {
                put("model", sessionModel.toString())
                put("voice", sessionVoice.toString())
                sessionConfig?.toJsonObject()?.forEach {
                    put(it.key, it.value)
                }
            }

            val requestBody = requestSessionConfig
                .toString()
                .toByteArray()
                .toRequestBody(contentTypeApplicationJson.toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("Authorization", "Bearer $dangerousOpenAiApiKey")
                .addHeader("Content-Type", contentTypeApplicationJson)
                .build()

            val client = OkHttpClient()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseBody = response.body?.string() ?: return null
                val json = JSONObject(responseBody)
                saferEphemeralApiKey = json.optJSONObject("client_secret")?.optString("value")
            } else {
                val responseBody = response.body?.string()
                Log.e(TAG, "Error creating OpenAI realtime session: $responseBody")
            }

            return saferEphemeralApiKey
        }

        /**
         * Send the offer SDP to OpenAI’s Realtime endpoint and get the answer SDP back.
         */
        private fun requestOpenAiRealtimeSdp(apiKey: String, offerSdp: String): String {
            val contentTypeApplicationSdp = "application/sdp"
            val baseUrl = "https://api.openai.com/v1/realtime"
            val model = "gpt-4o-realtime-preview-2024-12-17"

            val client = OkHttpClient()
            val body = offerSdp.toByteArray().toRequestBody(contentTypeApplicationSdp.toMediaTypeOrNull())

            val request = Request.Builder()
                .url("$baseUrl?model=$model")
                .post(body)
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", contentTypeApplicationSdp)
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful) {
                    Log.e(TAG, "requestOpenAiRealtimeSdp: Error creating OpenAI realtime session: $responseBody")
                    throw IllegalStateException("Failed to get OpenAI SDP answer: ${response.code}")
                }
                return responseBody.orEmpty()
            }
        }
    }

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    val isConnected: Boolean get() = peerConnection != null

    fun log() {
        //...
    }

    fun connect(
        context: Context,
        sessionConfig: SessionConfig? = null
        ) {
        if (isConnected) {
            throw IllegalStateException("RealtimeAPI is already connected")
        }

        val initOptions = PeerConnectionFactory.InitializationOptions
            .builder(context)
            .setEnableInternalTracer(false)
            .setFieldTrials("WebRTC-LogLevel/Warning/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val pcOptions = PeerConnectionFactory.Options()
        val peerConnectionFactory = PeerConnectionFactory
            .builder()
            .setOptions(pcOptions)
            .createPeerConnectionFactory()
        // ICE/STUN is not needed to talk to *server* (only needed for peer-to-peer)
        val iceServers = listOf<PeerConnection.IceServer>()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)

        val tagPc = "PeerConnection"
        peerConnection = peerConnectionFactory.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(newState: PeerConnection.SignalingState) {
                    Log.d(tagPc, "onSignalingChange($newState)")
                }
                override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
                    Log.d(tagPc, "onIceConnectionChange($newState)")
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d(tagPc, "onIceConnectionReceivingChange($receiving)")
                }
                override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
                    Log.d(tagPc, "onIceGatheringChange($newState)")
                }
                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d(tagPc, "onIceCandidate($candidate)")
                    // Typically you'd send new ICE candidates to the remote peer
                }
                override fun onIceCandidatesRemoved(candidates: Array<IceCandidate>) {
                    Log.d(tagPc, "onIceCandidatesRemoved($candidates)")
                }
                override fun onAddStream(stream: MediaStream) {
                    Log.d(tagPc, "onAddStream(${stream.id})")
                }
                override fun onRemoveStream(stream: MediaStream) {
                    Log.d(tagPc, "onRemoveStream(${stream.id})")
                }
                override fun onDataChannel(dc: DataChannel) {
                    Log.d(tagPc, "onDataChannel(${dc.label()})")
                    // If the remote side creates a DataChannel, handle it here...
                }
                override fun onRenegotiationNeeded() {
                    Log.d(tagPc, "onRenegotiationNeeded()")
                }
                override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<MediaStream>) {
                    Log.d(tagPc, "onAddTrack(${receiver.track()?.kind()}, mediaStreams(${mediaStreams.size})=[...])")
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
        // especially bluetooth headset/earplugs
        // TODO: Is there a way to hear/echo the captured microphone audio?

        // Microphone; Requires RECORD_AUDIO permission
        val audioConstraints = MediaConstraints()
        val audioSource = peerConnectionFactory.createAudioSource(audioConstraints)
        val localAudioTrackMicrophone = peerConnectionFactory.createAudioTrack("MIC_TRACK", audioSource)
        peerConnection?.addTrack(localAudioTrackMicrophone)

        // Speaker
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        // deprecated...
        audioManager.isSpeakerphoneOn = true

        //
        //endregion Audio
        //

        val dataChannelInit = DataChannel.Init()
        val tagDc = "DataChannel"
        dataChannel = peerConnection?.createDataChannel("oai-events", dataChannelInit)
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {
                Log.d(tagDc, "onBufferedAmountChange($previousAmount)")
            }
            override fun onStateChange() {
                val dataChannel = dataChannel
                Log.d(tagDc, "onStateChange(): dataChannel.state()=${dataChannel?.state()}")
                val state = dataChannel?.state()
                if (state == DataChannel.State.OPEN) {
                    val sessionConfigJson = sessionConfig?.toJsonObject()
                    if (sessionConfigJson != null) {
                        val dataJsonString =
                            """
                            {
                                "type": "session.update",
                                "session": $sessionConfigJson
                            }
                            """.trimIndent()
                        val dataByteBuffer = ByteBuffer.wrap(dataJsonString.toByteArray())
                        dataChannel.send(DataChannel.Buffer(dataByteBuffer, false))
                    }
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val dataByteBuffer = buffer.data
                val bytes = ByteArray(dataByteBuffer.remaining())
                dataByteBuffer.get(bytes)
                if (buffer.binary) {
                    Log.d(tagDc, "onMessage: binary buffer(${bytes.size} bytes)=...")
                } else {
                    val messageText = String(bytes, Charsets.UTF_8)
                    Log.d(tagDc, "onMessage: text buffer(${bytes.size} bytes)=\"$messageText\"")
                }
            }
        })

        val offerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(tagPc, "createOffer onCreateSuccessLocal($desc)")
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        Log.d(tagPc, "localDescription onCreateSuccess($sdp)")
                    }
                    override fun onSetSuccess() {
                        Log.d(tagPc, "localDescription setSuccess()")
                        CoroutineScope(Dispatchers.IO).launch {
                            val answerSdp = requestOpenAiRealtimeSdp(
                                apiKey = apiKey,
                                offerSdp = desc.description
                            )
                            withContext(Dispatchers.Main) {
                                val answerDescription = SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    answerSdp
                                )
                                peerConnection?.setRemoteDescription(object : SdpObserver {
                                    override fun onCreateSuccess(sdp: SessionDescription?) {
                                        Log.d(tagPc, "remoteDescription onCreateSuccess($sdp)")
                                    }
                                    override fun onSetSuccess() {
                                        Log.d(tagPc, "remoteDescription setSuccess()")
                                    }
                                    override fun onCreateFailure(error: String?) {
                                        Log.e(tagPc, "remoteDescription onCreateFailure($error)")
                                    }
                                    override fun onSetFailure(error: String) {
                                        Log.e(tagPc, "remoteDescription onSetFailure($error)")
                                    }
                                }, answerDescription)
                            }
                        }
                    }
                    override fun onCreateFailure(error: String?) {
                        Log.e(tagPc, "localDescription onCreateFailure($error)")
                    }
                    override fun onSetFailure(error: String) {
                        Log.e(tagPc, "localDescription onSetFailure($error)")
                    }
                }, desc)
            }
            override fun onSetSuccess() {
                Log.d(tagPc, "createOffer setSuccess()")
            }
            override fun onCreateFailure(error: String) {
                Log.e(tagPc, "createOffer onCreateFailure($error)")
            }
            override fun onSetFailure(error: String?) {
                Log.e(tagPc, "createOffer onSetFailure($error)")
            }
        }, offerConstraints)
    }

    fun disconnect() {
        val peerConnection = this.peerConnection
        if (peerConnection != null) {
            peerConnection.close()
            this.peerConnection = null
        }
        val dataChannel = this.dataChannel
        if (dataChannel != null) {
            dataChannel.close()
            this.dataChannel = null
        }
    }

    //receive(eventName, event)

    fun send(eventName: String, data: JsonObject? = null) {
        if (!isConnected) {
            throw IllegalStateException("RealtimeAPI is not connected")
        }
        val event = buildJsonObject {
            put("event_id", RealtimeUtils.generateId("evt_"))
            put("type", eventName)
            data?.forEach {
                put(it.key, it.value)
            }
        }
        dataChannel?.send(
            DataChannel.Buffer(
                ByteBuffer.wrap(event.toString().toByteArray()),
                false
            )
        )
    }

    // TODO:(pv) send voice start segment
    // TODO:(pv) send voice stop segment
}
