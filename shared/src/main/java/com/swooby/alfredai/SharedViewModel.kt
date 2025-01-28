package com.swooby.alfredai

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.swooby.alfredai.Utils.quote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class SharedViewModel(application: Application) :
    AndroidViewModel(application) {

    enum class PttState {
        Idle,
        Pressed
    }

    protected abstract val TAG: String
    protected abstract val remoteTypeName: String
    protected abstract val remoteCapabilityName: String

    private fun setRemoteAppNodeId(nodeId: String?) {
        Log.i(TAG, "setRemoteAppNodeId(nodeId=${quote(nodeId)})")
        _remoteAppNodeId.value = nodeId
    }
    private var _remoteAppNodeId = MutableStateFlow<String?>(null)
    val remoteAppNodeId = _remoteAppNodeId.asStateFlow() // public readonly

    protected fun setPushToTalkState(state: PttState) {
        Log.i(TAG, "setPushToTalkState(state=$state)")
        _pushToTalkState.value = state
    }
    private var _pushToTalkState = MutableStateFlow(PttState.Idle)
    val pushToTalkState = _pushToTalkState.asStateFlow() // public readonly

    private val capabilityClient by lazy { Wearable.getCapabilityClient(application) }
    private val messageClient by lazy { Wearable.getMessageClient(application) }
    private val messageClientListener = MessageClient.OnMessageReceivedListener {
        onMessageClientMessageReceived(it)
    }

    open fun init() {
        Log.d(TAG, "init()")
        messageClient.addListener(messageClientListener)
        searchForRemoteAppNode()
    }

    open fun close() {
        Log.d(TAG, "close()")
        messageClient.removeListener(messageClientListener)
    }

    private fun pickFirstNearbyNode(nodes: Set<Node>): Node? {
        return nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
    }

    private fun searchForRemoteAppNode() {
        val capabilityClientTask = capabilityClient.getCapability(
            remoteCapabilityName,
            CapabilityClient.FILTER_REACHABLE
        )
        capabilityClientTask.addOnCompleteListener {
            if (it.isSuccessful) {
                //Log.d(TAG, "Capability request succeeded");
                val nodeId = pickFirstNearbyNode(it.result.nodes)?.id
                if (nodeId == null) {
                    Log.d(TAG, "searchForRemoteAppNode: Detected no $remoteTypeName app node")
                } else {
                    Log.i(TAG, "searchForRemoteAppNode: Detected $remoteTypeName app nodeId=${quote(nodeId)}; Sending ping...")
                    sendPingCommand(nodeId)
                }
            } else {
                Log.e(TAG, "searchForRemoteAppNode: Capability request failed to return any results");
            }
        }
    }

    private fun sendMessageToNode(nodeId: String, path: String, data: String): Task<Int> {
        return sendMessageToNode(nodeId, path, data.toByteArray())
    }

    private fun sendMessageToNode(nodeId: String, path: String, data: ByteArray? = null): Task<Int> {
        return messageClient
            .sendMessage(nodeId, path, data)
            .addOnFailureListener { e ->
                Log.e(TAG, "sendMessageToNode: Message failed", e)
                setRemoteAppNodeId(null)
            }
    }

    private fun onMessageClientMessageReceived(messageEvent: MessageEvent) {
        when (val path = messageEvent.path) {
            "/ping" -> handlePingCommand(messageEvent)
            "/pong" -> handlePongCommand(messageEvent)
            "/pushToTalk" -> handlePushToTalkCommand(messageEvent)
            else -> Log.d(TAG, "onMessageClientMessageReceived: Unhandled messageEvent.path=${quote(path)}")
        }
    }

    private fun sendPingCommand(nodeId: String): Task<Int> {
        return sendMessageToNode(nodeId, "/ping")
    }

    private fun handlePingCommand(messageEvent: MessageEvent) {
        val nodeId = messageEvent.sourceNodeId
        Log.i(TAG, "handlePingCommand: Ping request from $remoteTypeName app nodeId=${quote(nodeId)}; Responding pong...")
        setRemoteAppNodeId(nodeId)
        sendPongCommand(nodeId)
    }

    private fun sendPongCommand(nodeId: String): Task<Int> {
        return sendMessageToNode(nodeId, "/pong")
    }

    private fun handlePongCommand(messageEvent: MessageEvent) {
        val nodeId = messageEvent.sourceNodeId
        Log.i(TAG, "handlePongCommand: Pong response from $remoteTypeName app nodeId=${quote(nodeId)}")
        setRemoteAppNodeId(nodeId)
    }

    protected fun sendPushToTalkCommand(nodeId: String, on: Boolean): Task<Int> {
        val path = "/pushToTalk"
        val payload = (if (on) "on" else "off")
        return sendMessageToNode(nodeId, path, payload)
    }

    private fun handlePushToTalkCommand(messageEvent: MessageEvent) {
        val nodeId = messageEvent.sourceNodeId
        val payloadString = String(messageEvent.data)
        Log.i(TAG, "handlePushToTalkCommand: PushToTalk request from $remoteTypeName app nodeId=${quote(nodeId)}! payloadString=${quote(payloadString)}")
        setRemoteAppNodeId(nodeId)
        when (payloadString) {
            "on" -> pushToTalk(true, sourceNodeId = nodeId)
            "off" -> pushToTalk(false, sourceNodeId = nodeId)
        }
    }

    abstract fun pushToTalk(on: Boolean, sourceNodeId: String? = null)

    protected open fun pushToTalkLocal(on: Boolean) {
        Log.i(TAG, "pushToTalkLocal(on=$on)")
    }
}