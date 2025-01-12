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
 * The default strategy. This strategy currently uses a `max_chunk_size_tokens` of `800` and `chunk_overlap_tokens` of `400`.
 *
 * @param type Always `auto`.
 */


data class AutoChunkingStrategyRequestParam (

    /* Always `auto`. */
    @Json(name = "type")
    val type: AutoChunkingStrategyRequestParam.Type

) {

    /**
     * Always `auto`.
     *
     * Values: auto
     */
    @JsonClass(generateAdapter = false)
    enum class Type(val value: kotlin.String) {
        @Json(name = "auto") auto("auto");
    }

}

