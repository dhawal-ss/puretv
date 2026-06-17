package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.desktop.ui.FollowedRailState
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/**
 * The followed "Live now" rail rendered inside NavigationSidebar.
 * [onOpenChannel] opens a channel page (same target as Home's FollowCard).
 * [onSignIn] switches the shell to the Account tab.
 */
@Composable
fun FollowedRail(
    state: FollowedRailState,
    onToggleOffline: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Column(modifier = modifier.fillMaxWidth().fillMaxHeight()) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(1.dp).background(c.hairline))
        Spacer(Modifier.height(12.dp))

        if (!state.isLoggedIn) {
            Kicker("Following", modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                "Sign in to see who's live",
                color = c.twitchPurpleLight,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSignIn() }
                    .handCursor()
                    .padding(horizontal = 18.dp, vertical = 6.dp),
            )
            return@Column
        }

        Kicker("Live now · ${state.live.size}", modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
        Spacer(Modifier.height(6.dp))

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            if (state.live.isEmpty()) {
                Text(
                    "No followed channels live",
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                )
            } else {
                // forEach (not LazyColumn) is fine for realistic follow counts; revisit with a LazyColumn if offline lists grow very large (Twitch allows up to ~2000 follows).
                state.live.forEach { FollowRowItem(it, onClick = { onOpenChannel(it.login) }) }
            }

            if (state.offline.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleOffline() }
                        .handCursor()
                        .padding(horizontal = 18.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (state.offlineExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        tint = c.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("Offline (${state.offline.size})", color = c.textMuted)
                }
                if (state.offlineExpanded) {
                    state.offline.forEach { FollowRowItem(it, onClick = { onOpenChannel(it.login) }) }
                }
            }
        }
    }
}

@Composable
private fun FollowRowItem(row: FollowRow, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .background(if (hovered) c.surfaceHover else Color.Transparent)
            .clickable(onClick = onClick)
            .handCursor()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        val avatar = row.avatarUrl
        if (avatar.isNullOrBlank()) {
            Box(
                modifier = Modifier.size(18.dp).clip(CircleShape).background(c.twitchPurple),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.displayName.take(1).uppercase().ifEmpty { "?" },
                    color = c.background,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            AsyncImage(
                model = avatar,
                contentDescription = row.displayName,
                modifier = Modifier.size(18.dp).clip(CircleShape),
            )
        }

        Text(
            row.displayName,
            color = if (row.isLive) c.textPrimary else c.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (row.isLive) {
            LiveDot(size = 6.dp)
            Spacer(Modifier.width(5.dp))
            Text(formatViewerCount(row.viewerCount), color = c.textSecondary, style = PureTvType.dataSmall)
        }
    }
}
