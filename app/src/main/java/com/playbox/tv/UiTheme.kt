package com.playbox.tv

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.util.Calendar

// ---------- LUMEN (2026-11-23) — the assistant's own design, from scratch ----------
// "Light on ink." The screen is deep ink; the light comes from the
// content itself and from a thin rim of light on every glass edge —
// no live blur anywhere (the 2 GB box and the phone cannot afford
// it), so the glass is layered translucency + a bright top edge.
// Two accents share the work: violet is the brand (focus, glow, the
// selected tab) and mint is the "go" colour (Play, Resume, progress,
// the selected track). Everything else is white or muted grey on ink.
// Motion is transform + opacity only, springy, 60fps.
internal val Primary = Color(0xFF8B5CF6)
internal val PrimaryContainer = Color(0xFF4C1D95)
internal val SecondaryContainer = Color(0xFF2E1A5E)
// 1.4.0 (Nocturne TV): ONE accent — the violet. Mint retired as a
// second accent (the owner's one-accent rule from Xreader); the
// value stays only so older call sites keep compiling while the
// components now draw every "go" surface in violet.
internal val Mint = Color(0xFF8EF3DB)
internal val Bg = Color(0xFF000000)
internal val Surface = Color(0xFF0C0D12)
internal val SurfaceHigh = Color(0xFF161822)
internal val White = Color.White
internal val Ink = Color(0xFF101524)
internal val Muted = Color(0xFF9AA3B2)
internal val Blue = Color(0xFF60A5FA)
internal val Periwinkle = Color(0xFFA5B4FC)
// Layered glass: translucent surface + a bright rim on the top edge.
internal val Glass = Color(0x940D1119)
internal val GlassStrong = Color(0xCC0D1119)
internal val GlassEdge = Color(0x14FFFFFF)
internal val GlassBorder = Color(0x2B8B5CF6)
// The rim light: a thin white line along the top edge of a glass
// surface — the one detail that makes glass read as glass.
internal val GlassRim = Color(0x40FFFFFF)
// Focus: violet ring + glow, scale 1.06, spring (Lumen).
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
