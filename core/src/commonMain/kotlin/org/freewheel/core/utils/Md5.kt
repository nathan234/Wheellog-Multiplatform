package org.freewheel.core.utils

/**
 * Platform-specific MD5 hash function.
 * Returns the 16-byte MD5 digest of the input.
 */
expect fun md5(input: ByteArray): ByteArray
