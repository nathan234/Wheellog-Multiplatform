package org.freewheel.core.utils

import java.security.MessageDigest

actual fun md5(input: ByteArray): ByteArray =
    MessageDigest.getInstance("MD5").digest(input)
