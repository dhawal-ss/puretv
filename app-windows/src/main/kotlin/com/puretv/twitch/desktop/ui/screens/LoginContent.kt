package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretv.twitch.desktop.ui.LoginViewModel
import com.puretv.twitch.desktop.ui.components.BoxScrim
import com.puretv.twitch.desktop.ui.components.ButtonSize
import com.puretv.twitch.desktop.ui.components.ButtonVariant
import com.puretv.twitch.desktop.ui.components.CoverImage
import com.puretv.twitch.desktop.ui.components.Kicker
import com.puretv.twitch.desktop.ui.components.PureButton
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvShape
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

@Composable
fun LoginContent(koin: Koin) {
    val viewModel = rememberDesktopViewModel { koin.get<LoginViewModel>() }
    val state by viewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Box(modifier = Modifier.fillMaxSize().background(c.background)) {
        // ── Atmospheric backdrop: duotone wash + double scrim so the card reads ──
        CoverImage(
            imageUrl = null,
            seed = "puretv-cinematheque",
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        )
        BoxScrim(Modifier.fillMaxSize())
        // Top + bottom fade toward the near-black canvas (a single tasteful gradient).
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to c.background.copy(alpha = 0.92f),
                    0.34f to c.background.copy(alpha = 0.55f),
                    0.62f to c.background.copy(alpha = 0.62f),
                    1f to c.background.copy(alpha = 0.96f),
                ),
            ),
        )

        // ── Centered editorial card column ──
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 440.dp)
                .padding(horizontal = 32.dp, vertical = 48.dp),
        ) {
            Kicker("PureTV for Twitch", accent = true)
            Spacer(Modifier.height(20.dp))
            Text(
                "Watch Twitch, ad-free & private.",
                style = MaterialTheme.typography.displayLarge,
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No servers, no tracking — everything runs locally on your machine. " +
                    "Sign in once with Twitch's official login; your password never touches this app.",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(36.dp))

            when {
                // ── Logged in: confident confirmation ──
                state.isLoggedIn -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = c.adBlockGreen,
                        modifier = Modifier.width(22.dp),
                    )
                    Text(
                        "You're signed in.",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.textPrimary,
                    )
                }

                // ── Authenticating: device user code in a codebox ──
                state.isAuthenticating -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.userCode?.let { code ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(PureTvShape.lg)
                                .background(c.surface)
                                .border(1.dp, c.hairline, PureTvShape.lg)
                                .padding(horizontal = 28.dp, vertical = 22.dp),
                        ) {
                            Text(
                                "ENTER THIS CODE AT TWITCH.TV/ACTIVATE",
                                style = PureTvType.kicker,
                                color = c.textTertiary,
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                code,
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = PureTvType.mono,
                                    letterSpacing = 8.sp,
                                ),
                                color = c.textPrimary,
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        PureButton(
                            text = "Copy code",
                            onClick = {
                                java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                    .setContents(java.awt.datatransfer.StringSelection(code), null)
                            },
                            variant = ButtonVariant.Secondary,
                            leadingIcon = Icons.Filled.ContentCopy,
                        )

                        Spacer(Modifier.height(18.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            color = c.twitchPurple,
                            strokeWidth = 2.dp,
                            modifier = Modifier.width(16.dp),
                        )
                        Text(
                            "We opened your browser — click “Authorize” there to finish.",
                            style = PureTvType.dataSmall,
                            color = c.textMuted,
                        )
                    }

                    if (state.userCode != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Didn't open? Go to twitch.tv/activate and enter this code.",
                            style = PureTvType.dataSmall,
                            color = c.textMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // ── Logged out: primary CTA ──
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PureButton(
                        text = "Sign in with Twitch",
                        onClick = viewModel::beginLogin,
                        variant = ButtonVariant.Primary,
                        size = ButtonSize.Lg,
                        leadingIcon = Icons.AutoMirrored.Filled.Login,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Opens your browser · takes you to Twitch's official login",
                        style = PureTvType.dataSmall,
                        color = c.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            state.error?.let { error ->
                Spacer(Modifier.height(24.dp))
                Box(
                    modifier = Modifier
                        .clip(PureTvShape.sm)
                        .background(c.live.copy(alpha = 0.10f))
                        .border(1.dp, c.live.copy(alpha = 0.35f), PureTvShape.sm)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.live,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
