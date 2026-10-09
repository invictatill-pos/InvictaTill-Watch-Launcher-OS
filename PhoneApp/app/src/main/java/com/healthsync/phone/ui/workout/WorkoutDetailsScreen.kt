package com.healthsync.phone.ui.workout

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.healthsync.phone.data.model.LatLngPoint
import com.healthsync.phone.data.model.WorkoutEntity
import com.healthsync.phone.data.SyncValidation
import com.healthsync.phone.ui.theme.AccentCyan
import com.healthsync.phone.ui.theme.AccentPurple
import com.healthsync.phone.ui.theme.BgCard
import com.healthsync.phone.ui.theme.BgCardHover
import com.healthsync.phone.ui.theme.BgDeep
import com.healthsync.phone.ui.theme.BorderSubtle
import com.healthsync.phone.ui.theme.MetricCalories
import com.healthsync.phone.ui.theme.MetricHeart
import com.healthsync.phone.ui.theme.MetricSpO2
import com.healthsync.phone.ui.theme.StatusError
import com.healthsync.phone.ui.theme.StatusSuccess
import com.healthsync.phone.ui.theme.StatusWarning
import com.healthsync.phone.ui.theme.TextDim
import com.healthsync.phone.ui.theme.TextSecondary
import com.healthsync.phone.ui.theme.TextWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailsScreen(workout: WorkoutEntity, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val route: List<LatLngPoint> = remember(workout.routeJson, workout.startTime, workout.endTime) { try {
        val validated = SyncValidation.sanitizeRouteJson(workout.routeJson, workout.startTime, workout.endTime) ?: "[]"
        Gson().fromJson<List<LatLngPoint>>(validated, object : TypeToken<List<LatLngPoint>>() {}.type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(workoutGpx(workout, route)) } ?: error("Unable to open file") }.isSuccess
            }
            Toast.makeText(context, if (success) "GPS route saved" else "Could not save route. Try another location.", Toast.LENGTH_LONG).show()
        }
    }

    val durationMs = workoutDurationSeconds(workout) * 1000
    val mins = (durationMs / 60000).toInt()
    val secs = ((durationMs / 1000) % 60).toInt()
    val distKm = workout.distanceMeters / 1000f
    val dateLine = "${SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(workout.startTime))} | ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(workout.startTime))}"
    val actUpper = workout.activityType.uppercase().replace(" ", "_")
    val isBadminton = actUpper.contains("BADMINTON")
    val isHome = actUpper.contains("HOME") || actUpper.contains("STRENGTH")
    val isRunOrWalk = !isBadminton && !isHome
    val (actIcon, accent) = activityVisual(actUpper)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextWhite)
                    }
                },
                actions = {
                    if (route.size > 1) IconButton(onClick = { exportLauncher.launch("HealthSync-route-${workout.startTime}.gpx") }) {
                        Icon(Icons.Default.FileDownload, "Save GPS route as GPX", tint = AccentCyan)
                    }
                    IconButton(onClick = {
                        try {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, workoutSummary(workout))
                            }, "Share workout"))
                        } catch (_: Exception) { Toast.makeText(context, "No sharing app is available", Toast.LENGTH_SHORT).show() }
                    }) { Icon(Icons.Default.Share, "Share workout summary", tint = AccentCyan) }
                },
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        containerColor = BgDeep
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 34.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                DetailHeader(
                    title = cleanActivityName(workout.activityType),
                    dateLine = dateLine,
                    icon = actIcon,
                    accent = accent
                )
            }

            item {
                HeroMetricCard(
                    accent = accent,
                    mainValue = when {
                        distKm > 0 -> String.format(Locale.getDefault(), "%.2f", distKm)
                        workout.swingsCount > 0 -> workout.swingsCount.toString()
                        else -> String.format(Locale.getDefault(), "%d:%02d", mins, secs)
                    },
                    mainLabel = when {
                        distKm > 0 -> "Kilometres"
                        workout.swingsCount > 0 && isBadminton -> "Swings"
                        workout.swingsCount > 0 -> "Repetitions"
                        else -> "Active time"
                    },
                    footnote = if (isRunOrWalk && durationMs > 0 && distKm > 0f) {
                        formatPace(durationMs, distKm)
                    } else {
                        "Recorded on your watch"
                    }
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailStatCard(Modifier.weight(1f), Icons.Default.Timer, AccentCyan, if (workout.durationSeconds > 0) "Active time" else "Duration", String.format(Locale.getDefault(), "%d:%02d", mins, secs))
                    DetailStatCard(Modifier.weight(1f), Icons.Default.Favorite, MetricHeart, "Avg HR", if (workout.avgHeartRate > 0) "${workout.avgHeartRate} bpm" else "-- bpm")
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailStatCard(Modifier.weight(1f), Icons.Default.LocalFireDepartment, MetricCalories, "Calories", "${workout.calories.toInt()} kcal")
                    DetailStatCard(
                        Modifier.weight(1f),
                        if (isRunOrWalk) Icons.Default.DirectionsWalk else Icons.Default.FitnessCenter,
                        StatusSuccess,
                        if (isRunOrWalk) "Steps" else if (isBadminton) "Swings" else "Repetitions",
                        if (isRunOrWalk) {
                            if (workout.steps > 0) "${workout.steps}" else "--"
                        } else {
                            if (workout.swingsCount > 0) workout.swingsCount.toString() else "--"
                        }
                    )
                }
            }

            item {
                PerformanceCard(workout = workout)
            }

            if (isRunOrWalk) {
                item {
                    RouteCard(route = route, accent = accent)
                }
            }

            item {
                InsightCard(workout = workout, minutes = mins)
            }
        }
    }
}

@Composable
private fun DetailHeader(title: String, dateLine: String, icon: ImageVector, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = 0.12f)).border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dateLine, color = TextDim, fontSize = 12.sp)
        }
    }
}

@Composable
private fun HeroMetricCard(accent: Color, mainValue: String, mainLabel: String, footnote: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = accent.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
    ) {
        Column(
            Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(mainValue, color = accent, fontSize = 58.sp, fontWeight = FontWeight.Black, lineHeight = 62.sp, maxLines = 1)
            Text(mainLabel, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(footnote, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun PerformanceCard(workout: WorkoutEntity) {
    val seconds = workoutDurationSeconds(workout)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Session Metrics", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 18.sp)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PerfMetric("Cal/min", if (seconds > 0) "%.1f".format(workout.calories * 60.0 / seconds) else "—", AccentCyan)
                PerfMetric("Steps/min", if (seconds > 0 && workout.steps > 0) (workout.steps * 60L / seconds).toString() else "—", StatusSuccess)
                PerfMetric("Avg speed", if (seconds > 0 && workout.distanceMeters > 0) "%.1f km/h".format(workout.distanceMeters * 3.6 / seconds) else "—", MetricSpO2)
            }
        }
    }
}

@Composable
private fun RouteCard(route: List<LatLngPoint>, accent: Color) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Route, null, tint = accent, modifier = Modifier.size(20.dp))
                Text("GPS Route", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (route.size > 1) {
                GpsRouteCanvas(route = route, accentColor = accent, modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(18.dp)))
            } else {
                Box(Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(18.dp)).background(BgCardHover), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LocationOff, null, tint = TextDim, modifier = Modifier.size(30.dp))
                        Text("No GPS route recorded", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightCard(workout: WorkoutEntity, minutes: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = AccentCyan.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(AccentCyan.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.AutoAwesome, null, tint = AccentCyan, modifier = Modifier.size(17.dp))
                }
                Text("Session Note", color = AccentCyan, fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
            Text(generateAdvancedAdvice(workout), color = TextWhite, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightTag(cleanActivityName(workout.activityType))
                InsightTag(if (minutes > 30) "Endurance" else "Quick Session")
            }
        }
    }
}

@Composable
private fun PerfMetric(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Text(label, color = TextDim, fontSize = 11.sp)
    }
}

@Composable
private fun InsightTag(label: String) {
    Surface(shape = RoundedCornerShape(18.dp), color = AccentCyan.copy(alpha = 0.1f)) {
        Text(label, color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
fun DetailStatCard(modifier: Modifier, icon: ImageVector, iconColor: Color, label: String, value: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BgCard), border = BorderStroke(1.dp, BorderSubtle), modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, null, tint = iconColor, modifier = Modifier.size(15.dp))
                Text(label, color = TextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(value, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

@Composable
fun GpsRouteCanvas(route: List<LatLngPoint>, accentColor: Color, modifier: Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF18201B))) {
        if (route.size < 2) return@Canvas

        val minLat = route.minOf { it.lat }
        val maxLat = route.maxOf { it.lat }
        val minLng = route.minOf { it.lng }
        val maxLng = route.maxOf { it.lng }
        val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
        val longitudeScale = cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.01)
        val lngRange = ((maxLng - minLng) * longitudeScale).coerceAtLeast(0.0001)
        val pad = 0.12f
        val drawWidth = size.width * (1f - 2 * pad)
        val drawHeight = size.height * (1f - 2 * pad)
        val offsetX = size.width * pad
        val offsetY = size.height * pad
        val uniformScale = minOf(drawWidth / lngRange, drawHeight / latRange)
        val centeredX = offsetX + (drawWidth - lngRange * uniformScale) / 2
        val centeredY = offsetY + (drawHeight - latRange * uniformScale) / 2
        val gridColor = Color.White.copy(alpha = 0.06f)

        for (i in 1..3) {
            val x = offsetX + drawWidth * i / 4f
            val y = offsetY + drawHeight * i / 4f
            drawLine(gridColor, Offset(x, offsetY), Offset(x, offsetY + drawHeight), strokeWidth = 1f)
            drawLine(gridColor, Offset(offsetX, y), Offset(offsetX + drawWidth, y), strokeWidth = 1f)
        }

        val path = Path()
        route.forEachIndexed { index, point ->
            val x = (centeredX + (point.lng - minLng) * longitudeScale * uniformScale).toFloat()
            val y = (centeredY + (maxLat - point.lat) * uniformScale).toFloat()
            if (index == 0 || point.segmentStart) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path, accentColor.copy(alpha = 0.22f), style = Stroke(width = 16f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, accentColor, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))

        fun mapPoint(point: LatLngPoint): Offset {
            val x = (centeredX + (point.lng - minLng) * longitudeScale * uniformScale).toFloat()
            val y = (centeredY + (maxLat - point.lat) * uniformScale).toFloat()
            return Offset(x, y)
        }
        drawCircle(StatusSuccess, 10f, mapPoint(route.first()))
        drawCircle(StatusError, 10f, mapPoint(route.last()))
    }
}

private fun activityVisual(activity: String): Pair<ImageVector, Color> = when {
    activity.contains("RUN") -> Icons.Default.DirectionsRun to MetricCalories
    activity.contains("WALK") -> Icons.Default.DirectionsWalk to StatusSuccess
    activity.contains("BADMINTON") -> Icons.Default.SportsTennis to MetricSpO2
    activity.contains("HOME") || activity.contains("STRENGTH") -> Icons.Default.FitnessCenter to AccentPurple
    else -> Icons.Default.FitnessCenter to AccentCyan
}

private fun cleanActivityName(activity: String): String =
    activity.replace("_", " ").lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }

fun generateAdvancedAdvice(workout: WorkoutEntity): String {
    val minutes = workoutDurationSeconds(workout) / 60
    return buildString {
        append("You recorded $minutes minutes of ${cleanActivityName(workout.activityType).lowercase(Locale.getDefault())}")
        if (workout.distanceMeters > 0) append(" over ${String.format(Locale.getDefault(), "%.2f", workout.distanceMeters / 1000f)} km")
        append(". Compare sessions of the same activity in Training to follow your progress.")
        if (workout.avgHeartRate <= 0) append(" Heart rate was not recorded for this session.")
    }
}

private fun formatPace(durationMs: Long, distanceKm: Float): String {
    val seconds = (durationMs / 1000.0 / distanceKm).toLong()
    return String.format(Locale.getDefault(), "%d:%02d min/km", seconds / 60, seconds % 60)
}
