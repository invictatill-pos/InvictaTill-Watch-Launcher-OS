package com.healthsync.phone.data

import java.io.StringReader
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class JsonLineReaderTest {
    @Test fun readsMultipleFramesWithWindowsAndUnixNewlines() {
        val frames = JsonLineReader(StringReader("{\"type\":\"PING\"}\r\n{\"type\":\"ACK\"}\n"))
        assertEquals("{\"type\":\"PING\"}", frames.readLine())
        assertEquals("{\"type\":\"ACK\"}", frames.readLine())
        assertNull(frames.readLine())
    }
    @Test(expected = IOException::class) fun rejectsOversizedMessages() {
        JsonLineReader(StringReader("12345\n"), 4).readLine()
    }
    @Test(expected = IOException::class) fun rejectsTruncatedFrames() {
        JsonLineReader(StringReader("{\"type\":")).readLine()
    }
}
