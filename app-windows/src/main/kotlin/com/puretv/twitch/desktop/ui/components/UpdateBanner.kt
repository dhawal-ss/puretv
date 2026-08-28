package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.update.UpdateState
import com.puretv.twitch.desktop.update.resolveReleaseUrl
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType

/**
 * The Material 3 banner: a dismissible strip under the title bar carrying one
 * piece of news and its actions. It sits in the window gutter rather than inside
 * the content pane, because an update is about the app rather than about
 * whatever the user is currently looking at.
 *
 * Renders nothing when [state] is Idle.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onUpdate: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = PureTvTheme.colors
    when (state) {
        UpdateState.Idle -> Unit
        is UpdateState.Available -> BannerShell(container = c.primaryContainer) {
            BannerIcon(ExpressiveIcons.Download, c.onPrimaryContainer, c.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    "Update available",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.info.notes.isNotBlank()) "${state.info.version} · ${state.info.notes}" else state.info.version,
                    style = PureTvType.data,
                    color = c.onPrimaryContainer.copy(alpha = 0.86f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ExpressiveButton(
                text = "Update",
                onClick = onUpdate,
                style = ExpressiveButtonStyle.Filled,
                size = ExpressiveButtonSize.Small,
                icon = ExpressiveIcons.Download,
            )
            DismissButton(onDismiss, c.onPrimaryContainer)
        }
        is UpdateState.Downloading -> BannerShell(container = c.primaryContainer) {
            BannerIcon(ExpressiveIcons.Download, c.onPrimaryContainer, c.primaryContainer)
            Column(Modifier.weight(1f)) {
                Text(
                    "Downloading update, ${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = c.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = c.onPrimaryContainer,
                    trackColor = c.onPrimaryContainer.copy(alpha = 0.24f),
                )
            }
        }
        is UpdateState.Error -> BannerShell(container = c.errorContainer) {
            BannerIcon(ExpressiveIcons.Refresh, c.onErrorContainer, c.errorContainer)
            Text(
                "Update failed: ${state.message}",
                style = MaterialTheme.typography.titleMedium,
                color = c.onErrorContainer,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            ExpressiveButton(
                text = "Open download page",
                onClick = { onOpenReleasePage(state.releaseUrl ?: resolveReleaseUrl("")) },
                style = ExpressiveButtonStyle.Outlined,
                size = ExpressiveButtonSize.Small,
            )
            ExpressiveButton(
                text = "Retry",
                onClick = onUpdate,
                style = ExpressiveButtonStyle.Filled,
                size = ExpressiveButtonSize.Small,
                icon = ExpressiveIcons.Refresh,
            )
            DismissButton(onDismiss, c.onErrorContainer)
        }
    }
}

@Composable
private fun BannerShell(
    container: androidx.compose.ui.graphics.Color,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .clip(PureTvTheme.shapes.cardShape)
            .background(container)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/** The banner's leading token: an inverted circular chip, the M3 banner idiom. */
@Composable
private fun BannerIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    circle: androidx.compose.ui.graphics.Color,
    glyph: androidx.compose.ui.graphics.Color,
) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(circle),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit, tint: androidx.compose.ui.graphics.Color) {
    ExpressiveIconButton(
        icon = ExpressiveIcons.Close,
        contentDescription = "Dismiss",
        onClick = onDismiss,
        boxSize = 40.dp,
        iconSize = 20.dp,
        tint = tint,
    )
}
