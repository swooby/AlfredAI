package com.swooby.alfredai

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class AlfredAiApp : Application(), ViewModelStoreOwner {
    private val appViewModelStore: ViewModelStore = ViewModelStore()

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}