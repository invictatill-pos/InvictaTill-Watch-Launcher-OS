package com.healthsync.phone.ui.dashboard

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthsync.phone.data.model.ConnectionInfo
import com.healthsync.phone.data.model.SleepEntity
import com.healthsync.phone.data.model.WorkoutEntity
import kotlin.math.roundToInt
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.data.heartRateFeedbackText
import com.healthsync.phone.data.heartRateMeasurementDetailsText
import com.healthsync.phone.ui.charts.Steps24HourChart
import com.healthsync.phone.ui.charts.StepsBarChart
import com.healthsync.phone.ui.charts.HeartRateLineChart
import com.healthsync.phone.ui.charts.SpO2LineChart
import com.healthsync.phone.ui.components.ResumeEffect
import com.healthsync.phone.ui.workout.workoutDurationSeconds
import kotlinx.coroutines.delay
import android.widget.Toast
import com.healthsync.phone.ui.theme.AccentBlue
import com.healthsync.phone.ui.theme.AccentCyan
import com.healthsync.phone.ui.theme.AccentPurple
import com.healthsync.phone.ui.theme.BgCard
import com.healthsync.phone.ui.theme.BgCardHover
import com.healthsync.phone.ui.theme.BgDeep
import com.healthsync.phone.ui.theme.BorderSubtle
import com.healthsync.phone.ui.theme.MetricCalories
import com.healthsync.phone.ui.theme.MetricHeart
import com.healthsync.phone.ui.theme.MetricSleep
import com.healthsync.phone.ui.theme.MetricSpO2
import com.healthsync.phone.ui.theme.MetricSteps
import com.healthsync.phone.ui.theme.SleepAwake
import com.healthsync.phone.ui.theme.SleepDeep
import com.healthsync.phone.ui.theme.SleepLight
import com.healthsync.phone.ui.theme.SleepRem
import com.healthsync.phone.ui.theme.StatusError
import com.healthsync.phone.ui.theme.StatusSuccess
import com.healthsync.phone.ui.theme.TextDim
import com.healthsync.phone.ui.theme.TextPrimary
import com.healthsync.phone.ui.theme.TextSecondary
import com.healthsync.phone.ui.theme.TextWhite
import com.healthsync.phone.viewmodel.DashboardUiState
import com.healthsync.phone.viewmodel.DashboardViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onWorkoutClick: (WorkoutEntity) -> Unit = {},
    onDeviceClick: () -> Unit = {},
    onWatchFacesClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onWorkoutsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val connState by BluetoothSyncService.connectionState.collectAsState()
    val heartFeedback by BluetoothSyncService.heartRateFeedback.collectAsState()
    val context = LocalContext.current
    var showHeartDetails by remember { mutableStateOf(false) }
    val heartReport = heartFeedback?.diagnosticReport
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var oxygenRequestedAt by remember { mutableLongStateOf(0L) }
    ResumeEffect { viewModel.refreshData(); currentTime = System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        while (true) { delay(5_000); currentTime = System.currentTimeMillis() }
    }
    val heartDetails = heartRateMeasurementDetailsText(heartFeedback, connState.isConnected, currentTime)
    val measureHeart = {
        if (connState.isConnected) {
            val sent = BluetoothSyncService.sendForceMeasureHrToWatch(context)
            Toast.makeText(context, if (sent) "Heart rate requested. Keep your watch on your wrist."
                else "Reconnect your watch and try again.", Toast.LENGTH_SHORT).show()
        } else onDeviceClick()
    }
    val measureOxygen = {
        if (connState.isConnected) {
            val sent = BluetoothSyncService.sendForceMeasureSpO2ToWatch(context)
            if (sent) oxygenRequestedAt = System.currentTimeMillis()
            Toast.makeText(context, if (sent) "Oxygen reading requested. Keep your watch still."
                else "Reconnect your watch and try again.", Toast.LENGTH_SHORT).show()
        } else onDeviceClick()
    }

    if (showHeartDetails && heartDetails != null) {
        AlertDialog(
            onDismissRequest = { showHeartDetails = false },
            title = { Text("Heart-rate measurement") },
            text = {
                Text(heartDetails, fontSize = 12.sp,
                    modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
            },
            confirmButton = {
                if (heartReport != null) TextButton(onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("HealthSync heart-rate report", heartReport))
                        Toast.makeText(context, "Measurement report copied", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Copy report") }
            },
            dismissButton = { TextButton(onClick = { showHeartDetails = false }) { Text("Close") } }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            DashboardHeader(
                connState = connState,
                onReconnect = onDeviceClick,
                onNotifications = onNotificationsClick
            )
        }

        if (uiState.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentCyan) }
        uiState.errorMessage?.let { message ->
            item { HealthCard { Text(message, color = StatusError, fontSize = 13.sp); TextButton(onClick = viewModel::refreshData) { Text("Retry", color = AccentCyan) } } }
        }

        if (!connState.isConnected && !uiState.isLoading && uiState.latestHeartRate == null && uiState.todaySteps == null && uiState.recentWorkouts.isEmpty()) {
            item {
                com.healthsync.phone.ui.components.AppStateCard(Icons.Default.Watch, "Your first sync",
                    "Connect your watch to bring your captured readings, steps and workouts into one place.", "Set up your watch", onDeviceClick)
            }
        }

        item {
            DailySummaryCard(
                steps = uiState.todaySteps?.steps,
                calories = uiState.todaySteps?.calories,
                goal = uiState.stepGoal,
                activeMinutes = recordedWorkoutMinutes(uiState, currentTime)
            )
        }

        item {
            CaptureStatusCard(uiState, connState.isConnected, currentTime, onDeviceClick)
        }

        item {
            QuickControlStrip(
                connected = connState.isConnected,
                onMeasureHr = measureHeart,
                onMeasureSpO2 = measureOxygen,
                onReconnect = onDeviceClick
            )
        }

        item {
            Surface(
                onClick = onWatchFacesClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = BgCard,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AccentCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(24.dp))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Watch Face Studio & Store", color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text("Browse dials and create your own watch face", color = TextDim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("Explore", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalCard(
                    modifier = Modifier.weight(1f),
                    title = "Heart",
                    value = if ((uiState.latestHeartRate?.bpm ?: 0) > 0) "${uiState.latestHeartRate?.bpm}" else "--",
                    unit = "bpm",
                    recordedAtText = "Last captured: ${readingAge(uiState.latestHeartRate?.timestamp, currentTime)}",
                    status = heartRateFeedbackText(heartFeedback, connState.isConnected, uiState.latestHeartRate?.timestamp, currentTime),
                    icon = Icons.Default.Favorite,
                    color = MetricHeart,
                    onTap = measureHeart
                )
                VitalCard(
                    modifier = Modifier.weight(1f),
                    title = "Oxygen",
                    value = if ((uiState.latestSpO2?.percentage ?: 0f) > 0f) String.format(Locale.getDefault(), "%.1f", uiState.latestSpO2?.percentage) else "--",
                    unit = "%",
                    recordedAtText = "Last captured: ${readingAge(uiState.latestSpO2?.timestamp, currentTime)}",
                    status = when {
                        oxygenRequestedAt > (uiState.latestSpO2?.timestamp ?: 0L) && connState.isConnected && currentTime - oxygenRequestedAt < 60_000L -> "Waiting for sensor reading"
                        oxygenRequestedAt > (uiState.latestSpO2?.timestamp ?: 0L) -> "No new reading received · retry"
                        else -> readingAge(uiState.latestSpO2?.timestamp, currentTime)
                    },
                    icon = Icons.Default.Bloodtype,
                    color = MetricSpO2,
                    onTap = measureOxygen
                )
            }
        }

        if (heartDetails != null) item {
            TextButton(onClick = { showHeartDetails = true }) { Text("Heart-rate measurement details", color = AccentCyan) }
        }

        if (uiState.heartRate24h.isNotEmpty()) item {
            HealthCard {
                val readings = uiState.heartRate24h.sortedBy { it.timestamp }
                SectionTitle("Heart rate", "Last 24 hours · ${readings.size} readings")
                Spacer(Modifier.height(8.dp))
                Text("${readings.minOf { it.bpm }}–${readings.maxOf { it.bpm }} bpm · average ${readings.map { it.bpm }.average().toInt()}", color = MetricHeart, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                HeartRateLineChart(readings, Modifier.fillMaxWidth().height(138.dp))
            }
        }

        if (uiState.spO2Last24h.isNotEmpty()) item {
            HealthCard {
                val readings = uiState.spO2Last24h.sortedBy { it.timestamp }
                SectionTitle("Oxygen", "Last 24 hours · ${readings.size} readings")
                Spacer(Modifier.height(8.dp))
                Text(String.format(Locale.getDefault(), "%.1f–%.1f%% recorded", readings.minOf { it.percentage }, readings.maxOf { it.percentage }), color = MetricSpO2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                SpO2LineChart(readings, Modifier.fillMaxWidth().height(132.dp))
            }
        }

        if (uiState.stepsLast24h.isNotEmpty()) {
            item {
                HealthCard {
                    SectionTitle("Movement", "Hourly steps · last 24 hours")
                    Spacer(Modifier.height(12.dp))
                    Steps24HourChart(uiState.stepsLast24h, Modifier.fillMaxWidth().height(118.dp))
                    Text("Recorded steps may include motion estimates. The source is unavailable for saved history.", color = TextDim, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        if (uiState.stepsLast7Days.isNotEmpty()) {
            item {
                HealthCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Week", "${uiState.stepsLast7Days.count { it.steps >= uiState.stepGoal }} goals reached")
                        TrendBadge("${uiState.stepsLast7Days.sumOf { it.steps }} total", MetricSteps)
                    }
                    Spacer(Modifier.height(12.dp))
                    StepsBarChart(uiState.stepsLast7Days.sortedBy { it.dayKey }, uiState.stepGoal, Modifier.fillMaxWidth().height(132.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("${uiState.stepsLast7Days.size} recorded days · ${uiState.stepsLast7Days.map { it.steps }.average().toInt()} steps/day on average", color = TextDim, fontSize = 12.sp)
                    Text("Recorded steps may include motion estimates. The source is unavailable for saved history.", color = TextDim, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }

        uiState.latestSleep?.let { sleep ->
            item { SleepCard(sleep = sleep, history = uiState.sleepLast7Nights) }
        } ?: item {
            HealthCard {
                SectionTitle("Rest tracking", "No captured rest estimate yet")
                Text("Wear your watch overnight. A duration appears after enough motion samples are captured. Sleep stages are unavailable on this watch.", color = TextSecondary, fontSize = 12.sp)
            }
        }

        if (uiState.recentWorkouts.isNotEmpty()) {
            item { WorkoutsPreview(uiState.recentWorkouts, onWorkoutClick, onWorkoutsClick) }
        }

        item {
            Text("Latest steps: ${readingAge(uiState.todaySteps?.timestamp, currentTime).lowercase(Locale.getDefault())}", color = TextDim, fontSize = 12.sp)
        }
    }
}

private fun recordedWorkoutMinutes(state: DashboardUiState, currentTime: Long): Int {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(currentTime))
    return state.recentWorkouts
        .filter { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(it.startTime)) == today }
        .sumOf { workoutDurationSeconds(it) }
        .let { (it / 60).toInt() }
}

@Composable
private fun DashboardHeader(
    connState: ConnectionInfo,
    onReconnect: () -> Unit,
    onNotifications: () -> Unit
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val dateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
    val connected = connState.isConnected

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(greeting, color = TextSecondary, fontSize = 14.sp)
                Text("Today", color = TextWhite, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                Text(dateStr, color = TextDim, fontSize = 13.sp)
            }
            Surface(onClick = onNotifications, shape = CircleShape, color = BgCard,
                border = BorderStroke(1.dp, BorderSubtle), modifier = Modifier.size(48.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Notifications, "Open alerts", tint = AccentCyan, modifier = Modifier.size(22.dp))
                }
            }
        }
        Surface(onClick = onReconnect, shape = RoundedCornerShape(16.dp),
            color = if (connected) StatusSuccess.copy(alpha = 0.08f) else BgCard,
            border = BorderStroke(1.dp, BorderSubtle)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Watch, null, tint = if (connected) StatusSuccess else TextDim, modifier = Modifier.size(18.dp))
                Text(if (connected) "Watch connected" else "Connect your watch", color = if (connected) StatusSuccess else TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(if (connected) "Manage" else "Set up", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun DailySummaryCard(steps: Int?, calories: Double?, goal: Int, activeMinutes: Int) {
    val safeGoal = goal.coerceAtLeast(1)
    val progress by animateFloatAsState(
        targetValue = ((steps ?: 0).toFloat() / safeGoal).coerceIn(0f, 1f),
        animationSpec = tween(900, easing = EaseOutCubic),
        label = "steps-progress"
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = AccentCyan,
        shadowElevation = 2.dp
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(AccentCyan, Color(0xFF245840), Color(0xFF183E2E))
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 14.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(
                            Color.White.copy(alpha = 0.18f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                        drawArc(
                            Color.White,
                            startAngle = -90f,
                            sweepAngle = 360f * progress,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (steps == null) "—" else "${(progress * 100).toInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
                        Text("goal", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.width(18.dp))

                Column(Modifier.weight(1f)) {
                    Text("Daily movement", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(steps?.toString() ?: "—", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                    Text(when { steps == null -> "Waiting for today’s step capture"; steps >= safeGoal -> "Goal reached! +${steps - safeGoal} steps"; else -> "${safeGoal - steps} steps to your goal" }, color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp)
                    Text("Recorded steps may include motion estimates", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryPill(calories?.toInt()?.toString() ?: "—", "est. kcal")
                        SummaryPill("$activeMinutes", "workout min")
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryPill(value: String, label: String) {
    Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = 0.16f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(value, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Color.White.copy(alpha = 0.68f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun QuickControlStrip(
    connected: Boolean,
    onMeasureHr: () -> Unit,
    onMeasureSpO2: () -> Unit,
    onReconnect: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ControlButton("HR", Icons.Default.MonitorHeart, MetricHeart, Modifier.weight(1f), onMeasureHr)
        ControlButton("SpO2", Icons.Default.Bloodtype, MetricSpO2, Modifier.weight(1f), onMeasureSpO2)
        ControlButton(if (connected) "Device" else "Pair", Icons.Default.Bluetooth, AccentBlue, Modifier.weight(1f), onReconnect)
    }
}

@Composable
private fun ControlButton(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(18.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun VitalCard(
    modifier: Modifier,
    title: String,
    value: String,
    unit: String,
    status: String,
    icon: ImageVector,
    color: Color,
    recordedAtText: String? = null,
    onTap: () -> Unit
) {
    HealthCard(modifier = modifier.clickable { onTap() }) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = onTap, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.PlayArrow, "Measure $title", tint = color, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = TextWhite, fontSize = 35.sp, fontWeight = FontWeight.Bold, lineHeight = 37.sp)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
        recordedAtText?.let { Text(it, color = TextDim, fontSize = 11.sp) }
        Text(status, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

private fun readingAge(timestamp: Long?, currentTime: Long): String {
    if (timestamp == null) return "No reading yet"
    val minutes = ((currentTime - timestamp) / 60000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just received"
        minutes < 60 -> "$minutes min ago"
        minutes < 1440 -> "${minutes / 60}h ${minutes % 60}m ago"
        else -> "${minutes / 1440} days ago"
    }
}

@Composable
private fun CaptureStatusCard(state: DashboardUiState, connected: Boolean, currentTime: Long, onDeviceClick: () -> Unit) {
    HealthCard {
        SectionTitle("Your captured data", if (connected) "Watch connected · history stays on this phone" else "Saved history · watch disconnected")
        Spacer(Modifier.height(10.dp))
        Text("${state.heartRate24h.size} heart-rate readings · ${state.spO2Last24h.size} oxygen readings in the last 24 hours", color = TextSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text("Steps: ${readingAge(state.todaySteps?.timestamp, currentTime)}", color = TextDim, fontSize = 12.sp)
        Text("Watch readings retain their capture time. Recorded steps may include motion estimates; the source is unavailable for saved step history. Calories, pace and rest duration are estimates or calculations. Missing measurements stay unavailable.", color = TextDim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        if (!connected) TextButton(onClick = onDeviceClick) { Text("Connect to capture and sync", color = AccentCyan) }
    }
}

@Composable
private fun SleepCard(sleep: SleepEntity, history: List<SleepEntity>) {
    HealthCard {
        SectionTitle("Rest estimate", SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(sleep.endTime)))
        Spacer(Modifier.height(10.dp))
        Text("${sleep.estimatedMinutes / 60}h ${sleep.estimatedMinutes % 60}m", color = MetricSleep, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Motion-based estimate from captured watch samples", color = TextSecondary, fontSize = 13.sp)
        Text("Stillness can include awake rest. Deep, REM and light sleep stages, sleep scores and readiness are unavailable.", color = TextDim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
        if (history.size > 1) {
            Spacer(Modifier.height(12.dp))
            Text("Recent rest estimates", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            history.take(7).forEach { record ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(record.endTime)), color = TextDim, fontSize = 12.sp)
                    Text("${record.estimatedMinutes / 60}h ${record.estimatedMinutes % 60}m", color = MetricSleep, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WorkoutsPreview(workouts: List<WorkoutEntity>, onClick: (WorkoutEntity) -> Unit, onViewAll: () -> Unit) {
    HealthCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Recent Workouts", "${workouts.size} sessions")
            TrendBadge("${workouts.sumOf { it.calories }.toInt()} est. kcal", MetricCalories)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onViewAll) { Text("View all workouts", color = AccentCyan) }
        workouts.take(4).forEachIndexed { index, workout ->
            WorkoutRow(workout) { onClick(workout) }
            if (index < workouts.take(4).lastIndex) Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun WorkoutRow(workout: WorkoutEntity, onClick: () -> Unit) {
    val actUpper = workout.activityType.uppercase().replace(" ", "_")
    val (icon, accent) = workoutVisuals(actUpper)
    val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(workout.startTime))
    val durMin = workoutDurationSeconds(workout) / 60

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = BgCardHover,
        border = BorderStroke(1.dp, BorderSubtle)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(cleanActivityName(workout.activityType), color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$dateStr | ${durMin}m", color = TextDim, fontSize = 11.sp, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${workout.calories.toInt()}", color = MetricCalories, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("est. kcal", color = TextDim, fontSize = 11.sp)
            }
        }
    }
}

private fun workoutVisuals(activity: String): Pair<ImageVector, Color> = when {
    activity.contains("RUN") -> Icons.Default.DirectionsRun to MetricCalories
    activity.contains("WALK") -> Icons.Default.DirectionsWalk to StatusSuccess
    activity.contains("CYCLING") -> Icons.Default.DirectionsRun to AccentBlue
    activity.contains("BADMINTON") -> Icons.Default.SportsTennis to MetricSpO2
    activity.contains("BASKETBALL") -> Icons.Default.SportsBasketball to MetricCalories
    activity.contains("CRICKET") -> Icons.Default.SportsCricket to MetricSteps
    activity.contains("YOGA") -> Icons.Default.SelfImprovement to MetricSleep
    activity.contains("HOME") || activity.contains("STRENGTH") -> Icons.Default.FitnessCenter to AccentPurple
    else -> Icons.Default.FitnessCenter to AccentCyan
}

private fun cleanActivityName(activity: String): String =
    activity.replace("_", " ").lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

@Composable
fun HealthCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(18.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(subtitle, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun TrendBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.12f), border = BorderStroke(1.dp, color.copy(alpha = 0.16f))) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
