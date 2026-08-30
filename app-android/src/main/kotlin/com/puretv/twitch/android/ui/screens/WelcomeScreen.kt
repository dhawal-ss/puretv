package com.puretv.twitch.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.puretv.twitch.core.api.TwitchConfig
import com.puretv.twitch.android.ui.LoginViewModel
import com.puretv.twitch.android.ui.WelcomeViewModel
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ShieldPill
import com.puretv.twitch.android.ui.components.expressiveClickable
import com.puretv.twitch.android.ui.components.streamThumbUrl
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 06.3: the Welcome gate (option C "hybrid peek"). A blurred grid of last
 * session's cached streams (or a branded gradient on first launch) sits behind a
 * glass connect card running the device-code flow. On sign-in, SessionState flips
 * and RootScreen crossfades this away into the populated tab shell.
 */
@Composable
fun WelcomeScreen(
    welcomeViewModel: WelcomeViewModel = koinViewModel(),
    loginViewModel: LoginViewModel = koinViewModel(),
) {
    val thumbnails by welcomeViewModel.thumbnails.collectAsState()
    val login by loginViewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val c = PureTvTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(c.surfaceLowest)) {
        if (thumbnails.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().blur(26.dp),
                userScrollEnabled = false,
                contentPadding = PaddingValues(6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(thumbnails) { url ->
                    AsyncImage(
                        model = streamThumbUrl(url),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(PureTvTheme.shapes.thumbShape),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(c.primary.copy(alpha = 0.28f), c.surfaceLowest)),
                ),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))

        // Same reason as LoginScreen: the card grows by a code plate, a button row
        // and a progress line once sign-in starts, and a landscape or split-screen
        // window is shorter than the result. Scroll keeps the code reachable.
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .padding(28.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(c.surfaceContainer)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppMark()

            Text("Watch Twitch, ad-free.", style = MaterialTheme.typography.displaySmall, color = c.onSurface, textAlign = TextAlign.Center)
            Text(
                "PureTV strips ads on-device before a stream ever reaches the player. " +
                    "No relay server, no logging of what you watch.",
                style = MaterialTheme.typography.bodyLarge,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            val code = login.userCode
            if (code == null) {
                Spacer(Modifier.height(2.dp))
                ExpressiveButton(
                    text = if (login.isAuthenticating) "Starting…" else "Connect with Twitch",
                    onClick = loginViewModel::beginLogin,
                    style = ExpressiveButtonStyle.Filled,
                    size = ExpressiveButtonSize.XLarge,
                    enabled = !login.isAuthenticating,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(PureTvTheme.shapes.mdShape)
                        .background(c.surfaceHigh)
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                ) {
                    Text("ENTER THIS CODE AT TWITCH.TV/ACTIVATE", style = PureTvType.kicker, color = c.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        code,
                        style = MaterialTheme.typography.displaySmall.copy(fontFamily = PureTvType.mono, letterSpacing = 5.sp),
                        color = c.onSurface,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CopyCodeButton(onCopyCode = { clipboard.setText(AnnotatedString(code)) })
                    ExpressiveButton(
                        text = "Open Twitch",
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TwitchConfig.ACTIVATE_URL)))
                        },
                        style = ExpressiveButtonStyle.Outlined,
                        size = ExpressiveButtonSize.Small,
                    )
                }
                if (login.isAuthenticating) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.width(16.dp).height(16.dp))
                        Text("Waiting for you to authorize…", style = PureTvType.dataSmall, color = c.onSurfaceVariant)
                    }
                }
            }

            login.error?.let { error ->
                Box(
                    modifier = Modifier
                        .clip(PureTvTheme.shapes.smShape)
                        .background(c.errorContainer)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer, textAlign = TextAlign.Center)
                }
            }

            ShieldPill(text = "Ad blocking runs on-device, always")
        }
    }
}

/** The 72dp app mark: a rounded square at rest that rounds all the way into a
 *  circle under the finger. Purely decorative, so the click is a no-op and only
 *  the interaction source's press state drives the morph. */
@Composable
private fun AppMark() {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(72.dp)
            .height(72.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = {},
                restRadius = 24.dp,
                pressRadius = 36.dp,
                color = c.primary,
            ),
    ) {
        Text(
            "P",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = PureTvType.display, fontSize = 32.sp),
            color = c.onPrimary,
        )
    }
}
