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
 * An optional field that will only be present when you set `stream_options: {\"include_usage\": true}` in your request. When present, it contains a null value except for the last chunk which contains the token usage statistics for the entire request. 
 *
 * @param completionTokens Number of tokens in the generated completion.
 * @param promptTokens Number of tokens in the prompt.
 * @param totalTokens Total number of tokens used in the request (prompt + completion).
 */


data class CreateChatCompletionStreamResponseUsage (

    /* Number of tokens in the generated completion. */
    @Json(name = "completion_tokens")
    val completionTokens: kotlin.Int,

    /* Number of tokens in the prompt. */
    @Json(name = "prompt_tokens")
    val promptTokens: kotlin.Int,

    /* Total number of tokens used in the request (prompt + completion). */
    @Json(name = "total_tokens")
    val totalTokens: kotlin.Int

) {


}

