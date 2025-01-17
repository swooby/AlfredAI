package com.swooby.alfredai

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.openai.infrastructure.Serializer
import com.openai.models.RealtimeSessionCreateRequest
import com.openai.models.RealtimeSessionTurnDetection
import com.openai.models.RealtimeSessionInputAudioTranscription
import com.openai.models.RealtimeSessionModel
import com.openai.models.RealtimeSessionVoice
import com.swooby.alfredai.PushToTalkViewModel.Companion.DEBUG
import com.swooby.alfredai.ui.theme.AlfredAITheme

class PushToTalkPreferences(context: Context) {
    companion object {
        val modelDefault = RealtimeSessionModel.`gpt-4o-mini-realtime-preview-2024-12-17`
        val voiceDefault = RealtimeSessionVoice.ash

        // No turn_detection; We will be PTTing...
        val turnDetectionDefault: RealtimeSessionTurnDetection? = null

        // Costs noticeably more money, so turn it off if we are DEBUG, unless we really need it
        val inputAudioTranscriptionDefault = if (DEBUG || false) {
            null
        } else {
            RealtimeSessionInputAudioTranscription(
                model = RealtimeSessionInputAudioTranscription.Model.`whisper-1`
            )
        }
        val sessionConfigDefault = RealtimeSessionCreateRequest(
            model = modelDefault,
            voice = voiceDefault,
            turnDetection = turnDetectionDefault,
            inputAudioTranscription = inputAudioTranscriptionDefault,
        )
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences("pushToTalkPreferences", Context.MODE_PRIVATE)

    @Suppress("SameParameterValue")
    private fun getBoolean(key: String, default: Boolean): Boolean {
        return prefs.getBoolean(key, default)
    }

    @Suppress("SameParameterValue")
    private fun putBoolean(key: String, value: Boolean) {
        with(prefs.edit()) {
            putBoolean(key, value)
            apply()
        }
    }

    @Suppress("SameParameterValue")
    private fun getString(key: String, default: String): String {
        return prefs.getString(key, default) ?: default
    }

    private fun putString(key: String, value: String) {
        with(prefs.edit()) {
            putString(key, value)
            apply()
        }
    }

    var autoConnect: Boolean
        get() = getBoolean("autoConnect", true)
        set(value) = putBoolean("autoConnect", value)

    var apiKey: String
        get() {
            val apiKeyEncrypted = getString("apiKey", BuildConfig.DANGEROUS_OPENAI_API_KEY)
            return Crypto.hardwareDecrypt(apiKeyEncrypted)
        }
        set(value) {
            val apiKeyEncrypted = Crypto.hardwareEncrypt(value)
            putString("apiKey", apiKeyEncrypted)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PushToTalkPreferenceScreen(pushToTalkViewModel: PushToTalkViewModel? = null) {
    val forceShowDialog = false // for debug/dev purposes
    val showDialog = remember {
        mutableStateOf(forceShowDialog || pushToTalkViewModel != null && pushToTalkViewModel.apiKey.value.isBlank())
    }
    if (showDialog.value) {
        BasicAlertDialog(onDismissRequest = { showDialog.value = false }) {
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = AlertDialogDefaults.TonalElevation
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = Utils.annotatedStringFromHtml(resId = R.string.missing_api_key_instructions))
                    Spacer(modifier = Modifier.height(24.dp))
                    TextButton(
                        onClick = {
                            showDialog.value = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Understood")
                    }
                }
            }
        }
    }

    // UI layout inside a LazyColumn so it can scroll if needed.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = pushToTalkViewModel?.autoConnect?.value ?: true,
                    onCheckedChange = { pushToTalkViewModel?.onAutoConnectChange(it) }
                )
                Text(text = "Auto Connect")
            }
        }

        item {
            TextField(
                label = { Text("OpenAI API Key*") },
                supportingText = { Text("*required: Locally encrypted & stored only for this app") },
                isError = pushToTalkViewModel?.apiKey?.value?.isBlank() ?: true,
                value = pushToTalkViewModel?.apiKey?.value ?: "",
                onValueChange = { pushToTalkViewModel?.onApiKeyChange(it) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    showBackground = true
)
@Composable
fun PushToTalkButtonPreferencesPreviewLight() {
    AlfredAITheme(dynamicColor = false) {
        PushToTalkPreferenceScreen()
    }
}

@Preview(
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true
)
@Composable
fun PushToTalkButtonPreferencesPreviewDark() {
    AlfredAITheme(dynamicColor = false) {
        PushToTalkPreferenceScreen()
    }
}
