package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.theme.PureTvTvTheme

enum class TvNavDestination(val label: String, val icon: ImageVector, val outlineIcon: ImageVector) {
    HOME("Live Channels", ExpressiveIcons.Home, ExpressiveIcons.HomeOutlined),
    BROWSE("Categories", ExpressiveIcons.Browse, ExpressiveIcons.BrowseOutlined),
    SEARCH("Search", ExpressiveIcons.Search, ExpressiveIcons.SearchOutlined),
    SETTINGS("Settings", ExpressiveIcons.Settings, ExpressiveIcons.SettingsOutlined),
    ACCOUNT("Account", ExpressiveIcons.Account, ExpressiveIcons.AccountOutlined),
}

/**
 * SECTION 07.2 / 07.3 [CRITICAL], collapsible left navigation rail.
 *
 * Per Section 7.3's D-pad rules: pressing LEFT while focus is on the leftmost
 * column of the content area moves focus into this rail (handled by the screen
 * via `focusRequester`, see [TvHomeScreen]) and expands it; the rail collapses
 * back to icon-only once focus leaves it. `animateContentSize` gives the
 * icon↔icon+label transition a smooth feel appropriate for the 10-foot UI.
 *
 * Each row is built on [Modifier.tvFocusClickable] so a focused row morphs,
 * scales and takes a primary ring, exactly like every other focusable object
 * in the app. Selection is a separate, persistent signal: the selected
 * destination keeps its `secondaryContainer` fill and filled glyph whether or
 * not it currently holds focus, since on a ten-foot UI the two are frequently
 * on different rows at once.
 */
@Composable
fun TvNavDrawer(
    selected: TvNavDestination,
    isLoggedIn: Boolean,
    onSelect: (TvNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val c = PureTvTvTheme.colors
    val shapes = PureTvTvTheme.shapes

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(if (expanded) 240.dp else 88.dp)
            .animateContentSize()
            .clip(shapes.paneShape)
            .background(c.surfaceContainer)
            // hasFocus is true while any child row holds focus, so the rail expands
            // on entry and collapses to icons the moment focus leaves it.
            .onFocusChanged { expanded = it.hasFocus }
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val destinations = TvNavDestination.entries.filter { it != TvNavDestination.ACCOUNT || !isLoggedIn }

        destinations.forEach { dest ->
            val isSelected = dest == selected
            val label = if (dest == TvNavDestination.ACCOUNT && !isLoggedIn) "Sign in" else dest.label
            val interaction = remember { MutableInteractionSource() }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .tvFocusClickable(
                        interaction = interaction,
                        onClick = { onSelect(dest) },
                        restRadius = 28.dp,
                        focusRadius = shapes.pillFocus,
                        color = if (isSelected) c.secondaryContainer else Color.Transparent,
                        focusColor = if (isSelected) c.secondaryContainer else c.surfaceHigh,
                    )
                    .padding(horizontal = 14.dp),
            ) {
                Icon(
                    imageVector = if (isSelected) dest.icon else dest.outlineIcon,
                    contentDescription = label,
                    tint = if (isSelected) c.onSecondaryContainer else c.onSurfaceVariant,
                    modifier = Modifier.size(26.dp),
                )
                if (expanded) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) c.onSecondaryContainer else c.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
