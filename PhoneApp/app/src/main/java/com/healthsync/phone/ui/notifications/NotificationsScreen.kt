package com.healthsync.phone.ui.notifications

import android.content.Intent
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.healthsync.phone.data.model.NotificationHistoryEntity
import com.healthsync.phone.ui.theme.AccentBlue
import com.healthsync.phone.ui.theme.AccentCyan
import com.healthsync.phone.ui.theme.BgCard
import com.healthsync.phone.ui.theme.BgCardHover
import com.healthsync.phone.ui.theme.BgDeep
import com.healthsync.phone.ui.theme.BgInput
import com.healthsync.phone.ui.theme.BorderSubtle
import com.healthsync.phone.ui.theme.StatusSuccess
import com.healthsync.phone.ui.theme.TextDim
import com.healthsync.phone.ui.theme.TextPlaceholder
import com.healthsync.phone.ui.theme.TextSecondary
import com.healthsync.phone.ui.theme.TextWhite
import com.healthsync.phone.viewmodel.AppFilterInfo
import com.healthsync.phone.viewmodel.NotificationsViewModel
import com.healthsync.phone.viewmodel.NotificationAppCategory
import com.healthsync.phone.viewmodel.filterNotificationApps
import com.healthsync.phone.ui.components.ResumeEffect
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDate
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel = hiltViewModel()) {
    val notifications by viewModel.notifications.collectAsState(initial = emptyList())
    val appFilters by viewModel.appFilters.collectAsState()
    val allAppsOn by viewModel.allAppsEnabled.collectAsState()
    val loadingApps by viewModel.isLoadingApps.collectAsState()
    val appLoadError by viewModel.appLoadError.collectAsState()
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var appSearch by rememberSaveable { mutableStateOf("") }
    var appCategory by rememberSaveable { mutableStateOf(NotificationAppCategory.ALL) }
    var period by rememberSaveable { mutableStateOf("All") }
    var hasAccess by remember { mutableStateOf(false) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    val openNotificationAccess = {
        try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (_: Exception) { }
    }
    ResumeEffect {
        hasAccess = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        today = LocalDate.now()
        viewModel.loadInstalledApps()
    }
    val since = when (period) {
        "Today" -> today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        "7 days" -> today.minusDays(6).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        else -> 0L
    }

    val groupedByDate = remember(notifications, searchQuery, since) {
        val filtered = notifications.filter {
            it.timestamp >= since && (searchQuery.isBlank() || it.title.contains(searchQuery, ignoreCase = true) ||
                    it.text.contains(searchQuery, ignoreCase = true) ||
                    it.appLabel.contains(searchQuery, ignoreCase = true))
        }
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        filtered.groupBy { df.format(Date(it.timestamp)) }.toList().sortedByDescending { it.first }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BgDeep),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            NotificationHeader(
                count = notifications.size,
                allAppsOn = allAppsOn,
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it }
            )
        }

        if (!hasAccess && selectedTab == 0) {
            item { NotificationAccessCard(onOpen = openNotificationAccess) }
        }

        if (selectedTab == 0) {
            item {
                SearchBox(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = "Search notifications"
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("All", "Today", "7 days")) { label ->
                        FilterChip(selected = period == label, onClick = { period = label }, label = { Text(label) })
                    }
                }
            }

            if (groupedByDate.isEmpty()) {
                item { EmptyNotificationsState(filtered = searchQuery.isNotBlank() || period != "All") }
            } else {
                groupedByDate.forEach { (dateKey, items) ->
                    item {
                        Text(
                            formatDateHeader(dateKey),
                            color = TextDim,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    items(items, key = { it.id }) { notification ->
                        NotificationCard(notification)
                    }
                }
            }
        } else {
            item { SearchBox(value = appSearch, onValueChange = { appSearch = it }, placeholder = "Find an app or system service") }
            item {
                AppFilterMasterCard(
                    allAppsOn = allAppsOn,
                    appCount = appFilters.size,
                    onMasterToggle = { viewModel.setAllAppsEnabled(it) },
                    onRefresh = { viewModel.loadInstalledApps() }
                )
            }
            if (!hasAccess) item { NotificationAccessCard(onOpen = openNotificationAccess) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (allAppsOn) "All apps includes system alerts. Turn this switch off to choose individual sources."
                        else "Choose apps and system services to mirror to your watch.",
                        color = TextSecondary, fontSize = 12.sp
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(NotificationAppCategory.entries) { category ->
                            FilterChip(
                                selected = appCategory == category,
                                onClick = { appCategory = category },
                                label = { Text(category.label) }
                            )
                        }
                    }
                }
            }

            if (loadingApps && appFilters.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = AccentCyan, modifier = Modifier.size(30.dp), strokeWidth = 3.dp)
                            Text("Loading apps", color = TextDim, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                appLoadError?.let { message -> item { Text(message, color = TextDim, fontSize = 13.sp) } }
                val matchingApps = filterNotificationApps(appFilters, appCategory, appSearch)
                val matchesInAllApps = filterNotificationApps(appFilters, NotificationAppCategory.ALL, appSearch)
                if (matchingApps.isEmpty()) item {
                    Column {
                        Text(
                            if (appCategory == NotificationAppCategory.SELECTED && appSearch.isBlank())
                                "No individual sources selected. Choose sources from All apps or System."
                            else if (appCategory != NotificationAppCategory.ALL && matchesInAllApps.isNotEmpty())
                                "This category hides matching apps. Search All apps to find them."
                            else "No matching sources. Refresh apps or check whether the app is in a paused or separate Android profile.",
                            color = TextDim, fontSize = 13.sp
                        )
                        if (appCategory != NotificationAppCategory.ALL) {
                            TextButton(onClick = { appCategory = NotificationAppCategory.ALL }) { Text("Show all apps", color = AccentCyan) }
                        }
                    }
                }
                items(matchingApps, key = { it.packageName }) { app ->
                    AppFilterItem(
                        app = app,
                        masterOn = allAppsOn,
                        onToggle = { enabled -> viewModel.toggleApp(app.packageName, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationAccessCard(onOpen: () -> Unit) {
    Surface(shape = RoundedCornerShape(18.dp), color = AccentBlue.copy(alpha = 0.08f), border = BorderStroke(1.dp, BorderSubtle)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Access needed to mirror notifications", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Android access enables HealthSync as the notification reader. Choose source apps here; mirroring starts after access is granted.", color = TextSecondary, fontSize = 12.sp)
            TextButton(onClick = onOpen) { Text("Enable notification access", color = AccentCyan) }
        }
    }
}

@Composable
private fun NotificationHeader(
    count: Int,
    allAppsOn: Boolean,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Alerts", color = TextWhite, fontSize = 34.sp, fontWeight = FontWeight.Black, lineHeight = 38.sp)
                Text("$count saved alerts", color = TextSecondary, fontSize = 13.sp)
            }
            Surface(shape = RoundedCornerShape(18.dp), color = AccentBlue.copy(alpha = 0.1f), border = BorderStroke(1.dp, AccentBlue.copy(alpha = 0.18f))) {
                Row(Modifier.padding(horizontal = 11.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (allAppsOn) Icons.Default.NotificationsActive else Icons.Default.FilterAlt, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                    Text(if (allAppsOn) "All apps" else "Filtered", color = AccentBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Surface(shape = RoundedCornerShape(20.dp), color = BgCard, border = BorderStroke(1.dp, BorderSubtle)) {
            Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                SegmentButton("History", selected = selectedTab == 0, modifier = Modifier.weight(1f), onClick = { onSelectTab(0) })
                SegmentButton("Filters", selected = selectedTab == 1, modifier = Modifier.weight(1f), onClick = { onSelectTab(1) })
            }
        }
    }
}

@Composable
private fun SegmentButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(42.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) AccentCyan else Color.Transparent
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = if (selected) Color.White else TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun SearchBox(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = TextPlaceholder, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = TextDim, modifier = Modifier.size(18.dp)) },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, "Clear search", tint = TextDim, modifier = Modifier.size(18.dp))
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

@Composable
private fun EmptyNotificationsState(filtered: Boolean = false) {
    Box(modifier = Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(78.dp).clip(CircleShape).background(BgCard).border(1.dp, BorderSubtle, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.NotificationsOff, null, tint = TextDim, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text(if (filtered) "No matching alerts" else "No notifications yet", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 19.sp)
            Text(if (filtered) "Change your search or time period" else "New allowed alerts appear here", color = TextDim, fontSize = 13.sp)
        }
    }
}

private fun formatDateHeader(dateStr: String): String {
    return try {
        val df = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val date = df.parse(dateStr) ?: return dateStr
        val today = Calendar.getInstance()
        val cal = Calendar.getInstance().apply { time = date }
        when {
            cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) && cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> "Today"
            dateStr == df.format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time) -> "Yesterday"
            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(date)
        }
    } catch (_: Exception) {
        dateStr
    }
}

@Composable
private fun NotificationCard(notification: NotificationHistoryEntity) {
    val timeFmt = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    var expanded by rememberSaveable(notification.id) { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Surface(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(AccentCyan.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(notification.appLabel.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = AccentCyan, fontSize = 16.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(notification.appLabel, color = AccentCyan, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(timeFmt.format(Date(notification.timestamp)), color = TextDim, fontSize = 10.sp)
                }
                Spacer(Modifier.height(3.dp))
                Text(notification.title.ifBlank { "Notification" }, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = if (expanded) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis)
                if (notification.text.isNotBlank()) {
                    Text(notification.text, color = TextSecondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                }
                if (expanded) TextButton(onClick = { clipboard.setText(AnnotatedString("${notification.title}\n${notification.text}")) }) { Text("Copy alert", color = AccentCyan, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun AppFilterMasterCard(
    allAppsOn: Boolean,
    appCount: Int,
    onMasterToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = BgCard,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(AccentCyan.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Apps, null, tint = AccentCyan, modifier = Modifier.size(21.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("App Mirroring", color = TextWhite, fontSize = 17.sp, fontWeight = FontWeight.Black)
                Text(if (allAppsOn) "$appCount apps allowed" else "Selected apps only", color = TextDim, fontSize = 12.sp)
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, "Refresh installed apps", tint = AccentBlue, modifier = Modifier.size(20.dp))
            }
            Switch(
                checked = allAppsOn,
                onCheckedChange = onMasterToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = BgCard,
                    uncheckedTrackColor = BgInput
                )
            )
        }
    }
}

@Composable
private fun AppFilterItem(app: AppFilterInfo, masterOn: Boolean, onToggle: (Boolean) -> Unit) {
    val enabled = masterOn || app.isEnabled
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = if (enabled) BgCard else BgCardHover, border = BorderStroke(1.dp, BorderSubtle)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(38.dp).clip(CircleShape).background(if (enabled) AccentCyan.copy(alpha = 0.12f) else TextDim.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Text(app.label.firstOrNull()?.uppercaseChar()?.toString() ?: "?", color = if (enabled) AccentCyan else TextDim, fontSize = 15.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text(app.label, color = if (enabled) TextWhite else TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.isSystemApp || app.hasPostedNotification) {
                    Text(
                        listOfNotNull(
                            "System app".takeIf { app.isSystemApp },
                            "Notification source".takeIf { app.hasPostedNotification }
                        ).joinToString(" · "),
                        color = AccentBlue, fontSize = 10.sp
                    )
                }
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                enabled = !masterOn,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentCyan,
                    uncheckedThumbColor = BgCard,
                    uncheckedTrackColor = BgInput,
                    disabledCheckedThumbColor = Color.White,
                    disabledCheckedTrackColor = StatusSuccess.copy(alpha = 0.42f)
                )
            )
        }
    }
}
