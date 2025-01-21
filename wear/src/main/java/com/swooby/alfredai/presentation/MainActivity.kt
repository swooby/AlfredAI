package com.swooby.alfredai.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.swooby.alfredai.R
import com.swooby.alfredai.presentation.theme.AlfredAITheme

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)
        setContent {
            WearApp("Wear")
        }
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        Log.i(TAG, "dispatchKeyEvent(event=$event)")
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_STEM_PRIMARY -> {
                    Log.i(TAG, "dispatchKeyEvent: hardware button 0")
                    onHardwareButton(KeyEvent.KEYCODE_STEM_PRIMARY)
                    return true
                }
                KeyEvent.KEYCODE_STEM_1 -> {
                    Log.i(TAG, "dispatchKeyEvent: hardware button 1")
                    onHardwareButton(KeyEvent.KEYCODE_STEM_1)
                    return true
                }

                KeyEvent.KEYCODE_STEM_2 -> {
                    Log.i(TAG, "dispatchKeyEvent: hardware button 2")
                    onHardwareButton(KeyEvent.KEYCODE_STEM_2)
                    return true
                }

                KeyEvent.KEYCODE_STEM_3 -> {
                    Log.i(TAG, "dispatchKeyEvent: hardware button 3")
                    onHardwareButton(KeyEvent.KEYCODE_STEM_3)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun onHardwareButton(keyCode: Int) {
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Wear")
}

@Composable
fun WearApp(greetingName: String) {
    AlfredAITheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            Greeting(greetingName = greetingName)
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}
