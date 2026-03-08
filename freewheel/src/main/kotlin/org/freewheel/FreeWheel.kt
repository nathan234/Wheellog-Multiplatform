package org.freewheel

import android.app.Application
import org.freewheel.compose.di.AppModule
import timber.log.Timber

class FreeWheel : Application() {
    override fun onCreate() {
        super.onCreate()
        AppModule.initialize(this)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree(), EventsLoggingTree(applicationContext))
        }
    }
}
