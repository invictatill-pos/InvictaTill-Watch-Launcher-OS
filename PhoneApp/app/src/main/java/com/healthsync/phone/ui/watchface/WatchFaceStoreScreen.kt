package com.healthsync.phone.ui.watchface

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.healthsync.phone.data.watchface.HswfPackage
import com.healthsync.phone.data.watchface.OnlineWatchFaceCatalog
import com.healthsync.phone.data.watchface.OnlineWatchFaceCatalog.StoreItem
import com.healthsync.phone.data.watchface.WatchFaceSender
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchFaceStoreScreen(
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connState by BluetoothSyncService.connectionState.collectAsState()

    var selectedCategory by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var selectedItemForDetail by remember { mutableStateOf<StoreItem?>(null) }

    val categories = listOf("All", "Luxury", "Sports", "Minimal", "Cyberpunk")

    val filteredItems = remember(selectedCategory, searchQuery) {
        OnlineWatchFaceCatalog.items.filter { item ->
            val matchesCategory = selectedCategory == "All" || item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    item.pkg.name.contains(searchQuery, ignoreCase = true) ||
                    item.pkg.author.contains(searchQuery, ignoreCase = true) ||
                    item.tags.any { it.contains(searchQuery, ignoreCase = true) }
            matchesCategory && matchesSearch
        }
    }

    Scaffold(
        containerColor = BgDeep,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Watch Face Studio",
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Curated dials for Kolabee U8 Ultra",
                            color = TextDim,
                            fontSize = 12.sp
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextWhite)
                        }
                    } else {
                        Box(Modifier.padding(start = 16.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = AccentCyan)
                        }
                    }
                },
                actions = {
                    // Bluetooth connection badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (connState.isConnected) StatusSuccess.copy(alpha = 0.12f) else StatusWarning.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (connState.isConnected) StatusSuccess.copy(alpha = 0.3f) else StatusWarning.copy(alpha = 0.3f)),
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (connState.isConnected) StatusSuccess else StatusWarning)
                            )
                            Text(
                                if (connState.isConnected) "Watch Connected" else "Disconnected",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (connState.isConnected) StatusSuccess else StatusWarning
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgDeep)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Search Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                color = BgCard,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextFieldWithPlaceholder(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = "Search by style, author, or features…",
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextDim, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Category Filter Pills
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        onClick = { selectedCategory = cat },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) AccentCyan else BgCard,
                        border = BorderStroke(1.dp, if (isSelected) AccentCyan else BorderSubtle)
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Watch Face Cards Grid
            if (filteredItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Watch, contentDescription = null, tint = TextDim, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No watch faces match your query", color = TextWhite, fontWeight = FontWeight.Bold)
                        Text("Try choosing another category or clearing search filters", color = TextDim, fontSize = 12.sp)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredItems, key = { it.pkg.id }) { item ->
                        StoreFaceCard(
                            item = item,
                            onClick = { selectedItemForDetail = item },
                            onQuickInstall = {
                                if (!connState.isConnected) {
                                    Toast.makeText(context, "Connect watch via Bluetooth first", Toast.LENGTH_SHORT).show()
                                    return@StoreFaceCard
                                }
                                scope.launch {
                                    Toast.makeText(context, "Beaming '${item.pkg.name}' to Watch…", Toast.LENGTH_SHORT).show()
                                    val ok = WatchFaceSender.sendFaceToWatch(item.pkg, setActive = true)
                                    if (ok) {
                                        Toast.makeText(context, "✓ '${item.pkg.name}' installed & active on Watch!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Failed to send to Watch. Check connection.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    // Detail Bottom Sheet
    selectedItemForDetail?.let { item ->
        WatchFaceDetailSheet(
            item = item,
            isWatchConnected = connState.isConnected,
            onDismiss = { selectedItemForDetail = null }
        )
    }
}

@Composable
private fun StoreFaceCard(
    item: StoreItem,
    onClick: () -> Unit,
    onQuickInstall: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1:1 Live Canvas Watch Preview
            Box(
                modifier = Modifier
                    .size(136.dp)
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                DynamicFacePreview(
                    pkg = item.pkg,
                    modifier = Modifier.fillMaxSize(),
                    hours = 10,
                    minutes = 10,
                    seconds = 32,
                    heartRate = 72,
                    steps = 5420,
                    stepGoal = 10000,
                    battery = 88
                )
            }

            Spacer(Modifier.height(10.dp))

            // Title & Author
            Text(
                item.pkg.name,
                color = TextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                item.pkg.author,
                color = TextDim,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(8.dp))

            // Category & Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AccentCyan.copy(alpha = 0.1f)
                ) {
                    Text(
                        item.category,
                        color = AccentCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = TextDim, modifier = Modifier.size(11.dp))
                    Text(item.downloadCount, color = TextDim, fontSize = 10.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Action Button
            Surface(
                onClick = onQuickInstall,
                shape = RoundedCornerShape(12.dp),
                color = AccentCyan.copy(alpha = 0.14f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.SendToMobile, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Beam to Watch", color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WatchFaceDetailSheet(
    item: StoreItem,
    isWatchConnected: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }
    var sendSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Live ticking preview state
    var liveSeconds by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.SECOND)) }
    var isAodMode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            val cal = Calendar.getInstance()
            liveSeconds = cal.get(Calendar.SECOND)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCard,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextDim) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Large Interactive Preview
            item {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(if (isAodMode) Color(0xFF020406) else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    val cal = Calendar.getInstance()
                    val hr = cal.get(Calendar.HOUR)
                    val min = cal.get(Calendar.MINUTE)

                    DynamicFacePreview(
                        pkg = item.pkg,
                        modifier = Modifier.fillMaxSize(),
                        hours = hr,
                        minutes = min,
                        seconds = if (isAodMode) 0 else liveSeconds,
                        heartRate = 74,
                        steps = 6210,
                        stepGoal = 10000,
                        battery = 85
                    )
                }
            }

            // Mode Selector: Normal vs AOD
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    FilterChip(
                        selected = !isAodMode,
                        onClick = { isAodMode = false },
                        label = { Text("Active Mode (Live)") },
                        leadingIcon = { Icon(Icons.Default.LightMode, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan,
                            selectedLabelColor = Color.White
                        )
                    )
                    FilterChip(
                        selected = isAodMode,
                        onClick = { isAodMode = true },
                        label = { Text("AOD Ambient Mode") },
                        leadingIcon = { Icon(Icons.Default.DarkMode, null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            // Title, Author, Category
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.pkg.name, color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Black)
                    Text("By ${item.pkg.author} · v${item.pkg.version}", color = TextDim, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BgInput
                            ) {
                                Text(tag, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                    }
                }
            }

            // Description
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgInput,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("About this Watch Face", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(item.pkg.description, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }

            // Complications Supported Specs
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BgInput,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Hardware Features", color = TextWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        SpecRow("Dial Engine", "Native 60 FPS Canvas (.hswf)")
                        SpecRow("Heart Rate Complication", if (item.pkg.complications.heartRate.enabled) "Supported (Live BPM)" else "Disabled")
                        SpecRow("Daily Steps Complication", if (item.pkg.complications.steps.enabled) "Supported (Target Arc)" else "Disabled")
                        SpecRow("Battery Level Gauge", if (item.pkg.complications.battery.enabled) "Supported" else "Disabled")
                        SpecRow("Always-On Display (AOD)", "Optimized Low-Power Ambient")
                    }
                }
            }

            // Install Action Button
            item {
                if (errorMessage != null) {
                    Text(errorMessage!!, color = StatusError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                }

                Button(
                    onClick = {
                        if (!isWatchConnected) {
                            errorMessage = "Please pair and connect Kolabee U8 Ultra first."
                            return@Button
                        }
                        scope.launch {
                            isSending = true
                            errorMessage = null
                            sendSuccess = false
                            val ok = WatchFaceSender.sendFaceToWatch(item.pkg, setActive = true)
                            isSending = false
                            if (ok) {
                                sendSuccess = true
                                Toast.makeText(context, "✓ '${item.pkg.name}' installed and active on Watch!", Toast.LENGTH_LONG).show()
                                delay(1200)
                                onDismiss()
                            } else {
                                errorMessage = "Bluetooth transmission failed. Ensure watch is in range."
                            }
                        }
                    },
                    enabled = !isSending,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (sendSuccess) StatusSuccess else AccentCyan,
                        contentColor = Color.White
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Beaming to Kolabee Watch…", fontWeight = FontWeight.Bold)
                    } else if (sendSuccess) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Installed & Active on Watch!", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.InstallMobile, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Install & Set Active on Watch", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = TextDim, fontSize = 12.sp)
        Text(value, color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun BasicTextFieldWithPlaceholder(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TextWhite,
            fontSize = 14.sp
        ),
        singleLine = true,
        modifier = modifier,
        decorationBox = { innerTextField ->
            if (value.isEmpty()) {
                Text(placeholder, color = TextPlaceholder, fontSize = 14.sp)
            }
            innerTextField()
        }
    )
}
