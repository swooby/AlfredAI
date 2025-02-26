package com.swooby.openai.realtime

import com.openai.infrastructure.ApiClient
import com.openai.infrastructure.Serializer

enum class TransportType {
    WEBRTC,
}

/**
 * Callbacks from the Transport layer.
 * These mirror some of what your RealtimeClientListener does now,
 * but at a lower level (just raw binary/text messages + basic “connected”/“error”).
 */
interface RealtimeTransportListener {
    /**
     * Called when the client is starting a connection.
     */
    fun onConnecting()

    /**
     * Called when the client has successfully established a connection.
     */
    fun onConnected()

    /**
     * Called when the client has disconnected.
     */
    fun onDisconnected()

    /**
     * Called when the client encounters an error.
     */
    fun onError(error: Exception)

    /**
     *
     */
    fun onDataChannelOpened()

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
}

interface RealtimeTransport {
    val transportType: TransportType

    val isConnectingOrConnected: Boolean
    val isConnected: Boolean

    /** Add or remove transport-level listeners (or expose callbacks). */
    fun addListener(listener: RealtimeTransportListener)
    fun removeListener(listener: RealtimeTransportListener)

    /** Connects and returns an ephemeral key or null if failed. */
    fun connect(): String?

    /** Disconnect the transport. */
    fun disconnect()

    /** Essentially unmute or mute the speaker */
    fun setLocalAudioTrackSpeakerEnabled(enabled: Boolean)

    /** Essentially unmute or mute the microphone */
    fun setLocalAudioTrackMicrophoneEnabled(enabled: Boolean)

    /** Send text data. Return true if successful. */
    fun dataSendText(message: String): Boolean

    /** Send binary data. Return true if successful. */
    fun dataSendBinary(data: ByteArray): Boolean
}

inline fun <reified T> RealtimeTransport.dataSend(content: T, mediaType: String? = ApiClient.JsonMediaType): Boolean {
    when {
        mediaType == null || (mediaType.startsWith("application/") && mediaType.endsWith("json")) ->
            return dataSendText(Serializer.serialize<T>(content))
        else ->
            throw UnsupportedOperationException("send currently only supports JSON body.")
    }
}
