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

import com.openai.models.AssistantObjectToolsInner
import com.openai.models.AssistantsApiResponseFormatOption
import com.openai.models.ModifyAssistantRequestToolResources

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * 
 *
 * @param model 
 * @param name The name of the assistant. The maximum length is 256 characters. 
 * @param description The description of the assistant. The maximum length is 512 characters. 
 * @param instructions The system instructions that the assistant uses. The maximum length is 256,000 characters. 
 * @param tools A list of tool enabled on the assistant. There can be a maximum of 128 tools per assistant. Tools can be of types `code_interpreter`, `file_search`, or `function`. 
 * @param toolResources 
 * @param metadata Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object in a structured format. Keys can be a maximum of 64 characters long and values can be a maximum of 512 characters long. 
 * @param temperature What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic. 
 * @param topP An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered.  We generally recommend altering this or temperature but not both. 
 * @param responseFormat 
 */


data class ModifyAssistantRequest (

    @Json(name = "model")
    val model: kotlin.String? = null,

    /* The name of the assistant. The maximum length is 256 characters.  */
    @Json(name = "name")
    val name: kotlin.String? = null,

    /* The description of the assistant. The maximum length is 512 characters.  */
    @Json(name = "description")
    val description: kotlin.String? = null,

    /* The system instructions that the assistant uses. The maximum length is 256,000 characters.  */
    @Json(name = "instructions")
    val instructions: kotlin.String? = null,

    /* A list of tool enabled on the assistant. There can be a maximum of 128 tools per assistant. Tools can be of types `code_interpreter`, `file_search`, or `function`.  */
    @Json(name = "tools")
    val tools: kotlin.collections.List<AssistantObjectToolsInner>? = arrayListOf(),

    @Json(name = "tool_resources")
    val toolResources: ModifyAssistantRequestToolResources? = null,

    /* Set of 16 key-value pairs that can be attached to an object. This can be useful for storing additional information about the object in a structured format. Keys can be a maximum of 64 characters long and values can be a maximum of 512 characters long.  */
    @Json(name = "metadata")
    val metadata: kotlin.Any? = null,

    /* What sampling temperature to use, between 0 and 2. Higher values like 0.8 will make the output more random, while lower values like 0.2 will make it more focused and deterministic.  */
    @Json(name = "temperature")
    val temperature: java.math.BigDecimal? = java.math.BigDecimal("1"),

    /* An alternative to sampling with temperature, called nucleus sampling, where the model considers the results of the tokens with top_p probability mass. So 0.1 means only the tokens comprising the top 10% probability mass are considered.  We generally recommend altering this or temperature but not both.  */
    @Json(name = "top_p")
    val topP: java.math.BigDecimal? = java.math.BigDecimal("1"),

    @Json(name = "response_format")
    val responseFormat: AssistantsApiResponseFormatOption? = null

) {


}

