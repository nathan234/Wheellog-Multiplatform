package com.cooper.wheellog.core.protocol

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

/**
 * iOS implementation of currentTimeMillis.
 */
actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
