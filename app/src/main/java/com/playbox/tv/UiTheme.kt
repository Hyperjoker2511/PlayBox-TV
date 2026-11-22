package com.playbox.tv

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.util.Calendar

// ---------- Batch 3 (Qwen design, approved 2026-11-22) ----------
// Material 3 Expressive + liquid glass. Deep ink background, violet
// focus glow, blue + periwinkle accents. Glass is a translucent solid,
// NOT real-time blur — the 2 GB Android 11 box cannot blur over live
// video cheaply, so beauty comes from colour, translucency, focus glow
// and spring motion (transform + opacity only).
internal val Primary = Color(0xFF8B5CF6)
internal val PrimaryContainer = Color(0xFF4C1D95)
internal val SecondaryContainer = Color(0xFF2E1A5E)
internal val Bg = Color(0xFF05070C)
internal val Surface = Color(0xFF0A0E18)
internal val SurfaceHigh = Color(0xFF121828)
internal val White = Color.White
internal val Muted = Color(0xFF9AA3B2)
internal val Blue = Color(0xFF60A5FA)
internal val Periwinkle = Color(0xFFA5B4FC)
// Frosted glass: translucent surface + thin luminous top edge.
internal val Glass = Color(0x940A0E18)
internal val GlassStrong = Color(0xCC0A0E18)
internal val GlassEdge = Color(0x14FFFFFF)
internal val GlassBorder = Color(0x2B8B5CF6)
// Focus: violet glow ring, scale 1.035–1.05, 240ms spring (Qwen).
internal val FocusBorder = Color(0xBF8B5CF6)
internal val FocusGlow = Color(0x408B5CF6)
internal val AmbientGlow = Color(0x298B5CF6)

internal fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (hour < 12) "Good morning" else if (hour < 18) "Good afternoon" else "Good evening"
}

internal fun resumePositions(context: Context): Map<String, Long> {
    val prefs = context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE)
    val out = mutableMapOf<String, Long>()
    prefs.all.forEach { (k, v) -> if (v is Long) out[k] = v }
    return out
}

// ---------- Resume positions (Batch 1.3) ----------
// Keys are the video path itself (see ResumeStore.keyFor). Batch 1.2 used
// path.hashCode(), which can collide and splits one video into two keys
// when it is reached as a file path and as a content:// URI.

internal fun savedResumePosition(context: Context, path: String): Long {
    val prefs = context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE)
    val current = prefs.getLong(ResumeStore.keyFor(path), 0L)
    if (current > 0L) return current
    // One-time migration from Batch 1.2 hashCode keys.
    val old = prefs.getLong(path.hashCode().toString(), 0L)
    if (old > 0L) {
        prefs.edit().putLong(ResumeStore.keyFor(path), old).remove(path.hashCode().toString()).apply()
        return old
    }
    return 0L
}

internal fun saveResumePosition(context: Context, path: String, posMs: Long, durationMs: Long) {
    val prefs = context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE)
    if (ResumeStore.shouldResume(posMs, durationMs)) {
        prefs.edit().putLong(ResumeStore.keyFor(path), posMs).apply()
        // Batch 1.4 (polish): "Continue watching" is ordered by when the
        // video was last watched, not by DATE_ADDED.
        recordWatchTime(context, path)
    } else {
        prefs.edit().remove(ResumeStore.keyFor(path)).apply()
        clearWatchTime(context, path)
    }
}

// ---------- Last-watched times (Batch 1.4) ----------
// Kept in a separate prefs file so the resume map stays position-only.

internal fun recordWatchTime(context: Context, path: String) {
    context.getSharedPreferences("playbox_resume_time", Context.MODE_PRIVATE)
        .edit().putLong(ResumeStore.keyFor(path), System.currentTimeMillis()).apply()
}

internal fun clearWatchTime(context: Context, path: String) {
    context.getSharedPreferences("playbox_resume_time", Context.MODE_PRIVATE)
        .edit().remove(ResumeStore.keyFor(path)).apply()
}

internal fun resumeTimes(context: Context): Map<String, Long> {
    val prefs = context.getSharedPreferences("playbox_resume_time", Context.MODE_PRIVATE)
    val out = mutableMapOf<String, Long>()
    prefs.all.forEach { (k, v) -> if (v is Long) out[k] = v }
    return out
}

internal fun clearResumePosition(context: Context, path: String) {
    context.getSharedPreferences("playbox_resume", Context.MODE_PRIVATE).edit()
        .remove(ResumeStore.keyFor(path)).remove(path.hashCode().toString()).apply()
}
