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

import com.openai.models.AuditLogProjectCreatedData

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The details for events with this `type`.
 *
 * @param id The project ID.
 * @param `data` 
 */


data class AuditLogProjectCreated (

    /* The project ID. */
    @Json(name = "id")
    val id: kotlin.String? = null,

    @Json(name = "data")
    val `data`: AuditLogProjectCreatedData? = null

) {


}

