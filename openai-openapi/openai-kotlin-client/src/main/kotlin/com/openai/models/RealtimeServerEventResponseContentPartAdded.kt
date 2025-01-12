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

import com.openai.models.RealtimeServerEventResponseContentPartAddedPart

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Returned when a new content part is added to an assistant message item during response generation. 
 *
 * @param eventId The unique ID of the server event.
 * @param type The event type, must be `response.content_part.added`.
 * @param responseId The ID of the response.
 * @param itemId The ID of the item to which the content part was added.
 * @param outputIndex The index of the output item in the response.
 * @param contentIndex The index of the content part in the item's content array.
 * @param part 
 */


data class RealtimeServerEventResponseContentPartAdded (

    /* The unique ID of the server event. */
    @Json(name = "event_id")
    val eventId: kotlin.String,

    /* The event type, must be `response.content_part.added`. */
    @Json(name = "type")
    val type: RealtimeServerEventResponseContentPartAdded.Type,

    /* The ID of the response. */
    @Json(name = "response_id")
    val responseId: kotlin.String,

    /* The ID of the item to which the content part was added. */
    @Json(name = "item_id")
    val itemId: kotlin.String,

    /* The index of the output item in the response. */
    @Json(name = "output_index")
    val outputIndex: kotlin.Int,

    /* The index of the content part in the item's content array. */
    @Json(name = "content_index")
    val contentIndex: kotlin.Int,

    @Json(name = "part")
    val part: RealtimeServerEventResponseContentPartAddedPart

) {

    /**
     * The event type, must be `response.content_part.added`.
     *
     * Values: responsePeriodContent_partPeriodAdded
     */
    @JsonClass(generateAdapter = false)
    enum class Type(val value: kotlin.String) {
        @Json(name = "response.content_part.added") responsePeriodContent_partPeriodAdded("response.content_part.added");
    }

}

