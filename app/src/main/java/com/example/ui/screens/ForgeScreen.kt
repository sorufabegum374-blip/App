package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.QualityTier
import com.example.data.ShaderDefaults
import com.example.data.TimeOfDayPreset
import com.example.gl.ShaderPreviewView
import com.example.model.ShaderPreset
import com.example.model.TargetPlatform
import com.example.ui.components.EditableColorSlot
import com.example.ui.components.InteractiveColorPaletteBar
import com.example.ui.components.PresetSelectionGrid
import com.example.ui.components.RgbHexColorPickerDialog
import com.example.ui.components.ShaderSlider
import com.example.ui.components.SnapshotPreviewDialog
import com.example.ui.components.TimeOfDayBar
import com.example.ui.components.ViewportCard
import com.example.ui.components.ViewportHudOverlay
import com.example.ui.viewmodel.CompileStatus
import com.example.ui.viewmodel.GenerationUiState

@Composable
fun ForgeScreen(
    activeShader: ShaderPreset,
    generationState: GenerationUiState,
    compileStatus: CompileStatus,
    fps: Int,
    compareMode: Boolean,
    qualityTier: QualityTier,
    capturedSnapshot: Bitmap?,
    isSaving: Boolean,
    onGenerateShader: (prompt: String, platform: TargetPlatform, refineExisting: Boolean) -> Unit,
    onUpdateShader: ((ShaderPreset) -> ShaderPreset) -> Unit,
    onApplyStarter: (ShaderPreset) -> Unit,
    onApplyTimeOfDay: (TimeOfDayPreset) -> Unit,
    onToggleCompare: () -> Unit,
    onCycleQualityTier: () -> Unit,
    onSnapshotCaptured: (Bitmap) -> Unit,
    onDismissSnapshot: () -> Unit,
    onSaveShader: () -> Unit,
    onCompileStatus: (Boolean, String) -> Unit,
    onFpsUpdated: (Int) -> Unit,
    onSoftwareGpuDetected: () -> Unit,
    onClearError: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var promptText by rememberSaveable { mutableStateOf(activeShader.prompt) }
    var selectedPlatform by rememberSaveable { mutableStateOf(activeShader.platformEnum) }
    var isPlaying by rememberSaveable { mutableStateOf(true) }
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    var selectedTuningTab by rememberSaveable { mutableIntStateOf(0) }
    var snapshotRequestNonce by remember { mutableIntStateOf(0) }
    var editingColorSlot by remember { mutableStateOf<EditableColorSlot?>(null) }

    LaunchedEffect(activeShader.prompt) {
        if (activeShader.prompt.isNotBlank() && activeShader.prompt != promptText) {
            promptText = activeShader.prompt
        }
    }

    LaunchedEffect(activeShader.targetPlatform) {
        selectedPlatform = activeShader.platformEnum
    }

    if (capturedSnapshot != null) {
        SnapshotPreviewDialog(
            bitmap = capturedSnapshot,
            shaderTitle = activeShader.title,
            onDismiss = onDismissSnapshot,
            onNotify = onNotify
        )
    }

    if (editingColorSlot != null) {
        val slot = editingColorSlot!!
        RgbHexColorPickerDialog(
            slot = slot,
            onDismiss = { editingColorSlot = null },
            onApplyHex = { newHex ->
                onUpdateShader { shader ->
                    when (slot.key) {
                        "zenith" -> shader.copy(skyZenithHex = newHex)
                        "horizon" -> shader.copy(skyHorizonHex = newHex)
                        "sun" -> shader.copy(sunLightHex = newHex)
                        "ambient" -> shader.copy(ambientTintHex = newHex)
                        "water" -> shader.copy(waterTintHex = newHex)
                        "fog" -> shader.copy(fogTintHex = newHex)
                        else -> shader
                    }
                }
            }
        )
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ShaderPreviewView(
                    preset = activeShader,
                    isAnimationPlaying = isPlaying,
                    compareSplit = if (compareMode) 0.5f else 0.0f,
                    qualitySteps = qualityTier.steps,
                    snapshotRequestNonce = snapshotRequestNonce,
                    onSnapshotCaptured = onSnapshotCaptured,
                    onCompileStatus = onCompileStatus,
                    onFpsUpdated = onFpsUpdated,
                    onSoftwareGpuDetected = onSoftwareGpuDetected,
                    modifier = Modifier.fillMaxSize()
                )
                ViewportHudOverlay(
                    title = activeShader.title,
                    fps = fps,
                    compileStatus = compileStatus,
                    isPlaying = isPlaying,
                    isFullscreen = true,
                    compareMode = compareMode,
                    qualityTier = qualityTier,
                    onTogglePlay = { isPlaying = !isPlaying },
                    onToggleFullscreen = { isFullscreen = false },
                    onToggleCompare = onToggleCompare,
                    onCycleQuality = onCycleQualityTier,
                    onRequestSnapshot = { snapshotRequestNonce++ },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth >= 840.dp

        if (isWide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ViewportCard(
                    activeShader = activeShader,
                    compileStatus = compileStatus,
                    fps = fps,
                    isPlaying = isPlaying,
                    isPausedForFullscreen = isFullscreen,
                    compareMode = compareMode,
                    qualityTier = qualityTier,
                    snapshotRequestNonce = snapshotRequestNonce,
                    onTogglePlay = { isPlaying = !isPlaying },
                    onToggleFullscreen = { isFullscreen = true },
                    onToggleCompare = onToggleCompare,
                    onCycleQuality = onCycleQualityTier,
                    onRequestSnapshot = { snapshotRequestNonce++ },
                    onSnapshotCaptured = onSnapshotCaptured,
                    onCompileStatus = onCompileStatus,
                    onFpsUpdated = onFpsUpdated,
                    onSoftwareGpuDetected = onSoftwareGpuDetected,
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxSize()
                )

                Column(
                    modifier = Modifier
                        .weight(0.95f)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ForgeControlsContent(
                        activeShader = activeShader,
                        promptText = promptText,
                        onPromptChange = { promptText = it },
                        selectedPlatform = selectedPlatform,
                        onPlatformChange = { platform ->
                            selectedPlatform = platform
                            onUpdateShader { it.copy(targetPlatform = platform.name) }
                        },
                        generationState = generationState,
                        isSaving = isSaving,
                        selectedTuningTab = selectedTuningTab,
                        onTuningTabChange = { selectedTuningTab = it },
                        onGenerateShader = onGenerateShader,
                        onUpdateShader = onUpdateShader,
                        onApplyStarter = { preset ->
                            promptText = preset.prompt
                            onApplyStarter(preset)
                        },
                        onApplyTimeOfDay = onApplyTimeOfDay,
                        onEditColor = { editingColorSlot = it },
                        onSaveShader = onSaveShader,
                        onClearError = onClearError
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ViewportCard(
                        activeShader = activeShader,
                        compileStatus = compileStatus,
                        fps = fps,
                        isPlaying = isPlaying,
                        isPausedForFullscreen = isFullscreen,
                        compareMode = compareMode,
                        qualityTier = qualityTier,
                        snapshotRequestNonce = snapshotRequestNonce,
                        onTogglePlay = { isPlaying = !isPlaying },
                        onToggleFullscreen = { isFullscreen = true },
                        onToggleCompare = onToggleCompare,
                        onCycleQuality = onCycleQualityTier,
                        onRequestSnapshot = { snapshotRequestNonce++ },
                        onSnapshotCaptured = onSnapshotCaptured,
                        onCompileStatus = onCompileStatus,
                        onFpsUpdated = onFpsUpdated,
                        onSoftwareGpuDetected = onSoftwareGpuDetected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(238.dp)
                    )

                    ForgeControlsContent(
                        activeShader = activeShader,
                        promptText = promptText,
                        onPromptChange = { promptText = it },
                        selectedPlatform = selectedPlatform,
                        onPlatformChange = { platform ->
                            selectedPlatform = platform
                            onUpdateShader { it.copy(targetPlatform = platform.name) }
                        },
                        generationState = generationState,
                        isSaving = isSaving,
                        selectedTuningTab = selectedTuningTab,
                        onTuningTabChange = { selectedTuningTab = it },
                        onGenerateShader = onGenerateShader,
                        onUpdateShader = onUpdateShader,
                        onApplyStarter = { preset ->
                            promptText = preset.prompt
                            onApplyStarter(preset)
                        },
                        onApplyTimeOfDay = onApplyTimeOfDay,
                        onEditColor = { editingColorSlot = it },
                        onSaveShader = onSaveShader,
                        onClearError = onClearError
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ForgeControlsContent(
    activeShader: ShaderPreset,
    promptText: String,
    onPromptChange: (String) -> Unit,
    selectedPlatform: TargetPlatform,
    onPlatformChange: (TargetPlatform) -> Unit,
    generationState: GenerationUiState,
    isSaving: Boolean,
    selectedTuningTab: Int,
    onTuningTabChange: (Int) -> Unit,
    onGenerateShader: (String, TargetPlatform, Boolean) -> Unit,
    onUpdateShader: ((ShaderPreset) -> ShaderPreset) -> Unit,
    onApplyStarter: (ShaderPreset) -> Unit,
    onApplyTimeOfDay: (TimeOfDayPreset) -> Unit,
    onEditColor: (EditableColorSlot) -> Unit,
    onSaveShader: () -> Unit,
    onClearError: () -> Unit
) {
    // Active Preset Header + Save Action
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = activeShader.categoryEnum.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = selectedPlatform.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = activeShader.summary.ifBlank { "Prompt Gemini AI or tap a preset below to craft any shader." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onSaveShader,
                enabled = !isSaving,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("save_shader_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.BookmarkAdd,
                        contentDescription = "Save shader to vault",
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = if (activeShader.id.isBlank()) "Save" else "Update")
            }
        }
    }

    // 8-Card Shader Preset Selection Grid (auto-fills prompt + live GPU preview)
    PresetSelectionGrid(
        activeShader = activeShader,
        currentPrompt = promptText,
        onSelectPreset = onApplyStarter
    )

    // Prompt Forge Box
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Prompt to Shader",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            OutlinedTextField(
                value = promptText,
                onValueChange = onPromptChange,
                label = { Text("Describe lighting, water, RTX bounce, cel shading, or 32-bit style") },
                placeholder = { Text("e.g. Vibrant Visuals sunset with golden god rays, RTX crystal reflections, and misty mountains") },
                minLines = 2,
                maxLines = 4,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("shader_prompt_input")
            )

            // Prompt Modifier Chips (append optical boosts cleanly)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Prompt Boosters (Tap to append)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ShaderDefaults.promptModifiers.forEach { (label, clause) ->
                        val isAlreadyIncluded = promptText.contains(clause, ignoreCase = true)
                        FilterChip(
                            selected = isAlreadyIncluded,
                            onClick = {
                                if (!isAlreadyIncluded) {
                                    val base = promptText.trimEnd()
                                    val next = if (base.isBlank()) clause else "$base, $clause"
                                    onPromptChange(next)
                                }
                            },
                            label = {
                                Text(text = label, style = MaterialTheme.typography.labelSmall)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("modifier_chip_$label")
                        )
                    }
                }
            }

            // Target Platform selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TargetPlatform.entries.forEach { platform ->
                    FilterChip(
                        selected = selectedPlatform == platform,
                        onClick = { onPlatformChange(platform) },
                        label = {
                            Text(
                                text = "${platform.label} (.${platform.extension})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("platform_chip_${platform.name}")
                    )
                }
            }

            AnimatedVisibility(visible = generationState is GenerationUiState.Error) {
                val errMsg = (generationState as? GenerationUiState.Error)?.message.orEmpty()
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generation_error_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = errMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onClearError,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            val isGenerating = generationState is GenerationUiState.Generating
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onGenerateShader(promptText, selectedPlatform, false) },
                    enabled = !isGenerating,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .height(48.dp)
                        .testTag("forge_shader_button")
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Forging AI Shader...")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Forge Shader")
                    }
                }

                OutlinedButton(
                    onClick = { onGenerateShader(promptText, selectedPlatform, true) },
                    enabled = !isGenerating,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .height(48.dp)
                        .testTag("refine_shader_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoFixHigh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Refine")
                }
            }
        }
    }

    // Real-Time Optical & RTX Sliders
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Real-Time Optical Pipeline",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            TimeOfDayBar(
                currentElevation = activeShader.sunElevation,
                onSelectTimeOfDay = onApplyTimeOfDay
            )

            InteractiveColorPaletteBar(
                activeShader = activeShader,
                onEditColor = onEditColor
            )

            TabRow(
                selectedTabIndex = selectedTuningTab,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.clip(MaterialTheme.shapes.small)
            ) {
                listOf("Atmosphere", "RTX & Water", "PBR & Tone").forEachIndexed { idx, label ->
                    Tab(
                        selected = selectedTuningTab == idx,
                        onClick = { onTuningTabChange(idx) },
                        text = { Text(label, style = MaterialTheme.typography.labelLarge) }
                    )
                }
            }

            when (selectedTuningTab) {
                0 -> {
                    ShaderSlider(
                        label = "Sun Elevation (Time of Day)",
                        value = activeShader.sunElevation.toFloat(),
                        range = -0.3f..1.35f,
                        onValueChange = { v -> onUpdateShader { it.copy(sunElevation = v.toDouble()) } }
                    )
                    ShaderSlider(
                        label = "Sun Warmth",
                        value = activeShader.sunWarmth.toFloat(),
                        range = 0.2f..2.0f,
                        onValueChange = { v -> onUpdateShader { it.copy(sunWarmth = v.toDouble()) } }
                    )
                    ShaderSlider(
                        label = "Volumetric God Rays",
                        value = activeShader.godRaysIntensity.toFloat(),
                        range = 0.0f..2.0f,
                        onValueChange = { v -> onUpdateShader { it.copy(godRaysIntensity = v.toDouble()) } }
                    )
                    ShaderSlider(
                        label = "Atmospheric Fog Density",
                        value = activeShader.fogDensity.toFloat(),
                        range = 0.0f..2.0f,
                        onValueChange = { v -> onUpdateShader { it.copy(fogDensity = v.toDouble()) } }
                    )
                }
                1 -> {
                    ShaderSlider(
                        label = "Ray-Bounced GI Intensity",
                        value = activeShader.giBounceIntensity.toFloat(),
                        range = 0.0f..2.0f,
                        onValueChange = { v -> onUpdateShader { it.copy(giBounceIntensity = v.toDouble()) } }
                    )
                    ShaderSlider(
                        label = "Water Fresnel Reflectivity",
                        value = activeShader.waterReflectivity.toFloat(),
                        range = 0.0f..1.5f,
                        onValueChange = { v -> onUpdateShader { it.copy(waterReflectivity = v.toDouble()) } }
                    )
                    ShaderSlider(
                        label = "Shadow Penumbra Softness",
                        value = activeShader.shadowSoftness.toFloat(),
                        range = 0.0f..2.0f,
                        onValueChange = { v -> onUpdateShader { it.copy(shadowSoftness = v.toDouble()) } }
                    )
                }
                else -> {
                    ShaderSlider(
                        label = "PBR Specular / Metallic Boost",
                        value = activeShader.pbrMetallicBoost.toFloat(),
                        range = 0.0f..2.0f,
                        onValueChange = { v -> onUpdateShader { it.copy(pbrMetallicBoost = v.toDouble()) } }
                    )
                    ShaderSlider(
                        label = "Emissive & Sun Bloom",
                        value = activeShader.bloomIntensity.toFloat(),
                        range = 0.0f..2.0f,
                        onValueChange = { v -> onUpdateShader { it.copy(bloomIntensity = v.toDouble()) } }
                    )
                    ShaderSlider(
                        label = "ACES Exposure",
                        value = activeShader.exposure.toFloat(),
                        range = 0.4f..2.5f,
                        onValueChange = { v -> onUpdateShader { it.copy(exposure = v.toDouble()) } }
                    )
                    ShaderSlider(
                        label = "Color Saturation",
                        value = activeShader.saturation.toFloat(),
                        range = 0.2f..2.2f,
                        onValueChange = { v -> onUpdateShader { it.copy(saturation = v.toDouble()) } }
                    )
                }
            }
        }
    }
}
