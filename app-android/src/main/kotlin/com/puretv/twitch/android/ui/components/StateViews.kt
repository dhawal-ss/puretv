package com.puretv.twitch.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.theme.PureTvTheme

/**
 * SECTION 06.4: shared LOADING / ERROR / EMPTY states.
 *
 * Every list screen previously turned a network failure into either an infinite
 * spinner or a silent blank screen. These three composables give a single,
 * consistent way to represent the non-content states, so a transient blip is
 * always recoverable (Error carries a Retry) and an empty result never looks
 * like a bug. Error and Empty sit inside an [ExpressivePanel] with an
 * [ExpressiveButton] for the retry action, so a non-content state reads as a
 * deliberate card rather than bare text floating on the background.
 */

@Composable
fun FullScreenLoading(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = PureTvTheme.colors.primary)
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressivePanel(modifier = Modifier.fillMaxWidth(0.92f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (onRetry != null) {
                    ExpressiveButton("Try again", onRetry, style = ExpressiveButtonStyle.Tonal)
                }
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val c = PureTvTheme.colors
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExpressivePanel(modifier = Modifier.fillMaxWidth(0.92f)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inbox,
                    contentDescription = null,
                    tint = c.onSurfaceVariant,
                    modifier = Modifier.size(44.dp),
                )
                Text(title, style = MaterialTheme.typography.titleLarge, color = c.onSurface, textAlign = TextAlign.Center)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                }
            }
        }
    }
}
