package com.example.data

import com.example.model.ShaderCategory
import com.example.model.ShaderPreset
import com.example.model.TargetPlatform
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class ShaderAiRepository {

    private val shaderSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Short creative title for the generated shader (max 60 chars)"),
            "presetCategory" to Schema.enumeration(
                values = ShaderCategory.entries.map { it.name },
                description = "Best matching shader category"
            ),
            "targetPlatform" to Schema.enumeration(
                values = TargetPlatform.entries.map { it.name },
                description = "Best target pack format"
            ),
            "summary" to Schema.string(description = "Concise 1-2 sentence technical breakdown of lighting, water, and GI effects"),
            "sunWarmth" to Schema.double(description = "Sun color warmth between 0.0 and 2.0"),
            "sunElevation" to Schema.double(description = "Sun elevation between -0.5 (night) and 1.5 (noon)"),
            "fogDensity" to Schema.double(description = "Atmospheric fog density between 0.0 and 2.0"),
            "godRaysIntensity" to Schema.double(description = "Volumetric sun shaft intensity between 0.0 and 2.0"),
            "waterReflectivity" to Schema.double(description = "Fresnel water reflection multiplier between 0.0 and 1.5"),
            "giBounceIntensity" to Schema.double(description = "Ray-traced style indirect GI bounce between 0.0 and 2.0"),
            "bloomIntensity" to Schema.double(description = "Emissive and highlight bloom between 0.0 and 2.0"),
            "saturation" to Schema.double(description = "Final color grading saturation between 0.0 and 2.5"),
            "exposure" to Schema.double(description = "Filmic tone-mapping exposure between 0.2 and 3.0"),
            "shadowSoftness" to Schema.double(description = "Penumbra shadow softness between 0.0 and 2.0"),
            "pbrMetallicBoost" to Schema.double(description = "Specular PBR metallic intensity between 0.0 and 2.0"),
            "skyZenithHex" to Schema.string(description = "6-digit hex color with # prefix for upper sky, e.g. #14357A"),
            "skyHorizonHex" to Schema.string(description = "6-digit hex color with # prefix for horizon sky, e.g. #FF9E59"),
            "sunLightHex" to Schema.string(description = "6-digit hex color with # prefix for direct sun/moon light"),
            "ambientTintHex" to Schema.string(description = "6-digit hex color with # prefix for ambient shadow fill"),
            "waterTintHex" to Schema.string(description = "6-digit hex color with # prefix for water absorption color"),
            "fogTintHex" to Schema.string(description = "6-digit hex color with # prefix for distance fog color"),
            "fragmentShader" to Schema.string(
                description = "Complete valid OpenGL ES 1.00 GLSL fragment shader (max 12000 chars) rendering a 3D ray-marched voxel landscape, reflective water, sky, clouds, and lighting using the provided uniforms."
            )
        )
    )

    private val systemPrompt = content {
        text(
            """
            You are Lumina Shader Forge, an expert graphics programmer specializing in Minecraft Vibrant Visuals PBR, RTX-style ray-traced lighting, and mobile OpenGL ES GLSL shaders.
            Given a user's prompt (in English or Bengali/Banglish), synthesize a complete, cohesive shader configuration AND valid OpenGL ES 1.00 fragment shader (`fragmentShader`).

            CRITICAL GLSL RULES FOR `fragmentShader`:
            1. Start with `precision mediump float;`
            2. Use GLSL ES 1.00 syntax (`gl_FragColor = vec4(...);`, NO `out vec4`, NO `texture()`, NO bitwise ops).
            3. Declare and use these exact uniforms so the app's real-time controls and drag-to-orbit camera work seamlessly:
               uniform vec2 uResolution;
               uniform float uTime;
               uniform float uYaw;
               uniform float uPitch;
               uniform float uSunWarmth;
               uniform float uSunElevation;
               uniform float uFogDensity;
               uniform float uGodRaysIntensity;
               uniform float uWaterReflectivity;
               uniform float uGiBounceIntensity;
               uniform float uBloomIntensity;
               uniform float uSaturation;
               uniform float uExposure;
               uniform float uShadowSoftness;
               uniform float uPbrMetallicBoost;
               uniform vec3 uSkyZenith;
               uniform vec3 uSkyHorizon;
               uniform vec3 uSunLight;
               uniform vec3 uAmbientTint;
               uniform vec3 uWaterTint;
               uniform vec3 uFogTint;
            4. Every float literal MUST have a decimal point (e.g., `1.0`, `0.0`, `3.0`). Never write `vec3(1)` or `float x = 0;`.
            5. All `for` loops MUST have constant integer bounds <= 42 (e.g., `for (int i = 0; i < 36; i++)`).
            6. Render a visually rich 3D ray-marched voxel terrain + reflective water plane + sky + volumetric sun shafts/aurora/glowing ores matching the prompt.
            7. Hex colors MUST strictly be 7 characters (`#RRGGBB`).
            """.trimIndent()
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = shaderSchema
                temperature = 0.75f
            },
            systemInstruction = systemPrompt
        )
    }

    suspend fun generateShader(
        userPrompt: String,
        targetPlatform: TargetPlatform,
        existingShader: ShaderPreset? = null
    ): ShaderPreset {
        val fullPrompt = buildString {
            appendLine("Target export platform: ${targetPlatform.label} (${targetPlatform.name}).")
            if (existingShader != null) {
                appendLine("Refine this existing shader preset based on the user's request:")
                appendLine("Current title: ${existingShader.title}")
                appendLine("Current summary: ${existingShader.summary}")
                appendLine(
                    "Current params: sunWarmth=${existingShader.sunWarmth}, sunElevation=${existingShader.sunElevation}, " +
                        "fogDensity=${existingShader.fogDensity}, godRays=${existingShader.godRaysIntensity}, " +
                        "waterReflectivity=${existingShader.waterReflectivity}, giBounce=${existingShader.giBounceIntensity}, " +
                        "bloom=${existingShader.bloomIntensity}, saturation=${existingShader.saturation}, " +
                        "exposure=${existingShader.exposure}, pbrMetallic=${existingShader.pbrMetallicBoost}."
                )
            }
            appendLine("User prompt: $userPrompt")
            appendLine("Also make sure the generated GLSL fragmentShader is self-contained, fast on mobile GPUs, and visually dramatic.")
        }

        val response = withTimeout(60_000L) {
            model.generateContent(fullPrompt)
        }

        val rawJson = response.text
        if (rawJson.isNullOrBlank()) {
            throw IllegalStateException("Gemini returned an empty shader response.")
        }

        val json = JSONObject(rawJson)
        val generatedCode = json.optString("fragmentShader").trim()
        val safeCode = if (generatedCode.contains("gl_FragColor") && generatedCode.contains("main")) {
            generatedCode
        } else {
            ShaderDefaults.BASE_VOXEL_FRAGMENT_SHADER
        }

        return ShaderPreset(
            id = existingShader?.id.orEmpty(),
            title = json.optString("title", "Forged Shader").take(80),
            prompt = userPrompt.take(600),
            presetCategory = ShaderCategory.fromRaw(json.optString("presetCategory")).name,
            targetPlatform = targetPlatform.name,
            summary = json.optString("summary", "AI-synthesized volumetric & PBR shader.").take(500),
            fragmentShader = safeCode.take(15000),
            sunWarmth = json.optDouble("sunWarmth", 1.2).coerceIn(0.0, 2.0),
            sunElevation = json.optDouble("sunElevation", 0.35).coerceIn(-0.5, 1.5),
            fogDensity = json.optDouble("fogDensity", 0.45).coerceIn(0.0, 2.0),
            godRaysIntensity = json.optDouble("godRaysIntensity", 1.1).coerceIn(0.0, 2.0),
            waterReflectivity = json.optDouble("waterReflectivity", 1.0).coerceIn(0.0, 1.5),
            giBounceIntensity = json.optDouble("giBounceIntensity", 1.1).coerceIn(0.0, 2.0),
            bloomIntensity = json.optDouble("bloomIntensity", 0.9).coerceIn(0.0, 2.0),
            saturation = json.optDouble("saturation", 1.25).coerceIn(0.0, 2.5),
            exposure = json.optDouble("exposure", 1.15).coerceIn(0.2, 3.0),
            shadowSoftness = json.optDouble("shadowSoftness", 0.8).coerceIn(0.0, 2.0),
            pbrMetallicBoost = json.optDouble("pbrMetallicBoost", 1.0).coerceIn(0.0, 2.0),
            skyZenithHex = ShaderPreset.normalizeHex(json.optString("skyZenithHex"), "#15306B"),
            skyHorizonHex = ShaderPreset.normalizeHex(json.optString("skyHorizonHex"), "#FFA25C"),
            sunLightHex = ShaderPreset.normalizeHex(json.optString("sunLightHex"), "#FFD68A"),
            ambientTintHex = ShaderPreset.normalizeHex(json.optString("ambientTintHex"), "#3A5894"),
            waterTintHex = ShaderPreset.normalizeHex(json.optString("waterTintHex"), "#1B748F"),
            fogTintHex = ShaderPreset.normalizeHex(json.optString("fogTintHex"), "#E8AD82"),
            favorite = existingShader?.favorite ?: false,
            createdAtMillis = existingShader?.createdAtMillis ?: 0L,
            updatedAtMillis = existingShader?.updatedAtMillis ?: 0L
        )
    }
}
