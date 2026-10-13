package com.healthsync.phone.ui.watchface

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthsync.phone.data.watchface.*
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.io.File
import java.util.UUID

enum class CreatorTab(val label: String, val icon: ImageVector) {
    PROJECT("Project", Icons.Default.FolderOpen), LAYERS("Layers", Icons.Default.Layers),
    BACKGROUND("Background", Icons.Default.Wallpaper), DIAL("Dial", Icons.Default.Toll),
    HANDS("Hands", Icons.Default.Schedule), CLOCK_DATE("Time & Date", Icons.Default.AccessTime),
    COMPLICATIONS("Widgets", Icons.Default.Speed), EFFECTS("Effects", Icons.Default.AutoAwesome),
    AMBIENT("Always-on", Icons.Default.Brightness4), TEST("Test", Icons.Default.Science)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchFaceCreatorScreen(initialPackage: HswfPackage? = null, onBack: () -> Unit = {}, onFaceSaved: (HswfPackage) -> Unit = {}) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val faceStore = remember { CustomFaceStore.getInstance(context) }
    val templates = remember { faceStore.getTemplates() }
    val history = remember {
        val recovery = faceStore.loadDraft(initialPackage?.id)
        val selected = initialPackage?.let {
            if (it.id.startsWith("custom_")) it else FaceStudioDocument.duplicate(it, it.name + " Custom")
        }
        FaceStudioHistory(if (recovery != null && (initialPackage == null || recovery.id == initialPackage.id)) recovery
            else selected ?: FaceStudioDocument.blank())
    }
    var editingPackage by remember { mutableStateOf(history.current) }
    var historyRevision by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableStateOf(CreatorTab.PROJECT) }
    var selectedLayerId by remember { mutableStateOf<String?>(null) }
    var ambient by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(true) }
    var snap by remember { mutableStateOf(true) }
    var previewShape by remember { mutableStateOf("round") }
    var menuOpen by remember { mutableStateOf(false) }
    var draftStatus by remember { mutableStateOf("Draft recovery ready") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var installedName by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var photoLoading by remember { mutableStateOf(false) }
    var photoTargetId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportKey by rememberSaveable { mutableStateOf<String?>(null) }
    var liveTime by remember { mutableStateOf(false) }
    var hours by remember { mutableIntStateOf(10) }
    var minutes by remember { mutableIntStateOf(10) }
    var seconds by remember { mutableIntStateOf(30) }
    var sampleValues by remember { mutableStateOf(false) }
    var battery by remember { mutableIntStateOf(85) }
    var steps by remember { mutableIntStateOf(5840) }
    var heartRate by remember { mutableIntStateOf(72) }
    val connection by BluetoothSyncService.connectionState.collectAsState()
    val watchVersion by BluetoothSyncService.connectedWatchVersionCode.collectAsState()
    val gesture = remember(history) { StudioGesture(history::beginGesture, history::endGesture) }

    fun edit(next: HswfPackage) {
        history.commit(next); editingPackage = history.current; historyRevision++
        draftStatus = "Saving draft…"
    }
    fun replaceLayer(layer: FaceLayer) {
        edit(editingPackage.copy(layers = editingPackage.layers.map { if (it.id == layer.id) layer else it }))
    }
    fun leave() {
        scope.launch {
            val saved = withContext(Dispatchers.IO) { faceStore.saveDraft(editingPackage) }
            if (saved) onBack() else errorMessage = "Draft could not be saved. Save or export the project before leaving."
        }
    }
    BackHandler { leave() }
    LaunchedEffect(editingPackage) {
        delay(700)
        val snapshot = editingPackage
        draftStatus = if (withContext(Dispatchers.IO) { faceStore.saveDraft(snapshot) }) "Draft saved on this phone" else "Draft save failed"
    }
    LaunchedEffect(liveTime) {
        while (liveTime) {
            val now = Calendar.getInstance()
            hours = now.get(Calendar.HOUR_OF_DAY); minutes = now.get(Calendar.MINUTE); seconds = now.get(Calendar.SECOND)
            delay(1000)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val imported = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytesBounded(512 * 1024) }
                        ?: error("Cannot open the selected file.")
                    FaceStudioDocument.importJson(bytes.toString(Charsets.UTF_8))
                }
                val pkg = imported.packageOrNull
                if (pkg == null) errorMessage = imported.errors.joinToString("\n")
                else {
                    edit(FaceStudioDocument.duplicate(pkg, pkg.name)); selectedLayerId = null
                    Toast.makeText(context, "Project imported as a new creation", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { errorMessage = e.message ?: "Could not import this project." }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        val key = pendingExportKey; pendingExportKey = null
        if (key != null) scope.launch {
            val snapshotFile = File(context.cacheDir, key)
            try {
                if (uri != null) withContext(Dispatchers.IO) {
                    check(snapshotFile.isFile) { "The export snapshot is unavailable. Export the project again." }
                    val json = snapshotFile.readText(Charsets.UTF_8)
                    context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                        ?: error("Cannot write to the selected destination.")
                }
                if (uri != null) Toast.makeText(context, "Portable .hswf project exported", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) { errorMessage = e.message ?: "Export failed." }
            finally { withContext(Dispatchers.IO) { snapshotFile.delete() } }
        } else if (uri != null) errorMessage = "The export was interrupted. Export your project again."
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val targetId = photoTargetId
        photoTargetId = null
        if (uri != null) scope.launch {
            photoLoading = true
            try {
                val encoded = withContext(Dispatchers.IO) {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image." }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1024) sample *= 2
                    val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                            val scale = minOf(1f, 1024f / maxOf(info.size.width, info.size.height))
                            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    } else {
                        val options = BitmapFactory.Options().apply { inSampleSize = sample }
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                            ?: error("Could not decode the image.")
                    }
                    try { CustomFaceStore.encodePhotoBitmap(bitmap) } finally { bitmap.recycle() }
                }
                if (targetId == editingPackage.id) edit(editingPackage.copy(background = editingPackage.background.copy(type = "image", imageBase64 = encoded)))
                else errorMessage = "The photo's original project is no longer open. Select the photo again for this project."
            } catch (e: Exception) { errorMessage = e.message ?: "Photo import failed." }
            finally { photoLoading = false }
        }
    }
    Scaffold(containerColor = BgDeep, topBar = {
        TopAppBar(title = { Column {
            Text("Face Studio", color = TextWhite, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text("Create • refine • wear", color = TextDim, fontSize = 11.sp)
        } }, navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.Default.ArrowBack, "Back", tint = TextWhite) } },
        actions = {
            IconButton(enabled = historyRevision >= 0 && history.canUndo, onClick = { focusManager.clearFocus(); editingPackage = history.undo(); historyRevision++ }) { Icon(Icons.Default.Undo, "Undo", tint = if (history.canUndo) TextWhite else TextDim.copy(alpha = 0.4f)) }
            IconButton(enabled = historyRevision >= 0 && history.canRedo, onClick = { focusManager.clearFocus(); editingPackage = history.redo(); historyRevision++ }) { Icon(Icons.Default.Redo, "Redo", tint = if (history.canRedo) TextWhite else TextDim.copy(alpha = 0.4f)) }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Project actions", tint = TextWhite) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Import .hswf project") }, onClick = {
                        menuOpen = false; importLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                    }, leadingIcon = { Icon(Icons.Default.FileOpen, null) })
                    DropdownMenuItem(text = { Text("Export .hswf project") }, onClick = {
                        menuOpen = false
                        val errors = FaceStudioDocument.validationErrors(editingPackage)
                        if (errors.isNotEmpty()) errorMessage = errors.joinToString("\n")
                        else scope.launch {
                            val snapshot = editingPackage
                            try {
                                val key = "studio-export-" + UUID.randomUUID() + ".json"
                                withContext(Dispatchers.IO) {
                                    File(context.cacheDir, key).writeText(FaceStudioDocument.exportJson(snapshot), Charsets.UTF_8)
                                }
                                pendingExportKey = key
                                exportLauncher.launch(snapshot.name.replace(Regex("[^A-Za-z0-9 _-]"), "_").take(60) + ".hswf")
                            } catch (e: Exception) { errorMessage = e.message ?: "Could not prepare export." }
                        }
                    }, leadingIcon = { Icon(Icons.Default.FileDownload, null) })
                    DropdownMenuItem(text = { Text("Duplicate project") }, onClick = {
                        menuOpen = false; edit(FaceStudioDocument.duplicate(editingPackage)); selectedLayerId = null
                    }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) })
                }
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep))
    }, bottomBar = {
        Surface(color = BgCard, tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(enabled = !busy, modifier = Modifier.weight(1f).height(48.dp), onClick = { scope.launch {
                    val snapshot = editingPackage
                    val errors = FaceStudioDocument.validationErrors(snapshot)
                    if (errors.isNotEmpty()) { errorMessage = errors.joinToString("\n"); return@launch }
                    val saved = withContext(Dispatchers.IO) { faceStore.saveCustomFace(snapshot) }
                    if (saved) { onFaceSaved(snapshot); Toast.makeText(context, "Saved to My Creations", Toast.LENGTH_SHORT).show() }
                    else errorMessage = "Could not save this project."
                } }) { Icon(Icons.Default.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Save") }
                Button(enabled = !busy, modifier = Modifier.weight(1.4f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), onClick = { scope.launch {
                    val snapshot = editingPackage
                    val errors = FaceStudioDocument.validationErrors(snapshot)
                    if (errors.isNotEmpty()) { errorMessage = errors.joinToString("\n"); return@launch }
                    if (!connection.isConnected) { errorMessage = "Connect your watch from Device before sending this face."; return@launch }
                    if (snapshot.layers.isNotEmpty() && (watchVersion == null || watchVersion!! < 34)) {
                        errorMessage = "Update the watch to HealthSync 2.9.0 or newer to render custom text and shape layers."; return@launch
                    }
                    busy = true
                    try {
                        if (!withContext(Dispatchers.IO) { faceStore.saveCustomFace(snapshot) }) errorMessage = "Could not save the project before sending."
                        else {
                            onFaceSaved(snapshot)
                            if (WatchFaceSender.sendFaceToWatch(snapshot, true)) installedName = snapshot.name
                            else errorMessage = "The watch did not confirm installation. Check Bluetooth and retry."
                        }
                    } catch (e: Exception) { errorMessage = e.message ?: "Transfer failed." }
                    finally { busy = false }
                } }) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.SendToMobile, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp)); Text(if (busy) "Sending…" else "Send to watch")
                }
            }
        }
    }) { padding ->
        CompositionLocalProvider(LocalStudioGesture provides gesture) {
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CreatorTab.entries.forEach { tab ->
                        FilterChip(selected = selectedTab == tab, onClick = { selectedTab = tab }, label = { Text(tab.label) },
                            leadingIcon = { Icon(tab.icon, null, Modifier.size(16.dp)) })
                    }
                }
                val preview: @Composable () -> Unit = {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(editingPackage.name.ifBlank { "Untitled face" }, color = TextWhite, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(draftStatus, color = TextDim, fontSize = 11.sp)
                            }
                            AssistChip(onClick = { ambient = !ambient }, label = { Text(if (ambient) "Ambient" else "Active") },
                                leadingIcon = { Icon(if (ambient) Icons.Default.Brightness4 else Icons.Default.Brightness7, null, Modifier.size(16.dp)) })
                        }
                        StudioCanvas(pkg = editingPackage, selectedLayerId = selectedLayerId,
                            onSelectLayer = { focusManager.clearFocus(); selectedLayerId = it; if (it != null) selectedTab = CreatorTab.LAYERS },
                            onLayerChange = ::replaceLayer, onGestureStart = history::beginGesture, onGestureEnd = { history.endGesture(); historyRevision++ },
                            showGrid = showGrid, snapToGrid = snap, isAmbient = ambient,
                            hours = hours, minutes = minutes, seconds = seconds,
                            batteryPct = if (sampleValues) battery else -1, steps = if (sampleValues) steps else -1, heartRate = if (sampleValues) heartRate else -1,
                            previewShape = previewShape, modifier = Modifier.widthIn(max = 260.dp).fillMaxWidth().aspectRatio(1f))
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(showGrid, { showGrid = !showGrid }, label = { Text("Guides") })
                            FilterChip(snap, { snap = !snap }, label = { Text("Snap") })
                            FilterChip(previewShape == "round", { previewShape = if (previewShape == "round") "square" else "round" },
                                label = { Text(if (previewShape == "round") "Round" else "Square") })
                        }
                        Text(if (sampleValues) "Example values • design simulation" else "Design preview • health data unavailable", color = TextDim, fontSize = 11.sp)
                        if (selectedLayerId != null) Text("Drag the selected layer to position it", color = AccentCyan, fontSize = 12.sp)
                    }
                }
                val inspector: @Composable () -> Unit = {
                    Surface(Modifier.fillMaxWidth().padding(12.dp), shape = RoundedCornerShape(20.dp), color = BgCard, border = BorderStroke(1.dp, BorderSubtle)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            key(editingPackage.id, selectedTab, selectedLayerId) { when (selectedTab) {
                                CreatorTab.PROJECT -> StudioProjectControl(editingPackage, templates, ::edit, { selectedLayerId = null; edit(FaceStudioDocument.blank(it)) })
                                CreatorTab.LAYERS -> StudioLayersControl(editingPackage, selectedLayerId, { focusManager.clearFocus(); selectedLayerId = it }, ::edit)
                                CreatorTab.BACKGROUND -> {
                                    BackgroundControl(editingPackage.background, { if (!photoLoading) { photoTargetId = editingPackage.id; photoPicker.launch("image/*") } }) { edit(editingPackage.copy(background = it)) }
                                    if (photoLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                                    StudioBackgroundPrecision(editingPackage.background) { edit(editingPackage.copy(background = it)) }
                                }
                                CreatorTab.DIAL -> {
                                    DialControl(editingPackage.dial) { edit(editingPackage.copy(dial = it)) }
                                    StudioDialPrecision(editingPackage.dial) { edit(editingPackage.copy(dial = it)) }
                                }
                                CreatorTab.HANDS -> {
                                    HandsControl(editingPackage.hands) { edit(editingPackage.copy(hands = it)) }
                                    editingPackage.hands?.let { hands -> StudioHandsPrecision(hands) { edit(editingPackage.copy(hands = it)) } }
                                }
                                CreatorTab.CLOCK_DATE -> {
                                    ClockDateControl(editingPackage.digitalClock, editingPackage.date, { edit(editingPackage.copy(digitalClock = it)) }, { edit(editingPackage.copy(date = it)) })
                                    StudioClockPrecision(editingPackage, ::edit)
                                }
                                CreatorTab.COMPLICATIONS -> {
                                    ComplicationsControl(editingPackage.complications) { edit(editingPackage.copy(complications = it)) }
                                    StudioWidgetPrecision(editingPackage.complications) { edit(editingPackage.copy(complications = it)) }
                                }
                                CreatorTab.EFFECTS -> StudioEffectsControl(editingPackage, ::edit)
                                CreatorTab.AMBIENT -> StudioAmbientControl(editingPackage.aod) { edit(editingPackage.copy(aod = it)); ambient = true }
                                CreatorTab.TEST -> StudioTestControl(editingPackage, liveTime, { liveTime = it },
                                    hours, { hours = it }, minutes, { minutes = it }, seconds, { seconds = it },
                                    sampleValues, { sampleValues = it }, battery, { battery = it }, steps, { steps = it }, heartRate, { heartRate = it })
                            } }
                        }
                    }
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    if (maxWidth >= 720.dp) Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f)) { preview() }; LazyColumn(Modifier.weight(1f).fillMaxHeight()) { item { inspector() } }
                    } else if (maxHeight >= 560.dp) Column(Modifier.fillMaxSize()) {
                        preview()
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) { item { inspector() } }
                    } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 12.dp)) {
                        item { preview() }; item { inspector() }
                    }
                }
            }
        }
    }
    errorMessage?.let { message -> AlertDialog(onDismissRequest = { errorMessage = null }, title = { Text("Studio notice") }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { errorMessage = null }) { Text("OK") } }) }
    installedName?.let { name -> AlertDialog(onDismissRequest = { installedName = null }, title = { Text("Watch face installed") },
        text = { Text("'" + name + "' was confirmed by your watch and activated.") },
        confirmButton = { TextButton(onClick = { installedName = null }) { Text("Done") } }) }
}

private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        require(output.size() + count <= limit) { "Project exceeds the 512 KB import limit." }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
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
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Pick Photo from Gallery", color = Color.White, fontWeight = FontWeight.Bold)
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
        Text("Dial Ticks", color = TextWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(modifier = Modifier.weight(2.5f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        Text("Numeral Markers", color = TextWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.weight(1.6f).horizontalScroll(rememberScrollState()),
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
                    .size(48.dp)
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
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        .size(48.dp)
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
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("arc_gauge" to "Arc Gauge", "subdial" to "Subdial", "text_only" to "Text Only").forEach { (st, label) ->
                    FilterChip(
                        selected = complications.heartRate.style == st,
                        onClick = { onChange(complications.copy(heartRate = complications.heartRate.copy(style = st))) },
                        label = { Text(label, fontSize = 11.sp) }
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
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("arc_gauge" to "Arc Gauge", "subdial" to "Subdial", "text_only" to "Text Only").forEach { (st, label) ->
                    FilterChip(
                        selected = complications.steps.style == st,
                        onClick = { onChange(complications.copy(steps = complications.steps.copy(style = st))) },
                        label = { Text(label, fontSize = 11.sp) }
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
