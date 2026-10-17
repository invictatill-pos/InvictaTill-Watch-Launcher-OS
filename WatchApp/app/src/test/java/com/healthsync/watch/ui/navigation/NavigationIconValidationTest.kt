package com.healthsync.watch.ui.navigation

import com.healthsync.watch.data.NavigationProtocol
import org.junit.Assert.*
import org.junit.Test

class NavigationIconValidationTest {
    // A real complete 1 x 1 PNG. Bounds validation is pure; BitmapFactory also validates decode.
    private fun png() = java.util.Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a2ioAAAAASUVORK5CYII=")

    @Test fun onlyBoundedCompletePngsWithSmallHeaderDimensionsReachBoundsDecoder() {
        assertTrue(validNavigationPngBytes(png()))
        assertFalse(validNavigationPngBytes(byteArrayOf()))
        assertFalse(validNavigationPngBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())))
        assertFalse(validNavigationPngBytes(png().copyOf(44)))
        assertFalse(validNavigationPngBytes(png().copyOf(NavigationProtocol.MAX_ICON_BYTES + 1)))
        assertFalse(validNavigationPngBytes(png().apply { this[0] = 0 }))
        assertFalse(validNavigationPngBytes(png().apply { this[lastIndex] = 0 }))
        assertFalse(validNavigationPngBytes(png().apply { this[11] = 12 }))
        assertFalse(validNavigationPngBytes(png().apply { this[12] = 0 }))
        assertFalse(validNavigationPngBytes(png().apply { this[19] = 97 }))
        assertFalse(validNavigationPngBytes(png().apply { this[23] = 97 }))
        assertFalse(validNavigationPngBytes(png().apply { this[19] = 0 }))
        assertFalse(validNavigationPngBytes(png().apply { this[16] = 0x7F }))
    }

    @Test fun bitmapBoundsMustBePositivePngDimensionsUnderNinetySixPixels() {
        assertTrue(validNavigationIconBounds(1, 1, "image/png"))
        assertTrue(validNavigationIconBounds(96, 96, "image/png"))
        assertFalse(validNavigationIconBounds(97, 1, "image/png"))
        assertFalse(validNavigationIconBounds(1, 97, "image/png"))
        assertFalse(validNavigationIconBounds(0, 96, "image/png"))
        assertFalse(validNavigationIconBounds(-1, 96, "image/png"))
        assertFalse(validNavigationIconBounds(96, 96, "image/jpeg"))
        assertFalse(validNavigationIconBounds(96, 96, null))
    }
}
