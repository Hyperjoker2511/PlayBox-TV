package com.playbox.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Date

// ---------- 1.4.0 (crash notes + breadcrumbs) ----------
// The 1.3.x crash hunt was blind: a native libvlc death leaves no
// Java stack, and the app had no record of how far a play attempt
// got. Two records fix that, both written with commit() — a record
// whose whole job is to survive the process dying must never ride
// the async apply() queue:
//  1. A breadcrumb: the last step a play attempt reached ("engine
//     chosen", "vlc: play called", "media3: ready"…). Steps are set
//     by the player screens as they advance. 2.1.0: the last few
//     steps are kept as a trail (CrashLog.trail) and shown on the
//     note screen — the stage-by-stage path through the open is
//     what names the dying stage (demuxer vs tracks vs surface).
//  2. A crash note: the full stack of any uncaught Java exception,
//     saved by the handler installed in MainActivity.onCreate.
// On the next cold start MainActivity shows the note screen before
// anything else when either a note exists or the VLC guard marker
// is stale (a native death leaves the marker but no note — the
// missing note IS the evidence). Continue clears both.

internal object CrashLog {
    private const val PREFS = "playbox_crash"
    private const val KEY_NOTE = "note"
    private const val KEY_STEP = "last_step"
    private const val KEY_TRAIL = "trail"

    // 2.1.0: the trail keeps the last few steps, not just the final
    // one — on the Jio box no log can ever be pulled, so this trail
    // is the only crash evidence that exists, and the step BEFORE
    // the last one is usually the stage that died.
    private const val MAX_TRAIL = 12

    /** Install the uncaught-exception handler. Call once, first
     * thing in MainActivity.onCreate. The previous handler still
     * runs afterwards, so the system crash behaviour is unchanged. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = error.stackTraceToString().lineSequence().take(40).joinToString("\n")
                val steps = trail(app).joinToString("\n")
                val note = "When: ${Date()}\nLast step: ${lastStep(app)}\nSteps:\n$steps\n\n$trace"
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY_NOTE, note).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Record how far the current play attempt got. Synchronous on
     * purpose (see the header). Cheap: a handful of calls per play. */
    fun step(context: Context, step: String) {
        runCatching {
            val app = context.applicationContext
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val steps = (trail(app) + step).takeLast(MAX_TRAIL)
            prefs.edit()
                .putString(KEY_STEP, step)
                .putString(KEY_TRAIL, steps.joinToString("\n"))
                .commit()
        }
    }

    /** Start a fresh attempt: the trail from any previous attempt
     * is dropped, then this step is recorded as its first. Called
     * where a play begins (MainActivity's engine-chosen point), so
     * the trail on the crash screen is always THIS attempt's path
     * through the open, not a mixture of two attempts. */
    fun beginAttempt(context: Context, step: String) {
        runCatching {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY_TRAIL).commit()
        }
        step(context, step)
    }

    fun lastStep(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STEP, "") ?: ""

    /** The current attempt's steps, oldest first. */
    fun trail(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRAIL, null)
            ?.split("\n")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun note(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NOTE, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_NOTE).remove(KEY_TRAIL).commit()
    }
}

/** The note screen: what crashed, or — when there is no Java note —
 * the fact that the app died natively, plus the last breadcrumb.
 * Shown once per crash; Continue clears the records.
 * 2.0.9 (review D5): when the VLC crash guard fired, say so in
 * plain language and name the file + decoding mode (vlcDeath) —
 * silent engine switching used to leave the owner wondering why
 * the next video opened in Media3. */
@Composable
internal fun CrashNoteScreen(
    note: String?,
    lastStep: String,
    vlcDeath: Pair<String?, String?>? = null,
    trail: List<String> = emptyList(),
    onContinue: () -> Unit
) {
    val continueFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { continueFocus.requestFocus() } }
    Column(
        modifier = Modifier.fillMaxSize().background(Bg).padding(64.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("PlayBox stopped last time", fontSize = 36.sp, color = White)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (note != null) {
                "It hit an error it could not recover from. The details are below — " +
                    "they say exactly where it happened."
            } else {
                "There is no error report, which means the player library itself died " +
                    "underneath the app (a native crash). The last step it reached is below."
            },
            fontSize = 20.sp, color = Muted
        )
        // 2.0.9 (review D5): the guard fired — VLC died on the last
        // video, so this one opens in Media3. Name the file and the
        // decoding mode so the crash becomes evidence, not a mystery.
        if (vlcDeath != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                buildString {
                    append("VLC crashed on the last video")
                    if (vlcDeath.first != null) append(" (${vlcDeath.first})")
                    if (vlcDeath.second != null) append(" in ${vlcDeath.second} mode")
                    append(", so this one opened in Media3 instead — change it back anytime.")
                },
                fontSize = 20.sp, color = White
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
        ) {
            if (note != null) {
                Text(note, fontSize = 15.sp, color = Muted)
            }
            // 2.1.0: the attempt's step trail — how far the open
            // got, stage by stage. For a native death (no note)
            // this is the whole evidence; the last line is where
            // it died.
            if (trail.isNotEmpty()) {
                if (note != null) Spacer(modifier = Modifier.height(16.dp))
                Text("Steps it reached:", fontSize = 15.sp, color = White)
                Spacer(modifier = Modifier.height(4.dp))
                trail.forEach { step ->
                    Text(step, fontSize = 15.sp, color = Muted)
                }
            } else if (note == null) {
                Text("Last step reached: $lastStep", fontSize = 15.sp, color = Muted)
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        TvButton("Continue", Modifier.focusRequester(continueFocus)) { onContinue() }
    }
}
