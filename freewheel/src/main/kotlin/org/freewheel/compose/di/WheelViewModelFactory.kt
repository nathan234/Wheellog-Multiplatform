package org.freewheel.compose.di

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import org.freewheel.compose.WheelViewModel

class WheelViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return WheelViewModel(
            application = application,
            appConfig = AppModule.appConfig,
            prefs = AppModule.prefs,
            vibrator = AppModule.vibrator,
        ) as T
    }
}
