package com.lagradost.cloudstream3.utils

import android.os.Handler
import android.os.Looper

fun runOnMainThreadNative(work: () -> Unit) {
    val mainHandler = Handler(Looper.getMainLooper())
    mainHandler.post {
        work()
    }
}