package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.puretv.twitch.desktop.data.ViewerHistoryAggregator
import com.puretv.twitch.desktop.ui.ChannelStatsViewModel
import com.puretv.twitch.desktop.ui.accountAgeLabel
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.LiveDot
import com.puretv.twitch.desktop.ui.components.Sparkline
import com.puretv.twitch.desktop.ui.formatCompact
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.uptimeLabel
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/**
 * The "Audience" data panel on the Channel screen — a Cinémathèque card showing
 * live viewers, follower count, a locally-tracked viewer sparkline with
 * peak/average/sessions, plus uptime, account age and broadcaster type. All
 * fields are real Twitch data or self-sampled history; nothing is estimated.
 */
@Composable
fun ChannelStatsPanel(koin: Koin, channelLogin: String, modifier: Modifier = Modifier) {
    val vm = rememberDesktopViewModel(channelLogin) {
        koin.get<ChannelStatsViewModel> { parametersOf(channelLogin) }
    }
    val state by vm.state.collectAsState()
    val c = PureTvTheme.colors
    val snap = state.snapshot
    val history = state.history
    val isLive = snap?.isLive == true

    Column(
        modifier = modifier
            .width(320.dp)
            .clip(PureTvShape.lg)
            .border(1.dp, c.hairline, PureTvShape.lg)
            .background(c.surface)
            .padding(22.dp),
    ) {
        Kicker("Audience", accent = true)
        Spacer(Modifier.height(18.dp))

        // Hero — live viewer count, or offline / loading
        when {
            state.isLoading && snap == null -> {
                Text("LOADING", style = PureTvType.dataSmall, color = c.textTertiary)
                Spacer(Modifier.height(6.dp))
                Text("…", style = MaterialTheme.typography.headlineMedium, color = c.textSecondary)
            }
            isLive && snap?.viewerCount != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(size = 7.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("WATCHING NOW", style = PureTvType.dataSmall, color = c.textTertiary)
                }
                Spacer(Modifier.height(6.dp))
                Text(formatCompact(snap.viewerCount!!.toLong()), style = MaterialTheme.typography.displayLarge, color = c.textPrimary)
                snap.gameName?.let {
                    Spacer(Modifier.height(10.dp))
                    CategoryChip(it)
                }
            }
            else -> {
                Text("OFFLINE", style = PureTvType.dataSmall, color = c.textTertiary)
                Spacer(Modifier.height(6.dp))
                Text("Not live right now", style = MaterialTheme.typography.headlineMedium, color = c.textSecondary)
            }
        }

        Spacer(Modifier.height(20.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.hairline))
        Spacer(Modifier.height(14.dp))

        // Stat rows — only real, available fields
        snap?.followerCount?.let { StatRow("Followers", formatCompact(it)) }
        if (history != null && history.samples.isNotEmpty()) {
            StatRow("Peak (tracked)", formatCompact(history.peakViewers.toLong()))
            StatRow("Average (tracked)", formatCompact(ViewerHistoryAggregator.averageViewers(history).toLong()))
            StatRow("Sessions tracked", history.sessionsTracked.toString())
        }
        if (isLive) uptimeLabel(snap?.startedAtIso)?.let { StatRow("Uptime", it) }
        snap?.createdAtIso?.takeIf { it.isNotBlank() }?.let { iso ->
            val age = accountAgeLabel(iso)
            if (age.isNotEmpty()) StatRow("On Twitch", age)
        }
        snap?.broadcasterType?.takeIf { it.isNotBlank() }?.let {
            StatRow("Type", it.replaceFirstChar(Char::uppercase))
        }

        // Sparkline of self-sampled viewers, or an honest hint
        Spacer(Modifier.height(20.dp))
        Kicker("Viewers while you've watched")
        Spacer(Modifier.height(12.dp))
        val points = history?.samples?.map { it.viewers } ?: emptyList()
        if (points.size >= 2) {
            Sparkline(points, modifier = Modifier.fillMaxWidth().height(56.dp))
        } else {
            Text(
                "We'll chart viewers here as you watch this channel.",
                style = PureTvType.dataSmall,
                color = c.textMuted,
            )
        }
    }
}

@Composable
private fun CategoryChip(game: String) {
    val c = PureTvTheme.colors
    Text(
        game,
        style = PureTvType.dataSmall,
        color = c.textSecondary,
        modifier = Modifier
            .clip(PureTvShape.xs)
            .background(c.surfaceVariant)
            .border(1.dp, c.hairline, PureTvShape.xs)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    val c = PureTvTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = PureTvType.dataSmall, color = c.textTertiary)
        Text(value, style = PureTvType.data, color = c.textPrimary)
    }
}
