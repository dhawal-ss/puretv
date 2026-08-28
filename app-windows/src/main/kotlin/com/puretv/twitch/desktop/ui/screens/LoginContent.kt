package com.puretv.twitch.desktop.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretv.twitch.desktop.ui.LoginViewModel
import com.puretv.twitch.desktop.ui.SettingsViewModel
import com.puretv.twitch.desktop.ui.components.DuotoneFill
import com.puretv.twitch.desktop.ui.components.ExpressiveButton
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonSize
import com.puretv.twitch.desktop.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.desktop.ui.components.ExpressiveIcons
import com.puretv.twitch.desktop.ui.components.ShieldPill
import com.puretv.twitch.desktop.ui.components.expressiveSurface
import com.puretv.twitch.desktop.ui.rememberDesktopViewModel
import com.puretv.twitch.desktop.ui.theme.PureTvTheme
import com.puretv.twitch.desktop.ui.theme.PureTvType
import org.koin.core.Koin

/**
 * The Account tab: PureTV's whole pitch (local-only, ad-free, private) plus
 * whichever step of the device-code sign-in the user is currently on. One card,
 * one narrative: the mark, the promise, then a single action area that swaps
 * between "sign in", "waiting on the browser" and "signed in" without moving
 * anything else on the screen.
 *
 * [SettingsViewModel] is pulled in only for [SettingsViewModel.state]'s
 * `loginUsername` and its already-real `logOut()`. The sign-in flow itself
 * still runs entirely through [LoginViewModel], unchanged.
 */
@Composable
fun LoginContent(koin: Koin) {
    val viewModel = rememberDesktopViewModel { koin.get<LoginViewModel>() }
    val state by viewModel.state.collectAsState()
    val settingsViewModel = rememberDesktopViewModel { koin.get<SettingsViewModel>() }
    val settingsState by settingsViewModel.state.collectAsState()
    val c = PureTvTheme.colors

    Box(Modifier.fillMaxSize()) {
        // Full-bleed art behind the card: a deterministic wash (same fallback
        // every missing-thumbnail surface uses) veiled toward `surface` so the
        // card sitting on top of it always reads clearly regardless of hue.
        DuotoneFill(seed = "puretv-account", modifier = Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to c.surface.copy(alpha = 0.90f),
                    0.4f to c.surface.copy(alpha = 0.55f),
                    1f to c.surface.copy(alpha = 0.95f),
                ),
            ),
        )

        Box(Modifier.fillMaxSize().padding(40.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    // A one-off radius, not one of the shape-intensity steps: the
                    // single largest, calmest surface on the app deliberately
                    // doesn't flex with the user's shape-intensity dial.
                    .clip(RoundedCornerShape(48.dp))
                    .background(c.surfaceContainer)
                    .padding(48.dp),
            ) {
                AppMark()

                Spacer(Modifier.height(28.dp))
                Text(
                    "Sign in once, then just watch.",
                    style = MaterialTheme.typography.displayMedium,
                    color = c.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "PureTV runs entirely on this machine. There is no relay server " +
                        "and nothing about what you watch is logged. Sign-in happens on " +
                        "Twitch's own page, so your password never reaches this app.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(32.dp))

                when {
                    state.isLoggedIn -> SignedInArea(
                        username = settingsState.loginUsername,
                        onSignOut = settingsViewModel::logOut,
                    )
                    state.isAuthenticating -> AuthenticatingArea(
                        userCode = state.userCode,
                        onCopyCode = { code ->
                            java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                .setContents(java.awt.datatransfer.StringSelection(code), null)
                        },
                    )
                    else -> SignedOutArea(onSignIn = viewModel::beginLogin)
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .clip(PureTvTheme.shapes.smShape)
                            .background(c.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            error,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onErrorContainer,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
                ShieldPill(text = "Ad blocking runs on-device, always")
            }
        }
    }
}

/** The 88dp app mark: a soft square at rest that rounds all the way into a circle on hover. */
@Composable
private fun AppMark() {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(88.dp)
            .hoverable(interaction)
            .expressiveSurface(
                interaction = interaction,
                restRadius = 32.dp,
                hoverRadius = 44.dp,
                color = c.primary,
            ),
    ) {
        Text(
            "P",
            style = MaterialTheme.typography.displaySmall.copy(
                fontFamily = PureTvType.display,
                fontSize = 48.sp,
            ),
            color = c.onPrimary,
        )
    }
}

/** Logged-out: the one action this whole card is building up to. */
@Composable
private fun SignedOutArea(onSignIn: () -> Unit) {
    val c = PureTvTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ExpressiveButton(
            text = "Sign in with Twitch",
            onClick = onSignIn,
            style = ExpressiveButtonStyle.Filled,
            size = ExpressiveButtonSize.XLarge,
            icon = ExpressiveIcons.SignIn,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Opens your browser and takes you to Twitch's official sign-in page.",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
        )
    }
}

/** Waiting on the device-code grant: the user code to enter, then the poll status. */
@Composable
private fun AuthenticatingArea(userCode: String?, onCopyCode: (String) -> Unit) {
    val c = PureTvTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        userCode?.let { code ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(PureTvTheme.shapes.mdShape)
                    .background(c.surfaceHigh)
                    .padding(horizontal = 28.dp, vertical = 22.dp),
            ) {
                Text(
                    "ENTER THIS CODE AT TWITCH.TV/ACTIVATE",
                    style = PureTvType.kicker,
                    color = c.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    code,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = PureTvType.mono,
                        letterSpacing = 8.sp,
                    ),
                    color = c.onSurface,
                )
            }

            Spacer(Modifier.height(18.dp))

            ExpressiveButton(
                text = "Copy code",
                onClick = { onCopyCode(code) },
                style = ExpressiveButtonStyle.Outlined,
                size = ExpressiveButtonSize.Small,
                icon = Icons.Filled.ContentCopy,
            )

            Spacer(Modifier.height(18.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                color = c.primary,
                strokeWidth = 2.dp,
                modifier = Modifier.width(16.dp),
            )
            Text(
                "We opened your browser. Click Authorize there to finish.",
                style = PureTvType.dataSmall,
                color = c.onSurfaceVariant,
            )
        }

        if (userCode != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Didn't open? Go to twitch.tv/activate and enter this code.",
                style = PureTvType.dataSmall,
                color = c.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Signed in: who, plus the real sign-out control ([SettingsViewModel.logOut]). */
@Composable
private fun SignedInArea(username: String?, onSignOut: () -> Unit) {
    val c = PureTvTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                ExpressiveIcons.CheckCircle,
                contentDescription = null,
                tint = c.tertiary,
                modifier = Modifier.width(22.dp),
            )
            Text(
                "Signed in as " + (username ?: "your Twitch account"),
                style = MaterialTheme.typography.titleLarge,
                color = c.onSurface,
            )
        }
        Spacer(Modifier.height(20.dp))
        ExpressiveButton(
            text = "Sign out",
            onClick = onSignOut,
            style = ExpressiveButtonStyle.Outlined,
            size = ExpressiveButtonSize.Medium,
            icon = ExpressiveIcons.SignOut,
        )
    }
}
