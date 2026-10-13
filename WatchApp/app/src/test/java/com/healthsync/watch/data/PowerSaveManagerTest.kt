package com.healthsync.watch.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSaveManagerTest {

    @Test
    fun testPowerSaveModeCodes() {
        assertEquals(PowerSaveMode.NORMAL, PowerSaveMode.fromCode(0))
        assertEquals(PowerSaveMode.EXTREME, PowerSaveMode.fromCode(1))
        assertEquals(PowerSaveMode.SUPER_EXTREME, PowerSaveMode.fromCode(2))
        // Invalid codes fallback to NORMAL
        assertEquals(PowerSaveMode.NORMAL, PowerSaveMode.fromCode(-1))
        assertEquals(PowerSaveMode.NORMAL, PowerSaveMode.fromCode(99))
    }

    @Test
    fun testCommunicationPolicyAcrossTiers() {
        // Normal mode keeps communication alive
        assertTrue("Normal mode must keep communication alive", PowerSaveManager.isCommunicationAlive(PowerSaveMode.NORMAL))

        // Extreme Saver mode MUST keep communication alive (User requirement)
        assertTrue("Extreme Saver mode must keep communication alive", PowerSaveManager.isCommunicationAlive(PowerSaveMode.EXTREME))

        // Super Extreme mode MUST stop communication (User requirement)
        assertFalse("Super Extreme mode must stop communication", PowerSaveManager.isCommunicationAlive(PowerSaveMode.SUPER_EXTREME))
    }

    @Test
    fun testEstimatedHoursScaling() {
        // Full battery (100%)
        assertEquals(24, PowerSaveManager.getEstimatedHours(100, PowerSaveMode.NORMAL))
        assertEquals(72, PowerSaveManager.getEstimatedHours(100, PowerSaveMode.EXTREME))
        assertEquals(200, PowerSaveManager.getEstimatedHours(100, PowerSaveMode.SUPER_EXTREME))

        // Half battery (50%)
        assertEquals(12, PowerSaveManager.getEstimatedHours(50, PowerSaveMode.NORMAL))
        assertEquals(36, PowerSaveManager.getEstimatedHours(50, PowerSaveMode.EXTREME))
        assertEquals(100, PowerSaveManager.getEstimatedHours(50, PowerSaveMode.SUPER_EXTREME))

        // Low battery (10%)
        assertEquals(2, PowerSaveManager.getEstimatedHours(10, PowerSaveMode.NORMAL))
        assertEquals(7, PowerSaveManager.getEstimatedHours(10, PowerSaveMode.EXTREME))
        assertEquals(20, PowerSaveManager.getEstimatedHours(10, PowerSaveMode.SUPER_EXTREME))
    }

    @Test
    fun testTitlesAndBadges() {
        assertEquals("Normal Mode", PowerSaveManager.getModeTitle(PowerSaveMode.NORMAL))
        assertEquals("Extreme Battery Saver", PowerSaveManager.getModeTitle(PowerSaveMode.EXTREME))
        assertEquals("Super Extreme Saver", PowerSaveManager.getModeTitle(PowerSaveMode.SUPER_EXTREME))

        assertTrue(PowerSaveManager.getModeBadge(PowerSaveMode.EXTREME).contains("PHONE LINKED"))
        assertTrue(PowerSaveManager.getModeBadge(PowerSaveMode.SUPER_EXTREME).contains("EMERGENCY"))
    }
}
