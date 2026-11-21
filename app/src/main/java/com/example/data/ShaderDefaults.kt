package com.example.data

import com.example.model.ShaderCategory
import com.example.model.ShaderPreset
import com.example.model.TargetPlatform

data class PresetGridItem(
    val id: String,
    val label: String,
    val badge: String,
    val accentHex: String,
    val secondaryHex: String,
    val preset: ShaderPreset
)

enum class TimeOfDayPreset(
    val label: String,
    val elevation: Double,
    val warmth: Double,
    val zenithHex: String,
    val horizonHex: String,
    val sunHex: String,
    val fogHex: String
) {
    DAWN("Dawn", 0.14, 1.3, "#1C2E66", "#FF8E72", "#FFC394", "#E09B89"),
    NOON("Noon", 1.15, 0.95, "#11469E", "#7ED0FF", "#FFF6DB", "#B3DEFA"),
    GOLDEN("Golden", 0.28, 1.4, "#14357A", "#FF9E59", "#FFD584", "#EDB48A"),
    SUNSET("Sunset", 0.08, 1.65, "#231B54", "#FF5E5B", "#FFA852", "#D96F66"),
    NIGHT("Midnight", -0.18, 0.55, "#050B1C", "#13294B", "#9CC2FF", "#162642")
}

enum class QualityTier(val label: String, val steps: Float) {
    FAST_32BIT("32-Bit Fast", 14f),
    BALANCED("Balanced", 24f),
    ULTRA("Ultra RTX", 40f)
}

object ShaderDefaults {

    const val BASE_VOXEL_FRAGMENT_SHADER = """precision mediump float;

uniform vec2 uResolution;
uniform float uTime;
uniform float uYaw;
uniform float uPitch;
uniform float uCompareSplit;
uniform float uQualitySteps;

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

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 3; i++) {
        v += a * noise2(p);
        p *= 2.05;
        a *= 0.5;
    }
    return v;
}

float voxelTerrain(vec2 p) {
    vec2 cell = floor(p * 1.6) / 1.6;
    float valley = smoothstep(0.6, 4.2, abs(cell.x - sin(cell.y * 0.25) * 1.4));
    float hills = fbm(cell * 0.32) * 3.8;
    float rawH = valley * hills - 0.55;
    return floor(rawH * 3.0) / 3.0;
}

float mapScene(vec3 p) {
    return p.y - voxelTerrain(p.xz);
}

vec3 calcNormal(vec3 p) {
    vec2 e = vec2(0.04, 0.0);
    return normalize(vec3(
        mapScene(p + e.xyy) - mapScene(p - e.xyy),
        mapScene(p + e.yxy) - mapScene(p - e.yxy),
        mapScene(p + e.yyx) - mapScene(p - e.yyx)
    ));
}

float softShadow(vec3 ro, vec3 rd) {
    float res = 1.0;
    float t = 0.14;
    float k = mix(12.0, 3.0, clamp(uShadowSoftness * 0.5, 0.0, 1.0));
    float maxShadowSteps = clamp(floor(uQualitySteps * 0.35), 3.0, 14.0);
    for (int i = 0; i < 14; i++) {
        if (float(i) >= maxShadowSteps) break;
        float h = mapScene(ro + rd * t);
        res = min(res, k * max(h, 0.0) / t);
        t += clamp(h, 0.18, 0.85);
        if (res < 0.02 || t > 12.0) break;
    }
    return clamp(res, 0.0, 1.0);
}

vec3 renderSky(vec3 rd, vec3 sunDir) {
    float h = clamp(rd.y, 0.0, 1.0);
    vec3 sky = mix(uSkyHorizon, uSkyZenith, pow(h, 0.55));
    float sunDot = max(dot(rd, sunDir), 0.0);

    vec3 warmSun = mix(uSunLight, vec3(1.0, 0.62, 0.25), clamp(uSunWarmth - 0.8, 0.0, 0.6));
    sky += warmSun * pow(sunDot, 12.0) * 0.45 * uSunWarmth;
    sky += warmSun * smoothstep(0.996, 0.9994, sunDot) * 2.6 * uBloomIntensity;

    if (rd.y > 0.04) {
        vec2 cloudUv = rd.xz / (rd.y + 0.18) * 1.4 + vec2(uTime * 0.035, uTime * 0.015);
        float c = fbm(cloudUv);
        float cloudMask = smoothstep(0.48, 0.78, c) * smoothstep(0.04, 0.25, rd.y);
        vec3 cloudCol = mix(uAmbientTint * 0.85 + 0.25, warmSun * 1.25, pow(sunDot, 3.0) * 0.7 + 0.2);
        sky = mix(sky, cloudCol, cloudMask * 0.78);
    }
    return sky;
}

vec3 shadeTerrain(vec3 p, vec3 n, vec3 rd, vec3 sunDir, bool vanillaMode) {
    vec2 cell = floor(p.xz * 1.6);
    float checker = hash21(cell);

    vec3 grassCol = vec3(0.22, 0.56, 0.28) * (0.88 + 0.22 * checker);
    vec3 stoneCol = vec3(0.32, 0.35, 0.41) * (0.85 + 0.25 * checker);
    float topMask = smoothstep(0.55, 0.85, n.y);
    vec3 albedo = mix(stoneCol, grassCol, topMask);

    if (vanillaMode) {
        float flatDiff = 0.55 + 0.45 * max(dot(n, normalize(vec3(0.3, 0.8, 0.4))), 0.0);
        return albedo * flatDiff;
    }

    float oreSeed = hash21(cell + floor(p.y * 2.0));
    float isOre = step(0.91, oreSeed) * (1.0 - topMask) * step(0.05, p.y);
    vec3 emissive = mix(vec3(0.15, 0.92, 1.0), uSunLight, 0.25) * isOre * (1.2 + uBloomIntensity * 1.5);

    float diff = max(dot(n, sunDir), 0.0);
    float sh = softShadow(p + n * 0.03, sunDir);

    vec3 giBounce = (uSkyHorizon * 0.35 + grassCol * 0.25) * uGiBounceIntensity * (0.4 + 0.6 * clamp(-n.y + 0.5, 0.0, 1.0));
    vec3 direct = uSunLight * diff * sh * (1.1 + 0.4 * uSunWarmth);
    vec3 ambient = uAmbientTint * (0.35 + 0.35 * max(n.y, 0.0));

    vec3 hVec = normalize(sunDir - rd);
    float spec = pow(max(dot(n, hVec), 0.0), mix(18.0, 64.0, clamp(uPbrMetallicBoost * 0.5, 0.0, 1.0)));
    vec3 specular = uSunLight * spec * sh * 0.45 * uPbrMetallicBoost;

    return albedo * (direct + ambient + giBounce) + specular + emissive;
}

vec3 acesToneMap(vec3 x) {
    float a = 2.51;
    float b = 0.03;
    float c = 2.43;
    float d = 0.59;
    float e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / max(uResolution.xy, vec2(1.0));
    vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution.xy) / max(uResolution.y, 1.0);
    bool vanillaMode = (uCompareSplit > 0.01) && (screenUv.x < uCompareSplit);

    float yaw = uYaw + sin(uTime * 0.12) * 0.08;
    float pitch = clamp(uPitch, -0.35, 0.65);

    vec3 ro = vec3(0.0, 1.45, -4.6 + uTime * 0.18);
    vec3 forward = normalize(vec3(sin(yaw) * cos(pitch), sin(pitch) - 0.12, cos(yaw) * cos(pitch)));
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), forward));
    vec3 up = cross(forward, right);
    vec3 rd = normalize(forward + uv.x * right + uv.y * up);

    float elev = clamp(uSunElevation, -0.25, 1.35);
    vec3 sunDir = normalize(vec3(0.45, elev, 0.85));

    float t = 0.0;
    float hit = 0.0;
    float maxSteps = clamp(uQualitySteps, 12.0, 42.0);
    float minStep = mix(0.16, 0.08, clamp((uQualitySteps - 14.0) / 16.0, 0.0, 1.0));
    for (int i = 0; i < 42; i++) {
        if (float(i) >= maxSteps) break;
        vec3 p = ro + rd * t;
        float d = mapScene(p);
        if (d < 0.03) {
            hit = 1.0;
            break;
        }
        if (t > 24.0) break;
        t += max(d * 0.65, minStep);
    }

    float waterT = -ro.y / min(rd.y, -0.0001);
    bool hitWater = (rd.y < -0.001) && (waterT > 0.0) && (waterT < 24.0) && (hit < 0.5 || waterT < t);

    vec3 col = vanillaMode ? mix(vec3(0.68, 0.82, 0.96), vec3(0.38, 0.62, 0.92), clamp(rd.y, 0.0, 1.0)) : renderSky(rd, sunDir);

    if (hitWater) {
        vec3 wp = ro + rd * waterT;
        if (vanillaMode) {
            col = vec3(0.14, 0.42, 0.72);
        } else {
            float wave1 = sin(wp.x * 5.2 + uTime * 2.2) * cos(wp.z * 4.4 - uTime * 1.7);
            float wave2 = fbm(wp.xz * 2.8 + vec2(uTime * 0.45, -uTime * 0.3));
            vec3 wn = normalize(vec3((wave1 + wave2 - 0.5) * 0.09, 1.0, (wave2 - 0.5) * 0.09));

            vec3 reflDir = reflect(rd, wn);
            vec3 reflCol = renderSky(reflDir, sunDir);

            float fresnel = pow(1.0 - max(dot(-rd, wn), 0.0), 3.2);
            float reflWeight = clamp(mix(0.2, 0.96, fresnel) * uWaterReflectivity, 0.0, 1.0);
            float caustics = pow(noise2(wp.xz * 3.5 + uTime * 0.6), 3.0) * 0.35;
            vec3 waterBase = uWaterTint * (0.45 + 0.55 * max(sunDir.y, 0.1)) + uSunLight * caustics;

            vec3 hVec = normalize(sunDir - rd);
            float glitter = pow(max(dot(wn, hVec), 0.0), 96.0) * (1.2 + uBloomIntensity);

            col = mix(waterBase, reflCol, reflWeight) + uSunLight * glitter;
            float fogAmount = 1.0 - exp(-waterT * 0.045 * uFogDensity);
            col = mix(col, uFogTint, clamp(fogAmount, 0.0, 0.92));
        }
    } else if (hit > 0.5) {
        vec3 p = ro + rd * t;
        vec3 n = calcNormal(p);
        col = shadeTerrain(p, n, rd, sunDir, vanillaMode);
        if (!vanillaMode) {
            float fogAmount = 1.0 - exp(-t * 0.05 * uFogDensity);
            col = mix(col, uFogTint, clamp(fogAmount, 0.0, 0.92));
        }
    }

    if (!vanillaMode) {
        float sunAlign = max(dot(rd, sunDir), 0.0);
        float rayShaft = pow(sunAlign, 6.0) * (0.35 + 0.45 * noise2(vec2(atan(rd.y, rd.x) * 4.0, uTime * 0.25)));
        col += uSunLight * rayShaft * uGodRaysIntensity * 0.42;

        col *= uExposure;
        col += max(col - vec3(0.85), vec3(0.0)) * uBloomIntensity * 0.35;
        col = acesToneMap(col);

        float luma = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(vec3(luma), col, uSaturation);
    }

    if (uCompareSplit > 0.01 && abs(screenUv.x - uCompareSplit) < 0.004) {
        col = vec3(0.18, 0.90, 1.0);
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

    const val CARTOON_CEL_FRAGMENT_SHADER = """precision mediump float;

uniform vec2 uResolution;
uniform float uTime;
uniform float uYaw;
uniform float uPitch;
uniform float uCompareSplit;
uniform float uQualitySteps;

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

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x),
        mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x),
        f.y
    );
}

float voxelTerrain(vec2 p) {
    vec2 cell = floor(p * 1.5) / 1.5;
    float valley = smoothstep(0.6, 4.0, abs(cell.x - sin(cell.y * 0.25) * 1.3));
    float hills = (noise2(cell * 0.35) + 0.4 * noise2(cell * 0.7)) * 2.6;
    return floor((valley * hills - 0.5) * 3.0) / 3.0;
}

vec3 calcNormal(vec3 p) {
    vec2 e = vec2(0.045, 0.0);
    return normalize(vec3(
        (p.y - voxelTerrain(p.xz + e.xy)) - (p.y - voxelTerrain(p.xz - e.xy)),
        0.09,
        (p.y - voxelTerrain(p.xz + e.yx)) - (p.y - voxelTerrain(p.xz - e.yx))
    ));
}

void main() {
    vec2 screenUv = gl_FragCoord.xy / max(uResolution.xy, vec2(1.0));
    vec2 uv = (gl_FragCoord.xy - 0.5 * uResolution.xy) / max(uResolution.y, 1.0);
    bool vanillaMode = (uCompareSplit > 0.01) && (screenUv.x < uCompareSplit);

    float yaw = uYaw + sin(uTime * 0.12) * 0.08;
    float pitch = clamp(uPitch, -0.35, 0.65);

    vec3 ro = vec3(0.0, 1.45, -4.6 + uTime * 0.18);
    vec3 forward = normalize(vec3(sin(yaw) * cos(pitch), sin(pitch) - 0.12, cos(yaw) * cos(pitch)));
    vec3 right = normalize(cross(vec3(0.0, 1.0, 0.0), forward));
    vec3 up = cross(forward, right);
    vec3 rd = normalize(forward + uv.x * right + uv.y * up);

    vec3 sunDir = normalize(vec3(0.45, clamp(uSunElevation, -0.25, 1.35), 0.85));

    float t = 0.0;
    float hit = 0.0;
    float maxSteps = clamp(uQualitySteps, 12.0, 42.0);
    float minStep = mix(0.16, 0.09, clamp((uQualitySteps - 14.0) / 16.0, 0.0, 1.0));
    for (int i = 0; i < 42; i++) {
        if (float(i) >= maxSteps) break;
        vec3 p = ro + rd * t;
        float d = p.y - voxelTerrain(p.xz);
        if (d < 0.035) {
            hit = 1.0;
            break;
        }
        if (t > 24.0) break;
        t += max(d * 0.68, minStep);
    }

    float waterT = -ro.y / min(rd.y, -0.0001);
    bool hitWater = (rd.y < -0.001) && (waterT > 0.0) && (waterT < 24.0) && (hit < 0.5 || waterT < t);

    // Stylized banded cartoon sky
    float skyBand = floor(clamp(rd.y, 0.0, 1.0) * 5.0) / 5.0;
    vec3 col = mix(uSkyHorizon, uSkyZenith, skyBand);
    float sunDot = max(dot(rd, sunDir), 0.0);
    if (sunDot > 0.992) col = uSunLight * 1.3;

    if (vanillaMode) {
        col = mix(vec3(0.68, 0.82, 0.96), vec3(0.38, 0.62, 0.92), clamp(rd.y, 0.0, 1.0));
    }

    if (hitWater) {
        vec3 wp = ro + rd * waterT;
        float foam = step(0.76, sin(wp.x * 6.0 + uTime * 2.5) * cos(wp.z * 5.0 - uTime * 2.0));
        col = vanillaMode ? vec3(0.14, 0.42, 0.72) : mix(uWaterTint, vec3(0.92, 0.98, 1.0), foam * 0.65);
    } else if (hit > 0.5) {
        vec3 p = ro + rd * t;
        vec3 n = calcNormal(p);
        float topMask = smoothstep(0.5, 0.8, n.y);
        vec3 albedo = mix(vec3(0.36, 0.38, 0.46), vec3(0.24, 0.74, 0.34), topMask);
        if (vanillaMode) {
            col = albedo * (0.55 + 0.45 * max(dot(n, sunDir), 0.0));
        } else {
            // 3-step toon cel shading + ink edge rim
            float rawDiff = max(dot(n, sunDir), 0.0);
            float celDiff = floor(rawDiff * 3.0 + 0.5) / 3.0;
            float rim = 1.0 - max(dot(-rd, n), 0.0);
            float inkEdge = step(0.68, rim);
            col = albedo * (uAmbientTint * 0.45 + uSunLight * (0.45 + 0.85 * celDiff));
            col = mix(col, vec3(0.06, 0.08, 0.14), inkEdge * 0.72);
        }
    }

    if (!vanillaMode) {
        float fogBand = floor((1.0 - exp(-t * 0.04 * uFogDensity)) * 4.0) / 4.0;
        col = mix(col, uFogTint, clamp(fogBand, 0.0, 0.8));
        col *= uExposure;
        float luma = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(vec3(luma), col, uSaturation);
    }

    if (uCompareSplit > 0.01 && abs(screenUv.x - uCompareSplit) < 0.004) {
        col = vec3(0.18, 0.90, 1.0);
    }

    gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""

    val starterVibrantVisuals = ShaderPreset(
        title = "Vibrant Visuals Golden Hour",
        prompt = "Vibrant Visuals shader with golden hour volumetric god rays, crisp PBR specular highlights, and reflective turquoise water",
        presetCategory = ShaderCategory.VIBRANT_VISUALS.name,
        targetPlatform = TargetPlatform.BEDROCK_VIBRANT.name,
        summary = "Golden hour Mie scattering, soft penumbra shadows, Fresnel turquoise water reflections, and ACES Vibrant Visuals tone grading.",
        fragmentShader = BASE_VOXEL_FRAGMENT_SHADER,
        sunWarmth = 1.35,
        sunElevation = 0.28,
        fogDensity = 0.42,
        godRaysIntensity = 1.35,
        waterReflectivity = 1.05,
        giBounceIntensity = 1.15,
        bloomIntensity = 0.95,
        saturation = 1.32,
        exposure = 1.18,
        shadowSoftness = 0.85,
        pbrMetallicBoost = 1.15,
        skyZenithHex = "#14357A",
        skyHorizonHex = "#FF9E59",
        sunLightHex = "#FFD584",
        ambientTintHex = "#3E5D9E",
        waterTintHex = "#157B96",
        fogTintHex = "#EDB48A"
    )

    val starterRtxRayTraced = ShaderPreset(
        title = "RTX Path-Traced Prism",
        prompt = "RTX ray-tracing shader with intense global illumination bounce, mirror-like water reflections, metallic PBR specular, and glowing emissive crystal ores",
        presetCategory = ShaderCategory.RTX_RAYTRACED.name,
        targetPlatform = TargetPlatform.BEDROCK_VIBRANT.name,
        summary = "High-contrast RTX-inspired GI bounce, ultra-reflective water surface, heightened PBR metallic specular, and luminous bloom.",
        fragmentShader = BASE_VOXEL_FRAGMENT_SHADER,
        sunWarmth = 1.15,
        sunElevation = 0.48,
        fogDensity = 0.28,
        godRaysIntensity = 1.1,
        waterReflectivity = 1.35,
        giBounceIntensity = 1.65,
        bloomIntensity = 1.35,
        saturation = 1.25,
        exposure = 1.22,
        shadowSoftness = 0.45,
        pbrMetallicBoost = 1.65,
        skyZenithHex = "#0B1D47",
        skyHorizonHex = "#62C2FF",
        sunLightHex = "#FFF1D0",
        ambientTintHex = "#2B4A7D",
        waterTintHex = "#0D5478",
        fogTintHex = "#7FAEE0"
    )

    val starterCartoon = ShaderPreset(
        title = "Cartoon Cel-Shaded Toon",
        prompt = "Stylized cartoon cel-shaded shader with stepped comic lighting bands, crisp dark ink outlines, bright vibrant grass, and foamy stylized water",
        presetCategory = ShaderCategory.STYLIZED.name,
        targetPlatform = TargetPlatform.JAVA_OPTIFINE.name,
        summary = "3-band toon cel quantization, crisp edge ink silhouettes, banded comic sky, and bold saturated palette.",
        fragmentShader = CARTOON_CEL_FRAGMENT_SHADER,
        sunWarmth = 1.1,
        sunElevation = 0.55,
        fogDensity = 0.2,
        godRaysIntensity = 0.4,
        waterReflectivity = 0.5,
        giBounceIntensity = 0.9,
        bloomIntensity = 0.6,
        saturation = 1.55,
        exposure = 1.1,
        shadowSoftness = 0.1,
        pbrMetallicBoost = 0.5,
        skyZenithHex = "#1B75FF",
        skyHorizonHex = "#8EE8FF",
        sunLightHex = "#FFF275",
        ambientTintHex = "#4C79B8",
        waterTintHex = "#18B4D4",
        fogTintHex = "#B5F0FF"
    )

    val starter32BitBoost = ShaderPreset(
        title = "32-Bit Potato Vibrant Boost",
        prompt = "Lightweight vibrant shader tuned for 32-bit mobile GPUs with warm sunrise lighting, clean contrast, and minimal fog overhead",
        presetCategory = ShaderCategory.PERFORMANCE_32BIT.name,
        targetPlatform = TargetPlatform.BEDROCK_VIBRANT.name,
        summary = "Balanced exposure and crisp warm shadows designed for smooth frame rates on 32-bit mobile hardware.",
        fragmentShader = BASE_VOXEL_FRAGMENT_SHADER,
        sunWarmth = 1.2,
        sunElevation = 0.42,
        fogDensity = 0.22,
        godRaysIntensity = 0.65,
        waterReflectivity = 0.78,
        giBounceIntensity = 0.75,
        bloomIntensity = 0.55,
        saturation = 1.28,
        exposure = 1.12,
        shadowSoftness = 0.4,
        pbrMetallicBoost = 0.85,
        skyZenithHex = "#1E4B94",
        skyHorizonHex = "#FFB56B",
        sunLightHex = "#FFE3A3",
        ambientTintHex = "#48689C",
        waterTintHex = "#1E859E",
        fogTintHex = "#F0C39C"
    )

    val starterNetherRtx = ShaderPreset(
        title = "Nether Crimson RTX",
        prompt = "Dramatic Nether crimson atmospheric RTX shader with dense scarlet volumetric fog, magma orange rim lighting, and glowing obsidian reflections",
        presetCategory = ShaderCategory.ATMOSPHERIC.name,
        targetPlatform = TargetPlatform.BEDROCK_VIBRANT.name,
        summary = "Dense volcanic scarlet scattering, warm magma bounce GI, and high-bloom obsidian specular.",
        fragmentShader = BASE_VOXEL_FRAGMENT_SHADER,
        sunWarmth = 1.85,
        sunElevation = 0.18,
        fogDensity = 0.95,
        godRaysIntensity = 1.45,
        waterReflectivity = 0.85,
        giBounceIntensity = 1.5,
        bloomIntensity = 1.55,
        saturation = 1.4,
        exposure = 1.1,
        shadowSoftness = 0.7,
        pbrMetallicBoost = 1.5,
        skyZenithHex = "#24060E",
        skyHorizonHex = "#BA241E",
        sunLightHex = "#FF7E38",
        ambientTintHex = "#591A22",
        waterTintHex = "#9E2A1C",
        fogTintHex = "#7A1C1F"
    )

    val starterAuroraNight = ShaderPreset(
        title = "Aurora Midnight Reflections",
        prompt = "Midnight aurora borealis shader with emerald and violet sky glow, moonlit silver water reflections, and soft mist",
        presetCategory = ShaderCategory.ATMOSPHERIC.name,
        targetPlatform = TargetPlatform.BEDROCK_VIBRANT.name,
        summary = "Cool nocturnal sky gradient, silver lunar reflections on calm water, and luminous crystal accents.",
        fragmentShader = BASE_VOXEL_FRAGMENT_SHADER,
        sunWarmth = 0.55,
        sunElevation = 0.22,
        fogDensity = 0.5,
        godRaysIntensity = 0.85,
        waterReflectivity = 1.25,
        giBounceIntensity = 1.2,
        bloomIntensity = 1.25,
        saturation = 1.35,
        exposure = 1.08,
        shadowSoftness = 1.1,
        pbrMetallicBoost = 1.2,
        skyZenithHex = "#080E29",
        skyHorizonHex = "#22C79D",
        sunLightHex = "#AEE3FF",
        ambientTintHex = "#223A66",
        waterTintHex = "#0F405C",
        fogTintHex = "#1E5B69"
    )

    val starterCyberNeon = ShaderPreset(
        title = "Cyberpunk Neon Ray-Trace",
        prompt = "Cyberpunk neon ray-tracing shader with magenta and cyan horizon, wet glossy metallic reflections, and high emissive crystal bloom",
        presetCategory = ShaderCategory.STYLIZED.name,
        targetPlatform = TargetPlatform.UNIVERSAL_GLSL.name,
        summary = "Synthwave neon horizon dispersion, wet mirror reflections, and glowing cyan crystal highlights.",
        fragmentShader = BASE_VOXEL_FRAGMENT_SHADER,
        sunWarmth = 1.0,
        sunElevation = 0.12,
        fogDensity = 0.45,
        godRaysIntensity = 1.25,
        waterReflectivity = 1.4,
        giBounceIntensity = 1.55,
        bloomIntensity = 1.65,
        saturation = 1.55,
        exposure = 1.18,
        shadowSoftness = 0.5,
        pbrMetallicBoost = 1.85,
        skyZenithHex = "#1B0B3B",
        skyHorizonHex = "#FF47B9",
        sunLightHex = "#47E8FF",
        ambientTintHex = "#482678",
        waterTintHex = "#17255C",
        fogTintHex = "#8A3380"
    )

    val starterPastelDream = ShaderPreset(
        title = "Pastel Sakura Morning",
        prompt = "Soft pastel morning shader with sakura pink horizon, warm creamy sunlight, gentle fog mist, and dreamy water shimmer",
        presetCategory = ShaderCategory.VIBRANT_VISUALS.name,
        targetPlatform = TargetPlatform.BEDROCK_VIBRANT.name,
        summary = "Soft sakura sky gradient, gentle atmospheric haze, and dreamy pastel ACES color grading.",
        fragmentShader = BASE_VOXEL_FRAGMENT_SHADER,
        sunWarmth = 1.25,
        sunElevation = 0.36,
        fogDensity = 0.52,
        godRaysIntensity = 1.05,
        waterReflectivity = 0.95,
        giBounceIntensity = 1.2,
        bloomIntensity = 1.0,
        saturation = 1.18,
        exposure = 1.24,
        shadowSoftness = 1.25,
        pbrMetallicBoost = 0.85,
        skyZenithHex = "#4E6EC2",
        skyHorizonHex = "#FFB8D0",
        sunLightHex = "#FFF0CE",
        ambientTintHex = "#7A7AB8",
        waterTintHex = "#3A94B5",
        fogTintHex = "#F5C6D6"
    )

    val presetSelectionGrid: List<PresetGridItem> = listOf(
        PresetGridItem(
            id = "vibrant",
            label = "Vibrant",
            badge = "God Rays",
            accentHex = "#FF9E59",
            secondaryHex = "#14357A",
            preset = starterVibrantVisuals
        ),
        PresetGridItem(
            id = "ray_tracing",
            label = "Ray Tracing",
            badge = "RTX GI",
            accentHex = "#2EE6FF",
            secondaryHex = "#0B1D47",
            preset = starterRtxRayTraced
        ),
        PresetGridItem(
            id = "cartoon",
            label = "Cartoon",
            badge = "Cel Toon",
            accentHex = "#FFF275",
            secondaryHex = "#1B75FF",
            preset = starterCartoon
        ),
        PresetGridItem(
            id = "32bit_boost",
            label = "32-Bit Fast",
            badge = "High FPS",
            accentHex = "#3CE89E",
            secondaryHex = "#1E4B94",
            preset = starter32BitBoost
        ),
        PresetGridItem(
            id = "nether_rtx",
            label = "Nether RTX",
            badge = "Magma Fog",
            accentHex = "#FF7E38",
            secondaryHex = "#591A22",
            preset = starterNetherRtx
        ),
        PresetGridItem(
            id = "aurora_night",
            label = "Aurora Night",
            badge = "Moonlit",
            accentHex = "#22C79D",
            secondaryHex = "#080E29",
            preset = starterAuroraNight
        ),
        PresetGridItem(
            id = "cyber_neon",
            label = "Cyber Neon",
            badge = "Synthwave",
            accentHex = "#FF47B9",
            secondaryHex = "#1B0B3B",
            preset = starterCyberNeon
        ),
        PresetGridItem(
            id = "pastel_dream",
            label = "Pastel Dream",
            badge = "Sakura",
            accentHex = "#FFB8D0",
            secondaryHex = "#4E6EC2",
            preset = starterPastelDream
        )
    )

    val promptModifiers: List<Pair<String, String>> = listOf(
        "+ Volumetric Fog" to "with dense atmospheric volumetric fog",
        "+ Mirror Water" to "with ultra-reflective mirror Fresnel water caustics",
        "+ Lens Bloom" to "with radiant emissive crystal and sun lens bloom",
        "+ Soft Shadows" to "with smooth penumbra ray-marched shadows",
        "+ 32-Bit Lite" to "optimized for smooth 32-bit mobile performance"
    )
}
