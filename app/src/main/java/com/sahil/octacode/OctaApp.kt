package com.sahil.octacode

import android.app.Application
import com.sahil.octacode.di.appModule
import com.sahil.octacode.di.chatModule
import com.sahil.octacode.di.dataModule
import com.sahil.octacode.di.engineModule
import com.sahil.octacode.di.runtimeModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OctaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@OctaApp)
            modules(appModule, dataModule, engineModule, chatModule, runtimeModule)
        }
    }
}
