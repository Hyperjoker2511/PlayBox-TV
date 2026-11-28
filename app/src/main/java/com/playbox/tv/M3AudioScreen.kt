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
import androidx.compose.material.icons.outlined.Album
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
// - the song-row chip shows the file's FORMAT word (FLAC /
//   MP3 / AAC) — the design's bitrate.split(" ")[0] — not a
//   bare kbps number.
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
    // 2.0.0 (fix pass): the real playing state (from the player
    // screens, via MainActivity) — the EqBars show the design's
    // paused state when the track is paused, instead of
    // animating forever. The shuffle mode is persistent
    // (store.tsx: shuffle is a boolean + setShuffle); the queue
    // honouring lands in Patch 5's Now Playing.
    isPlaying: Boolean = false,
    shuffleOn: Boolean = false,
    onShuffleChange: (Boolean) -> Unit = {},
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
    // The design's Math.round(total / 60) — not truncation.
    val totalMinutes = remember(audio) {
        Math.round(audio.sumOf { it.durationMs } / 60_000.0).toInt()
    }
    // M15 (2.0.1): autofocus on entry — the Shuffle button is
    // the first focusable; the user navigates down into the
    // songs from there.
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(modifier = Modifier.height(20.dp))
        M3ScreenHeader(
            title = "Audio",
            subtitle = "${audio.size} songs · ${albums.size} albums · $totalMinutes min",
            actions = {
                // 2.1.0 (UI batch): the design photo shows both
                // header actions as PLAIN TEXT with icons (white),
                // not a tonal + a filled button. Same handlers,
                // same autofocus, same disabled-when-empty.
                ZephTextButton(
                    text = "Shuffle",
                    onClick = {
                        if (audio.isNotEmpty()) {
                            onShuffleChange(true)
                            val shuffled = audio.shuffled()
                            onPlayFromStart(shuffled[Random.nextInt(shuffled.size)], shuffled)
                        }
                    },
                    modifier = Modifier.focusRequester(firstFocus),
                    // m4 (2.0.1): disabled when empty — the buttons
                    // were focusable but dead (guarded by
                    // audio.isNotEmpty() with no feedback).
                    enabled = audio.isNotEmpty(),
                    icon = Icons.Filled.Shuffle,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
                ZephTextButton(
                    text = "Play all",
                    onClick = {
                        if (audio.isNotEmpty()) {
                            onShuffleChange(false)
                            onPlayFromStart(audio.first(), audio)
                        }
                    },
                    enabled = audio.isNotEmpty(),
                    icon = Icons.Filled.PlayArrow,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            }
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            M3AudioTabs(tab = tab, onTab = { tab = it }, wide = wide)
        }
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val wide = maxWidth >= 700.dp
            val cols = if (wide) 5 else 3
            // Sub-tab content swaps on the same sequenced
            // fade-through as a screen change (2.1.0, motion
            // batch — audit MOT-7: this used to be a second,
            // hand-rolled copy of the old 420ms hold-and-scale
            // spec, with its own 200ms exit; the shell tabs, the
            // Settings panels and these sub-tabs are one spec now).
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    // 2.1.0 (motion batch, MOT-31): reduce motion
                    // collapses the swap to a plain short fade,
                    // like the shell's transitions.
                    if (isReduceMotion(context)) {
                        fadeIn(Motion.reducedFade()) togetherWith fadeOut(Motion.reducedFade())
                    } else {
                        fadeIn(Motion.screenEnterSpec()) togetherWith fadeOut(Motion.screenExitSpec())
                    }
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
                                                    onPlayFromStart(tracks.first(), tracks)
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
                                        isPlaying = isPlaying,
                                        isFav = favourites.contains(storageKeyFor(track.path)),
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

// (The album art is the shared M3AlbumArt in
// M3Components.kt — the design's layered hue art with ring
// and blob, not the flat gradient this screen used to draw.)

private fun hueOf(name: String): Int = ((name.hashCode() % 360) + 360) % 360

/** The design's primary Tabs (m3.tsx): 48dp, icon + title-s
 * label side by side, selected = primary text + a 3dp primary
 * indicator (inset 12dp, scale-x 400ms emphasized). The design's
 * Tab component stacks icon over text, so this is built by hand
 * to keep the icon beside the label.
 * 2.1.0 (UI batch): the design PHOTO's selected tab reads
 * on-surface (white) — only the indicator stays primary — and
 * the indicator spans the tab's content width (inset 24dp =
 * the content's own horizontal padding). */
@Composable
private fun M3AudioTabs(
    tab: String,
    onTab: (String) -> Unit,
    wide: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf(
                Triple("songs", "Songs", Icons.Filled.MusicNote to Icons.Outlined.MusicNote),
                Triple("albums", "Albums", Icons.Filled.Album to Icons.Outlined.Album),
                Triple("artists", "Artists", Icons.Filled.Person to Icons.Outlined.Person)
            ).forEach { (value, label, icons) ->
                val selected = tab == value
                val interaction = remember { MutableInteractionSource() }
                val indicatorScale by animateFloatAsState(
                    if (selected) 1f else 0f,
                    tween(400, easing = Motion.Emphasized), label = "audioTabIndicator"
                )
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        // The design's Tabs carry data-f but no lift
                        // class — outline + state layer, no zoom.
                        .zephFocus(interaction, focusScale = 1f, cornerRadius = 16.dp)
                        .clickable(interactionSource = interaction, indication = null) { onTab(value) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = if (wide) 24.dp else 16.dp)
                    ) {
                        Icon(
                            // Icons fill only when the tab is
                            // selected (m3.tsx: filled={selected}).
                            if (selected) icons.first else icons.second,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            label,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (selected) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = if (wide) 24.dp else 16.dp)
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
 * this is the current track, showing the REAL paused state),
 * title + "artist · album", and a trailing format chip (wide
 * screens only), favourite heart and duration. Selected (current
 * track) = secondaryContainer. A ListItem: 28dp radius
 * (rounded-xl), lift-sm focus (1.03). */
@Composable
private fun M3AudioSongRow(
    item: VideoItem,
    isCurrent: Boolean,
    isPlaying: Boolean,
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
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (isCurrent) MaterialTheme.colorScheme.secondaryContainer
                else Color.Transparent
            )
            .zephFocus(interaction, focusScale = 1.03f, cornerRadius = 28.dp)
            .clickable(interactionSource = interaction, indication = null) { onPlay() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .riseIn(minOf(index, 10) * 30),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            // The design's song-row art: 44dp, rounded-md (12dp).
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
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
                    // The REAL playing state — the bars flatten
                    // to 0.12 when the track is paused (the
                    // design's .paused.eq-bar), they no longer
                    // animate while paused.
                    ZephEqBars(playing = isPlaying, count = 3, color = Color.White)
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
                // The design's supporting is always
                // "artist · album" — never album alone.
                "$artist · $album",
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
                // The design's chip shows the FORMAT word
                // (bitrate.split(" ")[0] — FLAC / MP3 / AAC), not
                // the bare kbps number.
                val format = containerOf(item)
                Text(
                    format,
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

/** One album card (Music.tsx): square hue art + name + the
 * artist. The WHOLE card is focusable (lift), as on Home's
 * album cards. The design's sub is "artist · year" — the
 * scan has no year, so the artist alone; with no artist
 * known, the track count is the honest fallback. */
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
        modifier = modifier
            .riseIn(minOf(index, 10) * 60)
            .clip(RoundedCornerShape(16.dp))
            .zephFocus(interaction, cornerRadius = 16.dp)
            .clickable(interactionSource = interaction, indication = null) { onPlay() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
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
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // 2.0.2 (gridlines fix 3): album title/artist 12dp from
            // the card edge, matching the video card text (was 2dp).
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp)
        )
        Text(
            if (artist == "Unknown artist") "${tracks.size} tracks"
            else artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp)
        )
    }
}

/** One artist card (Music.tsx): ROUND hue art (person icon) +
 * name + "N album · M songs", centred. The whole card is the
 * button (the design's button has p-2 padding); tapping plays
 * the artist's tracks in library order — the design plays them
 * in data order, not sorted by album. */
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
        modifier = modifier
            .riseIn(minOf(index, 10) * 60)
            .clip(RoundedCornerShape(16.dp))
            .zephFocus(interaction, cornerRadius = 16.dp)
            .clickable(interactionSource = interaction, indication = null) { onPlay() }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(CircleShape)
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
