package com.playbox.tv

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.util.Calendar

internal val Primary = Color(0xFF7B5CFF)
internal val PrimaryContainer = Color(0xFF4A2E8A)
internal val SecondaryContainer = Color(0xFF3A2E5C)
internal val Bg = Color(0xFF121318)
internal val Surface = Color(0xFF1A1B22)
internal val SurfaceHigh = Color(0xFF23242E)
internal val White = Color.White
internal val Muted = Color(0xFFA1A1AA)

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
