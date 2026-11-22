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
