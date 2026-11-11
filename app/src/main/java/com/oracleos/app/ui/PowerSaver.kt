package com.oracleos.app.ui

import android.app.Activity
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.lang.ref.WeakReference

/**
 * Extreme power saving. Oracle cannot (and should not) change privileged Android settings, so it
 * does what an app can: pure-black screens (AMOLED pixels switch off), a dimmed window, no
 * animations, no screen kept awake, local AI unloaded and GPS stopped (Survival mode), and
 * shortcuts to Android's own battery saver and screen settings for the user to switch on.
 */
object PowerSaver {
    private const val PREFS = "oracle_power"
    /** Window brightness while saving; [SYSTEM] leaves the user's own setting alone. */
    const val SYSTEM = -1f
    val LEVELS = listOf(0.02f, 0.08f, 0.2f, SYSTEM)

    var enabled by mutableStateOf(false)
        private set
    var brightness by mutableFloatStateOf(0.08f)
        private set
    private var prefs: android.content.SharedPreferences? = null
    private var current: WeakReference<Activity>? = null

    fun init(app: Application) {
        val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        enabled = p.getBoolean("enabled", false)
        brightness = p.getFloat("brightness", 0.08f)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) { current = WeakReference(activity); applyTo(activity) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun set(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("enabled", on)?.apply()
        current?.get()?.let(::applyTo)
    }

    fun setLevel(level: Float) {
        brightness = level
        prefs?.edit()?.putFloat("brightness", level)?.apply()
        current?.get()?.let(::applyTo)
    }

    /** Dims this window and lets the screen sleep normally (the compass otherwise keeps it on). */
    fun applyTo(activity: Activity) {
        val window = activity.window ?: return
        val params = window.attributes
        params.screenBrightness = if (enabled && brightness != SYSTEM) brightness
            else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = params
        if (enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun openBatterySaver(context: Context) = open(context, Settings.ACTION_BATTERY_SAVER_SETTINGS)
    fun openDisplay(context: Context) = open(context, Settings.ACTION_DISPLAY_SETTINGS)
    fun openAirplane(context: Context) = open(context, Settings.ACTION_AIRPLANE_MODE_SETTINGS)

    private fun open(context: Context, action: String) {
        try {
            context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Rough hours left with the screen mostly off, from the battery level (a phone idles ~1 %/h). */
    fun idleHoursLeft(percent: Int): Int = if (percent < 0) -1 else percent
}
