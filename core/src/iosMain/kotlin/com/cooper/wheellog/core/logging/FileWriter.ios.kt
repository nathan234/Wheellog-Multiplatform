@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.cooper.wheellog.core.logging

import platform.Foundation.*

actual class FileWriter actual constructor() {

    private var fileHandle: NSFileHandle? = null

    actual fun open(path: String): Boolean {
        val manager = NSFileManager.defaultManager
        val dir = (path as NSString).stringByDeletingLastPathComponent
        if (!manager.fileExistsAtPath(dir)) {
            manager.createDirectoryAtPath(dir, withIntermediateDirectories = true, attributes = null, error = null)
        }
        // Create file (or truncate)
        manager.createFileAtPath(path, contents = null, attributes = null)
        fileHandle = NSFileHandle.fileHandleForWritingAtPath(path)
        return fileHandle != null
    }

    actual fun writeLine(line: String) {
        val data = (line + "\n").encodeToByteArray().toNSData()
        fileHandle?.writeData(data)
    }

    actual fun close() {
        fileHandle?.closeFile()
        fileHandle = null
    }
}

private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return NSString.create(string = this.decodeToString())
        .dataUsingEncoding(NSUTF8StringEncoding) ?: NSData()
}
