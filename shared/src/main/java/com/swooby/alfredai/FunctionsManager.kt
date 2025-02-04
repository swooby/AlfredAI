package com.swooby.alfredai

import android.content.Context
import android.util.Log
import com.openai.models.RealtimeSessionTools
import com.swooby.alfredai.Utils.quote
import com.swooby.alfredai.Utils.showToast
import org.json.JSONObject

class FunctionsManager(private val applicationContext: Context) {
    companion object {
        private val TAG = "FunctionsManager"

        private fun createFunctionInfo(
            enabled: Boolean = true,
            function: (JSONObject) -> String,
            name: String,
            description: String,
            parameters: Map<String, Any>,
            ): FunctionInfo {
            // OpenAI error response:
            // `Expected a string that matches the pattern '^[a-zA-Z0-9_-]+$'`
            require(name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
                "`name` must match the pattern '^[a-zA-Z0-9_-]+$' (per OpenAI error response)"
            }
            return FunctionInfo(
                enabled = enabled,
                function = function,
                tool = RealtimeSessionTools(
                    type = RealtimeSessionTools.Type.function,
                    name = name,
                    description = description,
                    parameters = parameters
                )
            )
        }

        private fun addFunction(
            mapFunctions: MutableMap<String, FunctionInfo>,
            functionInfo: FunctionInfo
        ) {
            mapFunctions[functionInfo.tool.name!!] = functionInfo
        }
    }

    data class FunctionInfo(
        var enabled: Boolean = true,
        val function: (JSONObject) -> String,
        val tool: RealtimeSessionTools
    )

    private val functionsMap = mutableMapOf<String, FunctionInfo>().apply {
        // TODO: "task" or "tasks"?
        addFunction(
            this, createFunctionInfo(
                function = ::functionTaskCreate,
                name = "task_create",
                description = "task/reminder create",
                parameters = mapOf(
                    "type" to "object",
                    "properties" to mapOf(
                        "name" to mapOf(
                            "type" to "string",
                            "description" to "Required name/description of the task/reminder"
                        ),
                        "time" to mapOf(
                            "type" to "string",
                            "description" to "Optional date/time associated with the task/reminder"
                        )
                    ),
                    "required" to listOf("name")
                )
            )
        )
        //...
        // TODO: task_get (empty returns all)
        // TODO: task_update
        // TODO: task_delete
    }.toMap()

    fun getFunctions(includeDisabled: Boolean = false): List<RealtimeSessionTools> {
        return functionsMap.values.filter { it.enabled }.map { it.tool }
    }

    fun run(name: String, arguments: JSONObject): String {
        Log.d(TAG, "+run: name=${quote(name)}, arguments=${quote(arguments)}")
        var output = ""
        val functionInfo = functionsMap[name]
        if (functionInfo == null) {
            Log.e(TAG, "run: Unknown function name: $name")
            output = "" // error/unknown/etc?
        } else {
            // TODO: Function start/stop listener
            output = functionInfo.function(arguments)
        }
        Log.d(TAG, "-run: output=$output")
        return output
    }

    private fun functionTaskCreate(arguments: JSONObject): String {
        Log.d(TAG, "+functionTaskCreate: arguments=${quote(arguments)}")
        val name = arguments.optString("name")
        val time = arguments.optString("time")
        val description = quote(name) + if (time.isEmpty()) "" else " at ${quote(time)}"
        showToast(
            applicationContext,
            "TODO: Create cloud task $description",
            forceInvokeOnMain = true
        )
        val output = "success"
        Log.d(TAG, "-functionTaskCreate: output=${quote(output)}")
        return output
    }
}
