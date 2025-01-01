package com.swooby.alfredai

import android.content.Context

object AppCommon {
    fun getPermissionText(context: Context, granted: Boolean): String {
        return if (granted)
            context.getString(R.string.microphone_permission_granted)
        else
            context.getString(R.string.microphone_permission_not_granted)
    }
}