package com.playbox.tv

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// ---------- ZEPH motion — Material 3, not custom springs ----------
// "Full Material 3" means the motion is M3's own: the theme sets
// MotionScheme.expressive() (M3Theme.kt), so M3 components animate
// on M3's spatial/effects springs. The app's own transitions use
// M3's duration tokens and emphasized easings from the M3 motion
// spec, and M3's transition patterns:
//   - Fade through: 200ms — content swaps (tab content, lists).
//   - Shared axis X: 300ms — tab switches, forward/back.
//   - Shared axis (large): 400ms — bigger screen moves.
//   - Container transform: 500ms — SearchBar growing into the
//     Search screen, a cover becoming Details.
//   - Spatial springs (M3 Expressive): focus travel, press morph —
//     read from the theme's motion scheme, so they ARE M3's springs.
// Transform + opacity only, 60fps, no live blur. Under reduce
// motion the screens swap these for plain fades.

internal object Motion {
    // M3 duration tokens (ms).
    const val FadeThroughMs = 200
    const val SharedAxisMs = 300
    const val SharedAxisLargeMs = 400
    const val ContainerTransformMs = 500

    // M3 emphasized easings (cubic-bezier from the M3 motion spec).
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    // M3 Expressive spatial springs (Float) — from the theme's
    // motion scheme. These are M3's springs, velocity-following,
    // interruptible mid-flight.
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun spatial(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.defaultSpatial

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun fastSpatial(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.fastSpatial

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun slowSpatial(): FiniteAnimationSpec<Float> = MaterialTheme.motionScheme.slowSpatial

    // The shared specs the screens already call — now M3 tweens
    // (M3's shared-axis / fade-through motion is tween-based with
    // emphasized easing, not spring-based).
    //   soft:   panels, the player dock, sheets, tab switches.
    //   bouncy: focus travel, buttons, cards (emphasized decelerate).
    //   snap:   seek thumb, toggles, small state changes.
    fun <T> soft(): FiniteAnimationSpec<T> =
        tween(durationMillis = SharedAxisMs, easing = Emphasized)

    fun <T> bouncy(): FiniteAnimationSpec<T> =
        tween(durationMillis = ContainerTransformMs, easing = EmphasizedDecelerate)

    fun <T> snap(): FiniteAnimationSpec<T> =
        tween(durationMillis = FadeThroughMs, easing = Emphasized)
}
