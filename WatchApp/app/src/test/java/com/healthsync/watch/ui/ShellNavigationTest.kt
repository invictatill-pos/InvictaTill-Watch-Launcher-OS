package com.healthsync.watch.ui

import com.healthsync.watch.ui.shell.ShellNavigation
import org.junit.Assert.*
import org.junit.Test

class ShellNavigationTest {
    @Test fun homeClearsNestedScreensAndBackDoesNotLeaveHome() {
        val nav=ShellNavigation(); nav.open("controls"); nav.open("settings"); nav.open("faces")
        assertEquals("settings",nav.back()); assertEquals("controls",nav.back())
        nav.home(); assertNull(nav.current); assertNull(nav.back())
    }
    @Test fun recreationRestoresValidHistoryWithoutDuplicateCurrentOrUnknownRoutes() {
        val nav=ShellNavigation(); nav.restore(listOf("apps","apps","unknown","settings","faces"))
        assertEquals(listOf("apps","settings","faces"),nav.snapshot())
        assertEquals("settings",nav.back()); nav.open("invalid"); assertEquals("settings",nav.current)
    }
    @Test fun returningToAnOpenDestinationDoesNotAccumulateNavigationLoops() {
        val nav=ShellNavigation(); nav.open("controls"); nav.open("settings"); nav.open("media")
        nav.open("settings"); assertEquals(listOf("controls","settings"),nav.snapshot())
        nav.open("controls"); assertEquals(listOf("controls"),nav.snapshot())
    }
}
