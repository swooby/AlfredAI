package com.swooby.openai.realtime

import android.content.Context
import android.os.SystemClock
import com.openai.apis.RealtimeApi
import com.openai.infrastructure.ApiClient
import com.openai.infrastructure.ClientException
import com.openai.models.RealtimeSessionCreateRequest
import com.swooby.Utils.quote
import com.swooby.Utils.redact
import com.swooby.openai.BuildConfig
import okhttp3.OkHttpClient
import java.net.UnknownHostException

abstract class RealtimeTransportBase(
    private var dangerousApiKey: String,
    private var sessionConfig: RealtimeSessionCreateRequest,
    protected val realtime: RealtimeApi,
) : RealtimeTransport {
    companion object {
        fun create(
            transportType: TransportType,
            applicationContext: Context,
            dangerousApiKey: String,
            sessionConfig: RealtimeSessionCreateRequest,
            httpClient: OkHttpClient,
            realtime: RealtimeApi,
            debug: Boolean = false): RealtimeTransportBase {
            return when (transportType) {
                TransportType.WEBRTC ->
                    RealtimeTransportWebRTC(
                        applicationContext,
                        dangerousApiKey,
                        sessionConfig,
                        realtime,
                        debug,
                    )
            }
        }

        @Suppress(
            "SimplifyBooleanWithConstants",
            "KotlinConstantConditions",
        )
        private val debugInduceMysteriousUnknownHostException = BuildConfig.DEBUG && false

        private const val MYSTERIOUS_UNKNOWN_HOST_EXCEPTION_MESSAGE = "Unable to resolve host \"api.openai.com\": No address associated with hostname"
    }

    protected open val log = RealtimeLog(RealtimeTransportBase::class)

    protected val listeners = mutableListOf<RealtimeTransportListener>()

    override fun addListener(listener: RealtimeTransportListener) {
        listeners.add(listener)
    }

    override fun removeListener(listener: RealtimeTransportListener) {
        listeners.remove(listener)
    }

    private var _isConnectingOrConnected: Boolean = false
    override var isConnectingOrConnected: Boolean
        get() = _isConnectingOrConnected
        protected set(value) {
            if (value != _isConnectingOrConnected) {
                _isConnectingOrConnected = value
                if (value) {
                    notifyConnecting()
                } else {
                    isConnected = false
                    notifyDisconnected()
                }
            }
        }

    private var _isConnected: Boolean = false
    override var isConnected: Boolean
        get() = _isConnected
        internal set(value) {
            if (value != _isConnected) {
                _isConnected = value
                if (value) {
                    notifyConnected()
                } else {
                    isConnectingOrConnected = false
                    notifyDisconnected()
                }
            }
        }

    private var connectStartMillis: Long = 0L
    private var connectIntervalMillis: Long = 0L

    /**
     * @return true if still isConnectingOrConnected, false if canceled
     */
    protected fun verifyStillConnectingOrConnected(stageName: String): Boolean {
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

    override fun connect(): String? {
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
            val realtimeSessionCreateResponse = try {
                if (debugInduceMysteriousUnknownHostException) {
                    throw UnknownHostException(MYSTERIOUS_UNKNOWN_HOST_EXCEPTION_MESSAGE)
                }
                realtime.createRealtimeSession(sessionConfig)
            } catch (exception: Exception) {
                log.e("connect: exception=$exception")
                disconnect(exception)
                null
            }
            ephemeralApiKey = realtimeSessionCreateResponse?.clientSecret?.value
            log.d("connect: ephemeralApiKey=${quote(redact(ephemeralApiKey, dangerousNullOK = true))}")
            if (ephemeralApiKey == null) {
                disconnect(ClientException("No Ephemeral API Key In Response"))
                return null
            }

            if (!verifyStillConnectingOrConnected("createRealtimeSession")) {
                ephemeralApiKey = null
                return null
            }

            // Set accessToken to the response's safer (1 minute TTL) ephemeralApiKey;
            // clear it after requestOpenAiRealtimeSdp (success or fail)
            ApiClient.accessToken = ephemeralApiKey

            ephemeralApiKey = connectInternal(ephemeralApiKey)

            if (!verifyStillConnectingOrConnected("createOffer")) {
                ephemeralApiKey = null
                return null
            }
        } finally {
            log.d("-connect(); ephemeralApiKey=${quote(redact(ephemeralApiKey, dangerousNullOK = true))}")
        }
        return ephemeralApiKey
    }

    protected abstract fun connectInternal(ephemeralApiKey: String): String?

    override fun disconnect(error: Exception?) {
        log.d("+disconnect($error)")
        ApiClient.accessToken = null
        if (error != null) {
            notifyError(error)
        }
        disconnectInternal()
        log.d("-disconnect($error)")
    }

    protected abstract fun disconnectInternal()

    private fun notifyConnecting() {
        listeners.forEach { it.onConnecting() }
    }

    private fun notifyConnected() {
        listeners.forEach { it.onConnected() } }

    private fun notifyError(e: Exception) {
        listeners.forEach { it.onError(e) }
    }

    private fun notifyDisconnected() {
        listeners.forEach { it.onDisconnected() }
    }
}