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
 * The refusal content generated by the assistant.
 *
 * @param type Always `refusal`.
 * @param refusal 
 */


data class MessageContentRefusalObject (

    /* Always `refusal`. */
    @Json(name = "type")
    val type: MessageContentRefusalObject.Type,

    @Json(name = "refusal")
    val refusal: kotlin.String

) {

    /**
     * Always `refusal`.
     *
     * Values: refusal
     */
    @JsonClass(generateAdapter = false)
    enum class Type(val value: kotlin.String) {
        @Json(name = "refusal") refusal("refusal");
    }

}

