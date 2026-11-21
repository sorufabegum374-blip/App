package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.ShaderDefaults
import com.example.export.ShaderPackExporter
import com.example.model.TargetPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Lumina Shader Forge", appName)
    }

    @Test
    fun `bedrock mcpack exporter writes valid zip entries with pbr manifest`() {
        val out = ByteArrayOutputStream()
        ShaderPackExporter.writeToOutputStream(
            ShaderDefaults.starterVibrantVisuals,
            TargetPlatform.BEDROCK_VIBRANT,
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
        assertTrue(entryNames.contains("manifest.json"))
        assertTrue(entryNames.contains("lighting/global.json"))
        assertTrue(entryNames.contains("atmospherics/atmospherics.json"))
        assertTrue(entryNames.contains("color_grading/color_grading.json"))
        assertTrue(entryNames.contains("water/water.json"))
        assertTrue(entryNames.contains("shadows/shadows.json"))
    }
}
