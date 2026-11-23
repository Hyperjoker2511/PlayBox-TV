package com.playbox.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

// ---------- 1.4.0 (Nocturne TV motion) ----------
// One motion language for the whole app, named once and reused —
// the same discipline as the colour tokens. Transform + opacity
// only: no blur animation, nothing the 2 GB box cannot hold at
// 60fps. Every spec is a spring, so motion follows velocity and
// can be interrupted mid-flight (a second D-pad press redirects
// the travel instead of queueing behind the first).
//  - Soft:   panels, the player dock, sheets — settles, no overshoot.
//  - Bouncy: focus travel, buttons, cards — a visible overshoot is
//            the delight the owner asks for.
//  - Snap:   seek thumb, toggles, small state changes.
// Under the reduce-motion setting the screens swap these for plain
// fades (see MainActivity's tab switch and TvComponents' focus).

internal object Motion {
    fun <T> soft(): SpringSpec<T> =
        spring(dampingRatio = 0.92f, stiffness = 260f)

    fun <T> bouncy(): SpringSpec<T> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 420f)

    fun <T> snap(): SpringSpec<T> =
        spring(dampingRatio = 0.78f, stiffness = 620f)
}
