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

import com.openai.models.RunObject

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Occurs when a [run](/docs/api-reference/runs/object) moves to a `requires_action` status.
 *
 * @param event 
 * @param `data` 
 */


data class RunStreamEventOneOf3 (

    @Json(name = "event")
    val event: RunStreamEventOneOf3.Event,

    @Json(name = "data")
    val `data`: RunObject

) {

    /**
     * 
     *
     * Values: threadPeriodRunPeriodRequires_action
     */
    @JsonClass(generateAdapter = false)
    enum class Event(val value: kotlin.String) {
        @Json(name = "thread.run.requires_action") threadPeriodRunPeriodRequires_action("thread.run.requires_action");
    }

}

