package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.audio.AudioNormalizerProfile
import com.puretv.twitch.core.model.AppSettings
import java.util.concurrent.ConcurrentHashMap

/**
 * The audio levelling the players should run with: the chosen strength while "Even out
 * loud and quiet audio" is on, null while it is off. The one place that reads the two
 * settings together, so every player and every engine key agree on it.
 */
fun AppSettings.audioLevellingInUse(): AudioLevelling? = if (audioNormalizer) audioLevelling else null

/**
 * What a libVLC instance was started with: the NVIDIA RTX Video it runs ([rtx]) and the
 * audio normalizer strength in its audio chain ([levelling], null for none). VLC 3.0
 * takes both only as instance arguments, so each combination is an instance of its own,
 * and switching either one (the normalizer on, off, or to another strength) means moving
 * the player to another instance.
 */
data class VlcEngineKey(val rtx: RtxVideoChoice, val levelling: AudioLevelling?) {
    /** The arguments libVLC must take for this key, or it is the wrong instance. */
    fun extraArgs(): List<String> = levelling?.let { AudioNormalizerProfile.vlcArgs(it) } ?: emptyList()

    /** The arguments worth trying but not worth failing over (see [VlcEngine]). */
    fun optionalArgs(): List<String> = rtx.vlcArgs()

    companion object {
        /** The plain instance: no RTX Video, with the normalizer at [levelling] or without it. */
        fun standard(levelling: AudioLevelling?) = VlcEngineKey(RtxVideoChoice.NONE, levelling)
    }
}

/**
 * Every libVLC instance of the app, one per [VlcEngineKey], each made the first time a
 * player asks for it. Making one is cheap; libVLC itself loads when the instance is
 * first used (see [VlcEngine]), so only the keys that really play ever cost a plugin
 * scan. The single-stream player, every multi-stream tile and every extra window share
 * the instance for their key: with the normalizer off and no RTX Video they all play on
 * the same one, as before.
 *
 * The normalizer is read from the settings ([levelling]) when a player is made, and a
 * change reaches the players that are already playing through [levellingChanged]
 * (main() watches the settings): each one moves to the instance with the new arguments
 * and starts its stream again, a second or two of rebuffering. An instance a player
 * moved away from stays loaded until shutdown, so switching back is instant.
 */
class VlcEngines(
    // The saved settings (see [audioLevellingInUse]). Read when a player is made; never
    // baked into an instance.
    private val levelling: () -> AudioLevelling?,
    // How an instance is made for a key. Tests count these without starting libVLC.
    private val make: (VlcEngineKey) -> VlcEngine = { key -> VlcEngine(key::extraArgs, key::optionalArgs) },
) {
    private val engines = ConcurrentHashMap<VlcEngineKey, VlcEngine>()

    // The players that move when the normalizer settings change (see [follow]).
    private val followers: MutableSet<(AudioLevelling?) -> Unit> = ConcurrentHashMap.newKeySet()

    /** What the settings ask for now: a strength, or null for off. */
    fun levellingWanted(): AudioLevelling? = levelling()

    /** The instance for [key]. Cheap; libVLC loads on first use. Never call warmUp in here. */
    fun engineFor(key: VlcEngineKey): VlcEngine = engines.computeIfAbsent(key, make)

    /** Whether the instance for [key] already started, so moving to it is instant. */
    fun isBuilt(key: VlcEngineKey): Boolean = engines[key]?.isBuilt == true

    /** Calls [onChange] with the new value whenever [levellingChanged] is told of one. */
    fun follow(onChange: (AudioLevelling?) -> Unit) {
        followers.add(onChange)
    }

    fun unfollow(onChange: (AudioLevelling?) -> Unit) {
        followers.remove(onChange)
    }

    /** The normalizer settings changed: every following player moves to the matching instance. */
    fun levellingChanged(level: AudioLevelling?) = followers.toList().forEach { runCatching { it(level) } }

    /** Frees every instance that was started. Call once at shutdown, after the players. */
    fun releaseIfBuilt() = engines.values.forEach { it.releaseIfBuilt() }
}
