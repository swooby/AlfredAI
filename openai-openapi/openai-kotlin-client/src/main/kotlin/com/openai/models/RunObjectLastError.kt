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
 * The last error associated with this run. Will be `null` if there are no errors.
 *
 * @param code One of `server_error`, `rate_limit_exceeded`, or `invalid_prompt`.
 * @param message A human-readable description of the error.
 */


data class RunObjectLastError (

    /* One of `server_error`, `rate_limit_exceeded`, or `invalid_prompt`. */
    @Json(name = "code")
    val code: RunObjectLastError.Code,

    /* A human-readable description of the error. */
    @Json(name = "message")
    val message: kotlin.String

) {

    /**
     * One of `server_error`, `rate_limit_exceeded`, or `invalid_prompt`.
     *
     * Values: server_error,rate_limit_exceeded,invalid_prompt
     */
    @JsonClass(generateAdapter = false)
    enum class Code(val value: kotlin.String) {
        @Json(name = "server_error") server_error("server_error"),
        @Json(name = "rate_limit_exceeded") rate_limit_exceeded("rate_limit_exceeded"),
        @Json(name = "invalid_prompt") invalid_prompt("invalid_prompt");
    }

}

