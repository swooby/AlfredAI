package com.swooby.alfredai

import android.content.Context
import android.media.MediaPlayer
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.JsonReader
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.StringReader
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlin.reflect.KClass

object Utils {
    private val TAG = Utils::class.java.simpleName

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

    fun getShortClassName(value: Any?): String {
        return when (value) {
            is KClass<*> -> value.simpleName ?: "null"
            else -> value?.javaClass?.simpleName ?: "null"
        }
    }

    fun playAudioResourceOnce(
        context: Context,
        audioResourceId: Int,
        volume: Float = 0.7f,
        state: Any? = null,
        onCompletion: ((Any?) -> Unit)? = null
    ) {
        Log.d(MainActivity.TAG, "+playAudioResourceOnce(..., audioResourceId=$audioResourceId, ...)")
        MediaPlayer.create(context, audioResourceId).apply {
            setVolume(volume, volume)
            setOnCompletionListener {
                onCompletion?.invoke(state)
                it.release()
                Log.d(MainActivity.TAG, "-playAudioResourceOnce(..., audioResourceId=$audioResourceId, ...)")
            }
            start()
        }
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
}
