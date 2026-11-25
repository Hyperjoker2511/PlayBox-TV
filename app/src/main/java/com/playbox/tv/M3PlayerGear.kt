package com.playbox.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

// ---------- ZEPH Player gear building blocks (M3 redesign) ----------
// The gear sheets (PlayerGear.kt, VlcGear.kt) keep all their settings
// logic — only the look changes. These blocks mirror the old
// GlassButton / GearSection signatures, so the restyle is a rename:
// GlassButton -> M3GearButton, GearSection -> M3GearSection.
// onClick is LAST here, so trailing-lambda syntax works (unlike the
// Zeph buttons, where onClick comes before modifier/icon).

/** An M3 gear button — same signature as the old GlassButton. */
@Composable
internal fun M3GearButton(
    text: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .zephFocus(interaction)
            .clip(RoundedCornerShape(28.dp))
            .background(
                when {
                    primary || active -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHigh
                },
                RoundedCornerShape(28.dp)
            )
            .border(
                1.dp,
                if (primary || active) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                RoundedCornerShape(28.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = if (primary || active) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/** An M3 gear section — title + content, M3 typography. */
@Composable
internal fun M3GearSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
