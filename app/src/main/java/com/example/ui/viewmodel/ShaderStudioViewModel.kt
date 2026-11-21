package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.QualityTier
import com.example.data.ShaderAiRepository
import com.example.data.ShaderDefaults
import com.example.data.ShaderRepository
import com.example.data.TimeOfDayPreset
import com.example.model.ShaderPreset
import com.example.model.TargetPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(val shaders: List<ShaderPreset>) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

sealed interface GenerationUiState {
    data object Idle : GenerationUiState
    data object Generating : GenerationUiState
    data class Error(val message: String) : GenerationUiState
}

data class CompileStatus(
    val isSuccess: Boolean = true,
    val log: String = "GLSL compiled cleanly"
)

class ShaderStudioViewModel(
    private val repository: ShaderRepository,
    private val aiRepository: ShaderAiRepository
) : ViewModel() {

    val libraryState: StateFlow<LibraryUiState> = repository.observeShaders()
        .map<List<ShaderPreset>, LibraryUiState> { LibraryUiState.Success(it) }
        .catch { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Error observing local shader vault", error)
            emit(LibraryUiState.Error(error.message ?: "Failed to load saved shaders."))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = LibraryUiState.Loading
        )

    private val _activeShader = MutableStateFlow(ShaderDefaults.starterVibrantVisuals)
    val activeShader: StateFlow<ShaderPreset> = _activeShader.asStateFlow()

    private val _generationState = MutableStateFlow<GenerationUiState>(GenerationUiState.Idle)
    val generationState: StateFlow<GenerationUiState> = _generationState.asStateFlow()

    private val _compileStatus = MutableStateFlow(CompileStatus())
    val compileStatus: StateFlow<CompileStatus> = _compileStatus.asStateFlow()

    private val _fps = MutableStateFlow(60)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _compareMode = MutableStateFlow(false)
    val compareMode: StateFlow<Boolean> = _compareMode.asStateFlow()

    private val _qualityTier = MutableStateFlow(QualityTier.BALANCED)
    val qualityTier: StateFlow<QualityTier> = _qualityTier.asStateFlow()

    private var hasAutoAdjustedGpu = false

    private val _capturedSnapshot = MutableStateFlow<Bitmap?>(null)
    val capturedSnapshot: StateFlow<Bitmap?> = _capturedSnapshot.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun generateShader(
        prompt: String,
        targetPlatform: TargetPlatform,
        refineExisting: Boolean
    ) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) {
            _generationState.value = GenerationUiState.Error("Enter a shader prompt first.")
            return
        }

        viewModelScope.launch {
            _generationState.value = GenerationUiState.Generating
            try {
                val base = if (refineExisting) _activeShader.value else null
                val generated = aiRepository.generateShader(
                    userPrompt = cleanPrompt,
                    targetPlatform = targetPlatform,
                    existingShader = base
                )
                _activeShader.value = generated
                _generationState.value = GenerationUiState.Idle
                _snackbarMessage.value = "Forged \"${generated.title}\" live on GPU!"
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Shader generation timed out", e)
                _generationState.value = GenerationUiState.Error(
                    "AI generation timed out after 60 seconds. Try a shorter prompt or retry."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Shader generation failed", e)
                _generationState.value = GenerationUiState.Error(
                    e.localizedMessage ?: "Failed to generate shader with Gemini AI."
                )
            }
        }
    }

    fun applyStarterPreset(preset: ShaderPreset) {
        _activeShader.value = preset
        _generationState.value = GenerationUiState.Idle
    }

    fun applyTimeOfDay(tod: TimeOfDayPreset) {
        _activeShader.value = _activeShader.value.copy(
            sunElevation = tod.elevation,
            sunWarmth = tod.warmth,
            skyZenithHex = tod.zenithHex,
            skyHorizonHex = tod.horizonHex,
            sunLightHex = tod.sunHex,
            fogTintHex = tod.fogHex
        )
    }

    fun toggleCompareMode() {
        _compareMode.value = !_compareMode.value
    }

    fun cycleQualityTier() {
        val entries = QualityTier.entries
        val next = entries[(_qualityTier.value.ordinal + 1) % entries.size]
        _qualityTier.value = next
        _snackbarMessage.value = "Viewport render mode: ${next.label}"
    }

    fun onSoftwareGpuDetected() {
        if (!hasAutoAdjustedGpu) {
            hasAutoAdjustedGpu = true
            _qualityTier.value = QualityTier.FAST_32BIT
        }
    }

    fun onSnapshotCaptured(bitmap: Bitmap) {
        _capturedSnapshot.value = bitmap
    }

    fun dismissSnapshot() {
        _capturedSnapshot.value = null
    }

    fun loadSavedPreset(preset: ShaderPreset) {
        _activeShader.value = preset
        _generationState.value = GenerationUiState.Idle
        _snackbarMessage.value = "Loaded \"${preset.title}\" into Forge."
    }

    fun updateActiveShader(transform: (ShaderPreset) -> ShaderPreset) {
        _activeShader.value = transform(_activeShader.value)
    }

    fun saveActiveShader() {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveShader(_activeShader.value)
            _isSaving.value = false
            result.onSuccess { savedId ->
                _activeShader.value = _activeShader.value.copy(id = savedId)
                _snackbarMessage.value = "Saved \"${_activeShader.value.title}\" to Vault."
            }.onFailure { error ->
                _snackbarMessage.value = error.localizedMessage ?: "Failed to save shader."
            }
        }
    }

    fun seedStarterPresetsToVault() {
        viewModelScope.launch {
            var count = 0
            ShaderDefaults.presetSelectionGrid.forEach { item ->
                val result = repository.saveShader(item.preset)
                if (result.isSuccess) count++
            }
            _snackbarMessage.value = "Added $count starter shaders to your Vault!"
        }
    }

    fun toggleFavorite(preset: ShaderPreset) {
        if (preset.id.isBlank()) {
            _activeShader.value = _activeShader.value.copy(favorite = !preset.favorite)
            return
        }
        viewModelScope.launch {
            val result = repository.toggleFavorite(preset.id, !preset.favorite)
            result.onFailure { error ->
                _snackbarMessage.value = error.localizedMessage ?: "Failed to update favorite."
            }
        }
    }

    fun deleteShader(shaderId: String) {
        viewModelScope.launch {
            val result = repository.deleteShader(shaderId)
            result.onSuccess {
                if (_activeShader.value.id == shaderId) {
                    _activeShader.value = _activeShader.value.copy(id = "")
                }
                _snackbarMessage.value = "Deleted shader from Vault."
            }.onFailure { error ->
                _snackbarMessage.value = error.localizedMessage ?: "Failed to delete shader."
            }
        }
    }

    fun updateCompileStatus(success: Boolean, log: String) {
        _compileStatus.value = CompileStatus(success, log)
    }

    fun updateFps(newFps: Int) {
        _fps.value = newFps
    }

    fun clearGenerationError() {
        if (_generationState.value is GenerationUiState.Error) {
            _generationState.value = GenerationUiState.Idle
        }
    }

    fun showMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeMessage() {
        _snackbarMessage.value = null
    }

    fun resetToDefaultGlsl() {
        _activeShader.value = _activeShader.value.copy(
            fragmentShader = ShaderDefaults.BASE_VOXEL_FRAGMENT_SHADER
        )
        _snackbarMessage.value = "Restored built-in Ray-Marched Voxel GLSL kernel."
    }

    companion object {
        private const val TAG = "ShaderStudioVM"
    }
}
