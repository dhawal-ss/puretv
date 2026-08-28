package com.puretv.twitch.desktop.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * One place to name every glyph the UI uses.
 *
 * The Material 3 Expressive spec draws with **Material Symbols Rounded**, which
 * is a variable icon font addressed by ligature. Compose Desktop has no
 * icon-font pipeline, so these are the nearest `material-icons-extended`
 * `ImageVector` equivalents: same semantics, slightly squarer terminals.
 * Substituting here rather than at 200 call sites means swapping in a real
 * Symbols font later is a one-file change.
 *
 * [Filled] and [Outlined] pairs exist for navigation, where M3 uses the fill
 * state to mark the selected destination.
 */
object ExpressiveIcons {
    // Navigation
    val Home: ImageVector = Icons.Filled.Home
    val HomeOutlined: ImageVector = Icons.Outlined.Home
    val Following: ImageVector = Icons.Filled.Favorite
    val FollowingOutlined: ImageVector = Icons.Filled.FavoriteBorder
    val Browse: ImageVector = Icons.Filled.GridView
    val BrowseOutlined: ImageVector = Icons.Outlined.GridView
    val Search: ImageVector = Icons.Filled.Search
    val SearchOutlined: ImageVector = Icons.Outlined.Search
    val Settings: ImageVector = Icons.Filled.Settings
    val SettingsOutlined: ImageVector = Icons.Outlined.Settings
    val Account: ImageVector = Icons.Filled.AccountCircle
    val AccountOutlined: ImageVector = Icons.Outlined.AccountCircle

    // Shell
    val Menu: ImageVector = Icons.Filled.Menu
    val Back: ImageVector = Icons.AutoMirrored.Filled.ArrowBack
    val Close: ImageVector = Icons.Filled.Close
    val ExpandMore: ImageVector = Icons.Filled.ExpandMore
    val ExpandLess: ImageVector = Icons.Filled.ExpandLess
    val More: ImageVector = Icons.Filled.MoreHoriz

    // Actions
    val Play: ImageVector = Icons.Filled.PlayArrow
    val Pause: ImageVector = Icons.Filled.Pause
    val Resume: ImageVector = Icons.Filled.PlayCircle
    val Add: ImageVector = Icons.Filled.Add
    val Check: ImageVector = Icons.Filled.Check
    val CheckCircle: ImageVector = Icons.Filled.CheckCircle
    val Notify: ImageVector = Icons.Filled.NotificationsNone
    val Refresh: ImageVector = Icons.Filled.Refresh
    val Download: ImageVector = Icons.Filled.Download
    val OpenInNew: ImageVector = Icons.Filled.OpenInNew
    val SignIn: ImageVector = Icons.AutoMirrored.Filled.Login
    val SignOut: ImageVector = Icons.AutoMirrored.Filled.Logout

    // Player
    val VolumeUp: ImageVector = Icons.AutoMirrored.Filled.VolumeUp
    val VolumeOff: ImageVector = Icons.AutoMirrored.Filled.VolumeOff
    val Quality: ImageVector = Icons.Filled.Hd
    val Tune: ImageVector = Icons.Filled.Tune
    val AspectRatio: ImageVector = Icons.Filled.AspectRatio
    val Fullscreen: ImageVector = Icons.Filled.Fullscreen
    val FullscreenExit: ImageVector = Icons.Filled.FullscreenExit
    val Chat: ImageVector = Icons.AutoMirrored.Filled.Chat
    val Emote: ImageVector = Icons.Filled.Mood
    val Send: ImageVector = Icons.AutoMirrored.Filled.Send

    // Status / layout
    val Shield: ImageVector = Icons.Filled.Shield
    val Grid: ImageVector = Icons.Filled.GridView
    val ListView: ImageVector = Icons.Filled.ViewList
}
