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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// ---------- ZEPH Legal (M3 redesign, Phase 7) ----------
// Patch 4 (Jio): Privacy Policy + Open-source licences.
// Jio's validation checklist requires both screens inside the app.
// Zeph Player collects nothing, so the policy is short and plain.
// Same words as the old Legal screens — only the look is M3.

@Composable
internal fun M3PrivacyPolicyScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Privacy policy", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Zeph Player — last updated November 2026", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        M3PrivacySection(
            "What Zeph Player is",
            "Zeph Player is a video and audio player for your TV. It plays videos and music stored on the TV, on a USB drive, or on folders and servers on your own home network (SMB / NAS, FTP / SFTP). There is no sign-in, no account, and no online service."
        )
        M3PrivacySection(
            "What the app collects",
            "Nothing. Zeph Player collects no personal data, no usage data, and no analytics. It has no ads, no trackers, and no third-party analytics SDK. Nothing you watch or listen to is recorded anywhere outside the TV."
        )
        M3PrivacySection(
            "Media access",
            "To show your library, the app reads the names, lengths, dates and thumbnails of video and audio files on the TV and on USB drives you connect. This information stays on the TV. It is never uploaded, shared, or sent to any server."
        )
        M3PrivacySection(
            "Casting (DLNA)",
            "When the cast receiver is on, a phone on the same Wi-Fi can send a video to the TV. The receiver only works on your local network — no video, title, or device information leaves your home network."
        )
        M3PrivacySection(
            "Network folders",
            "If you add a network folder (SMB / NAS, FTP / SFTP), the address and login you enter are stored only on this TV, so the app can reconnect. They are never sent anywhere else."
        )
        M3PrivacySection(
            "Permissions",
            "The app asks only for the permissions it needs to play your media: reading video and audio files, network access for local folders and casting, and keeping the screen awake while a video plays. It asks for nothing else."
        )
        M3PrivacySection(
            "Children",
            "Zeph Player has no accounts, no chat, and no online content. It plays only the files you already have."
        )
        M3PrivacySection(
            "Changes",
            "If this policy ever changes, the new version will appear here in the app."
        )
        M3PrivacySection(
            "Contact",
            "Questions about this policy: $PrivacyContactEmail"
        )
        Spacer(modifier = Modifier.height(28.dp))
        Row {
            ZephTonalButton(text = "Back", onClick = onBack)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun M3PrivacySection(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    Spacer(modifier = Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(modifier = Modifier.height(20.dp))
}

@Composable
internal fun M3LicencesScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val scroll = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 48.dp, vertical = 32.dp)) {
        Text("Open-source licences", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Zeph Player is built on the open-source libraries below. Their full licence texts are available from their authors and from the links in the app's source repository.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        OssLibraries.forEach { lib ->
            Text(lib.name, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(6.dp))
            Text(lib.use, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Text(lib.licence, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            ZephTonalButton(text = "Back", onClick = onBack)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
