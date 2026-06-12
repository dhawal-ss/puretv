package com.puretv.twitch.tv.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.tv.material3.NavigationDrawerItem
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
 * Per Section 7.3's D-pad rules: pressing LEFT while focus is on the
 * leftmost column of the content area should move focus into this rail
 * (handled by the screen via `focusRequester` — see [TvHomeScreen]) and
 * expand it; the rail collapses back to icon-only once focus leaves it.
 * `animateContentSize` gives the icon↔icon+label transition a smooth feel
 * appropriate for the 10-foot UI (no abrupt layout jumps under D-pad nav).
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
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val destinations = TvNavDestination.entries.filter { it != TvNavDestination.ACCOUNT || !isLoggedIn }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(destinations) { dest ->
                NavigationDrawerItem(
                    selected = dest == selected,
                    onClick = { onSelect(dest) },
                    leadingContent = {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            tint = if (dest == selected) PureTvTvColors.TwitchPurple else PureTvTvColors.TextSecondary,
                        )
                    },
                    modifier = Modifier
                        .onFocusChanged { state ->
                            if (state.isFocused || state.hasFocus) expanded = true
                        },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (dest == TvNavDestination.ACCOUNT && !isLoggedIn) "Sign in" else dest.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.wrapContentWidth(),
                        )
                    }
                }
            }
        }
    }
}
