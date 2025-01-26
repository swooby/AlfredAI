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

class WearAppViewModel(private val application: Application) :
    AndroidViewModel(application)
{
    companion object {
        private const val TAG = "WearAppViewModel"
    }

    private var _phoneAppNodeId = MutableStateFlow<String?>(null)
    val phoneAppNodeId = _phoneAppNodeId.asStateFlow()

    private val capabilityClient by lazy { Wearable.getCapabilityClient(application) }

    private val messageClient = Wearable.getMessageClient(application)
    private val messageClientListener = MessageClient.OnMessageReceivedListener {
        onMessageClientMessageReceived(it)
    }

    init {
        messageClient.addListener(messageClientListener)

        val capabilityClientTask = capabilityClient.getCapability(
            "verify_remote_example_mobile_app",
            CapabilityClient.FILTER_REACHABLE
        )
        capabilityClientTask.addOnCompleteListener {
            if (it.isSuccessful) {
                Log.d(TAG, "Capability request succeeded");
                _phoneAppNodeId.value = pickFirstNearbyNode(it.result.nodes)?.id
                if (phoneAppNodeId.value == null) {
                    Log.d(TAG, "Detected no phone app node")
                } else {
                    Log.i(TAG, "Detected phoneAppNodeId=${quote(phoneAppNodeId.value)}")
                }
            } else {
                Log.e(TAG, "Capability request failed to return any results");
            }
        }
    }

    fun close() {
        messageClient.removeListener(messageClientListener)
    }

    private fun pickFirstNearbyNode(nodes: Set<Node>): Node? {
        return nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
    }

    private fun onMessageClientMessageReceived(messageEvent: MessageEvent) {
        val path = messageEvent.path
        val payload = String(messageEvent.data, Charsets.UTF_8)
        // Handle incoming message from watch/phone
        Log.d(TAG, "onMessageReceived: $path, $payload")
    }

    private fun sendMessageToNode(nodeId: String, path: String, data: String): Task<Int> {
        return sendMessageToNode(nodeId, path, data.toByteArray())
    }

    private fun sendMessageToNode(nodeId: String, path: String, data: ByteArray): Task<Int> {
        return messageClient
            .sendMessage(nodeId, path, data)
            .addOnSuccessListener {
                Log.d(TAG, "sendMessageToNode: Message sent successfully to $nodeId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "sendMessageToNode: Message failed.", e)
            }
    }

    fun sendPushToTalkCommand(nodeId: String, on: Boolean): Task<Int> {
        val path = "/pushToTalk"
        val payload = (if (on) "on" else "off")
        return sendMessageToNode(nodeId, path, payload)
    }
}
