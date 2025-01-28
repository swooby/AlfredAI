package com.swooby.alfredai

import android.content.Context
import android.media.MediaPlayer
import android.util.JsonReader
import android.util.Log
import androidx.wear.phone.interactions.PhoneTypeHelper
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

    fun playAudioResourceOnce(
        context: Context,
        audioResourceId: Int,
        volume: Float = 0.7f,
        state: Any? = null,
        onCompletion: ((Any?) -> Unit)? = null
    ) {
        Log.d(TAG, "+playAudioResourceOnce(..., audioResourceId=$audioResourceId, ...)")
        MediaPlayer.create(context, audioResourceId).apply {
            setVolume(volume, volume)
            setOnCompletionListener {
                onCompletion?.invoke(state)
                it.release()
                Log.d(TAG, "-playAudioResourceOnce(..., audioResourceId=$audioResourceId, ...)")
            }
            start()
        }
    }

    fun getFriendlyPermissionName(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECORD_AUDIO
                -> "Microphone"

            android.Manifest.permission.BLUETOOTH,
            android.Manifest.permission.BLUETOOTH_CONNECT,
            android.Manifest.permission.BLUETOOTH_SCAN,
                -> "Nearby devices"

            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.FOREGROUND_SERVICE,
            //android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
            //android.Manifest.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING,
            android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
                -> "Notifications"

            //...

            else
                -> permission
        }
    }

    fun phoneDeviceTypeToString(phoneType: Int): String {
        val map = getMapOfIntFieldsToNames(
            PhoneTypeHelper::class,
            "DEVICE_TYPE_"
        )
        return (map[phoneType] ?: "INVALID") + "($phoneType)"
    }
}