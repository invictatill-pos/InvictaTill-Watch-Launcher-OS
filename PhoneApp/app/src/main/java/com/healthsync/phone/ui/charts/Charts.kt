package com.healthsync.phone.ui.charts

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.healthsync.phone.data.model.HeartRateEntity
import com.healthsync.phone.data.model.SpO2Entity
import com.healthsync.phone.data.model.StepBucketEntity
import com.healthsync.phone.data.model.StepsEntity
import java.text.SimpleDateFormat
import java.util.*

// ── Heart Rate Line Chart ─────────────────────────────────────────────────────

@Composable
fun HeartRateLineChart(data: List<HeartRateEntity>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                styleHeartRateChart(this)
            }
        },
        update = { chart ->
            if (data.isEmpty()) { chart.clear(); return@AndroidView }
            val baseTime = data.minOf { it.timestamp }
            val entries = data.sortedBy { it.timestamp }.map { e -> Entry(((e.timestamp - baseTime) / 60000f), e.bpm.toFloat()) }
            val ds = LineDataSet(entries, "BPM").apply {
                color = Color.TRANSPARENT
                setCircleColor(0xFFB7334F.toInt())
                circleRadius = 3f
                lineWidth = 2.5f
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(false)
                fillColor = 0xFFB7334F.toInt()
                fillAlpha = 40
                valueTextColor = Color.WHITE
                setDrawValues(false)
            }
            chart.data = LineData(ds)
            chart.xAxis.valueFormatter = timeFormatter(baseTime)
            chart.xAxis.setLabelCount(5, true)
            chart.contentDescription = "Heart rate: ${data.minOf { it.bpm }} to ${data.maxOf { it.bpm }} beats per minute, ${data.size} readings"
            chart.invalidate()
        }
    )
}

private fun styleHeartRateChart(chart: LineChart) {
    chart.apply {
        setBackgroundColor(Color.TRANSPARENT)
        description.isEnabled = false
        legend.isEnabled = false
        setDrawBorders(false)
        setScaleEnabled(false)
        setTouchEnabled(false)
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = 0xFF6D7A71.toInt()
            gridColor = 0xFFDCE5DF.toInt()
            axisLineColor = 0x44FFFFFF
            setDrawAxisLine(false)
        }
        axisLeft.apply {
            textColor = 0xFF6D7A71.toInt()
            gridColor = 0xFFDCE5DF.toInt()
            axisLineColor = 0
            setDrawAxisLine(false)
        }
        axisRight.isEnabled = false
    }
}

// ── Steps Bar Chart ───────────────────────────────────────────────────────────

@Composable
fun StepsBarChart(data: List<StepsEntity>, goal: Int = 10000, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            BarChart(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                styleBarChart(this)
            }
        },
        update = { chart ->
            if (data.isEmpty()) { chart.clear(); return@AndroidView }
            val baseDay = java.time.LocalDate.parse(data.minOf { it.dayKey })
            val entries = data.map { record ->
                BarEntry(java.time.temporal.ChronoUnit.DAYS.between(baseDay, java.time.LocalDate.parse(record.dayKey)).toFloat(), record.steps.toFloat())
            }
            val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
            val ds = BarDataSet(entries, "Steps").apply {
                colors = entries.map {
                    if (it.y >= goal) 0xFF2F765B.toInt() else 0xFFBC862F.toInt()
                }
                valueTextColor = 0xFF40584A.toInt()
                valueTextSize = 9f
            }
            chart.data = BarData(ds)
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    baseDay.plusDays(value.toLong()).format(dayFmt)
            }
            chart.contentDescription = "Steps across ${data.size} captured days; missing days have no bar"
            chart.axisLeft.removeAllLimitLines()
            chart.axisLeft.addLimitLine(
                com.github.mikephil.charting.components.LimitLine(goal.toFloat(), "Goal").apply {
                    lineColor = 0xAA2F765B.toInt()
                    lineWidth = 1.5f
                    textColor = 0xFF2F765B.toInt()
                    labelPosition = com.github.mikephil.charting.components.LimitLine.LimitLabelPosition.RIGHT_TOP
                }
            )
            chart.invalidate()
        }
    )
}

@Composable
fun Steps24HourChart(data: List<StepBucketEntity>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            BarChart(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                styleBarChart(this)
            }
        },
        update = { chart ->
            val hourly = aggregateHourlySteps(data)
            if (hourly.isEmpty()) {
                chart.clear()
                return@AndroidView
            }
            val hourFmt = SimpleDateFormat("ha", Locale.getDefault())
            val baseHour = hourly.first().timestamp
            val entries = hourly.map { bucket -> BarEntry(((bucket.timestamp - baseHour) / 3_600_000L).toFloat(), bucket.steps.toFloat()) }
            val ds = BarDataSet(entries, "24h steps").apply {
                color = 0xFF2F765B.toInt()
                valueTextColor = 0xFF40584A.toInt()
                valueTextSize = 8f
                setDrawValues(false)
            }
            chart.data = BarData(ds)
            chart.xAxis.valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String =
                    hourFmt.format(Date(baseHour + value.toLong() * 3_600_000L)).lowercase(Locale.getDefault())
            }
            chart.contentDescription = "Hourly captured steps; missing hours have no bar"
            chart.xAxis.setLabelCount(6, false)
            chart.invalidate()
        }
    )
}

private data class HourStep(val timestamp: Long, val steps: Int)

private fun aggregateHourlySteps(data: List<StepBucketEntity>): List<HourStep> {
    if (data.isEmpty()) return emptyList()
    val cal = Calendar.getInstance()
    return data
        .groupBy {
            cal.timeInMillis = it.timestamp
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }
        .map { (ts, buckets) -> HourStep(ts, buckets.sumOf { it.steps }) }
        .sortedBy { it.timestamp }
}

private fun styleBarChart(chart: BarChart) {
    chart.apply {
        setBackgroundColor(Color.TRANSPARENT)
        description.isEnabled = false
        legend.isEnabled = false
        setDrawBorders(false)
        setScaleEnabled(false)
        setTouchEnabled(false)
        xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            textColor = 0xFF6D7A71.toInt()
            gridColor = 0xFFDCE5DF.toInt()
            setDrawGridLines(false)
            setDrawAxisLine(false)
            granularity = 1f
        }
        axisLeft.apply {
            textColor = 0xFF6D7A71.toInt()
            gridColor = 0xFFDCE5DF.toInt()
            setDrawAxisLine(false)
            axisMinimum = 0f
        }
        axisRight.isEnabled = false
    }
}

// ── SpO2 Line Chart ───────────────────────────────────────────────────────────

@Composable
fun SpO2LineChart(data: List<SpO2Entity>, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            LineChart(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                styleHeartRateChart(this)
            }
        },
        update = { chart ->
            if (data.isEmpty()) { chart.clear(); return@AndroidView }
            val baseTime = data.minOf { it.timestamp }
            val entries = data.sortedBy { it.timestamp }.map { e -> Entry(((e.timestamp - baseTime) / 60000f), e.percentage) }
            val ds = LineDataSet(entries, "SpO2 %").apply {
                color = Color.TRANSPARENT
                setCircleColor(0xFF226B86.toInt())
                circleRadius = 3f
                lineWidth = 2.5f
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(false)
                fillColor = 0xFF226B86.toInt()
                fillAlpha = 40
                setDrawValues(false)
            }
            chart.data = LineData(ds)
            chart.xAxis.valueFormatter = timeFormatter(baseTime)
            chart.xAxis.setLabelCount(5, true)
            chart.axisLeft.apply {
                axisMinimum = (data.minOf { it.percentage } - 2f).coerceAtLeast(0f)
                axisMaximum = 100f
                removeAllLimitLines()
            }
            chart.contentDescription = "Oxygen saturation: ${String.format(Locale.getDefault(), "%.1f", data.minOf { it.percentage })} to ${String.format(Locale.getDefault(), "%.1f", data.maxOf { it.percentage })} percent, ${data.size} readings"
            chart.invalidate()
        }
    )
}

private fun timeFormatter(baseTime: Long): ValueFormatter = object : ValueFormatter() {
    private val format = SimpleDateFormat("HH:mm", Locale.getDefault())
    override fun getFormattedValue(value: Float): String = format.format(Date(baseTime + (value * 60000L).toLong()))
}
