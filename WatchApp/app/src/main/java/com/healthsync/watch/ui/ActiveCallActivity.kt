package com.healthsync.watch.ui

/** Uses the same phone-controlled call screen for calls confirmed by the phone. */
class ActiveCallActivity : InCallActivity() {
    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_NUMBER = "number"
    }
}
