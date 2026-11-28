package com.playbox.tv

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.util.Calendar

// ---------- ZEPH M3 Expressive (1.7.0) — shared colour constants ----------
// The Material 3 Expressive scheme (teal seed) as plain constants,
// for the places that draw outside MaterialTheme (gear sheets, the
// crash note, the player). Values match ZephColorScheme in M3Theme.kt.
// Motion is transform + opacity only, springy, 60fps.
internal val Primary = Color(0xFF4DD0C4)
internal val PrimaryContainer = Color(0xFF00504B)
internal val SecondaryContainer = Color(0xFF324B47)
internal val Mint = Color(0xFF7DF1E4)
internal val Bg = Color(0xFF0F1513)
internal val Surface = Color(0xFF1B2120)
internal val SurfaceHigh = Color(0xFF262D2B)
internal val White = Color.White
internal val Ink = Color(0xFF0A0F0E)
internal val Muted = Color(0xFFBFC9C5)
internal val Blue = Color(0xFF7DF1E4)
internal val Periwinkle = Color(0xFFE8B48C)
// Layered glass: translucent surface + a bright rim on the top edge.
internal val Glass = Color(0x941B2120)
internal val GlassStrong = Color(0xCC1B2120)
internal val GlassEdge = Color(0x14FFFFFF)
internal val GlassBorder = Color(0x2B4DD0C4)
// The rim light: a thin white line along the top edge of a glass
// surface — the one detail that makes glass read as glass.
internal val GlassRim = Color(0x40FFFFFF)
// Focus: teal ring + glow, scale 1.06, spring.
internal val FocusBorder = Color(0xBF4DD0C4)
internal val FocusGlow = Color(0x404DD0C4)
internal val AmbientGlow = Color(0x294DD0C4)

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
// Keys are the video's storage key (see ResumeStore.keyFor — the path
// itself, except FTP/SFTP play URLs, which are keyed without their
// password since 2.1.0). Batch 1.2 used path.hashCode(), which can
// collide and splits one video into two keys when it is reached as a
// file path and as a content:// URI.

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
    } else if (durationMs <= 0L && posMs > 5_000L) {
        // 2.0.9 (review A2): duration unknown (libvlc length is -1/0
        // until the demuxer reports it, and may never arrive) — store
        // the position RAW instead of deleting it. An early or sudden
        // exit used to ERASE the previously saved position, so the
        // video restarted from zero. Deletion stays only for
        // known-duration cases (finished / barely started, below).
        prefs.edit().putLong(ResumeStore.keyFor(path), posMs).apply()
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
