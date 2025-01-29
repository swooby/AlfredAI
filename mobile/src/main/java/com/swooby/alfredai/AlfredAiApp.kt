package com.swooby.alfredai

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelLazy
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/*
@MainThread
inline fun <reified VM : ViewModel> ComponentActivity.appViewModels(): Lazy<VM> {
    return ViewModelLazy(
        VM::class,
        { (application as AlfredAiApp).viewModelStore },
        { ViewModelProvider.AndroidViewModelFactory.getInstance(application) },
        { defaultViewModelCreationExtras }
    )
}
*/

class AlfredAiApp : Application(), ViewModelStoreOwner
{
    companion object {
        const val DEBUG = false
    }

    override val viewModelStore = ViewModelStore()

    val mobileViewModel by lazy {
        ViewModelProvider(
            owner = this,
            factory = ViewModelProvider.AndroidViewModelFactory.getInstance(this)
        )[MobileViewModel::class.java]
    }

    override fun onCreate() {
        super.onCreate()
        mobileViewModel.init()
    }

    override fun onTerminate() {
        super.onTerminate()
        mobileViewModel.close()
    }
}
