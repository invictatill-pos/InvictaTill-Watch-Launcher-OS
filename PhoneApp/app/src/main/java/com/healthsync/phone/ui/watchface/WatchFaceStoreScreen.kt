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
import com.healthsync.phone.data.watchface.CustomFaceStore
import com.healthsync.phone.data.watchface.FaceStudioDocument
import com.healthsync.phone.data.watchface.HswfPackage
import com.healthsync.phone.data.watchface.OnlineWatchFaceCatalog
import com.healthsync.phone.data.watchface.OnlineWatchFaceCatalog.StoreItem
import com.healthsync.phone.data.watchface.WatchFaceSender
import com.healthsync.phone.service.BluetoothSyncService
import com.healthsync.phone.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

enum class StudioSection(val title: String, val subtitle: String) {
    STORE("Watch Face Store", "Design previews · health fields show placeholders"),
    CREATOR("Watch Face Creator", "Design your personalized dial"),
    MY_CREATIONS("My Creations", "Personal dials & custom designs")
}

/**
 * Phase 4 & Phase 5: Watch Face Studio & Store.
 * Provides curated online dial store, interactive Watch Face Creator Studio,
 * and user's saved custom designs library with 1-tap beam to Kolabee U8 Ultra.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchFaceStoreScreen(
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connState by BluetoothSyncService.connectionState.collectAsState()

    var currentSection by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(StudioSection.STORE) }
    var editingPackageForCreator by remember { mutableStateOf<HswfPackage?>(null) }
    var myCreationsRefreshTrigger by remember { mutableIntStateOf(0) }

    var selectedCategory by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("All") }
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var selectedItemForDetail by remember { mutableStateOf<StoreItem?>(null) }

    val categories = listOf("All", "Animated", "Interactive", "Luxury", "Sports", "Cyberpunk", "Retro", "Minimal", "Art")

    androidx.activity.compose.BackHandler(enabled = currentSection != StudioSection.STORE) { currentSection = StudioSection.STORE }

    // If Creator mode is active, display the full creator studio
    if (currentSection == StudioSection.CREATOR) {
        WatchFaceCreatorScreen(
            initialPackage = editingPackageForCreator,
            onBack = { currentSection = StudioSection.MY_CREATIONS },
            onFaceSaved = {
                myCreationsRefreshTrigger++
            }
        )
        return
    }

    val filteredItems = remember(selectedCategory, searchQuery) {
        OnlineWatchFaceCatalog.items.filter { item ->
            val matchesCategory = when (selectedCategory) {
                "All" -> true
                "Animated" -> item.pkg.animation != null && item.pkg.animation.type != "none"
                "Interactive" -> item.pkg.interactive != null
                else -> item.category.equals(selectedCategory, ignoreCase = true)
            }
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
                            currentSection.title,
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            currentSection.subtitle,
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
            // Segmented Top Navigation: [Explore Store] | [Creator Studio] | [My Creations]
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(14.dp),
                color = BgCard,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Store Tab
                    val isStore = currentSection == StudioSection.STORE
                    Surface(
                        onClick = { currentSection = StudioSection.STORE },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isStore) AccentCyan else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Storefront,
                                contentDescription = null,
                                tint = if (isStore) Color.White else TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Store",
                                color = if (isStore) Color.White else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isStore) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }

                    // Creator Tab
                    Surface(
                        onClick = {
                            editingPackageForCreator = null
                            currentSection = StudioSection.CREATOR
                        },
                        modifier = Modifier.weight(1.1f),
                        shape = RoundedCornerShape(10.dp),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Creator",
                                color = AccentCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // My Creations Tab
                    val isMyCreations = currentSection == StudioSection.MY_CREATIONS
                    Surface(
                        onClick = { currentSection = StudioSection.MY_CREATIONS },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = if (isMyCreations) AccentCyan else Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.CollectionsBookmark,
                                contentDescription = null,
                                tint = if (isMyCreations) Color.White else TextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "My Dials",
                                color = if (isMyCreations) Color.White else TextWhite,
                                fontSize = 12.sp,
                                fontWeight = if (isMyCreations) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Display content based on active section
            when (currentSection) {
                StudioSection.MY_CREATIONS -> {
                    MyCreationsView(
                        onOpenCreator = { pkgToEdit ->
                            editingPackageForCreator = pkgToEdit
                            currentSection = StudioSection.CREATOR
                        },
                        refreshTrigger = myCreationsRefreshTrigger,
                        onRefreshNeeded = { myCreationsRefreshTrigger++ },
                        isWatchConnected = connState.isConnected
                    )
                }
                StudioSection.STORE, StudioSection.CREATOR -> {
                    // Hero Creator Banner
                    Surface(
                        onClick = {
                            editingPackageForCreator = null
                            currentSection = StudioSection.CREATOR
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = BgCard,
                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(AccentCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AutoAwesome, null, tint = AccentCyan, modifier = Modifier.size(22.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Create Custom Dial or Photo Face", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Build personalized watch faces or beam family photos to Kolabee U8", color = TextDim, fontSize = 11.sp)
                            }
                            Icon(Icons.Default.ArrowForward, null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Search Bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
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
                            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                                            Toast.makeText(context, "Sending '${item.pkg.name}' to your watch…", Toast.LENGTH_SHORT).show()
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
        }
    }

    // Detail Bottom Sheet
    selectedItemForDetail?.let { item ->
        WatchFaceDetailSheet(
            item = item,
            isWatchConnected = connState.isConnected,
            onDismiss = { selectedItemForDetail = null },
            onCustomize = { pkg ->
                selectedItemForDetail = null
                editingPackageForCreator = pkg.copy(
                    id = "custom_${System.currentTimeMillis()}",
                    name = "${pkg.name} Custom",
                    author = "You"
                )
                currentSection = StudioSection.CREATOR
            }
        )
    }
}

/**
 * My Creations View: Displays all user-created and saved custom watch faces.
 */
@Composable
private fun MyCreationsView(
    onOpenCreator: (HswfPackage?) -> Unit,
    refreshTrigger: Int,
    onRefreshNeeded: () -> Unit,
    isWatchConnected: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val faceStore = remember { CustomFaceStore.getInstance(context) }
    val watchVersion by BluetoothSyncService.connectedWatchVersionCode.collectAsState()
    var myFaces by remember { mutableStateOf<List<HswfPackage>>(emptyList()) }
    var hasLoaded by remember { mutableStateOf(false) }
    var libraryError by remember { mutableStateOf<String?>(null) }
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var sortByName by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var sendingFaceId by remember { mutableStateOf<String?>(null) }
    var mutationFaceId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<HswfPackage?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(refreshTrigger) {
        libraryError = null
        try {
            myFaces = withContext(Dispatchers.IO) { faceStore.listCustomFaces() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            libraryError = "Could not load saved projects. Open My Creations again to retry."
        } finally { hasLoaded = true }
    }
    val filteredFaces = remember(myFaces, query, sortByName) {
        val search = query.trim()
        val matches = myFaces.filter { face ->
            search.isEmpty() || face.name.contains(search, ignoreCase = true) ||
                face.type.contains(search, ignoreCase = true) ||
                (face.background.type == "image" && "photo".contains(search, ignoreCase = true))
        }
        if (sortByName) matches.sortedBy { it.name.lowercase(Locale.ROOT) } else matches
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Saved projects", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${myFaces.size} ${if (myFaces.size == 1) "project" else "projects"}", color = TextDim, fontSize = 12.sp)
            }
            Button(
                onClick = { onOpenCreator(FaceStudioDocument.blank()) },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentCyan)
            ) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New project", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (myFaces.isNotEmpty()) {
            Surface(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp), color = BgCard, border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Row(Modifier.padding(start = 14.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = TextDim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    BasicTextFieldWithPlaceholder(query, { query = it }, "Search name or type…", Modifier.weight(1f).padding(vertical = 14.dp))
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, "Clear project search", tint = TextDim, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${filteredFaces.size} shown", color = TextDim, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortMenuOpen = true }) {
                        Icon(Icons.Default.Sort, null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (sortByName) "Name A–Z" else "Newest first", color = AccentCyan, fontSize = 12.sp)
                    }
                    DropdownMenu(expanded = sortMenuOpen, onDismissRequest = { sortMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("Newest first") }, onClick = { sortByName = false; sortMenuOpen = false })
                        DropdownMenuItem(text = { Text("Name A–Z") }, onClick = { sortByName = true; sortMenuOpen = false })
                    }
                }
            }
        }
        if (libraryError != null) Text(libraryError!!, color = StatusError, fontSize = 12.sp, modifier = Modifier.padding(16.dp))
        when {
            !hasLoaded -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(28.dp))
            }
            myFaces.isEmpty() -> LibraryEmptyState(
                title = "Your first design starts here",
                message = "Create a watch face from a blank canvas, a template, or your favorite photo.",
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            filteredFaces.isEmpty() -> LibraryEmptyState(
                title = "No matching projects",
                message = "Try another name, analog, digital, hybrid, or photo.",
                modifier = Modifier.fillMaxWidth().weight(1f),
                onClearSearch = { query = "" }
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(filteredFaces, key = { it.id }) { face ->
                    var actionsOpen by remember(face.id) { mutableStateOf(false) }
                    Surface(
                        shape = RoundedCornerShape(20.dp), color = BgCard,
                        border = BorderStroke(1.dp, BorderSubtle), modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(136.dp).padding(4.dp), contentAlignment = Alignment.Center) {
                                DynamicFacePreview(pkg = face, modifier = Modifier.fillMaxSize(), hours = 10, minutes = 10,
                                    seconds = 32, heartRate = -1, steps = -1, battery = -1)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(face.name, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (face.background.type == "image") "Photo dial" else "${face.type.replaceFirstChar { it.uppercase() }} · ${face.layers.size} layers",
                                color = AccentCyan, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Button(
                                    enabled = sendingFaceId == null && mutationFaceId == null,
                                    onClick = {
                                        if (!isWatchConnected) {
                                            Toast.makeText(context, "Connect your watch from Device before sending this face.", Toast.LENGTH_LONG).show()
                                        } else if (face.layers.isNotEmpty() && (watchVersion == null || watchVersion!! < 34)) {
                                            Toast.makeText(context, "Update the watch to HealthSync 2.9.0 or newer to render custom text and shape layers.", Toast.LENGTH_LONG).show()
                                        } else scope.launch {
                                            sendingFaceId = face.id
                                            try {
                                                val ok = WatchFaceSender.sendFaceToWatch(face, setActive = true)
                                                Toast.makeText(context, if (ok) "'${face.name}' is active on your watch." else
                                                    "Could not confirm installation. Check your watch and connection.", Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                Toast.makeText(context, "Could not send this face. Check Bluetooth and retry.", Toast.LENGTH_LONG).show()
                                            } finally { sendingFaceId = null }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentCyan), contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    if (sendingFaceId == face.id) CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                    else Text("Send", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(onClick = { onOpenCreator(face) }, modifier = Modifier.size(40.dp),
                                    shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderSubtle), contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Default.Edit, "Edit ${face.name}", tint = TextWhite, modifier = Modifier.size(16.dp))
                                }
                                Box {
                                    OutlinedButton(enabled = mutationFaceId == null, onClick = { actionsOpen = true }, modifier = Modifier.size(40.dp),
                                        shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, BorderSubtle), contentPadding = PaddingValues(0.dp)) {
                                        if (mutationFaceId == face.id) CircularProgressIndicator(Modifier.size(16.dp), color = AccentCyan, strokeWidth = 2.dp)
                                        else Icon(Icons.Default.MoreVert, "Actions for ${face.name}", tint = TextWhite, modifier = Modifier.size(18.dp))
                                    }
                                    DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                                        DropdownMenuItem(text = { Text("Duplicate project") }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = {
                                            actionsOpen = false
                                            scope.launch {
                                                mutationFaceId = face.id
                                                try {
                                                    val saved = withContext(Dispatchers.IO) {
                                                        faceStore.saveCustomFace(FaceStudioDocument.duplicate(face))
                                                    }
                                                    if (saved) onRefreshNeeded()
                                                    Toast.makeText(context, if (saved) "Saved a new copy of '${face.name}'." else
                                                        "Could not save the duplicate. Your original project is unchanged.", Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    if (e is CancellationException) throw e
                                                    Toast.makeText(context, "Could not duplicate this project.", Toast.LENGTH_LONG).show()
                                                } finally { mutationFaceId = null }
                                            }
                                        })
                                        DropdownMenuItem(text = { Text("Delete project", color = StatusError) }, leadingIcon = {
                                            Icon(Icons.Default.DeleteOutline, null, tint = StatusError)
                                        }, onClick = { actionsOpen = false; deleteError = null; deleteTarget = face })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        val deleting = mutationFaceId == target.id
        AlertDialog(
            onDismissRequest = { if (!deleting) deleteTarget = null },
            title = { Text("Delete project?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Delete '${target.name}' from My Creations? The installed face on your watch stays available.")
                    deleteError?.let { Text(it, color = StatusError, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    scope.launch {
                        mutationFaceId = target.id
                        deleteError = null
                        try {
                            val deleted = withContext(Dispatchers.IO) { faceStore.deleteCustomFace(target.id) }
                            if (deleted) {
                                deleteTarget = null
                                onRefreshNeeded()
                                Toast.makeText(context, "Deleted '${target.name}'.", Toast.LENGTH_SHORT).show()
                            } else deleteError = "Could not delete this project. Please retry."
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            deleteError = "Could not delete this project. Please retry."
                        } finally { mutationFaceId = null }
                    }
                }) { Text(if (deleting) "Deleting…" else "Delete", color = StatusError) }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun LibraryEmptyState(title: String, message: String, modifier: Modifier, onClearSearch: (() -> Unit)? = null) {
    Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Palette, null, tint = AccentCyan, modifier = Modifier.size(40.dp))
            Text(title, color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(message, color = TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
            if (onClearSearch != null) TextButton(onClick = onClearSearch) { Text("Clear search", color = AccentCyan) }
        }
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
                    heartRate = -1,
                    steps = -1,
                    stepGoal = 10000,
                    battery = -1
                )

                // Animated & Interactive Badges
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.pkg.animation != null && item.pkg.animation.type != "none") {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF06B6D4).copy(alpha = 0.9f)
                        ) {
                            Text(
                                "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (item.pkg.interactive != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFA855F7).copy(alpha = 0.9f)
                        ) {
                            Text(
                                "TAP",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
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

            Spacer(Modifier.height(10.dp))

            // Action: Install Button
            Surface(
                onClick = onQuickInstall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                shape = RoundedCornerShape(10.dp),
                color = AccentCyan.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.SendToMobile,
                        contentDescription = null,
                        tint = AccentCyan,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Send to watch",
                        color = AccentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
    onDismiss: () -> Unit,
    onCustomize: (HswfPackage) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSending by remember { mutableStateOf(false) }
    var sendSuccess by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var simSeconds by remember { mutableIntStateOf(30) }
    var simMinutes by remember { mutableIntStateOf(10) }
    var simHours by remember { mutableIntStateOf(10) }

    // Live ticking simulation
    LaunchedEffect(Unit) {
        while (true) {
            val cal = Calendar.getInstance()
            simHours = cal.get(Calendar.HOUR_OF_DAY)
            simMinutes = cal.get(Calendar.MINUTE)
            simSeconds = cal.get(Calendar.SECOND)
            delay(1000L)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgCardHover,
        dragHandle = { BottomSheetDefaults.DragHandle(color = TextDim) }
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Interactive 1:1 Live Preview
            item {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(4.dp, Color(0xFF1E293B), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    DynamicFacePreview(
                        pkg = item.pkg,
                        modifier = Modifier.fillMaxSize(),
                        hours = simHours,
                        minutes = simMinutes,
                        seconds = simSeconds,
                        heartRate = -1,
                        steps = -1,
                        battery = -1
                    )
                }
            }

            // Title & Category Badge
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(item.pkg.name, color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("By ${item.pkg.author} · ${item.category}", color = AccentCyan, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.pkg.description,
                        color = TextDim,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            // Specs Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = BgCard,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpecRow("Type", item.pkg.type.replaceFirstChar { it.uppercase() })
                        item.pkg.animation?.takeIf { it.type != "none" }?.let { anim ->
                            SpecRow("Animation", "${anim.type.replace('_', ' ').replaceFirstChar { it.uppercase() }} (60 FPS)")
                        }
                        item.pkg.interactive?.let { inter ->
                            SpecRow("Touch Control", "${inter.tapEffect.replace('_', ' ').replaceFirstChar { it.uppercase() }} + Haptic")
                        }
                        SpecRow("Dial", if (item.pkg.dial.showTicks) "${item.pkg.dial.tickCount} Ticks (${item.pkg.dial.numberType})" else "Minimal")
                        SpecRow("Target Display", "Kolabee U8 Ultra (320x320)")
                        SpecRow("Health fields", "Captured values on watch")
                    }
                }
            }

            // Actions: Customize in Studio & Install
            item {
                if (errorMessage != null) {
                    Text(errorMessage!!, color = StatusError, fontSize = 13.sp, modifier = Modifier.padding(bottom = 6.dp))
                }

                // Customize in Studio Button
                OutlinedButton(
                    onClick = { onCustomize(item.pkg) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Customize in Creator Studio", color = AccentCyan, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(8.dp))

                // Install Action Button
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
                                errorMessage = "Could not confirm installation. Check your watch and connection."
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
                        Text("Sending to your watch…", fontWeight = FontWeight.Bold)
                    } else if (sendSuccess) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Installed & Active on Watch!", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.SendToMobile, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Install & Set Active on Watch", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
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
