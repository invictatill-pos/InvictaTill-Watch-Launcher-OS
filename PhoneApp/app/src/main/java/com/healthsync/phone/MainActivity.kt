package com.healthsync.phone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.service.CallMonitorService
import com.healthsync.phone.ui.dashboard.DashboardScreen
import com.healthsync.phone.ui.device.DeviceScreen
import com.healthsync.phone.ui.notifications.NotificationsScreen
import com.healthsync.phone.ui.settings.SettingsScreen
import com.healthsync.phone.ui.theme.*
import com.healthsync.phone.ui.workout.WorkoutDetailsScreen
import com.healthsync.phone.ui.workout.WorkoutsScreen
import com.healthsync.phone.viewmodel.DashboardViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requiredPermissions: Array<String>
        get() {
            val perms = mutableListOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CONTACTS)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) perms += Manifest.permission.ANSWER_PHONE_CALLS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms += Manifest.permission.BLUETOOTH_CONNECT
                perms += Manifest.permission.BLUETOOTH_SCAN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) perms += Manifest.permission.POST_NOTIFICATIONS
            return perms.toTypedArray()
        }

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { startServicesIfPossible() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val isFirstLaunch = getSharedPreferences("app_prefs", MODE_PRIVATE).getBoolean("first_launch", true)
        if (!isFirstLaunch) startServicesIfPossible()

        setContent {
            HealthSyncTheme {
                var showOnboarding by rememberSaveable { mutableStateOf(isFirstLaunch) }
                if (showOnboarding) {
                    OnboardingScreen(onComplete = {
                        getSharedPreferences("app_prefs", MODE_PRIVATE).edit().putBoolean("first_launch", false).apply()
                        showOnboarding = false
                        requestMissingPermissions()
                    })
                } else {
                    MainAppContent(onRequestPermissions = { requestMissingPermissions() })
                }
            }
        }
    }

    private fun requestMissingPermissions() {
        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
        else startServicesIfPossible()
    }

    private fun startServicesIfPossible() {
        val bluetoothGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        if (bluetoothGranted) {
            try { ContextCompat.startForegroundService(this, Intent(this, BluetoothSyncService::class.java)) }
            catch (e: Exception) { android.util.Log.e("MainActivity", "Bluetooth service could not start", e) }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            try { ContextCompat.startForegroundService(this, Intent(this, CallMonitorService::class.java)) }
            catch (e: Exception) { android.util.Log.e("MainActivity", "Call service could not start", e) }
        }
    }
}

// ── Navigation ────────────────────────────────────────────────────────────────

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Dashboard)
    object Workouts : Screen("workouts", "Workouts", Icons.Default.DirectionsRun)
    object Notifications : Screen("notifications", "Alerts", Icons.Default.Notifications)
    object Device : Screen("device", "Device", Icons.Default.Watch)
    object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

val bottomScreens = listOf(Screen.Dashboard, Screen.Workouts, Screen.Notifications, Screen.Device, Screen.Settings)

@Composable
fun MainAppContent(onRequestPermissions: () -> Unit = {}) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val updateViewModel: com.healthsync.phone.viewmodel.AppUpdateViewModel = hiltViewModel()
    val context = androidx.compose.ui.platform.LocalContext.current
    val showUpdateDialog by updateViewModel.showDialog.collectAsState()
    val updateInfo by updateViewModel.dialogUpdateInfo.collectAsState()
    val updateStatus by updateViewModel.status.collectAsState()
    val isWatchUpdate by updateViewModel.isWatchUpdate.collectAsState()

    LaunchedEffect(Unit) {
        updateViewModel.checkForUpdates(silent = true)
    }

    if (showUpdateDialog && updateInfo != null) {
        com.healthsync.phone.ui.components.AppUpdateDialog(
            updateInfo = updateInfo!!,
            status = updateStatus,
            isWatchUpdate = isWatchUpdate,
            onStartUpdate = { updateViewModel.startUpdate(context) },
            onDismiss = { updateViewModel.dismissDialog() }
        )
    }

    Scaffold(
        modifier = Modifier.background(BgDeep),
        containerColor = BgDeep,
        bottomBar = {
            if (currentDestination?.route?.startsWith("workout_details") != true) Surface(color = BgSurface, shadowElevation = 10.dp) {
                NavigationBar(
                    containerColor = BgSurface,
                    tonalElevation = 0.dp,
                    modifier = Modifier.heightIn(min = 76.dp)
                ) {
                    bottomScreens.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    screen.icon,
                                    contentDescription = screen.label,
                                    tint = if (selected) AccentCyan else TextDim,
                                    modifier = Modifier.size(if (selected) 24.dp else 20.dp)
                                )
                            },
                            label = {
                                Text(
                                    screen.label,
                                    color = if (selected) AccentCyan else TextDim,
                                    fontSize = 10.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = AccentCyan.copy(alpha = 0.12f),
                                selectedIconColor = AccentCyan,
                                unselectedIconColor = TextDim,
                                selectedTextColor = AccentCyan,
                                unselectedTextColor = TextDim
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(300)) }
        ) {
            composable(Screen.Dashboard.route) {
                val viewModel: DashboardViewModel = hiltViewModel()
                DashboardScreen(viewModel = viewModel, onWorkoutClick = { workout ->
                    navController.navigate("workout_details/${workout.id}")
                }, onDeviceClick = { navController.navigate(Screen.Device.route) })
            }
            composable(Screen.Workouts.route) {
                val viewModel: DashboardViewModel = hiltViewModel()
                WorkoutsScreen(viewModel = viewModel, onWorkoutClick = { workout ->
                    navController.navigate("workout_details/${workout.id}")
                })
            }
            composable("workout_details/{workoutId}") { entry ->
                val viewModel: DashboardViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()
                val workoutId = entry.arguments?.getString("workoutId")?.toLongOrNull()
                val workout = uiState.recentWorkouts.firstOrNull { it.id == workoutId }
                if (workout != null) {
                    WorkoutDetailsScreen(workout = workout, onBack = { navController.popBackStack() })
                } else {
                    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        if (uiState.isLoading) CircularProgressIndicator(color = AccentCyan)
                        else {
                            Text("This workout is unavailable", color = TextWhite)
                            TextButton(onClick = { navController.popBackStack() }) { Text("Back to workouts") }
                        }
                    }
                }
            }
            composable(Screen.Notifications.route) { NotificationsScreen() }
            composable(Screen.Device.route) { DeviceScreen(onRequestPermissions = onRequestPermissions) }
            composable(Screen.Settings.route) { SettingsScreen(onRequestPermissions = onRequestPermissions) }
        }
    }
}

// ── Onboarding Screen ──────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentPage by rememberSaveable { mutableIntStateOf(0) }

    val pages = listOf(
        OnboardingPage("HealthSync", "Your phone and watch now share one clean health cockpit for vitals, training, calls, and notifications.", Icons.Default.Favorite, MetricHeart),
        OnboardingPage("Live Vitals", "Check heart rate, SpO2, steps, sleep, and workout data without digging through separate screens.", Icons.Default.MonitorHeart, AccentCyan),
        OnboardingPage("Wrist Ready", "Mirror important alerts and calls to the watch with quick, glanceable controls.", Icons.Default.NotificationsActive, AccentBlue),
        OnboardingPage("Daily Progress", "Keep goals, trends, and workout history in a polished view built for everyday use.", Icons.Default.EmojiEvents, MetricSteps),
        OnboardingPage("You’re in control", "Nearby devices connects your watch. Phone permission enables call alerts. Notification access is optional and can be enabled in Settings. Your health history stays on this phone.", Icons.Default.Security, AccentCyan)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), BgDeep, Color(0xFFE9F4ED))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.weight(1f))

            AnimatedContent(targetState = currentPage, transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) }, label = "page") { page ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(CircleShape)
                            .background(pages[page].color.copy(alpha = 0.12f))
                            .border(1.dp, pages[page].color.copy(alpha = 0.18f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(pages[page].icon, contentDescription = null, tint = pages[page].color, modifier = Modifier.size(56.dp))
                    }
                    Spacer(Modifier.height(32.dp))
                    Text(pages[page].title, color = TextWhite, fontSize = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Text(pages[page].description, color = TextSecondary, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp)
                }
            }

            Spacer(Modifier.weight(1f))

            // Page indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(if (i == currentPage) 24.dp else 8.dp, 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == currentPage) AccentCyan else TextDim.copy(alpha = 0.3f))
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            Surface(
                onClick = {
                    if (currentPage < pages.size - 1) currentPage++
                    else onComplete()
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                color = AccentCyan
            ) {
                Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    Text(
                        if (currentPage < pages.size - 1) "Next" else "Get Started",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (currentPage < pages.size - 1) Icons.Default.ArrowForward else Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (currentPage < pages.size - 1) {
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onComplete) {
                    Text("Skip", color = TextDim, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private data class OnboardingPage(val title: String, val description: String, val icon: ImageVector, val color: Color)
