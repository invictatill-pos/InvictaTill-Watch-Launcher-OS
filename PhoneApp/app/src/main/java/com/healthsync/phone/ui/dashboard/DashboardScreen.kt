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
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.data.heartRateFeedbackText
import com.healthsync.phone.data.activeHeartRateSensorPreview
import com.healthsync.phone.data.heartRateMeasurementDetailsText
import com.healthsync.phone.ui.charts.SleepStackedBarChart
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
    onDeviceClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val connState by BluetoothSyncService.connectionState.collectAsState()
    val heartFeedback by BluetoothSyncService.heartRateFeedback.collectAsState()
    val context = LocalContext.current
    var showHeartDetails by remember { mutableStateOf(false) }
    val heartReport = heartFeedback?.diagnosticReport
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    ResumeEffect { viewModel.refreshData(); currentTime = System.currentTimeMillis() }
    LaunchedEffect(Unit) {
        while (true) { delay(5_000); currentTime = System.currentTimeMillis() }
    }
    val heartPreview = activeHeartRateSensorPreview(heartFeedback, connState.isConnected, currentTime)
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
            BluetoothSyncService.sendForceMeasureSpO2ToWatch(context)
            Toast.makeText(context, "Oxygen reading requested from your watch.", Toast.LENGTH_SHORT).show()
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
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            DashboardHeader(
                connState = connState,
                onReconnect = onDeviceClick
            )
        }

        if (uiState.isLoading) item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AccentCyan) }
        uiState.errorMessage?.let { message ->
            item { HealthCard { Text(message, color = StatusError, fontSize = 13.sp); TextButton(onClick = viewModel::refreshData) { Text("Retry", color = AccentCyan) } } }
        }

        if (!uiState.isLoading && uiState.latestHeartRate == null && uiState.todaySteps == null && uiState.recentWorkouts.isEmpty()) {
            item {
                HealthCard {
                    SectionTitle("Your first sync", "Connect your watch to get started")
                    Spacer(Modifier.height(8.dp))
                    Text("Your readings, daily steps and workout history appear automatically after the watch connects.", color = TextSecondary, fontSize = 13.sp)
                    TextButton(onClick = onDeviceClick) { Text("Set up your watch", color = AccentCyan) }
                }
            }
        }

        item {
            DailySummaryCard(
                steps = uiState.todaySteps?.steps ?: 0,
                calories = uiState.todaySteps?.calories ?: 0.0,
                goal = uiState.stepGoal,
                activeMinutes = estimateActiveMinutes(uiState)
            )
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalCard(
                    modifier = Modifier.weight(1f),
                    title = "Heart",
                    value = heartPreview?.bpm?.toString()
                        ?: if ((uiState.latestHeartRate?.bpm ?: 0) > 0) "${uiState.latestHeartRate?.bpm}" else "--",
                    unit = "bpm",
                    status = heartRateFeedbackText(heartFeedback, connState.isConnected, uiState.latestHeartRate?.timestamp, currentTime),
                    icon = Icons.Default.Favorite,
                    color = MetricHeart,
                    onTap = measureHeart
                )
                VitalCard(
                    modifier = Modifier.weight(1f),
                    title = "Oxygen",
                    value = if ((uiState.latestSpO2?.percentage ?: 0f) > 0f) "${uiState.latestSpO2?.percentage?.toInt()}" else "--",
                    unit = "%",
                    status = readingAge(uiState.latestSpO2?.timestamp, currentTime),
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
                Text("${readings.minOf { it.percentage }.toInt()}–${readings.maxOf { it.percentage }.toInt()}% recorded", color = MetricSpO2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                SpO2LineChart(readings, Modifier.fillMaxWidth().height(132.dp))
            }
        }

        if (uiState.stepsLast24h.isNotEmpty()) {
            item {
                HealthCard {
                    SectionTitle("Movement", "Hourly steps · last 24 hours")
                    Spacer(Modifier.height(12.dp))
                    Steps24HourChart(uiState.stepsLast24h, Modifier.fillMaxWidth().height(118.dp))
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
                }
            }
        }

        uiState.latestSleep?.let { sleep ->
            item { SleepCard(sleep = sleep, history = uiState.sleepLast7Nights) }
        }

        if (uiState.recentWorkouts.isNotEmpty()) {
            item { WorkoutsPreview(uiState.recentWorkouts, onWorkoutClick) }
        }

        item {
            Text("Latest steps: ${readingAge(uiState.todaySteps?.timestamp, currentTime).lowercase(Locale.getDefault())}", color = TextDim, fontSize = 12.sp)
        }
    }
}

private fun estimateActiveMinutes(state: DashboardUiState): Int {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    return state.recentWorkouts
        .filter { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.startTime)) == today }
        .sumOf { workoutDurationSeconds(it) }
        .let { (it / 60).toInt() }
}

@Composable
private fun DashboardHeader(connState: ConnectionInfo, onReconnect: () -> Unit) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val dateStr = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date())
    val connected = connState.isConnected

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(greeting, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("HealthSync", color = TextWhite, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
            Text(dateStr, color = TextDim, fontSize = 13.sp)
        }

        Surface(
            onClick = onReconnect,
            shape = RoundedCornerShape(24.dp),
            color = if (connected) StatusSuccess.copy(alpha = 0.12f) else StatusError.copy(alpha = 0.1f),
            border = BorderStroke(1.dp, if (connected) StatusSuccess.copy(alpha = 0.22f) else StatusError.copy(alpha = 0.22f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Icon(Icons.Default.Watch, null, tint = if (connected) StatusSuccess else StatusError, modifier = Modifier.size(16.dp))
                Text(if (connected) "Online" else "Offline", color = if (connected) StatusSuccess else StatusError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun DailySummaryCard(steps: Int, calories: Double, goal: Int, activeMinutes: Int) {
    val safeGoal = goal.coerceAtLeast(1)
    val progress by animateFloatAsState(
        targetValue = (steps.toFloat() / safeGoal).coerceIn(0f, 1f),
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
                        listOf(AccentCyan, Color(0xFF28745F), Color(0xFF25382F))
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(138.dp), contentAlignment = Alignment.Center) {
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
                        Text("${(progress * 100).toInt()}%", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black, lineHeight = 26.sp)
                        Text("goal", color = Color.White.copy(alpha = 0.72f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(Modifier.width(18.dp))

                Column(Modifier.weight(1f)) {
                    Text("Daily Progress", color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(steps.toString(), color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
                    Text(if (steps >= safeGoal) "Goal reached! +${steps - safeGoal} steps" else "${safeGoal - steps} steps to your goal", color = Color.White.copy(alpha = 0.76f), fontSize = 13.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryPill("${calories.toInt()}", "kcal")
                        SummaryPill("$activeMinutes", "min")
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
            Text(label, color = Color.White.copy(alpha = 0.68f), fontSize = 10.sp)
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
            Text(value, color = TextWhite, fontSize = 35.sp, fontWeight = FontWeight.Black, lineHeight = 37.sp)
            Spacer(Modifier.width(4.dp))
            Text(unit, color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp))
        }
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
private fun SleepCard(sleep: SleepEntity, history: List<SleepEntity>) {
    val total = ((sleep.endTime - sleep.startTime) / 60000).coerceAtLeast(0)

    HealthCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Sleep", SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(sleep.endTime)))
            TrendBadge("${total / 60}h ${total % 60}m", MetricSleep)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SleepStage("Deep", sleep.deepMinutes, SleepDeep)
            SleepStage("REM", sleep.remMinutes, SleepRem)
            SleepStage("Light", sleep.lightMinutes, SleepLight)
            SleepStage("Awake", sleep.awakeMinutes, SleepAwake)
        }
        if (history.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            SleepStackedBarChart(history, Modifier.fillMaxWidth().height(104.dp))
        }
    }
}

@Composable
private fun SleepStage(label: String, mins: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.height(5.dp))
        Text("${mins / 60}h ${mins % 60}m", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextDim, fontSize = 10.sp)
    }
}

@Composable
private fun WorkoutsPreview(workouts: List<WorkoutEntity>, onClick: (WorkoutEntity) -> Unit) {
    HealthCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Recent Workouts", "${workouts.size} sessions")
            TrendBadge("${workouts.sumOf { it.calories }.toInt()} kcal", MetricCalories)
        }
        Spacer(Modifier.height(12.dp))
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
                Text("${workout.calories.toInt()}", color = MetricCalories, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("kcal", color = TextDim, fontSize = 10.sp)
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
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = TextWhite, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(subtitle, color = TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun TrendBadge(text: String, color: Color) {
    Surface(shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.12f), border = BorderStroke(1.dp, color.copy(alpha = 0.16f))) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}
