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
 * Send this event to cancel an in-progress response. The server will respond  with a `response.cancelled` event or an error if there is no response to  cancel. 
 *
 * @param type The event type, must be `response.cancel`.
 * @param eventId Optional client-generated ID used to identify this event.
 * @param responseId A specific response ID to cancel - if not provided, will cancel an  in-progress response in the default conversation. 
 */


data class RealtimeClientEventResponseCancel (

    /* The event type, must be `response.cancel`. */
    @Json(name = "type")
    val type: RealtimeClientEventResponseCancel.Type,

    /* Optional client-generated ID used to identify this event. */
    @Json(name = "event_id")
    val eventId: kotlin.String? = null,

    /* A specific response ID to cancel - if not provided, will cancel an  in-progress response in the default conversation.  */
    @Json(name = "response_id")
    val responseId: kotlin.String? = null

) {

    /**
     * The event type, must be `response.cancel`.
     *
     * Values: responsePeriodCancel
     */
    @JsonClass(generateAdapter = false)
    enum class Type(val value: kotlin.String) {
        @Json(name = "response.cancel") responsePeriodCancel("response.cancel");
    }

}

