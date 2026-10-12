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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
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
import com.healthsync.phone.data.model.HeartRateEntity
import com.healthsync.phone.ui.charts.HeartRateLineChart
import com.healthsync.phone.data.SyncValidation
import com.healthsync.phone.data.distanceSourceLabel
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
fun WorkoutDetailsScreen(workout: WorkoutEntity, heartRates: List<HeartRateEntity> = emptyList(), onBack: () -> Unit) {
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
            Toast.makeText(context, if (success) "Location route saved" else "Could not save route. Try another location.", Toast.LENGTH_LONG).show()
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
                        Icon(Icons.Default.FileDownload, "Save location route as GPX", tint = AccentCyan)
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                    DetailStatCard(Modifier.weight(1f), Icons.Default.LocalFireDepartment, MetricCalories, "Est. calories", "${workout.calories.toInt()} kcal")
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
                if (isRunOrWalk) Text("Recorded steps may include motion estimates. The step source is unavailable for saved sessions.", color = TextDim, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 8.dp))
            }

            item {
                PerformanceCard(workout = workout)
            }

            item {
                Text(distanceSourceLabel(workout.distanceSource), color = TextDim, fontSize = 12.sp)
                Text("Calories are estimated from activity time and profile values.", color = TextDim, fontSize = 12.sp)
            }

            item { CapturedHeartRateCard(heartRates) }

            if (isRunOrWalk) {
                item {
                    RouteCard(route = route, workout = workout, accent = accent)
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
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = 0.12f)).border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = TextWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Text(mainValue, color = accent, fontSize = 58.sp, fontWeight = FontWeight.Bold, lineHeight = 62.sp, maxLines = 1)
            Text(mainLabel, color = TextSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(footnote, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
            Text("Session Metrics", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
private fun CapturedHeartRateCard(heartRates: List<HeartRateEntity>) {
    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = BgCard), border = BorderStroke(1.dp, BorderSubtle)) {
        Column(Modifier.padding(16.dp)) {
            Text("Captured heart rate", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(10.dp))
            if (heartRates.isEmpty()) {
                Text("No individual heart-rate samples are saved for this session. A recorded average alone cannot show a peak or time in heart-rate zones.", color = TextDim, fontSize = 12.sp, lineHeight = 17.sp)
            } else {
                Text("${heartRates.size} readings during this session · ${heartRates.minOf { it.bpm }}–${heartRates.maxOf { it.bpm }} bpm", color = MetricHeart, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                HeartRateLineChart(heartRates, Modifier.fillMaxWidth().height(160.dp))
                Text("Dots are captured sensor samples. Gaps have no measurements. Samples older than 30 days may have been removed from phone history.", color = TextDim, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun RouteCard(route: List<LatLngPoint>, workout: WorkoutEntity, accent: Color) {
    val splits = remember(route) { calculateCapturedSplits(route) }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Route, null, tint = accent, modifier = Modifier.size(20.dp))
                    Text("Recorded location route", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                if (route.size > 1) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = "${route.size} location points",
                            color = accent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (route.size > 1) {
                GpsRouteCanvas(
                    route = route,
                    accentColor = accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .clip(RoundedCornerShape(18.dp))
                )

                Spacer(Modifier.height(10.dp))

                // Dynamic Pace Gradient Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PaceLegendDot("Faster", Color(0xFF00E676))
                    PaceLegendDot("Steady", Color(0xFFFFD600))
                    PaceLegendDot("Slower", Color(0xFFFF5252))
                }

                // Km Split chips if workout recorded splits
                if (splits.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Kilometre Splits", color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(splits) { split ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BgCardHover,
                                border = BorderStroke(1.dp, BorderSubtle)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("KM ${split.kmNumber}", color = AccentCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    val mins = split.paceSecondsPerKm / 60
                                    val secs = split.paceSecondsPerKm % 60
                                    Text(
                                        text = String.format(Locale.getDefault(), "%d:%02d /km", mins, secs),
                                        color = TextWhite,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(118.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(BgCardHover),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.LocationOff, null, tint = TextDim, modifier = Modifier.size(30.dp))
                        Text("No location route recorded", color = TextDim, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaceLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
                Text("Session Note", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
            Text(value, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
fun GpsRouteCanvas(route: List<LatLngPoint>, accentColor: Color, modifier: Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF141A16))) {
        if (route.size < 2) return@Canvas

        val minLat = route.minOf { it.lat }
        val maxLat = route.maxOf { it.lat }
        val minLng = route.minOf { it.lng }
        val maxLng = route.maxOf { it.lng }
        val latRange = (maxLat - minLat).coerceAtLeast(0.0001)
        val longitudeScale = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(0.01)
        val lngRange = ((maxLng - minLng) * longitudeScale).coerceAtLeast(0.0001)
        val pad = 0.12f
        val drawWidth = size.width * (1f - 2 * pad)
        val drawHeight = size.height * (1f - 2 * pad)
        val offsetX = size.width * pad
        val offsetY = size.height * pad
        val uniformScale = minOf(drawWidth / lngRange, drawHeight / latRange)
        val centeredX = offsetX + (drawWidth - lngRange * uniformScale) / 2
        val centeredY = offsetY + (drawHeight - latRange * uniformScale) / 2
        val gridColor = Color.White.copy(alpha = 0.05f)

        for (i in 1..3) {
            val x = offsetX + drawWidth * i / 4f
            val y = offsetY + drawHeight * i / 4f
            drawLine(gridColor, Offset(x, offsetY), Offset(x, offsetY + drawHeight), strokeWidth = 1f)
            drawLine(gridColor, Offset(offsetX, y), Offset(offsetX + drawWidth, y), strokeWidth = 1f)
        }

        fun mapPoint(point: LatLngPoint): Offset {
            val x = (centeredX + (point.lng - minLng) * longitudeScale * uniformScale).toFloat()
            val y = (centeredY + (maxLat - point.lat) * uniformScale).toFloat()
            return Offset(x, y)
        }

        // Calculate segment speeds to create pace-gradient colors
        val speeds = mutableListOf<Double>()
        for (i in 1 until route.size) {
            val p0 = route[i - 1]
            val p1 = route[i]
            val dist = haversineMeters(p0.lat, p0.lng, p1.lat, p1.lng)
            val dt = (p1.timestamp - p0.timestamp) / 1000.0
            val speed = if (dt in 0.5..60.0 && dist > 0.0) dist / dt else 0.0
            speeds.add(speed)
        }

        val validSpeeds = speeds.filter { it > 0.1 }
        val minSpeed = validSpeeds.minOrNull() ?: 0.0
        val maxSpeed = validSpeeds.maxOrNull() ?: 0.0
        val speedRange = (maxSpeed - minSpeed).coerceAtLeast(0.5)

        // Draw pace-colored segments with halo and crisp line
        for (i in 1 until route.size) {
            val p0 = route[i - 1]
            val p1 = route[i]
            if (p1.segmentStart) continue

            val speed = speeds.getOrElse(i - 1) { 0.0 }
            val ratio = ((speed - minSpeed) / speedRange).toFloat().coerceIn(0f, 1f)
            val segmentColor = when {
                speed <= 0.0 -> accentColor
                ratio >= 0.65f -> Color(0xFF00E676) // Fast / Sprint
                ratio >= 0.35f -> Color(0xFFFFD600) // Steady
                else -> Color(0xFFFF5252)           // Slow / Incline
            }

            val startOffset = mapPoint(p0)
            val endOffset = mapPoint(p1)

            // Halo glow
            drawLine(
                color = segmentColor.copy(alpha = 0.28f),
                start = startOffset,
                end = endOffset,
                strokeWidth = 14f,
                cap = StrokeCap.Round
            )
            // Crisp route line
            drawLine(
                color = segmentColor,
                start = startOffset,
                end = endOffset,
                strokeWidth = 5.5f,
                cap = StrokeCap.Round
            )
        }

        // Start point badge
        val startPt = mapPoint(route.first())
        drawCircle(Color(0xFF00E676).copy(alpha = 0.25f), 14f, startPt)
        drawCircle(Color(0xFF00E676), 7f, startPt)
        drawCircle(Color.White, 3f, startPt)

        // Finish point badge
        val endPt = mapPoint(route.last())
        drawCircle(Color(0xFFFF5252).copy(alpha = 0.25f), 14f, endPt)
        drawCircle(Color(0xFFFF5252), 7f, endPt)
        drawCircle(Color.White, 3f, endPt)
    }
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    return r * c
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
