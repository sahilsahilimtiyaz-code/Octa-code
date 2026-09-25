package com.sahil.octacode

import android.app.Application
import android.util.Log
import com.sahil.octacode.data.settings.SettingsRepository
import com.sahil.octacode.di.appModule
import com.sahil.octacode.di.chatModule
import com.sahil.octacode.di.dataModule
import com.sahil.octacode.di.engineModule
import com.sahil.octacode.di.runtimeModule
import com.sahil.octacode.domain.chat.ChatRepository
import com.sahil.octacode.domain.chat.RetentionOutcome
import com.sahil.octacode.domain.chat.RetentionReport
import com.sahil.octacode.domain.chat.SessionRetention
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class OctaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val app = startKoin {
            androidContext(this@OctaApp)
            modules(appModule, dataModule, engineModule, chatModule, runtimeModule)
        }

        // Runs here rather than when Settings is opened, because the user
        // configured a rule and not a button to press: "archive after 7 days"
        // that only applies while you are looking at it is not that rule.
        //
        // The outcome is published rather than dropped — a pass that silently
        // does nothing is indistinguishable from one that never ran, which is
        // the exact confusion [RetentionReport] exists to prevent.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            val report = app.koin.get<RetentionReport>()
            try {
                val archived = SessionRetention.apply(
                    repo = app.koin.get<ChatRepository>(),
                    settings = app.koin.get<SettingsRepository>().value,
                    now = System.currentTimeMillis(),
                )
                report.publish(RetentionOutcome.Archived(archived))
            } catch (t: Exception) {
                Log.e(TAG, "session retention pass failed", t)
                report.publish(
                    RetentionOutcome.Failed(t.message ?: t.javaClass.simpleName)
                )
            }
        }
    }

    private companion object {
        const val TAG = "OctaApp"
    }
}
