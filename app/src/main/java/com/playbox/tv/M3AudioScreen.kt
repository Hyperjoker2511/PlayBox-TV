package com.playbox.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.random.Random

// ---------- ZEPH Audio screen (Patch 3 — the design's Music.tsx) ----------
// Header + Shuffle / Play all, Songs / Albums / Artists tabs.
//
// Honest mappings — the scan has no artist/album/bitrate tags
// (MediaLibrary reads no ARTIST/ALBUM columns):
// - album = the parent folder name; artist = the grandparent
//   folder name (the common Artist/Album/track layout).
// - bitrate = the track's average bitrate, computed from real
//   size and duration (sizeBytes * 8 / duration, kbps).
// - album art = the design's hue gradient from the folder name
//   (the same hue as Home's album cards).
// Song / album / artist taps play (the design's playTracks) —
// there is no Details entry on this screen. The favourite heart
// on a song row is display-only, as in the design; favouriting
// happens from Now Playing (Patch 5).

@Composable
internal fun M3AudioScreen(
    audio: List<VideoItem>,
    loading: Boolean,
    playing: VideoItem?,
    onPlayFromStart: (VideoItem, List<VideoItem>) -> Unit
) {
    val context = LocalContext.current
    val favourites = LibraryStores.favourites(context)
    var tab by remember { mutableStateOf("songs") }
    val albums = remember(audio) {
        audio.groupBy { albumOf(it) }.entries.sortedBy { it.key }
    }
    val artists = remember(audio) {
        audio.groupBy { artistOf(it) }.entries.sortedBy { it.key }
    }
    val totalMinutes = remember(audio) {
        (audio.sumOf { it.durationMs } / 60000L).toInt()
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(24.dp))
        M3ScreenHeader(
            title = "Audio",
            subtitle = "${audio.size} songs · ${albums.size} albums · $totalMinutes min",
            actions = {
                ZephTonalButton(
                    text = "Shuffle",
                    onClick = {
                        if (audio.isNotEmpty()) {
                            val shuffled = audio.shuffled()
                            onPlayFromStart(shuffled[Random.nextInt(shuffled.size)], shuffled)
                        }
                    },
                    icon = Icons.Filled.Shuffle
                )
                ZephButton(
                    text = "Play all",
                    onClick = {
                        if (audio.isNotEmpty()) onPlayFromStart(audio.first(), audio)
                    },
                    icon = Icons.Filled.PlayArrow
                )
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            M3AudioTabs(tab = tab, onTab = { tab = it }, wide = wide)
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            val cols = if (wide) 5 else 3
            // The design's tab content fades through on switch.
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    fadeIn(tween(420, easing = Motion.EmphasizedDecelerate)) togetherWith
                        fadeOut(tween(200))
                },
                label = "audioTab"
            ) { currentTab ->
                when (currentTab) {
                    "albums" -> {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                            if (albums.isEmpty()) {
                                item { M3AudioEmpty(loading) }
                            } else {
                                itemsIndexed(
                                    albums.chunked(cols),
                                    key = { _, row -> row.firstOrNull()?.key ?: "row" }
                                ) { rowIndex, row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        row.forEachIndexed { colIndex, (name, tracks) ->
                                            M3AlbumGridCard(
                                                name = name,
                                                artist = artistOf(tracks.first()),
                                                tracks = tracks,
                                                index = rowIndex * cols + colIndex,
                                                onPlay = { onPlayFromStart(tracks.first(), tracks) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat((cols - row.size).coerceAtLeast(0)) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                    "artists" -> {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                            if (artists.isEmpty()) {
                                item { M3AudioEmpty(loading) }
                            } else {
                                itemsIndexed(
                                    artists.chunked(cols),
                                    key = { _, row -> row.firstOrNull()?.key ?: "row" }
                                ) { rowIndex, row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        row.forEachIndexed { colIndex, (name, tracks) ->
                                            val albumCount = tracks.map { albumOf(it) }.distinct().size
                                            M3ArtistGridCard(
                                                name = name,
                                                albumCount = albumCount,
                                                trackCount = tracks.size,
                                                index = rowIndex * cols + colIndex,
                                                onPlay = {
                                                    val sorted = tracks.sortedWith(
                                                        compareBy({ albumOf(it) }, { it.name.lowercase() })
                                                    )
                                                    onPlayFromStart(sorted.first(), sorted)
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        repeat((cols - row.size).coerceAtLeast(0)) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                            if (audio.isEmpty()) {
                                item { M3AudioEmpty(loading) }
                            } else {
                                items(audio, key = { it.path }) { track ->
                                    M3AudioSongRow(
                                        item = track,
                                        isCurrent = playing?.path == track.path,
                                        isFav = favourites.contains(track.path),
                                        wide = wide,
                                        index = audio.indexOf(track),
                                        onPlay = { onPlayFromStart(track, audio) }
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The folder name of a track's parent folder — the album. */
private fun albumOf(item: VideoItem): String =
    File(item.path).parentFile?.name ?: "Music"

/** The grandparent folder name — the artist in the common
 * Artist/Album/track layout; "Unknown artist" when there is none. */
private fun artistOf(item: VideoItem): String {
    val parent = File(item.path).parentFile
    val grandparent = parent?.parentFile?.name
    return if (grandparent != null && grandparent != parent?.name) grandparent else "Unknown artist"
}

/** The design's AlbumArt: a hue gradient from a name + a centred
 * icon. The caller supplies size and shape in the modifier. */
@Composable
internal fun M3AlbumArt(
    hue: Int,
    icon: ImageVector,
    iconSize: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    Color.hsl(hue.toFloat(), 0.6f, 0.38f),
                    Color.hsl(((hue + 30) % 360).toFloat(), 0.65f, 0.22f)
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(iconSize)
        )
    }
}

private fun hueOf(name: String): Int = ((name.hashCode() % 360) + 360) % 360

/** The design's primary Tabs (m3.tsx): 48dp, icon + title-s
 * label side by side, selected = primary text + a 3dp primary
 * indicator (inset 12dp, scale-x 400ms emphasized). The design's
 * Tab component stacks icon over text, so this is built by hand
 * to keep the icon beside the label. */
@Composable
private fun M3AudioTabs(
    tab: String,
    onTab: (String) -> Unit,
    wide: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                Triple("songs", Icons.Filled.MusicNote, "Songs"),
                Triple("albums", Icons.Filled.Album, "Albums"),
                Triple("artists", Icons.Filled.Person, "Artists")
            ).forEach { (value, icon, label) ->
                val selected = tab == value
                val interaction = remember { MutableInteractionSource() }
                val indicatorScale by animateFloatAsState(
                    if (selected) 1f else 0f,
                    tween(400, easing = Motion.Emphasized), label = "audioTabIndicator"
                )
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .zephFocus(interaction)
                        .clickable(interactionSource = interaction, indication = null) { onTab(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = if (wide) 24.dp else 16.dp)
                    ) {
                        Icon(
                            icon, contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .height(3.dp)
                            .graphicsLayer { scaleX = indicatorScale }
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 1.dp)
    }
}

/** One song row (Music.tsx): 44dp album art (EqBars over it when
 * this is the current track), title + "artist · album", and a
 * trailing bitrate chip (wide screens only), favourite heart and
 * duration. Selected (current track) = secondaryContainer. */
@Composable
private fun M3AudioSongRow(
    item: VideoItem,
    isCurrent: Boolean,
    isFav: Boolean,
    wide: Boolean,
    index: Int,
    onPlay: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val album = albumOf(item)
    val artist = artistOf(item)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .zephFocus(interaction)
            .clickable(interactionSource = interaction, indication = null) { onPlay() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .riseIn(minOf(index, 10) * 30),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
        ) {
            M3AlbumArt(
                hue = hueOf(album),
                icon = Icons.Filled.MusicNote,
                iconSize = 20.dp,
                modifier = Modifier.fillMaxSize()
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    ZephEqBars(playing = true, count = 3, color = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (artist == "Unknown artist") album else "$artist · $album",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (wide) {
                val kbps = bitrateKbps(item)
                if (kbps > 0) {
                    Text(
                        "$kbps",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
            }
            if (isFav) {
                Icon(
                    Icons.Filled.Favorite, contentDescription = "Favourite",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
            }
            Text(
                if (item.durationMs > 0L) TimeFormatter.format(item.durationMs) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                modifier = Modifier.width(40.dp)
            )
        }
    }
}

/** A track's average bitrate from real size and duration. */
private fun bitrateKbps(item: VideoItem): Int =
    if (item.durationMs > 0 && item.sizeBytes > 0) {
        (item.sizeBytes * 8L * 1000L / item.durationMs).toInt()
    } else 0

/** One album card (Music.tsx): square hue art + name + "artist ·
 * N tracks". Focus lands on the art, as on Home's album cards. */
@Composable
private fun M3AlbumGridCard(
    name: String,
    artist: String,
    tracks: List<VideoItem>,
    index: Int,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.riseIn(minOf(index, 10) * 60)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .zephFocus(interaction)
                .clickable(interactionSource = interaction, indication = null) { onPlay() }
        ) {
            M3AlbumArt(
                hue = hueOf(name),
                icon = Icons.Filled.MusicNote,
                iconSize = 52.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp)
        )
        Text(
            if (artist == "Unknown artist") "${tracks.size} tracks"
            else "$artist · ${tracks.size} tracks",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, end = 2.dp)
        )
    }
}

/** One artist card (Music.tsx): ROUND hue art (person icon) +
 * name + "N album · M songs", centred. */
@Composable
private fun M3ArtistGridCard(
    name: String,
    albumCount: Int,
    trackCount: Int,
    index: Int,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = modifier.riseIn(minOf(index, 10) * 60),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
                .zephFocus(interaction)
                .clickable(interactionSource = interaction, indication = null) { onPlay() }
        ) {
            M3AlbumArt(
                hue = hueOf(name),
                icon = Icons.Filled.Person,
                iconSize = 52.dp,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            "$albumCount album · $trackCount songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun M3AudioEmpty(loading: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Filled.MusicNote, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (loading) "Loading…" else "Nothing here yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
