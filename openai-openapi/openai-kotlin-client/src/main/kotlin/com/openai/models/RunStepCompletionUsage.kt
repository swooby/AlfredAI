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
 * Usage statistics related to the run step. This value will be `null` while the run step's status is `in_progress`.
 *
 * @param completionTokens Number of completion tokens used over the course of the run step.
 * @param promptTokens Number of prompt tokens used over the course of the run step.
 * @param totalTokens Total number of tokens used (prompt + completion).
 */


data class RunStepCompletionUsage (

    /* Number of completion tokens used over the course of the run step. */
    @Json(name = "completion_tokens")
    val completionTokens: kotlin.Int,

    /* Number of prompt tokens used over the course of the run step. */
    @Json(name = "prompt_tokens")
    val promptTokens: kotlin.Int,

    /* Total number of tokens used (prompt + completion). */
    @Json(name = "total_tokens")
    val totalTokens: kotlin.Int

) {


}

