package com.playbox.tv

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// ---------- ZEPH Settings (M3 two-pane, 2.0.0 Patch 8) ----------
// The design's two-pane Settings: a 240dp category list on the
// left (Appearance / Video / Audio / Subtitles / About) and the
// category panel on the right (surface-container-low, anim-screen
// fade-through on change). On a narrow phone the categories become
// a chip row above the panel instead — same panels, same prefs.
// Every setting keeps its existing pref key, so the rebuild never
// loses a setting. The rail-label-mode row is GONE (the 88dp rail
// always shows its labels now, so the choice did nothing).
// The subtitle colour picker is gone too (the design's Subtitles
// panel is size + background + preview); the stored colour pref
// stays and the player still honours it.

private fun m3AppVersion(context: Context): Pair<String?, Long> = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName to info.longVersionCode
} catch (_: Exception) {
    null to 0L
}

/** The colour-grid dialog's swatches: the preset primaries plus a
 * spread of hues, so any seed is one tap away. */
private val ColourGrid = listOf(
    Color(0xFFF44336), Color(0xFFE91E63), Color(0xFF9C27B0),
    Color(0xFF673AB7), Color(0xFF3F51B5), Color(0xFF2196F3),
    Color(0xFF03A9F4), Color(0xFF00BCD4), Color(0xFF009688),
    Color(0xFF4CAF50), Color(0xFF8BC34A), Color(0xFFCDDC39),
    Color(0xFFFFEB3B), Color(0xFFFFC107), Color(0xFFFF9800),
    Color(0xFFFF5721), Color(0xFF795548), Color(0xFF9E9E9E)
)

private fun colourHex(colour: Color): String {
    val c = colour.value.toLong()
    return "#%06X".format((c shr 16) and 0xFFFFFF)
}

private enum class SettingsCategory(
    val label: String,
    val sub: String,
    val icon: ImageVector
) {
    Appearance("Appearance", "Theme & dynamic colour", Icons.Filled.Palette),
    Video("Video", "Decoding & playback", Icons.Filled.Movie),
    Audio("Audio", "Output & effects", Icons.Filled.Headphones),
    Subtitles("Subtitles", "Size & style", Icons.Filled.Subtitles),
    About("About", "Version & remote keys", Icons.Filled.Info)
}

@Composable
internal fun M3SettingsScreen(
    // Patch 4 (Jio): open the in-app Privacy Policy and Licences screens.
    onPrivacy: () -> Unit = {},
    onLicences: () -> Unit = {},
    // v5 (1.9.0): the theme choice — preset + custom seed. MainActivity
    // holds it in state so changing it re-renders the whole app.
    theme: String = ThemePresetViolet,
    onThemeChange: (String) -> Unit = {},
    themeSeedHex: String = "#6750A4",
    onThemeSeedChange: (String) -> Unit = {},
    // Patch 8: Dark / Light lives in MainActivity state too, so the
    // whole app re-renders when it flips.
    themeDarkMode: Boolean = true,
    onThemeDarkChange: (Boolean) -> Unit = {}
) {
    var category by remember { mutableStateOf(SettingsCategory.Appearance) }
    // The design auto-focuses the Appearance row when Settings opens.
    val appearanceFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { appearanceFocus.requestFocus() }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            ZephScreenTopBar("Settings")
            Spacer(modifier = Modifier.height(8.dp))
            if (wide) {
                Row(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier.width(240.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(SettingsCategory.values()) { cat ->
                            CategoryRow(
                                category = cat,
                                selected = cat == category,
                                onSelect = { category = cat },
                                focusRequester = if (cat == SettingsCategory.Appearance) appearanceFocus else null
                            )
                        }
                    }
                    SettingsPanel(modifier = Modifier.weight(1f)) {
                        CategoryPanel(
                            category = category,
                            theme = theme,
                            onThemeChange = onThemeChange,
                            themeSeedHex = themeSeedHex,
                            onThemeSeedChange = onThemeSeedChange,
                            themeDarkMode = themeDarkMode,
                            onThemeDarkChange = onThemeDarkChange,
                            onPrivacy = onPrivacy,
                            onLicences = onLicences
                        )
                    }
                }
            } else {
                // Narrow phone: categories as a chip row above the panel.
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(SettingsCategory.values()) { cat ->
                        ZephFilterChip(
                            label = cat.label,
                            selected = cat == category,
                            onClick = { category = cat }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                SettingsPanel(modifier = Modifier.weight(1f)) {
                    CategoryPanel(
                        category = category,
                        theme = theme,
                        onThemeChange = onThemeChange,
                        themeSeedHex = themeSeedHex,
                        onThemeSeedChange = onThemeSeedChange,
                        themeDarkMode = themeDarkMode,
                        onThemeDarkChange = onThemeDarkChange,
                        onPrivacy = onPrivacy,
                        onLicences = onLicences
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** The design's panel: surface-container-low, 12dp radius, 12dp
 * padding, and the anim-screen fade-through when the category
 * changes (420ms emphasized-decelerate, scale 0.94). */
@Composable
private fun SettingsPanel(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        content()
    }
}

/** One category in the left list — the design's ListItem: leading
 * icon, label + sub, selected = secondaryContainer. */
@Composable
private fun CategoryRow(
    category: SettingsCategory,
    selected: Boolean,
    onSelect: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .zephFocus(interaction, cornerRadius = 12.dp)
            .clickable(interactionSource = interaction, indication = null) { onSelect() }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            category.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                category.label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface
            )
            Text(
                category.sub,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryPanel(
    category: SettingsCategory,
    theme: String,
    onThemeChange: (String) -> Unit,
    themeSeedHex: String,
    onThemeSeedChange: (String) -> Unit,
    themeDarkMode: Boolean,
    onThemeDarkChange: (Boolean) -> Unit,
    onPrivacy: () -> Unit,
    onLicences: () -> Unit
) {
    AnimatedContent(
        targetState = category,
        transitionSpec = {
            (fadeIn(tween(420, easing = Motion.EmphasizedDecelerate)) + scaleIn(tween(420, easing = Motion.EmphasizedDecelerate), initialScale = 0.94f)) togetherWith
                fadeOut(tween(150))
        },
        label = "settings-panel"
    ) { cat ->
        when (cat) {
            SettingsCategory.Appearance -> AppearancePanel(
                theme = theme,
                onThemeChange = onThemeChange,
                themeSeedHex = themeSeedHex,
                onThemeSeedChange = onThemeSeedChange,
                themeDarkMode = themeDarkMode,
                onThemeDarkChange = onThemeDarkChange
            )
            SettingsCategory.Video -> VideoPanel()
            SettingsCategory.Audio -> AudioPanel()
            SettingsCategory.Subtitles -> SubtitlesPanel()
            SettingsCategory.About -> AboutPanel(onPrivacy = onPrivacy, onLicences = onLicences)
        }
    }
}

/** A segmented row in the design's style: title + desc above, chips
 * below (the design's Segmented is a checked chip row). */
@Composable
private fun SegmentedRow(
    title: String,
    desc: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(2.dp))
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            options.forEach { option ->
                ZephFilterChip(
                    label = option,
                    selected = selected == option,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

// ---------- Appearance ----------

@Composable
private fun AppearancePanel(
    theme: String,
    onThemeChange: (String) -> Unit,
    themeSeedHex: String,
    onThemeSeedChange: (String) -> Unit,
    themeDarkMode: Boolean,
    onThemeDarkChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var dynamicArtwork by remember { mutableStateOf(isDynamicArtworkOn(context)) }
    var reduceMotion by remember { mutableStateOf(isReduceMotion(context)) }
    var showColourPicker by remember { mutableStateOf(false) }
    var seedInput by remember(themeSeedHex) { mutableStateOf(themeSeedHex) }
    var seedError by remember { mutableStateOf<String?>(null) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SegmentedRow(
                title = "Theme",
                desc = "Choose a dark or light colour scheme",
                options = listOf("Dark", "Light"),
                selected = if (themeDarkMode) "Dark" else "Light",
                onSelect = { onThemeDarkChange(it == "Dark") }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            // The wallpaper Dynamic colour (1.9.0 Se3) — kept: it is
            // a real Android feature with no design counterpart.
            ZephSwitchRow(
                title = "Dynamic colour",
                subtitle = "Follows your device's wallpaper colours (Android 12+). Off goes back to Violet.",
                checked = theme == ThemePresetDynamic,
                onCheckedChange = { onThemeChange(if (it) ThemePresetDynamic else ThemePresetViolet) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            // The design's switch: Now Playing adopts colours from
            // the current album. Wired — it gates the Now Playing
            // dynamic-colour wash.
            ZephSwitchRow(
                title = "Dynamic colour from artwork",
                subtitle = "Now Playing adopts colours from the current album",
                checked = dynamicArtwork,
                onCheckedChange = { dynamicArtwork = it; saveDynamicArtworkOn(context, it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Text("Seed colour", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Material 3 generates a complete tonal palette from one colour.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemePresets.forEach { (preset, label) ->
                    val selected = theme == preset
                    // m13 (2.0.1): each swatch gets its own
                    // interaction source + zephFocus ring so the
                    // D-pad shows where focus is.
                    val swatchInteraction = remember { MutableInteractionSource() }
                    Box(
                        modifier = Modifier.size(44.dp)
                            .clip(CircleShape)
                            .zephFocus(swatchInteraction, cornerRadius = 22.dp)
                            .background(presetPrimary(preset), CircleShape)
                            .border(
                                if (selected) 3.dp else 1.dp,
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline,
                                CircleShape
                            )
                            .clickable(interactionSource = swatchInteraction, indication = null) { onThemeChange(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Filled.Check, contentDescription = "$label selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            // "Pick a colour" — a 44dp rainbow swatch that opens the
            // colour-grid dialog (the hex field lives inside the
            // dialog as the advanced path).
            // m13 (2.0.1): zephFocus ring so the D-pad shows it.
            val pickColourInteraction = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
                    .zephFocus(pickColourInteraction, cornerRadius = 16.dp)
                    .clickable(interactionSource = pickColourInteraction, indication = null) { showColourPicker = true }
            ) {
                Box(
                    modifier = Modifier.size(44.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    Color.Red, Color(0xFFFFEB3B), Color.Green,
                                    Color.Cyan, Color.Blue, Color(0xFFE91E63), Color.Red
                                )
                            )
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Palette, contentDescription = null,
                        tint = Color.White, modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Pick a colour", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        if (theme == ThemePresetCustom) "Current seed: $themeSeedHex" else "Any colour becomes the app's seed",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            // The design's tonal preview strips: the current scheme's
            // six tonal roles, live.
            TonalPreviewStrips()
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSwitchRow(
                title = "Reduce motion",
                subtitle = "Skips the Search stretch and the focus zoom. The app still animates, just less.",
                checked = reduceMotion,
                onCheckedChange = {
                    reduceMotion = it
                    context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE)
                        .edit().putBoolean("reduce_motion", it).apply()
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showColourPicker) {
        ColourPickerDialog(
            themeSeedHex = themeSeedHex,
            theme = theme,
            seedInput = seedInput,
            onSeedInputChange = { seedInput = it; seedError = null },
            seedError = seedError,
            onSeedErrorChange = { seedError = it },
            onPick = { hex ->
                onThemeSeedChange(hex)
                onThemeChange(ThemePresetCustom)
                seedInput = hex
                showColourPicker = false
            },
            onDismiss = { showColourPicker = false }
        )
    }
}

/** The design's six tonal strips, drawn from the live scheme. */
@Composable
private fun TonalPreviewStrips() {
    val scheme = MaterialTheme.colorScheme
    val strips = listOf(
        Triple("Primary", scheme.primary, scheme.onPrimary),
        Triple("Container", scheme.primaryContainer, scheme.onPrimaryContainer),
        Triple("Secondary", scheme.secondaryContainer, scheme.onSecondaryContainer),
        Triple("Tertiary", scheme.tertiaryContainer, scheme.onTertiaryContainer),
        Triple("Surface", scheme.surfaceContainerHighest, scheme.onSurface),
        Triple("Inverse", scheme.inverseSurface, scheme.inverseOnSurface)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        strips.forEach { (label, bg, fg) ->
            Box(
                modifier = Modifier.weight(1f).height(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg),
                contentAlignment = Alignment.BottomStart
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = fg,
                    modifier = Modifier.padding(6.dp)
                )
            }
        }
    }
}

// ---------- Video ----------

@Composable
private fun VideoPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    var hwDec by remember { mutableStateOf(hwDecPref(context)) }
    var defaultEngine by remember { mutableStateOf(defaultEnginePref(context)) }
    var resumeOn by remember { mutableStateOf(isResumeOn(context)) }
    var afr by remember { mutableStateOf(isAfrOn(context)) }
    var skip by remember { mutableStateOf(prefs.getInt("skip_seconds", 10)) }
    var speed by remember { mutableStateOf(prefs.getFloat("default_speed", 1f)) }
    var rememberVol by remember { mutableStateOf(rememberVolume(context)) }
    var brightness by remember { mutableStateOf(brightnessPct(context)) }
    var keepDisc by remember { mutableStateOf(keepDiscoverable(context)) }
    var castOn by remember { mutableStateOf(castReceiverOn(context)) }
    var smbServers by remember { mutableStateOf(SmbNetwork.servers(context)) }

    fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply { block(); apply() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // HW decoding and the default engine are SEPARATE controls
            // — the decoder is a choice INSIDE an engine, not the door.
            SegmentedRow(
                title = "Hardware decoding",
                desc = "Use the TV's video chip for smooth 4K playback",
                options = listOf("Off", "Decode", "Full"),
                selected = when (hwDec) {
                    "off" -> "Off"
                    "decode" -> "Decode"
                    else -> "Full"
                },
                onSelect = {
                    hwDec = when (it) {
                        "Off" -> "off"
                        "Decode" -> "decode"
                        else -> "full"
                    }
                    saveHwDecPref(context, hwDec)
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            SegmentedRow(
                title = "Default player",
                desc = "Auto is the default: Zeph picks the engine per file — Media3 for the common formats, VLC for HDR, FTP/SFTP files and formats Media3 cannot play. Any file can be given its own player on its Details page.",
                options = listOf("Auto", "Media3", "VLC"),
                selected = when (defaultEngine) {
                    PlayerEngine.AUTO -> "Auto"
                    PlayerEngine.MEDIA3 -> "Media3"
                    PlayerEngine.VLC -> "VLC"
                },
                onSelect = {
                    defaultEngine = when (it) {
                        "Media3" -> PlayerEngine.MEDIA3
                        "VLC" -> PlayerEngine.VLC
                        else -> PlayerEngine.AUTO
                    }
                    saveDefaultEnginePref(context, defaultEngine)
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSwitchRow(
                title = "Resume playback",
                subtitle = "Continue from where you stopped. When off, every video starts from the beginning.",
                checked = resumeOn,
                onCheckedChange = { resumeOn = it; save { putBoolean("resume_on", resumeOn) } }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            // Patch 8: the design's switch. The player-side frame-rate
            // matching lands in a later player update; the pref is
            // real persisted state.
            ZephSwitchRow(
                title = "Auto frame-rate matching",
                subtitle = "Switch display to 23.976 / 24 / 50 / 60 Hz",
                checked = afr,
                onCheckedChange = { afr = it; saveAfrOn(context, it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Text("Default speed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                    ZephFilterChip(label = "${s}x", selected = speed == s, onClick = { speed = s; save { putFloat("default_speed", s) } })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("New videos start at this speed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Text("Skip", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(10, 30).forEach { s ->
                    ZephFilterChip(label = "${s}s", selected = skip == s, onClick = { skip = s; save { putInt("skip_seconds", s) } })
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("How far −10s / +10s and the remote FF/RW keys jump.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSwitchRow(
                title = "Remember volume",
                subtitle = "Per video — each video reopens at the volume you left it.",
                checked = rememberVol,
                onCheckedChange = { rememberVol = it; saveRememberVolume(context, it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSliderRow(
                title = "Brightness",
                value = brightness / 100f,
                onValueChange = { brightness = (it * 100).toInt(); saveBrightnessPct(context, brightness) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Applies to the player window while a video is playing.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            // The cast door: the receiver is always on while the app
            // runs (live status), and the toggle decides whether it
            // stays discoverable after the app closes.
            // M25 (2.0.1): the receiver itself can now be switched
            // off — it starts/stops immediately, so no phone can
            // cast to this TV while it is off.
            Text("Cast receiver", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            ZephSwitchRow(
                title = "Cast receiver",
                subtitle = if (castOn) "On — your phone can cast videos to Zeph Player." else "Off — no phone can cast to this TV.",
                checked = castOn,
                onCheckedChange = { on ->
                    castOn = on
                    saveCastReceiverOn(context, on)
                    if (on) {
                        context.startService(android.content.Intent(context, CastService::class.java))
                    } else {
                        context.stopService(android.content.Intent(context, CastService::class.java))
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "The receiver starts with the app.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            ZephSwitchRow(
                title = "Keep discoverable when closed",
                subtitle = "Off (recommended): the cast receiver stops when the app closes, so nothing runs in the background. On: your phone can still find this TV after the app is closed.",
                checked = keepDisc,
                onCheckedChange = { keepDisc = it; saveKeepDiscoverable(context, keepDisc) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Text("SMB / NAS servers", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            if (smbServers.isEmpty()) {
                Text("No servers yet — add one from Browse.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                smbServers.forEach { server ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(formatSmbServer(server), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        ZephTonalButton(text = "Remove", onClick = {
                            SmbNetwork.removeServer(context, server)
                            smbServers = SmbNetwork.servers(context)
                        }, modifier = Modifier.height(44.dp))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
                Text("Add more servers from Browse.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text("Play from URL", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Text("In Browse. HTTP, HTTPS, HLS, DASH, RTSP, RTMP and NFS links.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------- Audio ----------

@Composable
private fun AudioPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    var eqOn by remember { mutableStateOf(prefs.getBoolean("eq_on", false)) }
    var loudness by remember { mutableStateOf(prefs.getInt("loudness_pct", 0)) }
    var passthrough by remember { mutableStateOf(isPassthroughOn(context)) }
    var replayGain by remember { mutableStateOf(isReplayGainOn(context)) }
    var gapless by remember { mutableStateOf(isGaplessOn(context)) }
    var visualizer by remember { mutableStateOf(isVisualizerOn(context)) }

    fun save(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply { block(); apply() }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            // Patch 8: the design's switches. Passthrough / replay
            // gain / gapless are real persisted prefs; the player-side
            // wiring lands in a later player update. The visualizer
            // switch IS wired now — it gates the Now Playing spectrum.
            ZephSwitchRow(
                title = "Audio passthrough",
                subtitle = "Send Dolby / DTS bitstream to your receiver",
                checked = passthrough,
                onCheckedChange = { passthrough = it; savePassthroughOn(context, it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSwitchRow(
                title = "Replay gain",
                subtitle = "Normalise loudness between tracks",
                checked = replayGain,
                onCheckedChange = { replayGain = it; saveReplayGainOn(context, it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSwitchRow(
                title = "Gapless playback",
                subtitle = "No silence between album tracks",
                checked = gapless,
                onCheckedChange = { gapless = it; saveGaplessOn(context, it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSwitchRow(
                title = "Show visualizer",
                subtitle = "Animated spectrum on the Now Playing screen",
                checked = visualizer,
                onCheckedChange = { visualizer = it; saveVisualizerOn(context, it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSwitchRow(
                title = "Equalizer",
                subtitle = "The bands and presets live in the player's gear.",
                checked = eqOn,
                onCheckedChange = { eqOn = it; save { putBoolean("eq_on", eqOn) } }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Text("Loudness", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZephTonalButton(text = "−", onClick = { loudness = (loudness - 10).coerceAtLeast(0); save { putInt("loudness_pct", loudness) } }, modifier = Modifier.height(44.dp))
                Text("$loudness%", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(64.dp))
                ZephTonalButton(text = "+", onClick = { loudness = (loudness + 10).coerceAtMost(100); save { putInt("loudness_pct", loudness) } }, modifier = Modifier.height(44.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Boosts quiet audio. 0% is off — this is the night-mode control.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Equalizer and loudness apply to the next video you play.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------- Subtitles ----------

@Composable
private fun SubtitlesPanel() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("playbox_settings", Context.MODE_PRIVATE) }
    var subsOn by remember { mutableStateOf(prefs.getBoolean("subtitles_on", true)) }
    var subSize by remember { mutableStateOf(subStyle(context).sizeSp) }
    var subBg by remember {
        mutableStateOf(
            context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                .getString("background", "black") == "black"
        )
    }
    val subColor = subStyle(context).color

    fun saveSubBg(on: Boolean) {
        context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
            .edit().putString("background", if (on) "black" else "none").apply()
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            ZephSwitchRow(
                title = "Subtitles",
                subtitle = "Subtitles are on by default for new videos.",
                checked = subsOn,
                onCheckedChange = { subsOn = it; prefs.edit().putBoolean("subtitles_on", subsOn).apply() }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            SegmentedRow(
                title = "Text size",
                desc = "Applies to all subtitle tracks",
                options = listOf("Small", "Medium", "Large"),
                selected = when (subSize) {
                    18f -> "Small"
                    32f -> "Large"
                    else -> "Medium"
                },
                onSelect = {
                    subSize = when (it) {
                        "Small" -> 18f
                        "Large" -> 32f
                        else -> 24f
                    }
                    context.getSharedPreferences("playbox_sub_style", Context.MODE_PRIVATE)
                        .edit().putFloat("size_sp", subSize).apply()
                }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            ZephSwitchRow(
                title = "Background",
                subtitle = "Dark scrim behind subtitle text",
                checked = subBg,
                onCheckedChange = { subBg = it; saveSubBg(it) }
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            // Live preview — the design's gradient box with a sample
            // cue, rendered with the current size / colour / scrim.
            Text("Preview", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(112.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.tertiaryContainer,
                                MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    )
                    .padding(16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    "“Some things are worth waiting for.”",
                    color = subColor,
                    fontSize = androidx.compose.ui.unit.TextUnit(subSize, androidx.compose.ui.unit.TextUnitType.Sp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .background(if (subBg) Color(0xB3000000) else Color.Transparent, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("These are the same subtitle settings as in the player's gear.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// ---------- About ----------

@Composable
private fun AboutPanel(
    onPrivacy: () -> Unit,
    onLicences: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(44.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Zeph Player", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
                    val (versionName, versionCode) = m3AppVersion(context)
                    Text(
                        "Version ${settingsVersionLabel(versionName, versionCode)} · Android TV · Material 3",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Text("Remote control", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))
            RemoteKeysTable()
            Spacer(modifier = Modifier.height(20.dp))
        }
        item {
            Text("Local and USB video and audio. Seek, fast-forward, rewind, speed, subtitles and audio tracks.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No sign-in. No ads. No Google Play Services.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ZephTonalButton(text = "Privacy policy", onClick = onPrivacy)
                ZephTonalButton(text = "Open-source licences", onClick = onLicences)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

/** The design's remote-keys table — the keys this app actually
 * handles. */
@Composable
private fun RemoteKeysTable() {
    val rows = listOf(
        "D-pad" to "Move focus",
        "OK / Enter" to "Select / play-pause",
        "◀ ▶ in player" to "Seek ±10 seconds",
        "▲ ▼ in player" to "Show controls",
        "Back" to "Close, go up",
        "Media keys" to "Play, pause, skip"
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (key, action) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    key,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.width(112.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(action, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------- Colour picker dialog (kept from 1.9.1 Se4) ----------

@Composable
private fun ColourPickerDialog(
    themeSeedHex: String,
    theme: String,
    seedInput: String,
    onSeedInputChange: (String) -> Unit,
    seedError: String?,
    onSeedErrorChange: (String?) -> Unit,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a colour") },
        text = {
            Column {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().height(190.dp)
                ) {
                    items(ColourGrid) { colour ->
                        val selected = theme == ThemePresetCustom && themeSeedHex.equals(colourHex(colour), ignoreCase = true)
                        Box(
                            modifier = Modifier.size(44.dp)
                                .clip(CircleShape)
                                .background(colour, CircleShape)
                                .border(
                                    if (selected) 3.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline,
                                    CircleShape
                                )
                                .clickable { onPick(colourHex(colour)) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (selected) {
                                Icon(
                                    Icons.Filled.Check, contentDescription = "Selected",
                                    tint = Color.White, modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ZephSearchField(
                        value = seedInput,
                        onValueChange = { onSeedInputChange(it); onSeedErrorChange(null) },
                        placeholder = "#6750A4",
                        modifier = Modifier.weight(1f)
                    )
                    ZephButton(text = "Apply", onClick = {
                        val hex = seedInput.trim()
                        if (Regex("^#?[0-9a-fA-F]{6}$").matches(hex)) {
                            val fixed = if (hex.startsWith("#")) hex else "#$hex"
                            onPick(fixed)
                            onSeedErrorChange(null)
                        } else {
                            onSeedErrorChange("Use 6 hex digits, like #6750A4.")
                        }
                    })
                }
                seedError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Any hex colour becomes the app's seed — the tonal scheme is derived from it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}
