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

import com.openai.models.MessageDeltaContentImageFileObjectImageFile

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * References an image [File](/docs/api-reference/files) in the content of a message.
 *
 * @param index The index of the content part in the message.
 * @param type Always `image_file`.
 * @param imageFile 
 */


data class MessageDeltaContentImageFileObject (

    /* The index of the content part in the message. */
    @Json(name = "index")
    val index: kotlin.Int,

    /* Always `image_file`. */
    @Json(name = "type")
    val type: MessageDeltaContentImageFileObject.Type,

    @Json(name = "image_file")
    val imageFile: MessageDeltaContentImageFileObjectImageFile? = null

) {

    /**
     * Always `image_file`.
     *
     * Values: image_file
     */
    @JsonClass(generateAdapter = false)
    enum class Type(val value: kotlin.String) {
        @Json(name = "image_file") image_file("image_file");
    }

}

