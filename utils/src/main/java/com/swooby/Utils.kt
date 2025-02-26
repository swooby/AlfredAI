package com.swooby

import android.bluetooth.BluetoothHeadset
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.JsonReader
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    fun quote(value: Any?, typeOnly: Boolean = false, stringQuoteChar: Char = '"'): String {
        if (value == null) {
            return "null"
        }

        if (typeOnly) {
            return getShortClassName(value)
        }

        if (value is String) {
            return "$stringQuoteChar$value$stringQuoteChar"
        }

        if (value is CharSequence) {
            return "'$value'"
        }

        return value.toString()
    }

    fun redact(input: String?, peekCount: Int = 3, dangerousNullOK: Boolean = false): String? {
        if (input.isNullOrBlank() || input.length <= 6 || input.length < (peekCount * 2 + 8)) {
            return if (dangerousNullOK && input.isNullOrBlank()) null else return "{REDACTED}"
        }
        val firstThree = input.take(peekCount)
        val lastThree = input.takeLast(peekCount)
        return "$firstThree{REDACTED}$lastThree"
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

    fun intFieldToName(clazz: KClass<*>, startsWith: String, value: Int, suffixValue: Boolean = true): String {
        return (getMapOfIntFieldsToNames(clazz, startsWith)[value] ?: "INVALID") + if (suffixValue) "($value)" else ""
    }

    fun bluetoothHeadsetStateToString(state: Int): String {
        return intFieldToName(BluetoothHeadset::class, "STATE_", state)
    }

    fun audioManagerScoStateToString(state: Int): String {
        return intFieldToName(AudioManager::class, "SCO_AUDIO_STATE_", state)
    }

    fun audioDeviceInfoToString(audioDeviceInfo: AudioDeviceInfo): String {
        val id = audioDeviceInfo.id
        val type = audioDeviceInfo.type.let {
            (getMapOfIntFieldsToNames(AudioDeviceInfo::class, "TYPE_")
                [it] ?: "INVALID") + "(${it})"
        }
        val productName = audioDeviceInfo.productName
//        audioDeviceInfo.address
//        audioDeviceInfo.audioProfiles
//        audioDeviceInfo.audioDescriptors
//        audioDeviceInfo.sampleRates
//        audioDeviceInfo.encodings
//        audioDeviceInfo.isSink
//        audioDeviceInfo.isSource
        return "id=$id, type=$type, productName=${quote(productName)}"
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

    /*
    fun provideAudibleFeedback(context: Context, pttState: PttState) {
        val resId = when (pttState) {
            PttState.Idle -> R.raw.quindar_nasa_apollo_outro
            PttState.Pressed -> R.raw.quindar_nasa_apollo_intro
        }
        val mediaPlayer = MediaPlayer.create(context, resId)
        mediaPlayer.start()
        mediaPlayer.setOnCompletionListener {
            it.release()
        }
    }

    fun provideHapticFeedback(context: Context) {
        val vibrator = context.getSystemService(VibratorManager::class.java)
            ?.defaultVibrator ?: context.getSystemService(Vibrator::class.java)
        vibrator?.vibrate(
            VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    }
    */

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
            android.Manifest.permission.FOREGROUND_SERVICE_MICROPHONE,
            android.Manifest.permission.FOREGROUND_SERVICE_REMOTE_MESSAGING,
            android.Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE,
                -> "Notifications"

            //...

            else
                -> permission
        }
    }

    fun showToast(context: Context,
                  text: String,
                  duration: Int = Toast.LENGTH_SHORT,
                  forceInvokeOnMain: Boolean = false) {
        if (forceInvokeOnMain) {
            CoroutineScope(Dispatchers.Main).launch {
                showToast(context, text, duration, false)
            }
        } else {
            Toast.makeText(context, text, duration).show()
        }
    }
}