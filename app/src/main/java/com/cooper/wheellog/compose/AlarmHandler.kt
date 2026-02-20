package com.cooper.wheellog.compose

import com.cooper.wheellog.core.alarm.AlarmResult
import com.cooper.wheellog.core.alarm.VibrationPatterns
import com.cooper.wheellog.core.domain.AlarmType

enum class AlarmAction(val value: Int) {
    PHONE_ONLY(0),
    PHONE_AND_WHEEL(1),
    ALL(2);

    companion object {
        fun fromValue(value: Int) = entries.firstOrNull { it.value == value } ?: PHONE_ONLY
    }
}

class AlarmHandler(
    private val vibrate: (pattern: LongArray) -> Unit,
    private val playTone: (toneType: Int, durationMs: Int) -> Unit,
    private val onWheelBeep: () -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lastFiredAt = mutableMapOf<AlarmType, Long>()

    fun handleAlarmResult(result: AlarmResult, action: AlarmAction) {
        for (alarm in result.triggeredAlarms) {
            if (isThrottled(alarm.type)) continue
            lastFiredAt[alarm.type] = clock()
            vibrate(VibrationPatterns.forAlarmType(alarm.type))
            playTone(TONE_ALARM, alarm.toneDuration)
            if (action != AlarmAction.PHONE_ONLY) onWheelBeep()
        }
        result.preWarning?.let {
            vibrate(PRE_WARNING_PATTERN)
            playTone(TONE_PRE_WARNING, PRE_WARNING_DURATION_MS)
        }
    }

    private fun isThrottled(type: AlarmType): Boolean {
        val last = lastFiredAt[type] ?: return false
        return (clock() - last) < THROTTLE_MS
    }

    companion object {
        const val THROTTLE_MS = 500L
        const val TONE_ALARM = 56 // ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD
        const val TONE_PRE_WARNING = 24 // ToneGenerator.TONE_PROP_BEEP
        const val PRE_WARNING_DURATION_MS = 50
        val PRE_WARNING_PATTERN = longArrayOf(0, 50)
    }
}
