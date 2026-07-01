package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.puretv.twitch.tv.ui.theme.PureTvTvColors

enum class TvNavDestination(val label: String, val icon: ImageVector) {
    HOME("Live Channels", Icons.Default.Home),
    BROWSE("Categories", Icons.Default.Apps),
    SEARCH("Search", Icons.Default.Search),
    SETTINGS("Settings", Icons.Default.Settings),
    ACCOUNT("Account", Icons.Default.AccountCircle),
}

/**
 * SECTION 07.2 / 07.3 [CRITICAL] — collapsible left navigation rail.
 *
 * Per Section 7.3's D-pad rules: pressing LEFT while focus is on the leftmost
 * column of the content area moves focus into this rail (handled by the screen
 * via `focusRequester` — see [TvHomeScreen]) and expands it; the rail collapses
 * back to icon-only once focus leaves it. `animateContentSize` gives the
 * icon↔icon+label transition a smooth feel appropriate for the 10-foot UI.
 *
 * Each row is a Compose-for-TV [Surface] with an `onClick` — a genuinely
 * focusable/clickable D-pad target that shows focus scaling and colour, rather
 * than the scoped `NavigationDrawerItem` (which requires a `NavigationDrawer`
 * host we don't use here).
 */
@Composable
fun TvNavDrawer(
    selected: TvNavDestination,
    isLoggedIn: Boolean,
    onSelect: (TvNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(if (expanded) 240.dp else 88.dp)
            .animateContentSize()
            .background(PureTvTvColors.Surface)
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

            Surface(
                onClick = { onSelect(dest) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = label,
                        tint = if (isSelected) PureTvTvColors.TwitchPurple else PureTvTvColors.TextSecondary,
                    )
                    if (expanded) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
