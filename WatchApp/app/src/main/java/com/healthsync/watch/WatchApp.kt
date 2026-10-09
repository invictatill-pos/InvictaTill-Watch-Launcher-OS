package com.healthsync.watch

import android.app.Application
import android.app.Activity
import android.os.Bundle

class WatchApp : Application() {
    companion object {
        lateinit var instance: WatchApp
            private set
        @Volatile var isForeground = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var started = 0
            override fun onActivityStarted(activity: Activity) { started++; isForeground = started > 0 }
            override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0); isForeground = started > 0 }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {
                com.healthsync.watch.service.BezelOverlayService.reconcile(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}
