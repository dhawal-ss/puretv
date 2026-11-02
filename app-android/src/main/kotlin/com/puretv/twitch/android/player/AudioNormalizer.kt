package com.puretv.twitch.android.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.puretv.twitch.core.audio.AudioLevelling
import com.puretv.twitch.core.audio.DynamicRangeCompressor
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * "Even out loud and quiet audio": runs core's [DynamicRangeCompressor] on the
 * decoded PCM inside ExoPlayer's audio sink. It works the same on every device
 * from API 26 up (the platform's DynamicsProcessing needs API 28), and [enabled]
 * switches it live, from any thread, with a short fade.
 *
 * The processor stays active for 16-bit and float PCM even while switched off,
 * because ExoPlayer only asks whether a processor is active when the sink is
 * configured. Switched off and faded out, it copies the audio through. Any other
 * encoding makes it inactive instead of failing playback.
 */
@UnstableApi
class AudioNormalizer : BaseAudioProcessor() {
    private val compressor = DynamicRangeCompressor()

    var enabled: Boolean
        get() = compressor.enabled
        set(value) { compressor.enabled = value }

    /** How strongly it works (Light, Balanced, Strong); switches live. */
    var level: AudioLevelling
        get() = compressor.level
        set(value) { compressor.level = value }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT -> inputAudioFormat
            else -> AudioProcessor.AudioFormat.NOT_SET
        }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val start = inputBuffer.position()
        val end = inputBuffer.limit()
        if (start == end) return
        val out = replaceOutputBuffer(end - start)
        if (compressor.isBypassed) {
            out.put(inputBuffer)
        } else {
            val channels = inputAudioFormat.channelCount
            val frameBytes = inputAudioFormat.bytesPerFrame
            val wholeFrames = start + (end - start) / frameBytes * frameBytes
            if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
                processPcm16(inputBuffer, start, wholeFrames, channels, out)
            } else {
                processFloat(inputBuffer, start, wholeFrames, channels, out)
            }
            // A partial frame never comes from the sink; pass it on untouched if it ever does.
            for (i in wholeFrames until end) out.put(inputBuffer.get(i))
            inputBuffer.position(end)
        }
        out.flip()
    }

    private fun processPcm16(input: ByteBuffer, start: Int, end: Int, channels: Int, out: ByteBuffer) {
        var frame = start
        while (frame < end) {
            var peak = 0
            for (c in 0 until channels) {
                val s = abs(input.getShort(frame + 2 * c).toInt())
                if (s > peak) peak = s
            }
            val gain = compressor.nextGain(peak / PCM16_FULL_SCALE)
            for (c in 0 until channels) {
                out.putShort(DynamicRangeCompressor.scale16(input.getShort(frame + 2 * c), gain))
            }
            frame += 2 * channels
        }
    }

    private fun processFloat(input: ByteBuffer, start: Int, end: Int, channels: Int, out: ByteBuffer) {
        var frame = start
        while (frame < end) {
            var peak = 0f
            for (c in 0 until channels) {
                val s = abs(input.getFloat(frame + 4 * c))
                if (s > peak) peak = s
            }
            val gain = compressor.nextGain(peak)
            for (c in 0 until channels) out.putFloat(input.getFloat(frame + 4 * c) * gain)
            frame += 4 * channels
        }
    }

    // flush() is where the format from configure() takes effect, so the
    // compressor picks up the sample rate and channel count here.
    override fun onFlush() {
        if (inputAudioFormat == AudioProcessor.AudioFormat.NOT_SET) return
        compressor.configure(inputAudioFormat.sampleRate, inputAudioFormat.channelCount)
    }

    override fun onReset() {
        compressor.reset()
    }

    private companion object {
        const val PCM16_FULL_SCALE = 32768f
    }
}

/**
 * ExoPlayer's default renderers with [normalizer] in the audio sink. Same sink as
 * [DefaultRenderersFactory] builds, plus the processor; ExoPlayer still adds its
 * own speed and silence processors after it.
 */
@UnstableApi
class NormalizingRenderersFactory(
    context: Context,
    private val normalizer: AudioNormalizer,
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink = DefaultAudioSink.Builder(context)
        .setEnableFloatOutput(enableFloatOutput)
        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
        .setAudioProcessors(arrayOf(normalizer))
        .build()
}
