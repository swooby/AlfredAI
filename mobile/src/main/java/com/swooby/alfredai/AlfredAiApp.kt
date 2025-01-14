package com.swooby.alfredai

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.google.firebase.Firebase
import com.google.firebase.initialize
import com.openai.models.RealtimeSessionCreateRequest
import com.openai.models.RealtimeSessionInputAudioTranscription
import com.openai.models.RealtimeSessionModel
import com.swooby.alfredai.openai.realtime.RealtimeClient

class AlfredAiApp : Application(), ViewModelStoreOwner {
    companion object {
        const val DEBUG = true
    }

    private val appViewModelStore: ViewModelStore = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    val sessionConfig = RealtimeSessionCreateRequest(
        model = RealtimeSessionModel.`gpt-4o-mini-realtime-preview-2024-12-17`,
        voice = RealtimeSessionCreateRequest.Voice.ash,
        // No turn_detection; We will be PTTing...
        turn_detection = null,
        // Costs noticeably more money to debug (or one day show text of what we said in conversation)...
        input_audio_transcription = if (DEBUG && false) {
            RealtimeSessionInputAudioTranscription(
                model = RealtimeSessionInputAudioTranscription.Model.`whisper-1`
            )
        } else {
            null
        })

    lateinit var realtimeClient: RealtimeClient

    override fun onCreate() {
        super.onCreate()

        AlfredViewModel.application = this

        Firebase.initialize(this)

        // TODO: Get API Key from server firestore via appCheck or remoteConfig
        realtimeClient = RealtimeClient(
            BuildConfig.DANGEROUS_OPENAI_API_KEY,
            sessionConfig,
            debug = DEBUG)
    }
}