package com.healthsync.phone.data

import java.io.Reader
import java.io.IOException

/** Newline framing supports fragmented Bluetooth reads and bounds malformed peer messages. */
class JsonLineReader(private val reader: Reader, private val maxLength: Int = 4 * 1024 * 1024) {
    fun readLine(): String? {
        val line = StringBuilder()
        while (true) {
            val character = reader.read()
            if (character == -1) {
                if (line.isEmpty()) return null
                throw IOException("Incomplete sync message")
            }
            if (character == '\n'.code) return line.toString().trimEnd('\r')
            if (line.length >= maxLength) throw IOException("Sync message exceeds limit")
            line.append(character.toChar())
        }
    }
}
