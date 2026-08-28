package com.puretv.twitch.android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.puretv.twitch.android.ui.LoginViewModel
import com.puretv.twitch.android.ui.components.ExpressiveButton
import com.puretv.twitch.android.ui.components.ExpressiveButtonSize
import com.puretv.twitch.android.ui.components.ExpressiveButtonStyle
import com.puretv.twitch.android.ui.components.ExpressiveIconButton
import com.puretv.twitch.android.ui.components.ExpressiveIcons
import com.puretv.twitch.android.ui.components.ShieldPill
import com.puretv.twitch.android.ui.components.expressiveClickable
import com.puretv.twitch.android.ui.theme.PureTvTheme
import com.puretv.twitch.android.ui.theme.PureTvType
import org.koin.androidx.compose.koinViewModel

/**
 * SECTION 03.2: Twitch login via the Device Code Grant flow (the same flow the
 * desktop app uses). The user opens twitch.tv/activate, enters the shown code,
 * and [LoginViewModel] polls until Twitch authorizes it. No browser redirect is
 * involved (Twitch does not accept custom-scheme redirect URIs), so this works
 * on phone, tablet, and the TV app alike.
 */
@Composable
fun LoginScreen(onLoggedIn: () -> Unit, onBack: () -> Unit) {
    val viewModel: LoginViewModel = koinViewModel()
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val c = PureTvTheme.colors

    LaunchedEffect(state.isLoggedIn) {
        if (state.isLoggedIn) onLoggedIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Log in to Twitch", color = c.onSurface) },
                navigationIcon = {
                    ExpressiveIconButton(icon = ExpressiveIcons.Back, contentDescription = "Back", onClick = onBack)
                },
            )
        },
        containerColor = c.surfaceLowest,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.surfaceContainer)
                    .padding(28.dp),
            ) {
                AppMark()

                Spacer(Modifier.height(20.dp))
                Text(
                    "Sign in once, then just watch.",
                    style = MaterialTheme.typography.displaySmall,
                    color = c.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "PureTV needs your Twitch account to load who you follow and let you " +
                        "chat. Sign-in happens on Twitch's own page, so your password never " +
                        "reaches this app.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = c.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                val code = state.userCode
                if (code == null) {
                    ExpressiveButton(
                        text = if (state.isAuthenticating) "Starting…" else "Sign in with Twitch",
                        onClick = viewModel::beginLogin,
                        style = ExpressiveButtonStyle.Filled,
                        size = ExpressiveButtonSize.XLarge,
                        icon = ExpressiveIcons.SignIn,
                        enabled = !state.isAuthenticating,
                    )
                } else {
                    val verifyUrl = state.verificationUri ?: "https://www.twitch.tv/activate"
                    AuthenticatingArea(
                        userCode = code,
                        isAuthenticating = state.isAuthenticating,
                        onCopyCode = { clipboard.setText(AnnotatedString(code)) },
                        onOpenTwitch = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(verifyUrl))) },
                    )
                }

                state.error?.let { error ->
                    Spacer(Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(PureTvTheme.shapes.smShape)
                            .background(c.errorContainer)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(error, style = MaterialTheme.typography.bodyMedium, color = c.onErrorContainer, textAlign = TextAlign.Center)
                    }
                }

                Spacer(Modifier.height(24.dp))
                ShieldPill(text = "Ad blocking runs on-device, always")
            }
        }
    }
}

/** The 80dp app mark: a rounded square at rest that rounds all the way into a
 *  circle under the finger. Purely decorative (no destination), so the click is
 *  a no-op and only the interaction source's press state drives the morph. */
@Composable
private fun AppMark() {
    val c = PureTvTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(80.dp)
            .height(80.dp)
            .expressiveClickable(
                interaction = interaction,
                onClick = {},
                restRadius = 28.dp,
                pressRadius = 40.dp,
                color = c.primary,
            ),
    ) {
        Text(
            "P",
            style = MaterialTheme.typography.displaySmall.copy(fontFamily = PureTvType.display, fontSize = 36.sp),
            color = c.onPrimary,
        )
    }
}

/** Waiting on the device-code grant: the user code to enter, then the poll status. */
@Composable
private fun AuthenticatingArea(
    userCode: String,
    isAuthenticating: Boolean,
    onCopyCode: () -> Unit,
    onOpenTwitch: () -> Unit,
) {
    val c = PureTvTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(PureTvTheme.shapes.mdShape)
                .background(c.surfaceHigh)
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Text("ENTER THIS CODE AT TWITCH.TV/ACTIVATE", style = PureTvType.kicker, color = c.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            Text(
                userCode,
                style = MaterialTheme.typography.displaySmall.copy(fontFamily = PureTvType.mono, letterSpacing = 6.sp),
                color = c.onSurface,
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ExpressiveButton(text = "Copy code", onClick = onCopyCode, style = ExpressiveButtonStyle.Outlined, size = ExpressiveButtonSize.Small)
            ExpressiveButton(text = "Open Twitch", onClick = onOpenTwitch, style = ExpressiveButtonStyle.Tonal, size = ExpressiveButtonSize.Small)
        }

        if (isAuthenticating) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.width(16.dp).height(16.dp))
                Text("Waiting for you to authorize…", style = PureTvType.dataSmall, color = c.onSurfaceVariant)
            }
        }
    }
}
