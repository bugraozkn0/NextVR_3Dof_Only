package com.samrat.cardboardhands

import android.app.Activity
import android.content.Context

/** Explicit fallback for builds without the proprietary SDK; never pretends to provide position. */
object EasyArTracker {
    fun missingReason(context: Context): String = if (BuildConfig.BE)
        "EasyAR Native SDK missing; configure easyar.local.properties and rebuild"
        else "EasyAR Native is available in Cinema only"

    fun create(activity: Activity, headAt: ((Long, FloatArray) -> Unit)? = null): SixDof? = null
    fun failureReason(): String? = null
}
