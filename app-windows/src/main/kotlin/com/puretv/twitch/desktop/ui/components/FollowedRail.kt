package com.puretv.twitch.desktop.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.puretv.twitch.core.follows.FollowRow
import com.puretv.twitch.desktop.ui.FollowedRailState
import com.puretv.twitch.desktop.ui.theme.PureTvMotion
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/**
 * The followed "Live now" rail rendered inside NavigationRail, expanded state only.
 * [onOpenChannel] opens a channel page (same target as Following's card grid).
 * [onSignIn] switches the shell to the Account tab.
 *
 * The parent already supplies the section's top padding and paints the
 * surfaceContainer ground this sits on, so every row here only owns its own
 * horizontal padding and hover fill.
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
    Column(modifier = modifier.fillMaxWidth()) {
        if (!state.isLoggedIn) {
            Text("Live now".uppercase(), style = PureTvType.kicker, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 26.dp))
            Spacer(Modifier.height(10.dp))
            SignInPrompt(onSignIn)
            return@Column
        }

        Text("Live now".uppercase(), style = PureTvType.kicker, color = c.onSurfaceVariant, modifier = Modifier.padding(horizontal = 26.dp))
        Spacer(Modifier.height(10.dp))

        // Explicit loading bar while the first load (cold start OR just-signed-in) is in flight.
        // isLoading is only true when there's no data yet, so this never flashes on the 60s poll
        // refresh once the list is populated.
        if (state.isLoading) {
            LinearProgressIndicator(
                color = c.primary,
                trackColor = c.outlineVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp).height(2.dp),
            )
            Spacer(Modifier.height(8.dp))
        }

        // LazyColumn (not Column+verticalScroll): only on-screen rows compose, so a power
        // user with hundreds of live/offline follows no longer measures+composes them all at
        // once when the rail or the Offline section expands. Bounded height comes from the
        // parent's Modifier.weight(1f), so this is a safe virtualization swap.
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp)) {
            val noData = state.live.isEmpty() && state.offline.isEmpty()
            when {
                // Distinguish first-load-in-flight and load-failure from a genuinely
                // empty follow list, so neither is mistaken for "nobody's live".
                state.isLoading && noData -> item { LoadingSkeleton() }
                state.errored && noData -> item {
                    Text("Couldn't load follows", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                state.live.isEmpty() -> item {
                    Text("No followed channels live", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                }
                // Keyed by login (live + offline are disjoint, so keys stay unique across both).
                else -> items(state.live, key = { it.login }) { FollowRowItem(it, onClick = { onOpenChannel(it.login) }) }
            }

            if (state.offline.isNotEmpty()) {
                item { OfflineToggleRow(expanded = state.offlineExpanded, count = state.offline.size, onClick = onToggleOffline) }
                if (state.offlineExpanded) {
                    items(state.offline, key = { it.login }) { FollowRowItem(it, onClick = { onOpenChannel(it.login) }) }
                }
            }
        }
    }
}

/** Sign-in nudge shown in place of the list when nobody is logged in. */
@Composable
private fun SignInPrompt(onSignIn: () -> Unit) {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink by animateColorAsState(if (hovered) c.onPrimaryContainer else c.primary, tween(PureTvMotion.Fast), label = "signInInk")
    Text(
        "Sign in to see who's live",
        style = MaterialTheme.typography.bodyMedium,
        color = ink,
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .handCursor()
            .clickable(interactionSource = interaction, indication = null, onClick = onSignIn)
            .padding(horizontal = 26.dp, vertical = 6.dp),
    )
}

/** One live/offline row: a pill that squares off to [PureTvTheme.shapes.pillMorph] on hover. */
@Composable
private fun FollowRowItem(row: FollowRow, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = shapes.pill,
                hoverRadius = shapes.pillMorph,
                hoverColor = c.surfaceHigh,
            )
            .padding(horizontal = 14.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val avatar = row.avatarUrl
        if (avatar.isNullOrBlank()) {
            Box(
                modifier = Modifier.size(28.dp).clip(CircleShape).background(c.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.displayName.take(1).uppercase().ifEmpty { "?" },
                    style = PureTvType.dataSmall,
                    color = c.surfaceLowest,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            AsyncImage(
                model = avatar,
                contentDescription = row.displayName,
                modifier = Modifier.size(28.dp).clip(CircleShape),
            )
        }

        Text(
            row.displayName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = c.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        if (row.isLive) {
            Text(formatViewerCount(row.viewerCount), style = PureTvType.dataSmall, color = c.onSurfaceVariant)
        }
    }
}

/** The "Offline (N)" expand/collapse row, styled as the same pill as a follow row. */
@Composable
private fun OfflineToggleRow(expanded: Boolean, count: Int, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    val shapes = PureTvTheme.shapes
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(
                interaction = interaction,
                onClick = onClick,
                restRadius = shapes.pill,
                hoverRadius = shapes.pillMorph,
                hoverColor = c.surfaceHigh,
            )
            .padding(horizontal = 14.dp)
            .height(48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (expanded) ExpressiveIcons.ExpandLess else ExpressiveIcons.ExpandMore,
            contentDescription = null,
            tint = c.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text("Offline ($count)", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
    }
}

/**
 * Placeholder rows shown while the first load is in flight, so the rail visibly reads
 * as "working" rather than empty. A gentle alpha pulse signals activity.
 */
@Composable
private fun LoadingSkeleton(rows: Int = 5) {
    val c = PureTvTheme.colors
    val transition = rememberInfiniteTransition(label = "followed-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column {
        repeat(rows) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(28.dp).clip(CircleShape).background(c.outlineVariant.copy(alpha = alpha)))
                Box(
                    Modifier.weight(1f).height(10.dp).clip(PureTvTheme.shapes.smShape)
                        .background(c.outlineVariant.copy(alpha = alpha)),
                )
            }
        }
    }
}
