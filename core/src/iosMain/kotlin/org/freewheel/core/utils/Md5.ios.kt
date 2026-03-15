package org.freewheel.core.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun md5(input: ByteArray): ByteArray {
    val digest = UByteArray(CC_MD5_DIGEST_LENGTH)
    input.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            CC_MD5(inputPinned.addressOf(0), input.size.convert(), digestPinned.addressOf(0))
        }
    }
    return digest.toByteArray()
}
