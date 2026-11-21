package com.example.model

enum class ShaderCategory(val label: String) {
    VIBRANT_VISUALS("Vibrant Visuals"),
    RTX_RAYTRACED("RTX Ray-Traced"),
    ATMOSPHERIC("Atmospheric"),
    STYLIZED("Stylized"),
    PERFORMANCE_32BIT("32-Bit Boost");

    companion object {
        fun fromRaw(raw: String?): ShaderCategory =
            entries.firstOrNull { it.name == raw } ?: VIBRANT_VISUALS
    }
}

enum class TargetPlatform(val label: String, val extension: String) {
    BEDROCK_VIBRANT("Bedrock Vibrant / PBR", "mcpack"),
    JAVA_OPTIFINE("Java Iris / OptiFine", "zip"),
    UNIVERSAL_GLSL("Universal GLSL", "fsh");

    companion object {
        fun fromRaw(raw: String?): TargetPlatform =
            entries.firstOrNull { it.name == raw } ?: BEDROCK_VIBRANT
    }
}

data class ShaderPreset(
    val id: String = "",
    val title: String = "Vibrant RTX Horizon",
    val prompt: String = "",
    val presetCategory: String = ShaderCategory.VIBRANT_VISUALS.name,
    val targetPlatform: String = TargetPlatform.BEDROCK_VIBRANT.name,
    val summary: String = "",
    val fragmentShader: String = "",
    val sunWarmth: Double = 1.25,
    val sunElevation: Double = 0.35,
    val fogDensity: Double = 0.45,
    val godRaysIntensity: Double = 1.2,
    val waterReflectivity: Double = 1.0,
    val giBounceIntensity: Double = 1.1,
    val bloomIntensity: Double = 0.85,
    val saturation: Double = 1.25,
    val exposure: Double = 1.15,
    val shadowSoftness: Double = 0.8,
    val pbrMetallicBoost: Double = 1.0,
    val skyZenithHex: String = "#15306B",
    val skyHorizonHex: String = "#FFA25C",
    val sunLightHex: String = "#FFD68A",
    val ambientTintHex: String = "#3A5894",
    val waterTintHex: String = "#1B748F",
    val fogTintHex: String = "#E8AD82",
    val favorite: Boolean = false,
    val createdAtMillis: Long = 0L,
    val updatedAtMillis: Long = 0L
) {
    val categoryEnum: ShaderCategory
        get() = ShaderCategory.fromRaw(presetCategory)

    val platformEnum: TargetPlatform
        get() = TargetPlatform.fromRaw(targetPlatform)

    fun sanitized(): ShaderPreset = copy(
        title = title.trim().ifBlank { "Custom Shader" }.take(80),
        prompt = prompt.trim().ifBlank { "Custom volumetric shader" }.take(600),
        presetCategory = ShaderCategory.fromRaw(presetCategory).name,
        targetPlatform = TargetPlatform.fromRaw(targetPlatform).name,
        summary = summary.trim().take(500),
        fragmentShader = fragmentShader.trim().ifBlank {
            "precision mediump float; void main() { gl_FragColor = vec4(1.0); }"
        }.take(15000),
        sunWarmth = sunWarmth.coerceIn(0.0, 2.0),
        sunElevation = sunElevation.coerceIn(-0.5, 1.5),
        fogDensity = fogDensity.coerceIn(0.0, 2.0),
        godRaysIntensity = godRaysIntensity.coerceIn(0.0, 2.0),
        waterReflectivity = waterReflectivity.coerceIn(0.0, 1.5),
        giBounceIntensity = giBounceIntensity.coerceIn(0.0, 2.0),
        bloomIntensity = bloomIntensity.coerceIn(0.0, 2.0),
        saturation = saturation.coerceIn(0.0, 2.5),
        exposure = exposure.coerceIn(0.2, 3.0),
        shadowSoftness = shadowSoftness.coerceIn(0.0, 2.0),
        pbrMetallicBoost = pbrMetallicBoost.coerceIn(0.0, 2.0),
        skyZenithHex = normalizeHex(skyZenithHex, "#15306B"),
        skyHorizonHex = normalizeHex(skyHorizonHex, "#FFA25C"),
        sunLightHex = normalizeHex(sunLightHex, "#FFD68A"),
        ambientTintHex = normalizeHex(ambientTintHex, "#3A5894"),
        waterTintHex = normalizeHex(waterTintHex, "#1B748F"),
        fogTintHex = normalizeHex(fogTintHex, "#E8AD82")
    )

    companion object {
        private val HEX_REGEX = Regex("^#[0-9A-Fa-f]{6}$")

        fun normalizeHex(input: String?, fallback: String): String {
            val trimmed = input?.trim().orEmpty()
            val candidate = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
            return if (HEX_REGEX.matches(candidate)) candidate.uppercase() else fallback
        }
    }
}
