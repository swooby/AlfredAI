package com.swooby.alfredai.openai.realtime

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

object RealtimeUtils {
    fun generateId(prefix: String, length: Int = 21): String {
        val chars = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
        require(prefix.length <= length) {
            "Prefix length cannot exceed the total length."
        }
        val neededLength = length - prefix.length
        val randomStr = (1..neededLength)
            .map {
                chars.random()
            }
            .joinToString("")
        return prefix + randomStr
    }

//    fun mergeAll(vararg objects: JsonObject): JsonObject {
//        return buildJsonObject {
//            objects.forEach { jsonObj ->
//                jsonObj.forEach { (key, value) ->
//                    put(key, value)
//                }
//            }
//        }
//    }
}