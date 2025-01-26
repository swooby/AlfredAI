package com.swooby.alfredai

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.appViewModels(): Lazy<VM> {
    return ViewModelLazy(
        VM::class,
        { (application as AlfredAiApp).viewModelStore },
        { ViewModelProvider.AndroidViewModelFactory.getInstance(application) },
        { defaultViewModelCreationExtras }
    )
}

class AlfredAiApp : Application(), ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()

    val wearViewModel by lazy {
        ViewModelProvider(
            this,
            ViewModelProvider.AndroidViewModelFactory.getInstance(this)
        )[WearViewModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        wearViewModel.init()
    }

    override fun onTerminate() {
        super.onTerminate()
        wearViewModel.close()
    }
}
