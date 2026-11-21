package com.example.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.ShaderPreset
import com.example.model.TargetPlatform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ShaderPackExporter {

    fun suggestedFileName(preset: ShaderPreset, platform: TargetPlatform): String {
        val slug = preset.title.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "lumina_shader" }
        return "${slug}_${platform.name.lowercase()}.${platform.extension}"
    }

    fun writeToOutputStream(
        preset: ShaderPreset,
        platform: TargetPlatform,
        outputStream: OutputStream
    ) {
        when (platform) {
            TargetPlatform.BEDROCK_VIBRANT -> writeBedrockMcpack(preset, outputStream)
            TargetPlatform.JAVA_OPTIFINE -> writeJavaShaderZip(preset, outputStream)
            TargetPlatform.UNIVERSAL_GLSL -> {
                outputStream.write(preset.fragmentShader.toByteArray(Charsets.UTF_8))
            }
        }
    }

    fun createShareUri(
        context: Context,
        preset: ShaderPreset,
        platform: TargetPlatform
    ): Uri {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, suggestedFileName(preset, platform))
        file.outputStream().use { out ->
            writeToOutputStream(preset, platform, out)
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun buildShareIntent(context: Context, preset: ShaderPreset, platform: TargetPlatform): Intent {
        val uri = createShareUri(context, preset, platform)
        val mime = when (platform) {
            TargetPlatform.BEDROCK_VIBRANT -> "application/octet-stream"
            TargetPlatform.JAVA_OPTIFINE -> "application/zip"
            TargetPlatform.UNIVERSAL_GLSL -> "text/plain"
        }
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, preset.title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun buildBedrockPreviewSummary(preset: ShaderPreset): String {
        return buildString {
            appendLine("// Bedrock Vibrant Visuals / Deferred PBR Pack Files")
            appendLine("manifest.json (capabilities: [\"pbr\"])")
            appendLine("lighting/global.json -> sunWarmth=${fmt(preset.sunWarmth)}, giBounce=${fmt(preset.giBounceIntensity)}")
            appendLine("atmospherics/atmospherics.json -> zenith=${preset.skyZenithHex}, horizon=${preset.skyHorizonHex}")
            appendLine("color_grading/color_grading.json -> sat=${fmt(preset.saturation)}, exp=${fmt(preset.exposure)}")
            appendLine("water/water.json -> reflectivity=${fmt(preset.waterReflectivity)}, tint=${preset.waterTintHex}")
            appendLine("shadows/shadows.json -> softness=${fmt(preset.shadowSoftness)}")
        }
    }

    private fun writeBedrockMcpack(preset: ShaderPreset, outputStream: OutputStream) {
        ZipOutputStream(outputStream).use { zip ->
            addZipEntry(zip, "manifest.json", buildBedrockManifest(preset))
            addZipEntry(zip, "lighting/global.json", buildBedrockLightingJson(preset))
            addZipEntry(zip, "atmospherics/atmospherics.json", buildBedrockAtmosphericsJson(preset))
            addZipEntry(zip, "color_grading/color_grading.json", buildBedrockColorGradingJson(preset))
            addZipEntry(zip, "water/water.json", buildBedrockWaterJson(preset))
            addZipEntry(zip, "shadows/shadows.json", buildBedrockShadowsJson(preset))
            addZipEntry(zip, "shaders/glsl_preview.fsh", preset.fragmentShader)
            addZipEntry(
                zip,
                "README.txt",
                """
                Lumina Shader Forge - Bedrock Vibrant Visuals / PBR Resource Pack
                Shader: ${preset.title}
                Category: ${preset.categoryEnum.label}
                Summary: ${preset.summary}

                How to use:
                1. Open this .mcpack file with Minecraft Bedrock Edition (or copy into resource_packs/).
                2. Activate the pack in Global Resources or World Resource Packs.
                3. Enable "Vibrant Visuals" or "Deferred Technical Preview" in Video Settings.
                """.trimIndent()
            )
        }
    }

    private fun writeJavaShaderZip(preset: ShaderPreset, outputStream: OutputStream) {
        val sunRgb = hexToRgbNormalized(preset.sunLightHex)
        val zenithRgb = hexToRgbNormalized(preset.skyZenithHex)
        val horizonRgb = hexToRgbNormalized(preset.skyHorizonHex)
        val waterRgb = hexToRgbNormalized(preset.waterTintHex)

        ZipOutputStream(outputStream).use { zip ->
            addZipEntry(
                zip,
                "shaders/shaders.properties",
                """
                # Generated by Lumina Shader Forge
                # ${preset.title}
                shadow.enabled=true
                shadowDistance=160.0
                sunPathRotation=-32.0
                """.trimIndent()
            )
            addZipEntry(
                zip,
                "shaders/gbuffers_terrain.vsh",
                """
                #version 120
                varying vec2 lmcoord;
                varying vec2 texcoord;
                varying vec4 glcolor;
                varying vec3 vNormal;
                void main() {
                    gl_Position = ftransform();
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
                    glcolor = gl_Color;
                    vNormal = normalize(gl_NormalMatrix * gl_Normal);
                }
                """.trimIndent()
            )
            addZipEntry(
                zip,
                "shaders/gbuffers_terrain.fsh",
                """
                #version 120
                uniform sampler2D texture;
                uniform sampler2D lightmap;
                varying vec2 lmcoord;
                varying vec2 texcoord;
                varying vec4 glcolor;
                varying vec3 vNormal;
                void main() {
                    vec4 color = texture2D(texture, texcoord) * glcolor;
                    vec3 light = texture2D(lightmap, lmcoord).rgb;
                    vec3 giBounce = vec3(${fmt(zenithRgb[0])}, ${fmt(zenithRgb[1])}, ${fmt(zenithRgb[2])}) * ${fmt(preset.giBounceIntensity * 0.28)};
                    vec3 sunTint = vec3(${fmt(sunRgb[0])}, ${fmt(sunRgb[1])}, ${fmt(sunRgb[2])}) * ${fmt(preset.sunWarmth * 0.85)};
                    color.rgb *= (light * sunTint + giBounce);
                    gl_FragData[0] = color;
                }
                """.trimIndent()
            )
            addZipEntry(
                zip,
                "shaders/gbuffers_water.vsh",
                """
                #version 120
                varying vec2 texcoord;
                varying vec4 glcolor;
                void main() {
                    gl_Position = ftransform();
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                    glcolor = gl_Color;
                }
                """.trimIndent()
            )
            addZipEntry(
                zip,
                "shaders/gbuffers_water.fsh",
                """
                #version 120
                uniform sampler2D texture;
                varying vec2 texcoord;
                varying vec4 glcolor;
                void main() {
                    vec4 base = texture2D(texture, texcoord) * glcolor;
                    vec3 waterTint = vec3(${fmt(waterRgb[0])}, ${fmt(waterRgb[1])}, ${fmt(waterRgb[2])});
                    vec3 skyRefl = vec3(${fmt(horizonRgb[0])}, ${fmt(horizonRgb[1])}, ${fmt(horizonRgb[2])}) * ${fmt(preset.waterReflectivity * 0.55)};
                    gl_FragData[0] = vec4(mix(base.rgb * waterTint, skyRefl, 0.42), clamp(base.a + 0.25, 0.0, 0.92));
                }
                """.trimIndent()
            )
            addZipEntry(
                zip,
                "shaders/final.vsh",
                """
                #version 120
                varying vec2 texcoord;
                void main() {
                    gl_Position = ftransform();
                    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
                }
                """.trimIndent()
            )
            addZipEntry(
                zip,
                "shaders/final.fsh",
                """
                #version 120
                uniform sampler2D colortex0;
                varying vec2 texcoord;
                vec3 aces(vec3 x) {
                    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
                }
                void main() {
                    vec3 col = texture2D(colortex0, texcoord).rgb * ${fmt(preset.exposure)};
                    col += max(col - vec3(0.85), vec3(0.0)) * ${fmt(preset.bloomIntensity * 0.35)};
                    col = aces(col);
                    float luma = dot(col, vec3(0.299, 0.587, 0.114));
                    col = mix(vec3(luma), col, ${fmt(preset.saturation)});
                    gl_FragColor = vec4(col, 1.0);
                }
                """.trimIndent()
            )
            addZipEntry(zip, "shaders/preview_reference.fsh", preset.fragmentShader)
        }
    }

    private fun buildBedrockManifest(preset: ShaderPreset): String {
        val headerUuid = UUID.nameUUIDFromBytes("header-${preset.title}".toByteArray()).toString()
        val moduleUuid = UUID.nameUUIDFromBytes("module-${preset.title}".toByteArray()).toString()
        val manifest = JSONObject().apply {
            put("format_version", 2)
            put("header", JSONObject().apply {
                put("name", preset.title)
                put("description", preset.summary.ifBlank { "Forged with Lumina Shader Forge" })
                put("uuid", headerUuid)
                put("version", JSONArray(listOf(1, 0, 0)))
                put("min_engine_version", JSONArray(listOf(1, 21, 0)))
            })
            put("modules", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", "resources")
                    put("uuid", moduleUuid)
                    put("version", JSONArray(listOf(1, 0, 0)))
                })
            })
            put("capabilities", JSONArray(listOf("pbr")))
        }
        return manifest.toString(2)
    }

    private fun buildBedrockLightingJson(preset: ShaderPreset): String {
        val sunRgb = hexToRgb255(preset.sunLightHex)
        val ambientRgb = hexToRgb255(preset.ambientTintHex)
        return JSONObject().apply {
            put("format_version", "1.21.40")
            put("minecraft:lighting_settings", JSONObject().apply {
                put("description", JSONObject().put("identifier", "lumina:lighting_${preset.id.ifBlank { "custom" }}"))
                put("directional_lights", JSONObject().apply {
                    put("sun", JSONObject().apply {
                        put("illuminance", (70000.0 * preset.sunWarmth * preset.exposure).toInt())
                        put("color", JSONArray(sunRgb))
                    })
                    put("moon", JSONObject().apply {
                        put("illuminance", 2200)
                        put("color", JSONArray(ambientRgb))
                    })
                })
                put("ambient", JSONObject().apply {
                    put("illuminance", (2400.0 * preset.giBounceIntensity).toInt())
                    put("color", JSONArray(ambientRgb))
                })
                put("pbr", JSONObject().apply {
                    put("emissive_desaturation", (0.25 / preset.bloomIntensity.coerceAtLeast(0.3)).coerceIn(0.05, 0.5))
                    put("metallic_specular_boost", preset.pbrMetallicBoost)
                })
            })
        }.toString(2)
    }

    private fun buildBedrockAtmosphericsJson(preset: ShaderPreset): String {
        return JSONObject().apply {
            put("format_version", "1.21.40")
            put("minecraft:atmosphere_settings", JSONObject().apply {
                put("description", JSONObject().put("identifier", "lumina:atmosphere_custom"))
                put("sky_zenith_color", JSONArray(hexToRgb255(preset.skyZenithHex)))
                put("sky_horizon_color", JSONArray(hexToRgb255(preset.skyHorizonHex)))
                put("fog_color", JSONArray(hexToRgb255(preset.fogTintHex)))
                put("rayleigh_strength", (1.2 + preset.fogDensity * 1.4))
                put("mie_strength", (0.8 + preset.godRaysIntensity * 1.6))
                put("sun_glare_shape", (1.5 + preset.bloomIntensity * 2.0))
            })
        }.toString(2)
    }

    private fun buildBedrockColorGradingJson(preset: ShaderPreset): String {
        return JSONObject().apply {
            put("format_version", "1.21.40")
            put("minecraft:color_grading_settings", JSONObject().apply {
                put("description", JSONObject().put("identifier", "lumina:color_grading_custom"))
                put("color_grading", JSONObject().apply {
                    put("saturation", JSONArray(listOf(preset.saturation, preset.saturation, preset.saturation)))
                    put("gain", JSONArray(listOf(preset.exposure, preset.exposure, preset.exposure)))
                })
                put("tone_mapping", JSONObject().put("operator", "aces"))
            })
        }.toString(2)
    }

    private fun buildBedrockWaterJson(preset: ShaderPreset): String {
        val waterRgb = hexToRgbNormalized(preset.waterTintHex)
        return JSONObject().apply {
            put("format_version", "1.21.40")
            put("minecraft:water_settings", JSONObject().apply {
                put("description", JSONObject().put("identifier", "lumina:water_custom"))
                put("particle_concentrations", JSONObject().apply {
                    put("cdom", (1.0 - waterRgb[2]).coerceIn(0.05, 1.5))
                    put("chlorophyll", waterRgb[1].coerceIn(0.05, 1.5))
                    put("suspended_sediment", (preset.fogDensity * 0.4).coerceIn(0.02, 1.2))
                })
                put("waves", JSONObject().apply {
                    put("enabled", true)
                    put("depth", (0.6 + preset.waterReflectivity * 0.8))
                })
            })
        }.toString(2)
    }

    private fun buildBedrockShadowsJson(preset: ShaderPreset): String {
        return JSONObject().apply {
            put("format_version", "1.21.40")
            put("minecraft:shadow_settings", JSONObject().apply {
                put("description", JSONObject().put("identifier", "lumina:shadows_custom"))
                put("texel_size", (0.002 + preset.shadowSoftness * 0.006))
                put("soft_shadows_enabled", preset.shadowSoftness > 0.15)
            })
        }.toString(2)
    }

    private fun addZipEntry(zip: ZipOutputStream, path: String, content: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun hexToRgb255(hex: String): List<Int> {
        val clean = hex.removePrefix("#")
        if (clean.length != 6) return listOf(255, 255, 255)
        return listOf(
            clean.substring(0, 2).toIntOrNull(16) ?: 255,
            clean.substring(2, 4).toIntOrNull(16) ?: 255,
            clean.substring(4, 6).toIntOrNull(16) ?: 255
        )
    }

    private fun hexToRgbNormalized(hex: String): List<Double> =
        hexToRgb255(hex).map { (it / 255.0).coerceIn(0.0, 1.0) }

    private fun fmt(v: Double): String = String.format(java.util.Locale.US, "%.3f", v)
}
