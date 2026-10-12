package com.healthsync.watch.ui

import com.healthsync.watch.service.SensorCollectorService

/** An approximate marker describes the capture source without changing the captured count. */
internal object StepDisplayLabels {
    fun isEstimated(source:String):Boolean=source=="MOTION_ESTIMATE" || source=="MIXED_CAPTURE"

    fun display(valueText:String,count:Int,source:String=SensorCollectorService.stepCaptureSource):String = when {
        count<0 -> "—"
        isEstimated(source) -> "≈$valueText"
        else -> valueText
    }

    fun spoken(count:Int,source:String=SensorCollectorService.stepCaptureSource):String = when {
        count<0 -> "Steps unavailable"
        isEstimated(source) -> "$count recorded steps, includes motion estimates"
        else -> "$count recorded steps"
    }
}
