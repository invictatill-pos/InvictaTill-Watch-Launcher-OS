package com.healthsync.watch.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.healthsync.watch.timer.TimerPhase
import com.healthsync.watch.timer.WatchTimerScheduler
import com.healthsync.watch.timer.WatchTimerStore

class WatchTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WatchTimerScheduler.ACTION_STOP -> WatchTimerScheduler.stop(context)
            WatchTimerScheduler.ACTION_EXPIRE -> WatchTimerScheduler.expire(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                if (WatchTimerStore.read(context).phase == TimerPhase.RUNNING) WatchTimerScheduler.schedule(context)
            }
        }
    }
}
