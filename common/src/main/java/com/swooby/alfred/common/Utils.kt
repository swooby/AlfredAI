package com.swooby.alfred.common

import android.util.JsonReader
import android.util.Log
import java.io.IOException
import java.io.StringReader
import kotlin.reflect.KClass
import kotlin.reflect.full.companionObject
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.staticProperties

object Utils {
    private val TAG = Utils::class.java.simpleName

    fun getShortClassName(value: Any?): String {
        return when (value) {
            is KClass<*> -> value.simpleName ?: "null"
            else -> value?.javaClass?.simpleName ?: "null"
        }
    }

    fun quote(value: Any?, typeOnly: Boolean = false): String {
        if (value == null) {
            return "null"
        }

        if (typeOnly) {
            return getShortClassName(value)
        }

        if (value is String) {
            return "\"$value\""
        }

        if (value is CharSequence) {
            return "\"$value\""
        }

        return value.toString()
    }

    fun extractValue(key: String, jsonString: String): String? {
        try {
            val reader = JsonReader(StringReader(jsonString))
            reader.beginObject()
            while (reader.hasNext()) {
                val name = reader.nextName()
                if (name == key) {
                    val type = reader.nextString()
                    reader.close()
                    return type
                } else {
                    reader.skipValue()
                }
            }
            reader.endObject()
            reader.close()
        } catch (e: IOException) {
            Log.e(TAG, "extractValue: Error parsing JSON: ${e.message}")
        }
        return null
    }

    /**
     * Creates a map of a class' field values to names
     */
    fun getMapOfIntFieldsToNames(clazz: KClass<*>, startsWith: String?): Map<Int, String> {
        val staticProperties = clazz.staticProperties
        if (staticProperties.isEmpty()) {
            val companion = clazz.companionObject
            val companionInstance = companion?.objectInstance
            if (companion != null && companionInstance != null) {
                return companion.memberProperties.filter {
                    (it.returnType.classifier == Int::class) && // Check if the property type is Int
                            (startsWith.isNullOrBlank() || it.name.startsWith(startsWith))
                }.associate {
                    it.getter.call(companionInstance) as Int to it.name
                }
            }
        }
        return staticProperties.filter {
            (it.returnType.classifier == Integer::class) &&
                    (startsWith.isNullOrBlank() ||
                            it.name.startsWith(startsWith))
        }.associate {
            it.getter.call() as Int to it.name
        }
    }
}