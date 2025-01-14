package com.swooby.alfredai.openai.realtime

import android.util.Log
import com.swooby.alfredai.Utils
import kotlin.reflect.KClass

class RealtimeLog(val TAG: String, var logLevel: Int = Log.DEBUG) {

    constructor(clazz: KClass<*>) : this(Utils.getShortClassName(clazz))

    fun v(msg: String) {
        if (logLevel <= Log.VERBOSE)
            Log.v(TAG, msg)
    }

    fun d(msg: String) {
        if (logLevel <= Log.DEBUG)
            Log.d(TAG, msg)
    }

    fun i(msg: String) {
        if (logLevel <= Log.INFO)
            Log.i(TAG, msg)
    }

    fun w(msg: String) {
        if (logLevel <= Log.WARN)
            Log.w(TAG, msg)
    }

    fun e(msg: String) {
        if (logLevel <= Log.ERROR)
            Log.e(TAG, msg)
    }

    fun f(msg: String, e: Exception) {
        if (logLevel <= Log.ASSERT)
            Log.wtf(TAG, msg, e)
    }
}