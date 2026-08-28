package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.dp
import com.puretv.twitch.core.model.ChannelStatsSnapshot
import com.puretv.twitch.desktop.data.ChannelHistory
import com.puretv.twitch.desktop.data.ViewerHistoryAggregator
import com.puretv.twitch.desktop.ui.ChannelStatsViewModel
import com.puretv.twitch.desktop.ui.accountAgeLabel
import com.puretv.twitch.desktop.ui.components.ExpressiveDivider
import com.puretv.twitch.desktop.ui.components.ExpressivePanel
import com.puretv.twitch.desktop.ui.components.LiveDot
import com.puretv.twitch.desktop.ui.formatCompact
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import com.puretv.twitch.desktop.ui.uptimeLabel
import org.koin.core.Koin
import org.koin.core.parameter.parametersOf

/**
 * The "Audience" data column on the Channel screen: a `primaryContainer` hero
 * tile (live viewer count, or the offline / loading state) with the self-sampled
 * viewer sparkline drawn straight into it, followed by a `surfaceLow` tile that
 * carries every other real field: followers, tracked peak/average/sessions,
 * uptime, account age and broadcaster type. All of it is real Twitch data or
 * locally-tracked history; nothing here is estimated.
 */
@Composable
fun ChannelStatsPanel(koin: Koin, channelLogin: String, modifier: Modifier = Modifier) {
    val vm = rememberDesktopViewModel(channelLogin) {
        koin.get<ChannelStatsViewModel> { parametersOf(channelLogin) }
    }
    val state by vm.state.collectAsState()
    val snap = state.snapshot
    val history = state.history
    val isLive = snap?.isLive == true

    Column(modifier = modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LiveViewersTile(isLoading = state.isLoading, snap = snap, isLive = isLive, history = history)
        SecondaryStatsTile(snap = snap, history = history, isLive = isLive)
    }
}

/** The primaryContainer hero: live viewer count (or offline/loading) plus the sparkline. */
@Composable
private fun LiveViewersTile(isLoading: Boolean, snap: ChannelStatsSnapshot?, isLive: Boolean, history: ChannelHistory?) {
    val c = PureTvTheme.colors
    ExpressivePanel(modifier = Modifier.fillMaxWidth(), color = c.primaryContainer, padding = 24.dp) {
        Column {
            when {
                isLoading && snap == null -> {
                    Text("LOADING", style = PureTvType.kicker, color = c.onPrimaryContainer.copy(alpha = 0.86f))
                    Text("…", style = MaterialTheme.typography.displayMedium, color = c.onPrimaryContainer)
                }
                isLive && snap?.viewerCount != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(size = 7.dp, color = c.onPrimaryContainer)
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE VIEWERS", style = PureTvType.kicker, color = c.onPrimaryContainer.copy(alpha = 0.86f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(formatCompact(snap.viewerCount!!.toLong()), style = MaterialTheme.typography.displayMedium, color = c.onPrimaryContainer)
                    snap.gameName?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(10.dp))
                        HeroChip(it)
                    }
                }
                else -> {
                    Text("OFFLINE", style = PureTvType.kicker, color = c.onPrimaryContainer.copy(alpha = 0.86f))
                    Spacer(Modifier.height(8.dp))
                    Text("Not live right now", style = MaterialTheme.typography.headlineMedium, color = c.onPrimaryContainer)
                }
            }

            Spacer(Modifier.height(16.dp))
            val points = history?.samples?.map { it.viewers } ?: emptyList()
            if (points.size >= 2) {
                BarSparkline(points, tint = c.onPrimaryContainer, modifier = Modifier.fillMaxWidth().height(44.dp))
            } else {
                Text(
                    "We'll chart viewers here as you watch this channel.",
                    style = PureTvType.dataSmall,
                    color = c.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}

/**
 * The viewer-count sparkline: rounded bars rather than the line-and-area chart the
 * old design used. Bars survive being tinted [tint] against any container fill,
 * which a filled area chart does not, and this is the only sparkline in the app.
 */
@Composable
private fun BarSparkline(points: List<Int>, tint: Color, modifier: Modifier = Modifier) {
    val bars = tint.copy(alpha = 0.55f)
    Canvas(modifier) {
        if (points.size < 2) return@Canvas
        val maxV = (points.maxOrNull() ?: 1).coerceAtLeast(1)
        val minV = points.minOrNull() ?: 0
        val range = (maxV - minV).coerceAtLeast(1).toFloat()
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (points.size - 1)) / points.size).coerceAtLeast(1f)
        val minBarHeight = size.height * 0.08f
        val footRadius = CornerRadius(2.dp.toPx())
        points.forEachIndexed { i, v ->
            val fraction = (v - minV) / range
            val barHeight = (minBarHeight + fraction * (size.height - minBarHeight)).coerceIn(minBarHeight, size.height)
            val x = i * (barWidth + gap)
            val headRadius = CornerRadius(barWidth / 2)
            val bar = Path().apply {
                addRoundRect(
                    RoundRect(
                        rect = Rect(Offset(x, size.height - barHeight), Size(barWidth, barHeight)),
                        topLeft = headRadius,
                        topRight = headRadius,
                        bottomLeft = footRadius,
                        bottomRight = footRadius,
                    ),
                )
            }
            drawPath(bar, color = bars, style = Fill)
        }
    }
}

@Composable
private fun HeroChip(game: String) {
    val c = PureTvTheme.colors
    Text(
        game,
        style = PureTvType.dataSmall,
        color = c.onPrimaryContainer,
        modifier = Modifier
            .clip(PureTvTheme.shapes.pillShape)
            .background(c.onPrimaryContainer.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

/** The surfaceLow tile: followers as the headline number, then every smaller stat, kicker-over-value. */
@Composable
private fun SecondaryStatsTile(snap: ChannelStatsSnapshot?, history: ChannelHistory?, isLive: Boolean) {
    val c = PureTvTheme.colors

    val minorStats = buildList {
        if (history != null && history.samples.isNotEmpty()) {
            add("Peak tracked" to formatCompact(history.peakViewers.toLong()))
            add("Average tracked" to formatCompact(ViewerHistoryAggregator.averageViewers(history).toLong()))
            add("Sessions tracked" to history.sessionsTracked.toString())
        }
        if (isLive) uptimeLabel(snap?.startedAtIso)?.let { add("Uptime" to it) }
        snap?.createdAtIso?.takeIf { it.isNotBlank() }?.let { iso ->
            val age = accountAgeLabel(iso)
            if (age.isNotEmpty()) add("On Twitch" to age)
        }
        snap?.broadcasterType?.takeIf { it.isNotBlank() }?.let {
            add("Type" to it.replaceFirstChar(Char::uppercase))
        }
    }

    val followers = snap?.followerCount
    if (followers == null && minorStats.isEmpty()) return

    ExpressivePanel(modifier = Modifier.fillMaxWidth(), color = c.surfaceLow, padding = 24.dp) {
        Column {
            if (followers != null) {
                Text("FOLLOWERS", style = PureTvType.kicker, color = c.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text(formatCompact(followers), style = MaterialTheme.typography.displayMedium, color = c.onSurface)
                if (minorStats.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(18.dp))
                }
            }
            minorStats.forEachIndexed { index, (label, value) ->
                MinorStat(label, value)
                if (index != minorStats.lastIndex) {
                    Spacer(Modifier.height(14.dp))
                    ExpressiveDivider()
                    Spacer(Modifier.height(14.dp))
                }
            }
        }
    }
}

@Composable
private fun MinorStat(label: String, value: String) {
    val c = PureTvTheme.colors
    Text(label.uppercase(), style = PureTvType.kicker, color = c.onSurfaceVariant)
    Spacer(Modifier.height(6.dp))
    Text(value, style = PureTvType.data, color = c.onSurface)
}
