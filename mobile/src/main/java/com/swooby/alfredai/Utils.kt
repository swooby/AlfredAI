package com.swooby.alfredai

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

object Utils {
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

    fun playAudioResourceOnce(context: Context, audioResourceId: Int, state: Any? = null, onCompletion: ((Any?) -> Unit)? = null) {
        CoroutineScope(Dispatchers.IO).launch {
            val mediaPlayer = MediaPlayer.create(context, audioResourceId)
            mediaPlayer.setOnCompletionListener {
                CoroutineScope(Dispatchers.Main).launch {
                    onCompletion?.invoke(state)
                }
                it.release()
            }
            mediaPlayer.start()
        }
    }
}