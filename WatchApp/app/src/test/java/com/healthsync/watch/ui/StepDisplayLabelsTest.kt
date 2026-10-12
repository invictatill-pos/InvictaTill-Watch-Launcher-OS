package com.healthsync.watch.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StepDisplayLabelsTest {
    @Test fun onlyMotionSourcesMarkCountsApproximate() {
        assertEquals("≈1.2k",StepDisplayLabels.display("1.2k",1200,"MOTION_ESTIMATE"))
        assertEquals("≈1.2k",StepDisplayLabels.display("1.2k",1200,"MIXED_CAPTURE"))
        assertEquals("1.2k",StepDisplayLabels.display("1.2k",1200,"HARDWARE_COUNTER"))
        assertEquals("1.2k",StepDisplayLabels.display("1.2k",1200,"HARDWARE_DETECTOR"))
    }

    @Test fun unavailableNeverRendersAValueOrApproximateCount() {
        assertEquals("—",StepDisplayLabels.display("-1",-1,"MOTION_ESTIMATE"))
        assertEquals("Steps unavailable",StepDisplayLabels.spoken(-1,"MIXED_CAPTURE"))
        assertEquals("0",StepDisplayLabels.display("0",0,"HARDWARE_COUNTER"))
        assertEquals("0 recorded steps",StepDisplayLabels.spoken(0,"HARDWARE_COUNTER"))
    }
}
