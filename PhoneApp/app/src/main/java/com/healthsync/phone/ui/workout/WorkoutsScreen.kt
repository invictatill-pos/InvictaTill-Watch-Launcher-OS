package com.healthsync.phone.ui.workout

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsCricket
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthsync.phone.data.model.WorkoutEntity
import com.healthsync.phone.ui.theme.AccentBlue
import com.healthsync.phone.ui.theme.AccentCyan
import com.healthsync.phone.ui.theme.AccentPurple
import com.healthsync.phone.ui.theme.BgCard
import com.healthsync.phone.ui.theme.BgCardHover
import com.healthsync.phone.ui.theme.BgDeep
import com.healthsync.phone.ui.theme.BgInput
import com.healthsync.phone.ui.theme.BorderSubtle
import com.healthsync.phone.ui.theme.MetricCalories
import com.healthsync.phone.ui.theme.MetricHeart
import com.healthsync.phone.ui.theme.MetricSleep
import com.healthsync.phone.ui.theme.MetricSpO2
import com.healthsync.phone.ui.theme.MetricSteps
import com.healthsync.phone.ui.theme.StatusSuccess
import com.healthsync.phone.ui.theme.TextDim
import com.healthsync.phone.ui.theme.TextPlaceholder
import com.healthsync.phone.ui.theme.TextSecondary
import com.healthsync.phone.ui.theme.TextWhite
import com.healthsync.phone.viewmodel.DashboardViewModel
import com.healthsync.phone.ui.components.ResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutsScreen(
    viewModel: DashboardViewModel,
    onWorkoutClick: (WorkoutEntity) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val workouts = uiState.recentWorkouts
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var period by rememberSaveable { mutableStateOf("All time") }
    var sorting by rememberSaveable { mutableStateOf("Newest") }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExport by rememberSaveable { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExport) } ?: error("Unable to open file") }.isSuccess
            }
            Toast.makeText(context, if (success) "Workout CSV saved" else "Could not save CSV. Try another location.", Toast.LENGTH_LONG).show()
            pendingExport = ""
        }
    }
    ResumeEffect { today = LocalDate.now(); viewModel.refreshData() }
    val since = when (period) {
        "7 days" -> today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        "30 days" -> today.minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        else -> 0L
    }

    val types = remember(workouts) {
        listOf("All") + workouts.map { cleanActivityName(it.activityType) }.distinct()
    }

    val filtered = remember(workouts, selectedFilter, searchQuery, since, sorting) {
        workouts.filter { workout ->
            val type = cleanActivityName(workout.activityType)
            workout.startTime >= since && (selectedFilter == "All" || selectedFilter == type) &&
                (searchQuery.isBlank() || type.contains(searchQuery, ignoreCase = true))
        }.let { matches -> when (sorting) {
            "Oldest" -> matches.sortedBy { it.startTime }
            "Distance" -> matches.sortedByDescending { it.distanceMeters }
            "Duration" -> matches.sortedByDescending { workoutDurationSeconds(it) }
            else -> matches.sortedByDescending { it.startTime }
        } }
    }

    val totalKm = filtered.sumOf { it.distanceMeters / 1000.0 }
    val totalCal = filtered.sumOf { it.calories }
    val totalMins = filtered.sumOf { workoutDurationSeconds(it) } / 60

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Training", color = TextWhite, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
                Text("${filtered.size} sessions in view", color = TextSecondary, fontSize = 13.sp)
            }
        }

        if (uiState.isLoading) {
            item { Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentCyan) } }
        } else if (uiState.errorMessage != null) {
            item { Text(uiState.errorMessage ?: "", color = TextDim); TextButton(onClick = viewModel::refreshData) { Text("Retry") } }
        } else if (workouts.isEmpty()) {
            item {
                EmptyWorkoutState()
            }
        } else {
            item {
                WorkoutTotalsCard(
                    sessions = filtered.size,
                    distanceKm = totalKm,
                    calories = totalCal,
                    minutes = totalMins
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search workouts", color = TextPlaceholder, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextDim, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, "Clear workout search", tint = TextDim, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentCyan.copy(alpha = 0.35f),
                        unfocusedBorderColor = BorderSubtle,
                        focusedTextColor = TextWhite,
                        unfocusedTextColor = TextWhite,
                        cursorColor = AccentCyan,
                        focusedContainerColor = BgInput,
                        unfocusedContainerColor = BgInput
                    )
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All time", "7 days", "30 days")) { label ->
                        FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label) })
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("Newest", "Oldest", "Distance", "Duration")) { label ->
                        FilterChip(selected = sorting == label, onClick = { sorting = label }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
                if (filtered.isNotEmpty()) TextButton(onClick = {
                    pendingExport = workoutsCsv(filtered)
                    exportLauncher.launch("HealthSync-workouts-$today.csv")
                }) { Text("Export ${filtered.size} sessions as CSV", color = AccentCyan) }
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(types) { type ->
                        val selected = selectedFilter == type
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = type },
                            label = { Text(type, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                            shape = RoundedCornerShape(18.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = BgCard,
                                selectedContainerColor = AccentCyan.copy(alpha = 0.13f),
                                labelColor = TextSecondary,
                                selectedLabelColor = AccentCyan
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = BorderSubtle,
                                selectedBorderColor = AccentCyan.copy(alpha = 0.35f)
                            )
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No workouts match", color = TextDim, fontSize = 14.sp)
                            TextButton(onClick = { selectedFilter = "All"; period = "All time"; searchQuery = "" }) { Text("Clear filters", color = AccentCyan) }
                        }
                    }
                }
            } else if (sorting == "Newest" || sorting == "Oldest") {
                filtered.groupBy { SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(it.startTime)) }.forEach { (month, sessions) ->
                    item { Text(month, color = TextDim, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    items(sessions, key = { it.id }) { workout ->
                        WorkoutCard(workout = workout, onClick = { onWorkoutClick(workout) })
                    }
                }
            } else {
                items(filtered, key = { it.id }) { workout -> WorkoutCard(workout, onClick = { onWorkoutClick(workout) }) }
            }
        }
    }
}

@Composable
private fun WorkoutTotalsCard(sessions: Int, distanceKm: Double, calories: Double, minutes: Long) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 1.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(AccentCyan.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Bolt, null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("Workout Log", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("Recent performance", color = TextDim, fontSize = 12.sp)
                    }
                }
                Icon(Icons.Default.CalendarMonth, null, tint = TextDim, modifier = Modifier.size(22.dp))
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TotalStat(sessions.toString(), "Sessions", AccentCyan, Modifier.weight(1f))
                TotalStat(String.format(Locale.getDefault(), "%.1f", distanceKm), "km", StatusSuccess, Modifier.weight(1f))
                TotalStat(calories.toInt().toString(), "kcal", MetricCalories, Modifier.weight(1f))
                TotalStat(if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m", "Time", MetricSpO2, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TotalStat(value: String, label: String, color: Color, modifier: Modifier) {
    Surface(modifier = modifier.height(72.dp), shape = RoundedCornerShape(18.dp), color = color.copy(alpha = 0.1f)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(value, color = color, fontSize = 18.sp, fontWeight = FontWeight.Black, maxLines = 1)
            Text(label, color = TextDim, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyWorkoutState() {
    Box(modifier = Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(CircleShape)
                    .background(AccentCyan.copy(alpha = 0.1f))
                    .border(1.dp, AccentCyan.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.DirectionsRun, null, tint = AccentCyan, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("No workouts yet", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 20.sp)
            Text("Start one from the watch", color = TextDim, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun WorkoutCard(workout: WorkoutEntity, onClick: () -> Unit) {
    val dateStr = SimpleDateFormat("EEE, MMM d | HH:mm", Locale.getDefault()).format(Date(workout.startTime))
    val durMin = workoutDurationSeconds(workout) / 60
    val actUpper = workout.activityType.uppercase().replace(" ", "_")
    val (icon, accent) = workoutVisuals(actUpper)
    val mainMetric = when {
        actUpper.contains("BADMINTON") && workout.swingsCount > 0 -> workout.swingsCount.toString() to "swings"
        (actUpper.contains("HOME") || actUpper.contains("STRENGTH")) && workout.swingsCount > 0 -> workout.swingsCount.toString() to "reps"
        workout.distanceMeters > 0 -> String.format(Locale.getDefault(), "%.2f", workout.distanceMeters / 1000f) to "km"
        workout.steps > 0 -> workout.steps.toString() to "steps"
        else -> "--" to "metric"
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BgCard),
        border = BorderStroke(1.dp, BorderSubtle),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .height(118.dp)
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.28f))))
            )
            Row(
                modifier = Modifier.weight(1f).padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                        Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(cleanActivityName(workout.activityType), color = TextWhite, fontWeight = FontWeight.Black, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(dateStr, color = TextDim, fontSize = 12.sp, maxLines = 1)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            InlineMetric(Icons.Default.Timer, "${durMin}m", accent)
                            if (workout.avgHeartRate > 0) InlineMetric(Icons.Default.Favorite, "${workout.avgHeartRate} bpm", MetricHeart)
                            InlineMetric(Icons.Default.LocalFireDepartment, "${workout.calories.toInt()} kcal", MetricCalories)
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(mainMetric.first, color = accent, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1)
                    Text(mainMetric.second, color = TextDim, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun InlineMetric(icon: ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, null, tint = color, modifier = Modifier.size(12.dp))
        Text(value, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

private fun workoutVisuals(activity: String): Pair<ImageVector, Color> = when {
    activity.contains("RUN") -> Icons.Default.DirectionsRun to MetricCalories
    activity.contains("WALK") -> Icons.Default.DirectionsWalk to StatusSuccess
    activity.contains("CYCLING") -> Icons.Default.DirectionsBike to AccentBlue
    activity.contains("BADMINTON") -> Icons.Default.SportsTennis to MetricSpO2
    activity.contains("BASKETBALL") -> Icons.Default.SportsBasketball to MetricCalories
    activity.contains("CRICKET") -> Icons.Default.SportsCricket to MetricSteps
    activity.contains("YOGA") -> Icons.Default.SelfImprovement to MetricSleep
    activity.contains("HOME") || activity.contains("STRENGTH") -> Icons.Default.FitnessCenter to AccentPurple
    else -> Icons.Default.FitnessCenter to AccentCyan
}

private fun cleanActivityName(activity: String): String =
    activity.replace("_", " ").lowercase(Locale.getDefault()).replaceFirstChar { it.titlecase(Locale.getDefault()) }
