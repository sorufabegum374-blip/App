package com.example

import com.example.data.ShaderDefaults
import com.example.export.ShaderPackExporter
import com.example.model.ShaderPreset
import com.example.model.TargetPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class ExampleUnitTest {

    @Test
    fun `sanitized clamps optical bounds and normalizes hex`() {
        val dirty = ShaderPreset(
            title = "  RTX Ultra  ",
            sunWarmth = 9.0,
            giBounceIntensity = -1.0,
            skyZenithHex = "1a2b3c",
            fogTintHex = "INVALID"
        )
        val clean = dirty.sanitized()
        assertEquals("RTX Ultra", clean.title)
        assertEquals(2.0, clean.sunWarmth, 0.001)
        assertEquals(0.0, clean.giBounceIntensity, 0.001)
        assertEquals("#1A2B3C", clean.skyZenithHex)
        assertEquals("#E8AD82", clean.fogTintHex)
    }

    @Test
    fun `java shader zip exporter writes optifine glsl pipeline`() {
        val out = ByteArrayOutputStream()
        ShaderPackExporter.writeToOutputStream(
            ShaderDefaults.starterRtxRayTraced,
            TargetPlatform.JAVA_OPTIFINE,
            out
        )
        val entryNames = mutableListOf<String>()
        ZipInputStream(ByteArrayInputStream(out.toByteArray())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                entryNames.add(entry.name)
                entry = zip.nextEntry
            }
        }
        assertTrue(entryNames.contains("shaders/shaders.properties"))
        assertTrue(entryNames.contains("shaders/gbuffers_terrain.fsh"))
        assertTrue(entryNames.contains("shaders/gbuffers_water.fsh"))
        assertTrue(entryNames.contains("shaders/final.fsh"))
    }

    @Test
    fun `preset selection grid contains vibrant ray tracing and cartoon presets`() {
        val labels = ShaderDefaults.presetSelectionGrid.map { it.label }
        assertTrue(labels.contains("Vibrant"))
        assertTrue(labels.contains("Ray Tracing"))
        assertTrue(labels.contains("Cartoon"))
        assertEquals(8, ShaderDefaults.presetSelectionGrid.size)
        ShaderDefaults.presetSelectionGrid.forEach { item ->
            assertTrue(item.preset.prompt.isNotBlank())
            assertTrue(item.preset.fragmentShader.contains("gl_FragColor"))
        }
        assertEquals(
            ShaderDefaults.CARTOON_CEL_FRAGMENT_SHADER,
            ShaderDefaults.starterCartoon.fragmentShader
        )
    }
}
