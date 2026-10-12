package com.healthsync.watch.ui

import android.app.Activity
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.healthsync.watch.sensor.InstalledHealthAppFallback

/** Launch the watch's original health app so the user can measure there, then return and retry. */
fun showHealthApps(activity: Activity) {
    val fallback = InstalledHealthAppFallback(activity)
    val apps = try { fallback.listApps() } catch (_: Exception) { emptyList() }
    if (apps.isEmpty()) {
        Toast.makeText(activity, "No other watch apps are available", Toast.LENGTH_LONG).show()
        return
    }
    val labels = apps.map { app -> if (app.suggested) "${app.label} • Health" else app.label }.toTypedArray()
    AlertDialog.Builder(activity)
        .setTitle("Open watch health app")
        .setItems(labels) { _, index ->
            if (!fallback.openHealthApp(apps[index].packageName))
                Toast.makeText(activity, "This watch app could not be opened", Toast.LENGTH_LONG).show()
        }
        .setNegativeButton("Back", null)
        .show()
}
