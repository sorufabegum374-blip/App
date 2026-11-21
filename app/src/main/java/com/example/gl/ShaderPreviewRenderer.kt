package com.example.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.data.ShaderDefaults
import com.example.model.ShaderPreset
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ShaderPreviewRenderer(
    private val onCompileStatus: (Boolean, String) -> Unit,
    private val onFpsUpdated: (Int) -> Unit,
    private val onSoftwareGpuDetected: () -> Unit = {}
) : GLSurfaceView.Renderer {

    @Volatile
    private var activePreset: ShaderPreset = ShaderDefaults.starterVibrantVisuals

    @Volatile
    private var pendingFragmentCode: String? = ShaderDefaults.BASE_VOXEL_FRAGMENT_SHADER

    @Volatile
    var yaw: Float = 0.0f

    @Volatile
    var pitch: Float = 0.08f

    @Volatile
    var isPausedTime: Boolean = false

    @Volatile
    var compareSplit: Float = 0.0f

    @Volatile
    var qualitySteps: Float = 42.0f

    @Volatile
    private var pendingSnapshotCallback: ((Bitmap) -> Unit)? = null

    private var programId: Int = 0
    private var width: Int = 1
    private var height: Int = 1
    private var startTimeMs: Long = SystemClock.uptimeMillis()
    private var lastFpsTimeMs: Long = SystemClock.uptimeMillis()
    private var frameCount: Int = 0
    private val mainHandler = Handler(Looper.getMainLooper())

    private val quadVertices: FloatBuffer = ByteBuffer
        .allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
            position(0)
        }

    fun updatePreset(preset: ShaderPreset, recompileIfChanged: Boolean) {
        val prevCode = activePreset.fragmentShader
        activePreset = preset
        if (recompileIfChanged && preset.fragmentShader != prevCode) {
            pendingFragmentCode = preset.fragmentShader
        }
    }

    fun requestSnapshot(onCaptured: (Bitmap) -> Unit) {
        pendingSnapshotCallback = onCaptured
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.03f, 0.04f, 0.07f, 1f)
        startTimeMs = SystemClock.uptimeMillis()
        val gpuRenderer = GLES20.glGetString(GLES20.GL_RENDERER).orEmpty()
        if (
            gpuRenderer.contains("SwiftShader", ignoreCase = true) ||
            gpuRenderer.contains("Emulator", ignoreCase = true) ||
            gpuRenderer.contains("llvmpipe", ignoreCase = true) ||
            gpuRenderer.contains("Android", ignoreCase = true)
        ) {
            mainHandler.post { onSoftwareGpuDetected() }
        }
        pendingFragmentCode = activePreset.fragmentShader.ifBlank {
            ShaderDefaults.BASE_VOXEL_FRAGMENT_SHADER
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this.width = width.coerceAtLeast(1)
        this.height = height.coerceAtLeast(1)
        GLES20.glViewport(0, 0, this.width, this.height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val codeToCompile = pendingFragmentCode
        if (codeToCompile != null) {
            pendingFragmentCode = null
            compileProgramWithFallback(codeToCompile)
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (programId == 0) return

        GLES20.glUseProgram(programId)

        val posHandle = GLES20.glGetAttribLocation(programId, "aPosition")
        if (posHandle >= 0) {
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, quadVertices)
        }

        val elapsedSec = if (isPausedTime) 2.5f else (SystemClock.uptimeMillis() - startTimeMs) / 1000f
        val p = activePreset

        setUniform2f("uResolution", width.toFloat(), height.toFloat())
        setUniform1f("uTime", elapsedSec)
        setUniform1f("uYaw", yaw)
        setUniform1f("uPitch", pitch)
        setUniform1f("uCompareSplit", compareSplit)
        setUniform1f("uQualitySteps", qualitySteps)

        setUniform1f("uSunWarmth", p.sunWarmth.toFloat())
        setUniform1f("uSunElevation", p.sunElevation.toFloat())
        setUniform1f("uFogDensity", p.fogDensity.toFloat())
        setUniform1f("uGodRaysIntensity", p.godRaysIntensity.toFloat())
        setUniform1f("uWaterReflectivity", p.waterReflectivity.toFloat())
        setUniform1f("uGiBounceIntensity", p.giBounceIntensity.toFloat())
        setUniform1f("uBloomIntensity", p.bloomIntensity.toFloat())
        setUniform1f("uSaturation", p.saturation.toFloat())
        setUniform1f("uExposure", p.exposure.toFloat())
        setUniform1f("uShadowSoftness", p.shadowSoftness.toFloat())
        setUniform1f("uPbrMetallicBoost", p.pbrMetallicBoost.toFloat())

        setUniformHex3f("uSkyZenith", p.skyZenithHex)
        setUniformHex3f("uSkyHorizon", p.skyHorizonHex)
        setUniformHex3f("uSunLight", p.sunLightHex)
        setUniformHex3f("uAmbientTint", p.ambientTintHex)
        setUniformHex3f("uWaterTint", p.waterTintHex)
        setUniformHex3f("uFogTint", p.fogTintHex)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        if (posHandle >= 0) {
            GLES20.glDisableVertexAttribArray(posHandle)
        }

        val snapshotCb = pendingSnapshotCallback
        if (snapshotCb != null && width > 1 && height > 1) {
            pendingSnapshotCallback = null
            captureGlFrame()?.let { bitmap ->
                mainHandler.post { snapshotCb(bitmap) }
            }
        }

        frameCount++
        val now = SystemClock.uptimeMillis()
        if (now - lastFpsTimeMs >= 500L) {
            val fps = ((frameCount * 1000L) / (now - lastFpsTimeMs).coerceAtLeast(1L)).toInt()
            frameCount = 0
            lastFpsTimeMs = now
            mainHandler.post { onFpsUpdated(fps) }
        }

        // Gentle pacing in 32-Bit Fast mode prevents software SwiftShader / low-end GPUs from saturating CPU
        if (qualitySteps <= 16.0f) {
            try {
                Thread.sleep(24L)
            } catch (_: InterruptedException) {
                // Ignore interrupt during surface teardown
            }
        }
    }

    private fun captureGlFrame(): Bitmap? {
        return try {
            val w = width
            val h = height
            val raw = IntArray(w * h)
            val flipped = IntArray(w * h)
            val buffer = IntBuffer.wrap(raw)
            buffer.position(0)
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            for (y in 0 until h) {
                val srcOffset = y * w
                val dstOffset = (h - 1 - y) * w
                for (x in 0 until w) {
                    val abgr = raw[srcOffset + x]
                    // Convert ABGR (OpenGL RGBA bytes in little-endian int) to ARGB for Android Bitmap
                    val a = abgr and -0x1000000
                    val b = (abgr and 0x00FF0000) shr 16
                    val g = abgr and 0x0000FF00
                    val r = (abgr and 0x000000FF) shl 16
                    flipped[dstOffset + x] = a or r or g or b
                }
            }
            Bitmap.createBitmap(flipped, w, h, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
            null
        }
    }

    private fun compileProgramWithFallback(fragmentSource: String) {
        val result = buildProgram(VERTEX_SHADER, fragmentSource)
        if (result.programId != 0) {
            if (programId != 0) GLES20.glDeleteProgram(programId)
            programId = result.programId
            mainHandler.post { onCompileStatus(true, "GLSL compiled cleanly") }
        } else {
            val fallback = buildProgram(VERTEX_SHADER, ShaderDefaults.BASE_VOXEL_FRAGMENT_SHADER)
            if (fallback.programId != 0) {
                if (programId != 0) GLES20.glDeleteProgram(programId)
                programId = fallback.programId
            }
            val log = result.errorLog.ifBlank { "GLSL compilation error; rendered optical fallback." }
            mainHandler.post { onCompileStatus(false, log) }
        }
    }

    private data class ProgramResult(val programId: Int, val errorLog: String)

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): ProgramResult {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        if (vs.first == 0) return ProgramResult(0, vs.second)

        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        if (fs.first == 0) {
            GLES20.glDeleteShader(vs.first)
            return ProgramResult(0, fs.second)
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            GLES20.glDeleteShader(vs.first)
            GLES20.glDeleteShader(fs.first)
            return ProgramResult(0, "Unable to allocate GL program")
        }

        GLES20.glAttachShader(program, vs.first)
        GLES20.glAttachShader(program, fs.first)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        GLES20.glDeleteShader(vs.first)
        GLES20.glDeleteShader(fs.first)

        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            return ProgramResult(0, log)
        }
        return ProgramResult(program, "")
    }

    private fun compileShader(type: Int, source: String): Pair<Int, String> {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0 to "glCreateShader failed"
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        return if (compiled[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            0 to log
        } else {
            shader to ""
        }
    }

    private fun setUniform1f(name: String, value: Float) {
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc >= 0) GLES20.glUniform1f(loc, value)
    }

    private fun setUniform2f(name: String, x: Float, y: Float) {
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc >= 0) GLES20.glUniform2f(loc, x, y)
    }

    private fun setUniformHex3f(name: String, hex: String) {
        val loc = GLES20.glGetUniformLocation(programId, name)
        if (loc < 0) return
        val clean = hex.removePrefix("#")
        if (clean.length != 6) {
            GLES20.glUniform3f(loc, 1f, 1f, 1f)
            return
        }
        val r = (clean.substring(0, 2).toIntOrNull(16) ?: 255) / 255f
        val g = (clean.substring(2, 4).toIntOrNull(16) ?: 255) / 255f
        val b = (clean.substring(4, 6).toIntOrNull(16) ?: 255) / 255f
        GLES20.glUniform3f(loc, r, g, b)
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """
    }
}
