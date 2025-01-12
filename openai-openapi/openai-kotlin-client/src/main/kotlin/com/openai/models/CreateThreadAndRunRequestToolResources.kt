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

import com.openai.models.AssistantObjectToolResourcesFileSearch
import com.openai.models.CreateAssistantRequestToolResourcesCodeInterpreter

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * A set of resources that are used by the assistant's tools. The resources are specific to the type of tool. For example, the `code_interpreter` tool requires a list of file IDs, while the `file_search` tool requires a list of vector store IDs. 
 *
 * @param codeInterpreter 
 * @param fileSearch 
 */


data class CreateThreadAndRunRequestToolResources (

    @Json(name = "code_interpreter")
    val codeInterpreter: CreateAssistantRequestToolResourcesCodeInterpreter? = null,

    @Json(name = "file_search")
    val fileSearch: AssistantObjectToolResourcesFileSearch? = null

) {


}

