package com.lastcalled.app

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class LastCalledApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Re-read the system call log whenever the app comes to the foreground.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                if (container.permissions.hasCallLog()) container.syncManager.refreshIfStale()
            }
        })
    }
}

val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as LastCalledApplication).container
