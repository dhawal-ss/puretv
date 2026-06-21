package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.FollowingViewModel
import com.puretv.twitch.android.ui.components.EmptyState
import com.puretv.twitch.android.ui.components.StreamCard
import com.puretv.twitch.android.ui.components.StreamCardSkeleton
import com.puretv.twitch.android.ui.theme.PureTvColors
import org.koin.androidx.compose.koinViewModel

/** SECTION 06.4: the Following tab. Live followed channels, or a connect prompt. */
@Composable
fun FollowingScreen(
    onOpenStream: (String) -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: FollowingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = { TopAppBar(title = { Text("Following", color = PureTvColors.TextPrimary) }) },
        containerColor = PureTvColors.Background,
    ) { padding ->
        when {
            !state.isLoggedIn -> ConnectPrompt(onConnect = onOpenLogin, modifier = Modifier.padding(padding))
            state.isLoading && state.liveFollows.isEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { items(6) { StreamCardSkeleton() } }
            state.liveFollows.isEmpty() -> EmptyState(
                title = "No one is live",
                subtitle = "None of the channels you follow are streaming right now.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.liveFollows, key = { it.userLogin }) { s ->
                    StreamCard(stream = s, onClick = { onOpenStream(s.userLogin) })
                }
            }
        }
    }
}

@Composable
private fun ConnectPrompt(onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Follow your favorites", style = MaterialTheme.typography.headlineSmall, color = PureTvColors.TextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Connect your Twitch account to see the channels you follow, live and ad-free.",
            style = MaterialTheme.typography.bodyMedium,
            color = PureTvColors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onConnect,
            colors = ButtonDefaults.buttonColors(containerColor = PureTvColors.TwitchPurple),
        ) { Text("Connect with Twitch") }
    }
}
