package com.healthsync.phone.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/** Refresh system settings after returning from a permission or Bluetooth screen. */
@Composable
fun ResumeEffect(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val action = rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) action.value()
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) action.value()
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
