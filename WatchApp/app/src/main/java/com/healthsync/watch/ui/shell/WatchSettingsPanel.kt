package com.healthsync.watch.ui.shell

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.healthsync.watch.ui.RoundScrollView
import com.healthsync.watch.ui.WatchOptionsActivity
import com.healthsync.watch.ui.launcher.QuickSettingsActivity

/** Compact watch settings categories. Detailed forms remain ordinary scrollable screens. */
class WatchSettingsPanel(
    private val activity: AppCompatActivity,
    private val onClose: () -> Unit,
    private val navigate: (String) -> Unit,
    private val onTools: () -> Unit
) {
    private val content=LinearLayout(activity).apply {
        orientation=LinearLayout.VERTICAL
        val size=minOf(resources.displayMetrics.widthPixels,resources.displayMetrics.heightPixels)
        setPadding((size*.15f).toInt(),(size*.16f).toInt(),(size*.15f).toInt(),(size*.18f).toInt())
    }
    val view: View=RoundScrollView(activity).apply { setBackgroundColor(Color.BLACK); isFillViewport=true; addView(content) }
    init {
        content.addView(TextView(activity).apply { text="Settings"; textSize=23f; setTextColor(Color.WHITE); gravity=Gravity.CENTER },
            LinearLayout.LayoutParams(-1,dp(44)))
        row("Watch faces", "Choose your clock") { navigate("faces") }
        row("Quick controls", "Display · sound · connectivity") { navigate("controls") }
        row("Apps", "All · favorites · recent") { navigate("apps") }
        row("Phone controls", "Music · find phone · battery") { navigate("media") }
        row("Watch tools", "Timer · stopwatch · alarm") { onTools() }
        row("Health", "Measurements & readings") { options(WatchOptionsActivity.SCREEN_HEALTH) }
        row("Sensors", "Measurement intervals") { options(WatchOptionsActivity.SCREEN_SENSORS) }
        row("Phone connection", "Pairing & reconnect") { options(WatchOptionsActivity.SCREEN_PHONE) }
        row("Profile", "Goals · weight · height") { options(WatchOptionsActivity.SCREEN_PROFILE) }
        row("Display & AOD", "Faces · dim styles · brightness") { options(WatchOptionsActivity.SCREEN_DISPLAY) }
        row("Software bezel", "Round mask over other apps") { options(WatchOptionsActivity.SCREEN_BEZEL) }
        row("Home app", "Select or restore launcher") { launch(Intent(activity,QuickSettingsActivity::class.java).putExtra(QuickSettingsActivity.EXTRA_SCREEN,QuickSettingsActivity.SCREEN_HOME)) }
        row("Device information", "Android & available sensors") { launch(Intent(activity,QuickSettingsActivity::class.java).putExtra(QuickSettingsActivity.EXTRA_SCREEN,QuickSettingsActivity.SCREEN_DEVICE)) }
        row("App permissions", "Android permission settings") { launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}"))) }
        row("Watch notifications", "Launcher control · notification access") { options(WatchOptionsActivity.SCREEN_NOTIFICATIONS) }
        row("Battery optimization", "Background app settings") { launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
        row("System update", "Check for watch updates") { com.healthsync.watch.update.WatchUpdateManager.checkForDirectUpdate(activity) { msg -> Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() } }
        row("Android settings", "All system settings") { launch(Intent(Settings.ACTION_SETTINGS)) }
        row("Back", "") { onClose() }
    }
    private fun options(screen:String)=launch(Intent(activity,WatchOptionsActivity::class.java).putExtra(WatchOptionsActivity.EXTRA_SCREEN,screen))
    private fun launch(intent:Intent) { runCatching { activity.startActivity(intent) }.onFailure { Toast.makeText(activity,"This screen is unavailable on your watch",Toast.LENGTH_SHORT).show() } }
    private fun row(title:String,subtitle:String,action:()->Unit) {
        val item=LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER_VERTICAL; minimumHeight=dp(56)
            setPadding(dp(16),dp(12),dp(16),dp(12)); background=GradientDrawable().apply { cornerRadius=dp(24).toFloat(); setColor(0xFF14221B.toInt()) }
            isClickable=true; isFocusable=true; contentDescription=if(subtitle.isEmpty()) title else "$title. $subtitle"
            setOnClickListener { performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); action() }
        }
        item.addView(TextView(activity).apply { text=title; textSize=15f; setTextColor(Color.WHITE) })
        if(subtitle.isNotEmpty()) item.addView(TextView(activity).apply { text=subtitle; textSize=11f; setTextColor(0xFF9FAEA6.toInt()); setPadding(0,dp(3),0,0) })
        content.addView(item,LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,-2).apply { bottomMargin=dp(8) })
    }
    fun onResume()=Unit
    fun onPause()=Unit
    fun destroy()=Unit
    private fun dp(n:Int)=(n*activity.resources.displayMetrics.density).toInt()
}
