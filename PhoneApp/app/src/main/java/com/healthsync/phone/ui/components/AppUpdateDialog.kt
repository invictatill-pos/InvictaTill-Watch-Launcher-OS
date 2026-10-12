package com.healthsync.phone.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.healthsync.phone.data.update.AppVersionInfo
import com.healthsync.phone.data.update.UpdateStatus

@Composable
fun AppUpdateDialog(
    updateInfo: AppVersionInfo,
    status: UpdateStatus,
    isWatchUpdate: Boolean = false,
    onStartUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val busy = status is UpdateStatus.Downloading || status is UpdateStatus.TransferringToWatch || status is UpdateStatus.Checking
    val finishedSending = status is UpdateStatus.WatchInstallRequired
    val dismissible = !busy && (!updateInfo.forceUpdate || finishedSending)
    AlertDialog(
        onDismissRequest = { if (dismissible) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
                Icon(if (finishedSending) Icons.Default.CheckCircle else if (isWatchUpdate) Icons.Default.Watch else Icons.Default.SystemUpdate,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        },
        title = { Text(if (finishedSending) "Finish on your watch" else if (isWatchUpdate) "Update your watch" else "Update HealthSync", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(10.dp)) {
                    Text("Version ${updateInfo.versionName}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (updateInfo.changelog.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("What's new", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(updateInfo.changelog, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                when (status) {
                    is UpdateStatus.Checking -> UpdateProgress("Checking for an update…", null)
                    is UpdateStatus.Downloading -> UpdateProgress(
                        if (status.percent in 0..99) "Downloading · ${status.percent}%" else if (status.percent == 100) "Verifying your download…" else "Downloading your update…",
                        status.percent.takeIf { it in 0..99 }?.div(100f))
                    is UpdateStatus.TransferringToWatch -> UpdateProgress(
                        if (status.percent < 100) "Sending to your watch · ${status.percent}%" else "Waiting for watch verification…",
                        status.percent.takeIf { it < 100 }?.div(100f))
                    is UpdateStatus.WatchInstallRequired -> UpdateNotice(
                        if (status.verified) "Your watch verified the update. Confirm installation on the watch to finish."
                            else "Open the update notification on your watch and confirm installation. The current watch app cannot report verification back to your phone.")
                    is UpdateStatus.ReadyToInstall -> UpdateNotice("Your download is verified. If Android asks, allow HealthSync to install updates, return here, and tap Install.")
                    is UpdateStatus.Error -> Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(14.dp)) {
                        Text(status.message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    else -> Text(if (isWatchUpdate) "Keep your watch connected and nearby while the update transfers. Android will ask you to confirm installation on the watch."
                        else "Android will ask you to confirm installation after the download is verified.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            if (!busy) {
                Button(onClick = if (finishedSending) onDismiss else onStartUpdate, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp)) {
                    Text(when (status) {
                        is UpdateStatus.WatchInstallRequired -> "Done"
                        is UpdateStatus.ReadyToInstall -> "Install"
                        is UpdateStatus.Error -> "Try again"
                        else -> if (isWatchUpdate) "Download & send" else "Download update"
                    })
                }
            }
        },
        dismissButton = {
            if (dismissible && !finishedSending) TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (status is UpdateStatus.ReadyToInstall || status is UpdateStatus.Error) "Close" else "Later")
            }
        }
    )
}

@Composable
private fun UpdateProgress(label: String, progress: Float?) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
        else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun UpdateNotice(message: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp)) {
        Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
