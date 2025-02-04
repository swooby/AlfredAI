package com.swooby.alfredai.openai.realtime

import com.openai.models.RealtimeConversationItem
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
import com.swooby.alfredai.openai.realtime.RealtimeClient.ServerEventOutputAudioBufferAudioStarted
import com.swooby.alfredai.openai.realtime.RealtimeClient.ServerEventOutputAudioBufferAudioStopped

interface RealtimeProtocol {
    fun dataSendSessionUpdate(sessionConfig: RealtimeSessionCreateRequest): Boolean
    fun dataSendInputAudioBufferClear(): Boolean
    fun dataSendInputAudioBufferCommit(): Boolean
    fun dataSendResponseCreate(): Boolean
    fun dataSendResponseCancel(responseId: String? = null): Boolean
    fun dataSendConversationItemTruncate(itemId: String, audioEndMs: Int): Boolean
    fun dataSendConversationItemCreateMessageInputText(text: String, role: RealtimeConversationItem.Role = RealtimeConversationItem.Role.user): Boolean
    fun dataSendConversationItemCreateFunctionCallOutput(callId: String, output: String): Boolean
}

interface RealtimeProtocolListener {
    fun onServerEventConversationCreated(message: RealtimeServerEventConversationCreated)
    fun onServerEventConversationItemCreated(message: RealtimeServerEventConversationItemCreated)
    fun onServerEventConversationItemDeleted(message: RealtimeServerEventConversationItemDeleted)
    fun onServerEventConversationItemInputAudioTranscriptionCompleted(message: RealtimeServerEventConversationItemInputAudioTranscriptionCompleted)
    fun onServerEventConversationItemInputAudioTranscriptionFailed(message: RealtimeServerEventConversationItemInputAudioTranscriptionFailed)
    fun onServerEventConversationItemTruncated(message: RealtimeServerEventConversationItemTruncated)
    fun onServerEventError(message: RealtimeServerEventError)
    fun onServerEventInputAudioBufferCleared(message: RealtimeServerEventInputAudioBufferCleared)
    fun onServerEventInputAudioBufferCommitted(message: RealtimeServerEventInputAudioBufferCommitted)
    fun onServerEventInputAudioBufferSpeechStarted(message: RealtimeServerEventInputAudioBufferSpeechStarted)
    fun onServerEventInputAudioBufferSpeechStopped(message: RealtimeServerEventInputAudioBufferSpeechStopped)
    fun onServerEventOutputAudioBufferAudioStarted(message: ServerEventOutputAudioBufferAudioStarted)
    fun onServerEventOutputAudioBufferAudioStopped(message: ServerEventOutputAudioBufferAudioStopped)
    fun onServerEventRateLimitsUpdated(message: RealtimeServerEventRateLimitsUpdated)
    fun onServerEventResponseAudioDelta(message: RealtimeServerEventResponseAudioDelta)
    fun onServerEventResponseAudioDone(message: RealtimeServerEventResponseAudioDone)
    fun onServerEventResponseAudioTranscriptDelta(message: RealtimeServerEventResponseAudioTranscriptDelta)
    fun onServerEventResponseAudioTranscriptDone(message: RealtimeServerEventResponseAudioTranscriptDone)
    fun onServerEventResponseContentPartAdded(message: RealtimeServerEventResponseContentPartAdded)
    fun onServerEventResponseContentPartDone(message: RealtimeServerEventResponseContentPartDone)
    fun onServerEventResponseCreated(message: RealtimeServerEventResponseCreated)
    fun onServerEventResponseDone(message: RealtimeServerEventResponseDone)
    fun onServerEventResponseFunctionCallArgumentsDelta(message: RealtimeServerEventResponseFunctionCallArgumentsDelta)
    fun onServerEventResponseFunctionCallArgumentsDone(message: RealtimeServerEventResponseFunctionCallArgumentsDone)
    fun onServerEventResponseOutputItemAdded(message: RealtimeServerEventResponseOutputItemAdded)
    fun onServerEventResponseOutputItemDone(message: RealtimeServerEventResponseOutputItemDone)
    fun onServerEventResponseTextDelta(message: RealtimeServerEventResponseTextDelta)
    fun onServerEventResponseTextDone(message: RealtimeServerEventResponseTextDone)
    fun onServerEventSessionCreated(message: RealtimeServerEventSessionCreated)
    fun onServerEventSessionUpdated(message: RealtimeServerEventSessionUpdated)
    fun onServerEventSessionExpired(message: RealtimeServerEventError)
}