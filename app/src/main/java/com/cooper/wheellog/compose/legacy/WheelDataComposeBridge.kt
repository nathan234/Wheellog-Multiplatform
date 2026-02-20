package com.cooper.wheellog.compose.legacy

import androidx.compose.runtime.*
import com.cooper.wheellog.WheelData

@Stable
object WheelDataComposeBridge {
    val data: WheelData
        get() = WheelData.getInstance()
}