package com.puretv.twitch.desktop.player

import com.puretv.twitch.core.model.UpscalingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpvScalerPropsTest {
    private val shaders = ShaderPaths(
        cas = "C:/s/CAS.glsl",
        animePipeline = listOf("C:/s/Clamp.glsl", "C:/s/Restore.glsl", "C:/s/UpM.glsl"),
    )

    @Test fun offIsBilinearAndClearsShader() {
        val p = mpvScalerProps(UpscalingMode.OFF, shaders)
        assertEquals("bilinear", p["scale"])
        assertEquals("bilinear", p["cscale"])
        assertEquals("", p["glsl-shaders"], "Off must clear the shader even when paths are supplied")
    }

    @Test fun standardIsEwaPlusCas() {
        val p = mpvScalerProps(UpscalingMode.STANDARD, shaders)
        assertEquals("ewa_lanczossharp", p["scale"])
        assertEquals("ewa_lanczossharp", p["cscale"])
        assertEquals("mitchell", p["dscale"])
        assertTrue(p["glsl-shaders"]!!.contains("CAS.glsl"), "Sharp adds CAS sharpening: ${p["glsl-shaders"]}")
    }

    @Test fun standardWithoutCasDegradesToPlainEwa() {
        // Missing shader file -> no broken path, just the (still good) base scaler.
        val p = mpvScalerProps(UpscalingMode.STANDARD, ShaderPaths(cas = "", animePipeline = emptyList()))
        assertEquals("ewa_lanczossharp", p["scale"])
        assertEquals("", p["glsl-shaders"])
    }

    @Test fun animeUsesFullPipelineInOrder() {
        val p = mpvScalerProps(UpscalingMode.ANIME, shaders)
        assertEquals("ewa_lanczossharp", p["scale"])
        val v = p["glsl-shaders"]!!
        assertTrue(v.indexOf("Clamp.glsl") < v.indexOf("Restore.glsl"), "pipeline must keep order: $v")
        assertTrue(v.indexOf("Restore.glsl") < v.indexOf("UpM.glsl"), "pipeline must keep order: $v")
    }

    @Test fun animeWithoutPipelineClearsShader() {
        val p = mpvScalerProps(UpscalingMode.ANIME, ShaderPaths(cas = "x", animePipeline = emptyList()))
        assertEquals("", p["glsl-shaders"])
    }

    @Test fun pipelineIsJoinedWithOsPathSeparator() {
        // mpv `glsl-shaders` is an OS-path list (';' on Windows, ':' on Unix) — using
        // the wrong separator would silently load 0 shaders. Guard the actual join.
        val v = mpvScalerProps(UpscalingMode.ANIME, shaders)["glsl-shaders"]!!
        assertTrue(v.contains(java.io.File.pathSeparator), "multi-shader pipeline must be path-separator-joined: $v")
    }

    @Test fun everyModeReturnsTheSameKeysSoLiveSwitchingFullyOverwrites() {
        val keys = setOf("scale", "cscale", "dscale", "glsl-shaders")
        assertEquals(keys, mpvScalerProps(UpscalingMode.OFF, shaders).keys)
        assertEquals(keys, mpvScalerProps(UpscalingMode.STANDARD, shaders).keys)
        assertEquals(keys, mpvScalerProps(UpscalingMode.ANIME, shaders).keys)
    }
}
