package com.puretv.twitch.desktop.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.update.UpdateState
import com.puretv.twitch.desktop.update.resolveReleaseUrl
import com.puretv.twitch.desktop.ui.theme.PureTvTheme

/**
 * Slim, dismissible update bar shown under the title bar when an update is
 * available / downloading / failed. Renders nothing when [state] is Idle.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onUpdate: () -> Unit,
    onOpenReleasePage: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        UpdateState.Idle -> Unit
        is UpdateState.Available -> BannerShell {
            Column(Modifier.weight(1f)) {
                Text(
                    "Update available — ${state.info.version}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.info.notes.isNotBlank()) {
                    Text(
                        state.info.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureTvTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            UpdateButton("Update", onUpdate)
            Spacer(Modifier.width(4.dp))
            DismissButton(onDismiss)
        }
        is UpdateState.Downloading -> BannerShell {
            Column(Modifier.weight(1f)) {
                Text(
                    "Downloading update… ${(state.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PureTvTheme.colors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = PureTvTheme.colors.twitchPurpleLight,
                    trackColor = PureTvTheme.colors.surfaceHover,
                )
            }
        }
        is UpdateState.Error -> BannerShell {
            Text(
                "Update failed: ${state.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = PureTvTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            UpdateButton("Open download page") { onOpenReleasePage(state.releaseUrl ?: resolveReleaseUrl("")) }
            Spacer(Modifier.width(4.dp))
            UpdateButton("Retry", onUpdate)
            Spacer(Modifier.width(4.dp))
            DismissButton(onDismiss)
        }
    }
}

@Composable
private fun BannerShell(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
    Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
}

@Composable
private fun UpdateButton(label: String, onClick: () -> Unit) {
    val c = PureTvTheme.colors
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = c.twitchPurple, contentColor = c.textPrimary),
        shape = RoundedCornerShape(8.dp),
    ) {
        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DismissButton(onDismiss: () -> Unit) {
    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
        Icon(
            Icons.Filled.Close,
            contentDescription = "Dismiss",
            tint = PureTvTheme.colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
    }
}
