/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport"
)

package com.openai.models


import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Sent by the server when in `server_vad` mode to indicate that speech has been  detected in the audio buffer. This can happen any time audio is added to the  buffer (unless speech is already detected). The client may want to use this  event to interrupt audio playback or provide visual feedback to the user.   The client should expect to receive a `input_audio_buffer.speech_stopped` event  when speech stops. The `item_id` property is the ID of the user message item  that will be created when speech stops and will also be included in the  `input_audio_buffer.speech_stopped` event (unless the client manually commits  the audio buffer during VAD activation). 
 *
 * @param eventId The unique ID of the server event.
 * @param type The event type, must be `input_audio_buffer.speech_started`.
 * @param audioStartMs Milliseconds from the start of all audio written to the buffer during the  session when speech was first detected. This will correspond to the  beginning of audio sent to the model, and thus includes the  `prefix_padding_ms` configured in the Session. 
 * @param itemId The ID of the user message item that will be created when speech stops. 
 */


data class RealtimeServerEventInputAudioBufferSpeechStarted (

    /* The unique ID of the server event. */
    @Json(name = "event_id")
    val eventId: kotlin.String,

    /* The event type, must be `input_audio_buffer.speech_started`. */
    @Json(name = "type")
    val type: RealtimeServerEventInputAudioBufferSpeechStarted.Type,

    /* Milliseconds from the start of all audio written to the buffer during the  session when speech was first detected. This will correspond to the  beginning of audio sent to the model, and thus includes the  `prefix_padding_ms` configured in the Session.  */
    @Json(name = "audio_start_ms")
    val audioStartMs: kotlin.Int,

    /* The ID of the user message item that will be created when speech stops.  */
    @Json(name = "item_id")
    val itemId: kotlin.String

) {

    /**
     * The event type, must be `input_audio_buffer.speech_started`.
     *
     * Values: input_audio_bufferPeriodSpeech_started
     */
    @JsonClass(generateAdapter = false)
    enum class Type(val value: kotlin.String) {
        @Json(name = "input_audio_buffer.speech_started") input_audio_bufferPeriodSpeech_started("input_audio_buffer.speech_started");
    }

}

