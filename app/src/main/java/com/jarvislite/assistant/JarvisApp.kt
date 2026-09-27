package com.jarvislite.assistant

import android.app.Application
import android.content.Context

class JarvisApp : Application() {
    companion object {
        lateinit var appContext: Context
            private set
    }

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
    }
}
