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
//     by the player screens as they advance.
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

    /** Install the uncaught-exception handler. Call once, first
     * thing in MainActivity.onCreate. The previous handler still
     * runs afterwards, so the system crash behaviour is unchanged. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = error.stackTraceToString().lineSequence().take(40).joinToString("\n")
                val note = "When: ${Date()}\nLast step: ${lastStep(app)}\n\n$trace"
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
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_STEP, step).commit()
        }
    }

    fun lastStep(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STEP, "") ?: ""

    fun note(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_NOTE, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY_NOTE).commit()
    }
}

/** The note screen: what crashed, or — when there is no Java note —
 * the fact that the app died natively, plus the last breadcrumb.
 * Shown once per crash; Continue clears the records. */
@Composable
internal fun CrashNoteScreen(
    note: String?,
    lastStep: String,
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
        Spacer(modifier = Modifier.height(20.dp))
        Column(
            modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
        ) {
            Text(
                note ?: "Last step reached: $lastStep",
                fontSize = 15.sp, color = Muted
            )
        }
        Spacer(modifier = Modifier.height(28.dp))
        TvButton("Continue", Modifier.focusRequester(continueFocus)) { onContinue() }
    }
}
