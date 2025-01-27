package com.swooby.alfredai

import android.content.Context
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object AppCommon {
    /*
    fun getPermissionText(context: Context, granted: Boolean): String {
        return if (granted)
            context.getString(R.string.microphone_permission_granted)
        else
            context.getString(R.string.microphone_permission_not_granted)
    }
    */

    public val SmallButtonSize = 42.dp
    public val DefaultButtonSize = 56.dp
    public val LargeButtonSize = 70.dp

    public val LargeIconSize = 60.dp

    @Composable
    public fun iconButtonColors(
        contentColor: Color = MaterialTheme.colorScheme.onSurface,
    ): ButtonColors {
        return ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = contentColor,
        )
    }
}