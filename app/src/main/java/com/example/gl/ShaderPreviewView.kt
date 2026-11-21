package com.example.gl

import android.graphics.Bitmap
import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.model.ShaderPreset

@Composable
fun ShaderPreviewView(
    preset: ShaderPreset,
    isAnimationPlaying: Boolean,
    compareSplit: Float,
    qualitySteps: Float,
    snapshotRequestNonce: Int,
    onSnapshotCaptured: (Bitmap) -> Unit,
    onCompileStatus: (Boolean, String) -> Unit,
    onFpsUpdated: (Int) -> Unit,
    onSoftwareGpuDetected: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val renderer = remember {
        ShaderPreviewRenderer(
            onCompileStatus = onCompileStatus,
            onFpsUpdated = onFpsUpdated,
            onSoftwareGpuDetected = onSoftwareGpuDetected
        )
    }

    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
    }

    LaunchedEffect(preset, preset.fragmentShader) {
        renderer.updatePreset(preset, recompileIfChanged = true)
    }

    LaunchedEffect(isAnimationPlaying) {
        renderer.isPausedTime = !isAnimationPlaying
    }

    LaunchedEffect(compareSplit) {
        renderer.compareSplit = compareSplit
    }

    LaunchedEffect(qualitySteps) {
        renderer.qualitySteps = qualitySteps
    }

    LaunchedEffect(snapshotRequestNonce) {
        if (snapshotRequestNonce > 0) {
            renderer.requestSnapshot(onSnapshotCaptured)
        }
    }

    DisposableEffect(lifecycleOwner, glSurfaceView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> glSurfaceView.onResume()
                Lifecycle.Event.ON_PAUSE -> glSurfaceView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            glSurfaceView.onPause()
        }
    }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                renderer.yaw += dragAmount.x * 0.0065f
                renderer.pitch = (renderer.pitch - dragAmount.y * 0.0045f).coerceIn(-0.35f, 0.65f)
            }
        }
    ) {
        AndroidView(
            factory = { glSurfaceView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
