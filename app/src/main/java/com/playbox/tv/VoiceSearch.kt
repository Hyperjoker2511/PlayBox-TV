package com.playbox.tv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// ---------- Patch 4 (Jio): voice search ----------
// Shared by the Nova, Qwen and Aura search screens. The Jio remote
// has a mic, so the search field gets a mic button that opens the
// system's voice recognizer and puts the spoken words into the
// search field. Returns the mic button's click handler, or null
// when the box has no voice recognizer installed — the button hides
// then, so it never dead-ends.

@Composable
internal fun rememberVoiceSearch(
    context: Context,
    onSpoken: (String) -> Unit
): (() -> Unit)? {
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a video or song name")
        }
    }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) onSpoken(spoken)
        }
    }
    if (!voiceAvailable) return null
    return { runCatching { voiceLauncher.launch(voiceIntent) } }
}
