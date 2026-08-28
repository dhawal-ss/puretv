package com.puretv.twitch.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.puretv.twitch.android.ui.FollowingViewModel
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.ExpressivePanel
import com.puretv.twitch.android.ui.components.PageTitle
import com.puretv.twitch.android.ui.components.SectionHeading
import com.puretv.twitch.android.ui.components.StreamCard
import com.puretv.twitch.android.ui.components.StreamCardSkeleton
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import org.koin.androidx.compose.koinViewModel

/**
 * Following, mirroring the desktop screen's shape: a kicker, [PageTitle], a
 * short intro, then whichever state applies below it, all in ONE [LazyColumn]
 * so the header never has to be duplicated per branch the way a top-level
 * `when` would force.
 *
 * [FollowingUiState] carries only live follows: there is no offline list to
 * show (unlike desktop's [FollowRow]-backed offline section), so this screen
 * never renders one rather than inventing data that isn't there. There is
 * also no error copy to show: [FollowingUiState.error] is declared but never
 * assigned by [FollowingViewModel] (its `loadFollowsForCurrentUser` calls are
 * wrapped in a bare `runCatching` that swallows the failure), so a failed
 * load looks identical to "no one is live" today, same as before this pass.
 */
@Composable
fun FollowingScreen(
    onOpenStream: (String) -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: FollowingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Scaffold(containerColor = c.surfaceLowest) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { FollowingHeader() }

            when {
                !state.isLoggedIn -> item { SignedOutPanel(onSignIn = onOpenLogin) }

                state.isLoading && state.liveFollows.isEmpty() -> item { LoadingList() }

                state.liveFollows.isEmpty() -> item {
                    EmptyPanel(
                        title = "No one is live",
                        message = "None of the channels you follow are streaming right now.",
                    )
                }

                else -> {
                    item { SectionHeading(title = "Live · ${state.liveFollows.size}") }
                    items(state.liveFollows, key = { it.userLogin }) { s ->
                        StreamCard(stream = s, onClick = { onOpenStream(s.userLogin) }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowingHeader() {
    val c = PureTvTheme.colors
    Column {
        Text("Your follows".uppercase(), style = PureTvType.kicker, color = c.primary)
        Spacer(Modifier.height(8.dp))
        PageTitle("Following")
        Spacer(Modifier.height(8.dp))
        Text(
            "Every channel you follow lives here, live ones first so you always know who to check on next.",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onSurfaceVariant,
        )
    }
}

@Composable
private fun SignedOutPanel(onSignIn: () -> Unit) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text("Sign in to see who's live", style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(
                "Connect your Twitch account to pull in your follows and their live status.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            ExpressiveButton(
                text = "Connect with Twitch",
                onClick = onSignIn,
                style = ExpressiveButtonStyle.Filled,
                size = ExpressiveButtonSize.Medium,
                icon = ExpressiveIcons.SignIn,
            )
        }
    }
}

@Composable
private fun EmptyPanel(title: String, message: String) {
    val c = PureTvTheme.colors
    ExpressivePanel {
        Column {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.onSurface)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
        }
    }
}

/** Card-shaped shimmer placeholders, content-sized so they are safe inside a lazy item. */
@Composable
private fun LoadingList() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(4) { StreamCardSkeleton(modifier = Modifier.fillMaxWidth()) }
    }
}
