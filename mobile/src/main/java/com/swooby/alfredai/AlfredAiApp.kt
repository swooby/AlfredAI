package com.swooby.alfredai

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AlfredAiApp : Application(), ViewModelStoreOwner {
    companion object {
        const val DEBUG = true
    }

    val appViewModelStore: ViewModelStore = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    lateinit var pushToTalkViewModel: PushToTalkViewModel

    override fun onCreate() {
        super.onCreate()
        pushToTalkViewModel = PushToTalkViewModel(this)
    }
}
