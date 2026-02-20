package com.cooper.wheellog

import android.app.Application
import android.content.res.Configuration
import androidx.preference.PreferenceManager
import com.cooper.wheellog.di.*
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class WheelLog : Application() {
    override fun onCreate() {
        super.onCreate()

        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val useCompose = prefs.getBoolean("use_compose_ui", false)

        val modules = mutableListOf(settingModule, dbModule)
        if (!useCompose) {
            modules.addAll(listOf(notificationsModule, volumeKeyModule))
        }

        startKoin {
            androidContext(this@WheelLog)
            modules(modules)
        }
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree(), FileLoggingTree(applicationContext))
        }

        if (!useCompose) {
            WheelData.initiate()
        }

        // YandexMetrica.
//        if (BuildConfig.metrica_api.isNotEmpty()) {
//            val config = YandexMetricaConfig
//                .newConfigBuilder(BuildConfig.metrica_api)
//                .withLocationTracking(false)
//                .withStatisticsSending(AppConfig.yandexMetricaAccepted)
//                .build()
//            YandexMetrica.activate(applicationContext, config)
//        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.setLocale(this)
    }
}