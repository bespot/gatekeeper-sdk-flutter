package com.bespot.gatekeeper_sdk_flutter

import android.os.Handler
import android.os.Looper

/** Runs work on the main thread. Unit tests replace it, because they have no main Looper. */
internal interface MainThread {
    fun post(task: Runnable)

    fun postDelayed(
        task: Runnable,
        delayMillis: Long,
    )

    fun removeCallbacks(task: Runnable)
}

internal class LooperMainThread : MainThread {
    // Created on first use, so creating the plugin does not need a Looper.
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    override fun post(task: Runnable) {
        handler.post(task)
    }

    override fun postDelayed(
        task: Runnable,
        delayMillis: Long,
    ) {
        handler.postDelayed(task, delayMillis)
    }

    override fun removeCallbacks(task: Runnable) {
        handler.removeCallbacks(task)
    }
}
