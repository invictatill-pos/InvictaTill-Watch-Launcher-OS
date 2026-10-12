package com.healthsync.phone.ui.watchface

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.healthsync.phone.data.watchface.*
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

enum class CreatorTab(val label: String, val icon: ImageVector) {
    TEMPLATES("Presets", Icons.Default.AutoAwesome),
    BACKGROUND("Background", Icons.Default.Wallpaper),
    DIAL("Dial", Icons.Default.Toll),
    HANDS("Hands", Icons.Default.Schedule),
    CLOCK_DATE("Time & Date", Icons.Default.AccessTime),
    COMPLICATIONS("Complications", Icons.Default.Speed)
}

enum class WatchCaseStyle(val label: String, val outerColor: Color, val innerColor: Color, val accentColor: Color) {
    TITANIUM_ULTRA("Titanium Ultra", Color(0xFF334155), Color(0xFF1E293B), Color(0xFFF97316)),
    STEEL_MIRROR("Silver Steel", Color(0xFF94A3B8), Color(0xFF475569), Color(0xFF38BDF8)),
    MATTE_BLACK("Midnight Black", Color(0xFF18181B), Color(0xFF09090B), Color(0xFF10B981)),
    ROSE_GOLD("Rose Gold", Color(0xFFB45309), Color(0xFF78350F), Color(0xFFF43F5E))
}

/**
 * Supercharged Watch Face Creator Studio.
 * Allows users to design custom watch faces, import personal photos with dim filters,
 * customize dials, hands, and complications with real-time 1:1 Canvas preview,
 * interactive watch case frames, simulation test bench, save to local library,
 * and beam directly to Kolabee U8 Ultra over Bluetooth.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchFaceCreatorScreen(
    initialPackage: HswfPackage? = null,
    onBack: () -> Unit = {},
    onFaceSaved: (HswfPackage) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connState by BluetoothSyncService.connectionState.collectAsState()

    val faceStore = remember { CustomFaceStore.getInstance(context) }
    val templates = remember { faceStore.getTemplates() }

    // Active package state being edited
    var editingPackage by remember {
        mutableStateOf(
            initialPackage ?: templates.first().copy(
                id = "custom_${System.currentTimeMillis()}",
                name = "My Custom Dial",
                author = "You"
            )
        )
    }

    var selectedTab by remember { mutableStateOf(CreatorTab.TEMPLATES) }
    var isAmbientPreview by remember { mutableStateOf(false) }
    var faceNameInput by remember { mutableStateOf(editingPackage.name) }
    var selectedCaseStyle by remember { mutableStateOf(WatchCaseStyle.TITANIUM_ULTRA) }
    var showTestBench by remember { mutableStateOf(false) }

    // Transmission status
    var isBeaming by remember { mutableStateOf(false) }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Live ticking simulation
    var tickSeconds by remember { mutableIntStateOf(30) }
    var tickMinutes by remember { mutableIntStateOf(10) }
    var tickHours by remember { mutableIntStateOf(10) }

    // Interactive Test Bench Values
    var testBattery by remember { mutableFloatStateOf(85f) }
    var testSteps by remember { mutableFloatStateOf(5840f) }
    var testHeartRate by remember { mutableFloatStateOf(72f) }
    var liveTicking by remember { mutableStateOf(true) }

    LaunchedEffect(liveTicking) {
        while (liveTicking) {
            val cal = Calendar.getInstance()
            tickHours = cal.get(Calendar.HOUR)
            tickMinutes = cal.get(Calendar.MINUTE)
            tickSeconds = cal.get(Calendar.SECOND)
            delay(1000L)
        }
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= 28) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.isMutableRequired = true
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }

                val encodedBase64 = CustomFaceStore.encodePhotoBitmap(bitmap, targetDimension = 320, quality = 85)
                editingPackage = editingPackage.copy(
                    background = editingPackage.background.copy(
                        type = "image",
                        imageBase64 = encodedBase64
                    )
                )
                Toast.makeText(context, "Photo loaded & optimized for smartwatch", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Watch Face Creator", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Personalized Dial Studio for Kolabee U8 Ultra", color = TextDim, fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                    }
                },
                actions = {
                    IconButton(onClick = { isAmbientPreview = !isAmbientPreview }) {
                        Icon(
                            if (isAmbientPreview) Icons.Default.Brightness7 else Icons.Default.Brightness4,
                            contentDescription = "Toggle Ambient Preview",
                            tint = if (isAmbientPreview) AccentCyan else TextDim
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        },
        bottomBar = {
            Surface(
                color = BgCard,
                border = BorderStroke(1.dp, BorderSubtle),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Save Button
                    OutlinedButton(
                        onClick = {
                            val toSave = editingPackage.copy(
                                name = faceNameInput.ifBlank { "Custom Dial" }
                            )
                            val saved = faceStore.saveCustomFace(toSave)
                            if (saved) {
                                Toast.makeText(context, "Saved to My Creations!", Toast.LENGTH_SHORT).show()
                                onFaceSaved(toSave)
                            } else {
                                Toast.makeText(context, "Failed to save dial", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = TextWhite, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", color = TextWhite, fontWeight = FontWeight.SemiBold)
                    }

                    // Beam to Watch Button
                    Button(
                        onClick = {
                            if (!connState.isConnected) {
                                errorMessage = "Please connect Kolabee U8 Ultra via Bluetooth before installing."
                                return@Button
                            }
                            val finalFace = editingPackage.copy(
                                name = faceNameInput.ifBlank { "Custom Dial" }
                            )
                            faceStore.saveCustomFace(finalFace)
                            isBeaming = true
                            scope.launch {
                                val success = WatchFaceSender.sendFaceToWatch(pkg = finalFace, setActive = true)
                                isBeaming = false
                                if (success) {
                                    showSuccessDialog = true
                                    onFaceSaved(finalFace)
                                } else {
                                    errorMessage = "Transfer failed. Check Bluetooth pairing and retry."
                                }
                            }
                        },
                        modifier = Modifier.weight(1.3f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan),
                        enabled = !isBeaming
                    ) {
                        if (isBeaming) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Beaming…", color = Color.Black, fontWeight = FontWeight.Bold)
                        } else {
                            Icon(Icons.Default.SendToMobile, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Beam to Watch", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Live Watch Preview Viewport with Simulated Case ────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(236.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(6.dp, selectedCaseStyle.innerColor, CircleShape)
                            .border(10.dp, selectedCaseStyle.outerColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        DynamicFacePreview(
                            pkg = editingPackage,
                            modifier = Modifier.fillMaxSize(),
                            hours = tickHours,
                            minutes = tickMinutes,
                            seconds = tickSeconds,
                            heartRate = testHeartRate.toInt(),
                            steps = testSteps.toInt(),
                            battery = testBattery.toInt()
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (isAmbientPreview) "Ambient / AOD View" else "Live 60 FPS Watch Simulation",
                            color = TextDim,
                            fontSize = 12.sp
                        )
                        TextButton(
                            onClick = { showTestBench = !showTestBench },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (showTestBench) "Hide Test Bench" else "Test Bench", color = AccentCyan, fontSize = 12.sp)
                        }
                    }

                    // Simulated Case Style Chips
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WatchCaseStyle.values().forEach { case ->
                            val isSel = selectedCaseStyle == case
                            FilterChip(
                                selected = isSel,
                                onClick = { selectedCaseStyle = case },
                                label = { Text(case.label, fontSize = 10.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(case.accentColor)
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // ── Interactive Simulation Test Bench (Collapsible) ───────────────────
            if (showTestBench) {
                item {
                    PaddingWrapper {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BgCard,
                            border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Interactive Telemetry Simulator", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Live Ticking", color = TextDim, fontSize = 11.sp)
                                        Spacer(Modifier.width(6.dp))
                                        Switch(
                                            checked = liveTicking,
                                            onCheckedChange = { liveTicking = it },
                                            modifier = Modifier.height(24.dp)
                                        )
                                    }
                                }

                                // Battery Slider
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Battery Level", color = TextDim, fontSize = 12.sp)
                                        Text("${testBattery.toInt()}%", color = StatusSuccess, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = testBattery,
                                        onValueChange = { testBattery = it },
                                        valueRange = 0f..100f,
                                        colors = SliderDefaults.colors(thumbColor = StatusSuccess, activeTrackColor = StatusSuccess)
                                    )
                                }

                                // Steps Slider
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Step Progress", color = TextDim, fontSize = 12.sp)
                                        Text("${testSteps.toInt()} Steps", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = testSteps,
                                        onValueChange = { testSteps = it },
                                        valueRange = 0f..15000f,
                                        colors = SliderDefaults.colors(thumbColor = AccentCyan, activeTrackColor = AccentCyan)
                                    )
                                }

                                // Heart Rate Slider
                                Column {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Heart Rate (BPM)", color = TextDim, fontSize = 12.sp)
                                        Text("${testHeartRate.toInt()} BPM", color = StatusError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Slider(
                                        value = testHeartRate,
                                        onValueChange = { testHeartRate = it },
                                        valueRange = 40f..180f,
                                        colors = SliderDefaults.colors(thumbColor = StatusError, activeTrackColor = StatusError)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Dial Name Input ───────────────────────────────────────────────────
            item {
                PaddingWrapper {
                    OutlinedTextField(
                        value = faceNameInput,
                        onValueChange = { faceNameInput = it },
                        label = { Text("Watch Face Name", color = TextDim) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = AccentCyan,
                            unfocusedBorderColor = BorderSubtle
                        ),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ── Section Tabs ──────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CreatorTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            onClick = { selectedTab = tab },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) AccentCyan else BgCard,
                            border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    tab.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.Black else TextWhite,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    tab.label,
                                    color = if (isSelected) Color.Black else TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ── Section Controls ──────────────────────────────────────────────────
            item {
                PaddingWrapper {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = BgCard,
                        border = BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            when (selectedTab) {
                                CreatorTab.TEMPLATES -> {
                                    TemplatesControl(
                                        templates = templates,
                                        onSelectTemplate = { t ->
                                            editingPackage = t.copy(
                                                id = "custom_${System.currentTimeMillis()}",
                                                name = "${t.name} Custom",
                                                author = "You"
                                            )
                                            faceNameInput = editingPackage.name
                                        }
                                    )
                                }
                                CreatorTab.BACKGROUND -> {
                                    BackgroundControl(
                                        bg = editingPackage.background,
                                        onPickPhoto = { photoPickerLauncher.launch("image/*") },
                                        onChange = { newBg ->
                                            editingPackage = editingPackage.copy(background = newBg)
                                        }
                                    )
                                }
                                CreatorTab.DIAL -> {
                                    DialControl(
                                        dial = editingPackage.dial,
                                        onChange = { newDial ->
                                            editingPackage = editingPackage.copy(dial = newDial)
                                        }
                                    )
                                }
                                CreatorTab.HANDS -> {
                                    HandsControl(
                                        hands = editingPackage.hands,
                                        onChange = { newHands ->
                                            editingPackage = editingPackage.copy(hands = newHands)
                                        }
                                    )
                                }
                                CreatorTab.CLOCK_DATE -> {
                                    ClockDateControl(
                                        clock = editingPackage.digitalClock,
                                        date = editingPackage.date,
                                        onClockChange = { editingPackage = editingPackage.copy(digitalClock = it) },
                                        onDateChange = { editingPackage = editingPackage.copy(date = it) }
                                    )
                                }
                                CreatorTab.COMPLICATIONS -> {
                                    ComplicationsControl(
                                        complications = editingPackage.complications,
                                        onChange = { editingPackage = editingPackage.copy(complications = it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Success Dialog ────────────────────────────────────────────────────────────
    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showSuccessDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StatusSuccess)
                    Text("Dial Beamed Successfully!", color = TextWhite)
                }
            },
            text = {
                Text(
                    "Your custom watch face '${editingPackage.name}' was transferred to Kolabee U8 Ultra and activated on your wrist.",
                    color = TextDim,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
                ) {
                    Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = BgCard
        )
    }

    // ── Error Alert ───────────────────────────────────────────────────────────────
    errorMessage?.let { err ->
        AlertDialog(
            onDismissRequest = { errorMessage = null },
            title = { Text("Transfer Notice", color = StatusError) },
            text = { Text(err, color = TextWhite) },
            confirmButton = {
                TextButton(onClick = { errorMessage = null }) {
                    Text("OK", color = AccentCyan)
                }
            },
            containerColor = BgCard
        )
    }
}

@Composable
private fun PaddingWrapper(content: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(horizontal = 16.dp)) { content() }
}

// ── Control Sub-Panels ────────────────────────────────────────────────────────────

@Composable
private fun TemplatesControl(
    templates: List<HswfPackage>,
    onSelectTemplate: (HswfPackage) -> Unit
) {
    Text("Starting Presets", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    Text("Choose a foundation to kickstart your personalized design:", color = TextDim, fontSize = 13.sp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        templates.forEach { tmpl ->
            Surface(
                onClick = { onSelectTemplate(tmpl) },
                shape = RoundedCornerShape(12.dp),
                color = BgDeep,
                border = BorderStroke(1.dp, BorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .border(1.dp, AccentCyan.copy(alpha = 0.5f), CircleShape)
                    ) {
                        DynamicFacePreview(pkg = tmpl, modifier = Modifier.fillMaxSize())
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(tmpl.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(tmpl.description, color = TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentCyan)
                }
            }
        }
    }
}

@Composable
private fun BackgroundControl(
    bg: BackgroundConfig,
    onPickPhoto: () -> Unit,
    onChange: (BackgroundConfig) -> Unit
) {
    Text("Dial Background", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    // Mode Selector
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(
            "photo" to "Personal Photo",
            "solid" to "Solid Color",
            "gradient_radial" to "Radial Glow",
            "gradient_linear" to "Linear Fade"
        ).forEach { (typeKey, label) ->
            val isCurrent = if (typeKey == "photo") bg.type == "image" else bg.type == typeKey
            FilterChip(
                selected = isCurrent,
                onClick = {
                    if (typeKey == "photo") {
                        if (bg.imageBase64 != null) onChange(bg.copy(type = "image"))
                        else onPickPhoto()
                    } else {
                        onChange(bg.copy(type = typeKey))
                    }
                },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }

    if (bg.type == "image" || bg.imageBase64 != null) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onPickPhoto,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Pick Photo from Gallery", color = Color.Black, fontWeight = FontWeight.Bold)
            }
            if (bg.imageBase64 != null) {
                TextButton(
                    onClick = { onChange(bg.copy(type = "solid", imageBase64 = null)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Remove Photo (Revert to Solid)", color = StatusError)
                }
            }
        }
    }

    // 12 Curated Theme Color Swatches
    Text("Theme Color Palette:", color = TextDim, fontSize = 12.sp)
    val colorSwatches = listOf(
        "#0A0D12" to "Onyx Black",
        "#0F172A" to "Slate Dark",
        "#022C22" to "Deep Emerald",
        "#1E1B4B" to "Royal Indigo",
        "#3B0764" to "Cyber Violet",
        "#450A0A" to "Crimson Bold",
        "#1C1917" to "Titanium Gray",
        "#172554" to "Midnight Blue",
        "#14532D" to "Forest Green",
        "#701A75" to "Deep Magenta",
        "#7C2D12" to "Burnt Amber",
        "#042F2E" to "Dark Teal"
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colorSwatches.forEach { (hex, _) ->
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(2.dp, if (bg.colorHex.equals(hex, ignoreCase = true)) AccentCyan else BorderSubtle, CircleShape)
                    .clickable {
                        onChange(bg.copy(colorHex = hex, gradientStartHex = hex))
                    }
            )
        }
    }
}

@Composable
private fun DialControl(
    dial: DialConfig,
    onChange: (DialConfig) -> Unit
) {
    Text("Ticks & Numerals", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    // Tick Count
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Dial Ticks", color = TextWhite, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "None", 12 to "12 Hours", 60 to "60 Mins").forEach { (count, label) ->
                FilterChip(
                    selected = if (count == 0) !dial.showTicks else (dial.showTicks && dial.tickCount == count),
                    onClick = {
                        onChange(dial.copy(showTicks = count > 0, tickCount = if (count > 0) count else dial.tickCount))
                    },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
    }

    // Number Type
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Numeral Markers", color = TextWhite, fontSize = 14.sp)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                "none" to "None",
                "arabic" to "12h Arabic",
                "roman" to "Roman",
                "minimal_cardinal" to "12-3-6-9"
            ).forEach { (typeKey, label) ->
                val isSelected = if (typeKey == "none") !dial.showNumbers else (dial.showNumbers && dial.numberType == typeKey)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        onChange(dial.copy(showNumbers = typeKey != "none", numberType = if (typeKey != "none") typeKey else dial.numberType))
                    },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
    }

    // Accent Colors
    Text("Marker Accent Color:", color = TextDim, fontSize = 12.sp)
    val accentSwatches = listOf("#00E5FF", "#D4AF37", "#FFFFFF", "#FF3366", "#10B981", "#F59E0B", "#E2E8F0", "#A855F7")
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        accentSwatches.forEach { hex ->
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(hex)))
                    .border(2.dp, if (dial.majorTickColorHex.equals(hex, ignoreCase = true)) AccentCyan else BorderSubtle, CircleShape)
                    .clickable {
                        onChange(dial.copy(majorTickColorHex = hex, numberColorHex = hex))
                    }
            )
        }
    }
}

@Composable
private fun HandsControl(
    hands: HandsConfig?,
    onChange: (HandsConfig?) -> Unit
) {
    Text("Analog Clock Hands", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    val isHandsEnabled = hands != null
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Show Analog Hands", color = TextWhite, fontSize = 14.sp)
        Switch(
            checked = isHandsEnabled,
            onCheckedChange = { enable ->
                onChange(if (enable) HandsConfig() else null)
            }
        )
    }

    if (hands != null) {
        // Hand Shape
        Text("Hand Silhouette Shape:", color = TextDim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("sword" to "Sword", "baton" to "Baton", "needle" to "Needle", "arrow" to "Arrow").forEach { (shape, label) ->
                FilterChip(
                    selected = hands.hourHand.shape == shape,
                    onClick = {
                        onChange(
                            hands.copy(
                                hourHand = hands.hourHand.copy(shape = shape),
                                minuteHand = hands.minuteHand.copy(shape = shape)
                            )
                        )
                    },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        // Second Hand Color
        Text("Second Hand Accent Color:", color = TextDim, fontSize = 12.sp)
        val secSwatches = listOf("#FF3366", "#EF4444", "#38BDF8", "#00E5FF", "#D4AF37", "#10B981", "#FFFFFF")
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            secSwatches.forEach { hex ->
                val currentSec = hands.secondHand?.colorHex ?: "#FF3366"
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(android.graphics.Color.parseColor(hex)))
                        .border(2.dp, if (currentSec.equals(hex, ignoreCase = true)) AccentCyan else BorderSubtle, CircleShape)
                        .clickable {
                            val sec = hands.secondHand ?: HandStyle(lengthRatio = 0.88f, widthDp = 1.8f, colorHex = hex)
                            onChange(hands.copy(secondHand = sec.copy(colorHex = hex)))
                        }
                )
            }
        }

        // Second Hand Sweep
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Smooth Sweep Seconds Hand", color = TextWhite, fontSize = 13.sp)
            Switch(
                checked = hands.secondHand?.smoothSweep == true,
                onCheckedChange = { sweep ->
                    val sec = hands.secondHand ?: HandStyle(lengthRatio = 0.88f, widthDp = 1.8f, colorHex = "#FF3366")
                    onChange(hands.copy(secondHand = sec.copy(smoothSweep = sweep)))
                }
            )
        }
    }
}

@Composable
private fun ClockDateControl(
    clock: DigitalClockConfig?,
    date: DateConfig?,
    onClockChange: (DigitalClockConfig?) -> Unit,
    onDateChange: (DateConfig?) -> Unit
) {
    Text("Digital Time & Date Display", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    // Digital Time Toggle
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Digital Time Display", color = TextWhite, fontSize = 14.sp)
        Switch(
            checked = clock?.enabled == true,
            onCheckedChange = { enable ->
                onClockChange(
                    if (enable) (clock ?: DigitalClockConfig()).copy(enabled = true)
                    else clock?.copy(enabled = false)
                )
            }
        )
    }

    if (clock?.enabled == true) {
        // Time Formats
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("HH:mm" to "24-Hour (14:30)", "hh:mm a" to "12-Hour (02:30 PM)", "HH:mm:ss" to "Seconds (14:30:45)").forEach { (fmt, label) ->
                FilterChip(
                    selected = clock.format == fmt,
                    onClick = { onClockChange(clock.copy(format = fmt)) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }

        // Font Family
        Text("Digital Typography Style:", color = TextDim, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("monospace" to "Tech Monospace", "sans" to "Modern Sans", "serif" to "Luxury Serif").forEach { (font, label) ->
                FilterChip(
                    selected = clock.fontFamily == font,
                    onClick = { onClockChange(clock.copy(fontFamily = font)) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
    }

    HorizontalDivider(color = BorderSubtle)

    // Date Window Toggle
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Text("Calendar Date Aperture", color = TextWhite, fontSize = 14.sp)
        Switch(
            checked = date?.enabled == true,
            onCheckedChange = { enable ->
                onDateChange(
                    if (enable) (date ?: DateConfig()).copy(enabled = true)
                    else date?.copy(enabled = false)
                )
            }
        )
    }

    if (date?.enabled == true) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("EEE d" to "Mon 12", "EEE, MMM d" to "Mon, Oct 12", "dd/MM" to "12/10", "d" to "12 (Day Only)").forEach { (fmt, label) ->
                FilterChip(
                    selected = date.format == fmt,
                    onClick = { onDateChange(date.copy(format = fmt)) },
                    label = { Text(label, fontSize = 11.sp) }
                )
            }
        }
    }
}

@Composable
private fun ComplicationsControl(
    complications: ComplicationsConfig,
    onChange: (ComplicationsConfig) -> Unit
) {
    Text("Smart Telemetry Complications", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    // Heart Rate Complication
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = StatusError, modifier = Modifier.size(18.dp))
                Text("Heart Rate Sensor", color = TextWhite, fontSize = 13.sp)
            }
            Switch(
                checked = complications.heartRate.enabled,
                onCheckedChange = { en ->
                    onChange(complications.copy(heartRate = complications.heartRate.copy(enabled = en)))
                }
            )
        }
        if (complications.heartRate.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("arc_gauge" to "Arc Gauge", "subdial" to "Subdial", "text_only" to "Text Only").forEach { (st, label) ->
                    FilterChip(
                        selected = complications.heartRate.style == st,
                        onClick = { onChange(complications.copy(heartRate = complications.heartRate.copy(style = st))) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }
    }

    HorizontalDivider(color = BorderSubtle)

    // Steps Complication
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                Text("Step Goal Progress", color = TextWhite, fontSize = 13.sp)
            }
            Switch(
                checked = complications.steps.enabled,
                onCheckedChange = { en ->
                    onChange(complications.copy(steps = complications.steps.copy(enabled = en)))
                }
            )
        }
        if (complications.steps.enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("arc_gauge" to "Arc Gauge", "subdial" to "Subdial", "text_only" to "Text Only").forEach { (st, label) ->
                    FilterChip(
                        selected = complications.steps.style == st,
                        onClick = { onChange(complications.copy(steps = complications.steps.copy(style = st))) },
                        label = { Text(label, fontSize = 10.sp) }
                    )
                }
            }
        }
    }

    HorizontalDivider(color = BorderSubtle)

    // Battery Complication
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
                Text("Watch Battery Level", color = TextWhite, fontSize = 13.sp)
            }
            Switch(
                checked = complications.battery.enabled,
                onCheckedChange = { en ->
                    onChange(complications.copy(battery = complications.battery.copy(enabled = en)))
                }
            )
        }
    }
}
