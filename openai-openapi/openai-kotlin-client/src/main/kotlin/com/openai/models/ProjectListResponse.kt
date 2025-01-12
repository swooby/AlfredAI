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

import com.openai.models.Project

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param `object` 
 * @param `data` 
 * @param firstId 
 * @param lastId 
 * @param hasMore 
 */


data class ProjectListResponse (

    @Json(name = "object")
    val `object`: ProjectListResponse.`Object`,

    @Json(name = "data")
    val `data`: kotlin.collections.List<Project>,

    @Json(name = "first_id")
    val firstId: kotlin.String,

    @Json(name = "last_id")
    val lastId: kotlin.String,

    @Json(name = "has_more")
    val hasMore: kotlin.Boolean

) {

    /**
     * 
     *
     * Values: list
     */
    @JsonClass(generateAdapter = false)
    enum class `Object`(val value: kotlin.String) {
        @Json(name = "list") list("list");
    }

}

