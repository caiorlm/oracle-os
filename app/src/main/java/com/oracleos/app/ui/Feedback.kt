package com.oracleos.app.ui

import android.content.Context
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import android.view.View
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView

/** Strength of the touch response: a light tick, a confirmation, or an alert for emergency actions. */
enum class Tap { LIGHT, CONFIRM, ALERT }

/**
 * Touch feedback settings. Vibration uses the system haptic engine (no VIBRATE permission and it
 * follows the phone's own "touch vibration" setting); sound is the system click, off by default.
 */
object OracleFeedback {
    private const val PREFS = "oracle_feedback"
    var haptics by mutableStateOf(true)
        private set
    var sound by mutableStateOf(false)
        private set
    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        haptics = p.getBoolean("haptics", true)
        sound = p.getBoolean("sound", false)
    }

    fun updateHaptics(enabled: Boolean) { haptics = enabled; prefs?.edit()?.putBoolean("haptics", enabled)?.apply() }
    fun updateSound(enabled: Boolean) { sound = enabled; prefs?.edit()?.putBoolean("sound", enabled)?.apply() }

    fun perform(view: View, tap: Tap) {
        if (haptics) {
            val constant = when (tap) {
                Tap.LIGHT -> HapticFeedbackConstants.KEYBOARD_TAP
                Tap.CONFIRM -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                Tap.ALERT -> HapticFeedbackConstants.LONG_PRESS
            }
            view.performHapticFeedback(constant)
        }
        if (sound) view.playSoundEffect(SoundEffectConstants.CLICK)
    }
}

/** Returns a function that gives touch feedback from the current view. */
@Composable
fun rememberTapFeedback(): (Tap) -> Unit {
    val view = LocalView.current
    return remember(view) { { tap: Tap -> OracleFeedback.perform(view, tap) } }
}

/** Clickable with ripple, a slight press-in scale and touch feedback. */
fun Modifier.oracleClickable(tap: Tap = Tap.LIGHT, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val feedback = rememberTapFeedback()
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = LocalIndication.current) {
            feedback(tap)
            onClick()
        }
}
