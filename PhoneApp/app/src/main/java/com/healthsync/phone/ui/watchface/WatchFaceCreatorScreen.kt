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
import java.util.UUID

enum class CreatorTab(val label: String, val icon: ImageVector) {
    TEMPLATES("Presets", Icons.Default.AutoAwesome),
    BACKGROUND("Background", Icons.Default.Wallpaper),
    DIAL("Dial", Icons.Default.Toll),
    HANDS("Hands", Icons.Default.Schedule),
    CLOCK_DATE("Time & Date", Icons.Default.AccessTime),
    COMPLICATIONS("Complications", Icons.Default.Speed)
}

/**
 * Phase 5: Watch Face Creator Studio.
 * Allows users to design custom watch faces, import personal photos,
 * tweak dials, hands, and complications with real-time 1:1 Canvas preview,
 * save to local library, and beam directly to Kolabee U8 Ultra.
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
    val faceAck by BluetoothSyncService.watchFaceAck.collectAsState()

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

    // Transmission status
    var isBeaming by remember { mutableStateOf(false) }
    var beamProgressText by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Live ticking simulation
    var tickSeconds by remember { mutableIntStateOf(30) }
    var tickMinutes by remember { mutableIntStateOf(10) }
    var tickHours by remember { mutableIntStateOf(10) }

    LaunchedEffect(Unit) {
        while (true) {
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
                        Text("Design your personalized dial", color = TextDim, fontSize = 12.sp)
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
            // ── Live Watch Preview Viewport ───────────────────────────────────────
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(230.dp)
                            .clip(CircleShape)
                            .background(Color.Black)
                            .border(6.dp, Color(0xFF1E293B), CircleShape)
                            .border(8.dp, Color(0xFF0F172A), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        DynamicFacePreview(
                            pkg = editingPackage,
                            modifier = Modifier.fillMaxSize(),
                            hours = tickHours,
                            minutes = tickMinutes,
                            seconds = tickSeconds,
                            heartRate = 75,
                            steps = 5840,
                            battery = 88
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (isAmbientPreview) "Ambient / AOD View" else "Live 60 FPS Watch Simulation",
                        color = TextDim,
                        fontSize = 12.sp
                    )
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
                            .size(52.dp)
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
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("photo" to "Personal Photo", "solid" to "Solid Color", "gradient_radial" to "Radial Glow", "gradient_linear" to "Linear Fade").forEach { (typeKey, label) ->
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

    // Color swatches
    Text("Theme Palette Swatches:", color = TextDim, fontSize = 12.sp)
    val colorSwatches = listOf(
        "#0A0D12" to "Onyx Black",
        "#0F172A" to "Slate Dark",
        "#022C22" to "Deep Emerald",
        "#1E1B4B" to "Royal Indigo",
        "#3B0764" to "Cyber Violet",
        "#450A0A" to "Crimson Bold",
        "#1C1917" to "Titanium Gray"
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        colorSwatches.forEach { (hex, name) ->
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
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                "none" to "None",
                "arabic" to "12h",
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
    Text("Accent Marker Color:", color = TextDim, fontSize = 12.sp)
    val accentSwatches = listOf("#00E5FF", "#D4AF37", "#FFFFFF", "#FF3366", "#10B981", "#F59E0B")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("HH:mm" to "24-Hour (14:30)", "hh:mm a" to "12-Hour (02:30 PM)", "HH:mm:ss" to "Seconds (14:30:45)").forEach { (fmt, label) ->
                FilterChip(
                    selected = clock.format == fmt,
                    onClick = { onClockChange(clock.copy(format = fmt)) },
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
}

@Composable
private fun ComplicationsControl(
    complications: ComplicationsConfig,
    onChange: (ComplicationsConfig) -> Unit
) {
    Text("Smart Telemetry Complications", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

    // Heart Rate Complication
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = StatusError, modifier = Modifier.size(18.dp))
            Text("Live Heart Rate Gauge", color = TextWhite, fontSize = 13.sp)
        }
        Switch(
            checked = complications.heartRate.enabled,
            onCheckedChange = { en ->
                onChange(complications.copy(heartRate = complications.heartRate.copy(enabled = en)))
            }
        )
    }

    // Steps Complication
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
            Text("Step Goal Progress Ring", color = TextWhite, fontSize = 13.sp)
        }
        Switch(
            checked = complications.steps.enabled,
            onCheckedChange = { en ->
                onChange(complications.copy(steps = complications.steps.copy(enabled = en)))
            }
        )
    }

    // Battery Complication
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.BatteryChargingFull, contentDescription = null, tint = StatusSuccess, modifier = Modifier.size(18.dp))
            Text("Watch Battery Percentage", color = TextWhite, fontSize = 13.sp)
        }
        Switch(
            checked = complications.battery.enabled,
            onCheckedChange = { en ->
                onChange(complications.copy(battery = complications.battery.copy(enabled = en)))
            }
        )
    }
}
