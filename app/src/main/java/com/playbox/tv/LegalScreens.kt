package com.playbox.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- Patch 4 (Jio): Privacy Policy + Open-source licences ----------
// Jio's validation checklist requires both screens inside the app.
// Zeph Player collects nothing, so the policy is short and plain.

/** The contact address for privacy questions — the same address the
 * Jio developer account is registered under. */
internal const val PrivacyContactEmail = "eldenring25112001@gmail.com"

@Composable
internal fun PrivacyPolicyScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Privacy policy", fontSize = 28.sp, color = White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Zeph Player — last updated November 2026", fontSize = 16.sp, color = Muted)
        Spacer(modifier = Modifier.height(24.dp))
        PrivacySection(
            "What Zeph Player is",
            "Zeph Player is a video and audio player for your TV. It plays videos and music stored on the TV, on a USB drive, or on folders and servers on your own home network (SMB / NAS, FTP / SFTP). There is no sign-in, no account, and no online service."
        )
        PrivacySection(
            "What the app collects",
            "Nothing. Zeph Player collects no personal data, no usage data, and no analytics. It has no ads, no trackers, and no third-party analytics SDK. Nothing you watch or listen to is recorded anywhere outside the TV."
        )
        PrivacySection(
            "Media access",
            "To show your library, the app reads the names, lengths, dates and thumbnails of video and audio files on the TV and on USB drives you connect. This information stays on the TV. It is never uploaded, shared, or sent to any server."
        )
        PrivacySection(
            "Casting (DLNA)",
            "When the cast receiver is on, a phone on the same Wi-Fi can send a video to the TV. The receiver only works on your local network — no video, title, or device information leaves your home network."
        )
        PrivacySection(
            "Network folders",
            "If you add a network folder (SMB / NAS, FTP / SFTP), the address and login you enter are stored only on this TV, so the app can reconnect. They are never sent anywhere else."
        )
        PrivacySection(
            "Permissions",
            "The app asks only for the permissions it needs to play your media: reading video and audio files, network access for local folders and casting, and keeping the screen awake while a video plays. It asks for nothing else."
        )
        PrivacySection(
            "Children",
            "Zeph Player has no accounts, no chat, and no online content. It plays only the files you already have."
        )
        PrivacySection(
            "Changes",
            "If this policy ever changes, the new version will appear here in the app."
        )
        PrivacySection(
            "Contact",
            "Questions about this policy: $PrivacyContactEmail"
        )
        Spacer(modifier = Modifier.height(28.dp))
        Row {
            TvButton("Back", onClick = onBack)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Text(title, fontSize = 20.sp, color = White, fontWeight = FontWeight.SemiBold)
    Spacer(modifier = Modifier.height(6.dp))
    Text(body, fontSize = 17.sp, color = Muted)
    Spacer(modifier = Modifier.height(20.dp))
}

/** One open-source library in the app: its name, what it does, and
 * its licence. Only libraries actually in the build are listed. */
private data class OssLibrary(val name: String, val use: String, val licence: String)

private val OssLibraries = listOf(
    OssLibrary(
        "LibVLC 3.7.7", "The second playback engine — formats Media3 cannot play, HDR tone mapping, audio/subtitle delay, deinterlace, video adjust, volume above 100%, chapters.",
        "GNU Lesser General Public License v2.1 (LGPL 2.1) — VideoLAN"
    ),
    OssLibrary(
        "Media3 / ExoPlayer 1.6.1", "The main playback engine, plus DASH/HLS streaming and the media session for background audio.",
        "Apache License 2.0 — Google"
    ),
    OssLibrary(
        "Jetpack Compose + AndroidX", "The user interface, lifecycle, and document-file (storage access) support.",
        "Apache License 2.0 — Google"
    ),
    OssLibrary(
        "Kotlin", "The programming language the app is written in.",
        "Apache License 2.0 — JetBrains"
    ),
    OssLibrary(
        "jcifs-ng 2.1.9", "SMB / NAS folder browsing (SMB1/2/3).",
        "GNU Lesser General Public License v2.1 (LGPL 2.1)"
    ),
    OssLibrary(
        "Apache Commons Net 3.10.0", "FTP folder browsing.",
        "Apache License 2.0 — Apache Software Foundation"
    ),
    OssLibrary(
        "JSch 0.2.18 (mwiede fork)", "SFTP folder browsing.",
        "BSD-style licence — JCraft / mwiede"
    ),
    OssLibrary(
        "Plus Jakarta Sans", "The app's font.",
        "SIL Open Font License 1.1 — Tokotype"
    )
)

@Composable
internal fun LicencesScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Open-source licences", fontSize = 28.sp, color = White, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Zeph Player is built on the open-source libraries below. Their full licence texts are available from their authors and from the links in the app's source repository.",
            fontSize = 16.sp, color = Muted
        )
        Spacer(modifier = Modifier.height(24.dp))
        OssLibraries.forEach { lib ->
            Text(lib.name, fontSize = 20.sp, color = White, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(6.dp))
            Text(lib.use, fontSize = 17.sp, color = Muted)
            Spacer(modifier = Modifier.height(4.dp))
            Text(lib.licence, fontSize = 16.sp, color = Muted)
            Spacer(modifier = Modifier.height(20.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            TvButton("Back", onClick = onBack)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
