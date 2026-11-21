package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.data.PresetGridItem
import com.example.data.QualityTier
import com.example.data.ShaderDefaults
import com.example.data.TimeOfDayPreset
import com.example.gl.ShaderPreviewView
import com.example.model.ShaderPreset
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.CompileStatus
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class EditableColorSlot(
    val key: String,
    val label: String,
    val hex: String
)

@Composable
fun ViewportCard(
    activeShader: ShaderPreset,
    compileStatus: CompileStatus,
    fps: Int,
    isPlaying: Boolean,
    isPausedForFullscreen: Boolean,
    compareMode: Boolean,
    qualityTier: QualityTier,
    snapshotRequestNonce: Int,
    onTogglePlay: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleCompare: () -> Unit,
    onCycleQuality: () -> Unit,
    onRequestSnapshot: () -> Unit,
    onSnapshotCaptured: (Bitmap) -> Unit,
    onCompileStatus: (Boolean, String) -> Unit,
    onFpsUpdated: (Int) -> Unit,
    onSoftwareGpuDetected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.45f)
                )
            ),
            shape = MaterialTheme.shapes.large
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.large)
        ) {
            if (!isPausedForFullscreen) {
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
            }
            ViewportHudOverlay(
                title = activeShader.title,
                fps = fps,
                compileStatus = compileStatus,
                isPlaying = isPlaying,
                isFullscreen = false,
                compareMode = compareMode,
                qualityTier = qualityTier,
                onTogglePlay = onTogglePlay,
                onToggleFullscreen = onToggleFullscreen,
                onToggleCompare = onToggleCompare,
                onCycleQuality = onCycleQuality,
                onRequestSnapshot = onRequestSnapshot,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            )
        }
    }
}

@Composable
fun ViewportHudOverlay(
    title: String,
    fps: Int,
    compileStatus: CompileStatus,
    isPlaying: Boolean,
    isFullscreen: Boolean,
    compareMode: Boolean,
    qualityTier: QualityTier,
    onTogglePlay: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onToggleCompare: () -> Unit,
    onCycleQuality: () -> Unit,
    onRequestSnapshot: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .horizontalScroll(rememberScrollState())
            ) {
                HudBadge(
                    text = "$fps FPS",
                    accentColor = MaterialTheme.colorScheme.primary
                )
                HudBadge(
                    text = if (compileStatus.isSuccess) "GPU LIVE" else "FALLBACK",
                    accentColor = if (compileStatus.isSuccess) SuccessEmerald else MaterialTheme.colorScheme.secondary
                )
                Surface(
                    color = Color.Black.copy(alpha = 0.62f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.65f),
                            RoundedCornerShape(50)
                        )
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onCycleQuality)
                        .testTag("quality_tier_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Speed,
                            contentDescription = "Switch shader quality tier",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = qualityTier.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onToggleCompare,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(
                            if (compareMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                            else Color.Black.copy(alpha = 0.5f),
                            CircleShape
                        )
                        .testTag("compare_split_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Compare,
                        contentDescription = "Toggle Vanilla vs Shader split compare",
                        tint = if (compareMode) MaterialTheme.colorScheme.onPrimary else Color.White
                    )
                }
                IconButton(
                    onClick = onRequestSnapshot,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("capture_snapshot_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Capture shader snapshot",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onTogglePlay,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("toggle_preview_play_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause preview animation" else "Resume preview animation",
                        tint = Color.White
                    )
                }
                IconButton(
                    onClick = onToggleFullscreen,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        .testTag("toggle_fullscreen_preview_button")
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullscreen) "Exit fullscreen preview" else "Expand preview fullscreen",
                        tint = Color.White
                    )
                }
            }
        }

        if (compareMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HudBadge(text = "VANILLA", accentColor = Color.LightGray)
                HudBadge(text = "FORGED SHADER", accentColor = MaterialTheme.colorScheme.primary)
            }
        }

        Surface(
            color = Color.Black.copy(alpha = 0.54f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.BottomStart)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = "Drag to orbit",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.72f)
                )
            }
        }
    }
}

@Composable
fun HudBadge(text: String, accentColor: Color) {
    Surface(
        color = Color.Black.copy(alpha = 0.58f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(50))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }
}

@Composable
fun PresetSelectionGrid(
    activeShader: ShaderPreset,
    currentPrompt: String,
    onSelectPreset: (ShaderPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Shader Presets",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Tap to auto-fill & preview",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val rows = remember { ShaderDefaults.presetSelectionGrid.chunked(2) }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { item ->
                            val isSelected = activeShader.title == item.preset.title ||
                                currentPrompt.trim() == item.preset.prompt.trim()
                            PresetGridCard(
                                item = item,
                                isSelected = isSelected,
                                onClick = { onSelectPreset(item.preset) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetGridCard(
    item: PresetGridItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = remember(item.accentHex) { parseSafeColor(item.accentHex, Color.Cyan) }
    val secondaryColor = remember(item.secondaryHex) { parseSafeColor(item.secondaryHex, Color.Blue) }
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant

    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, MaterialTheme.shapes.medium)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("preset_grid_${item.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(Brush.horizontalGradient(listOf(secondaryColor, accentColor)))
            )
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected preset",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = accentColor.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(50)
                    ) {
                        Text(
                            text = item.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = item.preset.platformEnum.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun TimeOfDayBar(
    currentElevation: Double,
    onSelectTimeOfDay: (TimeOfDayPreset) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Quick Time of Day",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimeOfDayPreset.entries.forEach { tod ->
                val isClosest = kotlin.math.abs(currentElevation - tod.elevation) < 0.08
                val icon = when (tod) {
                    TimeOfDayPreset.DAWN -> Icons.Filled.WbTwilight
                    TimeOfDayPreset.NOON -> Icons.Filled.WbSunny
                    TimeOfDayPreset.GOLDEN -> Icons.Filled.Brightness6
                    TimeOfDayPreset.SUNSET -> Icons.Filled.WbTwilight
                    TimeOfDayPreset.NIGHT -> Icons.Filled.NightsStay
                }
                Surface(
                    color = if (isClosest) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .border(
                            1.dp,
                            if (isClosest) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelectTimeOfDay(tod) }
                        .testTag("tod_chip_${tod.name}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isClosest) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = tod.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isClosest) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveColorPaletteBar(
    activeShader: ShaderPreset,
    onEditColor: (EditableColorSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    val slots = listOf(
        EditableColorSlot("zenith", "Zenith", activeShader.skyZenithHex),
        EditableColorSlot("horizon", "Horizon", activeShader.skyHorizonHex),
        EditableColorSlot("sun", "Sun", activeShader.sunLightHex),
        EditableColorSlot("ambient", "Ambient", activeShader.ambientTintHex),
        EditableColorSlot("water", "Water", activeShader.waterTintHex),
        EditableColorSlot("fog", "Fog", activeShader.fogTintHex)
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Atmosphere & Water Palette (Tap to customize)",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slots.forEach { slot ->
                val parsed = remember(slot.hex) { parseSafeColor(slot.hex, Color.Gray) }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onEditColor(slot) }
                        .padding(vertical = 2.dp)
                        .testTag("color_swatch_${slot.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(26.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(parsed)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                    )
                    Text(
                        text = slot.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun RgbHexColorPickerDialog(
    slot: EditableColorSlot,
    onDismiss: () -> Unit,
    onApplyHex: (String) -> Unit
) {
    val initialColor = remember(slot.hex) {
        try {
            android.graphics.Color.parseColor(slot.hex)
        } catch (_: Exception) {
            android.graphics.Color.WHITE
        }
    }
    var red by remember(slot) { mutableFloatStateOf(android.graphics.Color.red(initialColor).toFloat()) }
    var green by remember(slot) { mutableFloatStateOf(android.graphics.Color.green(initialColor).toFloat()) }
    var blue by remember(slot) { mutableFloatStateOf(android.graphics.Color.blue(initialColor).toFloat()) }
    var hexText by remember(slot) { mutableStateOf(slot.hex.uppercase(Locale.US)) }

    val syncHexFromRgb = {
        hexText = String.format(
            Locale.US,
            "#%02X%02X%02X",
            red.toInt().coerceIn(0, 255),
            green.toInt().coerceIn(0, 255),
            blue.toInt().coerceIn(0, 255)
        )
    }

    val previewColor = Color(
        red = red.toInt().coerceIn(0, 255) / 255f,
        green = green.toInt().coerceIn(0, 255) / 255f,
        blue = blue.toInt().coerceIn(0, 255) / 255f
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize ${slot.label} Tint") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(previewColor)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                )
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { input ->
                        hexText = input.uppercase(Locale.US)
                        val candidate = if (input.startsWith("#")) input else "#$input"
                        if (Regex("^#[0-9A-Fa-f]{6}$").matches(candidate)) {
                            val parsed = android.graphics.Color.parseColor(candidate)
                            red = android.graphics.Color.red(parsed).toFloat()
                            green = android.graphics.Color.green(parsed).toFloat()
                            blue = android.graphics.Color.blue(parsed).toFloat()
                        }
                    },
                    label = { Text("Hex (#RRGGBB)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                ColorChannelSlider("Red", red) {
                    red = it
                    syncHexFromRgb()
                }
                ColorChannelSlider("Green", green) {
                    green = it
                    syncHexFromRgb()
                }
                ColorChannelSlider("Blue", blue) {
                    blue = it
                    syncHexFromRgb()
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalized = String.format(
                        Locale.US,
                        "#%02X%02X%02X",
                        red.toInt().coerceIn(0, 255),
                        green.toInt().coerceIn(0, 255),
                        blue.toInt().coerceIn(0, 255)
                    )
                    onApplyHex(normalized)
                    onDismiss()
                }
            ) {
                Text("Apply Tint")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ColorChannelSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodySmall)
            Text(text = value.toInt().toString(), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(0f, 255f),
            onValueChange = onChange,
            valueRange = 0f..255f
        )
    }
}

@Composable
fun SnapshotPreviewDialog(
    bitmap: Bitmap,
    shaderTitle: String,
    onDismiss: () -> Unit,
    onNotify: (String) -> Unit
) {
    val context = LocalContext.current
    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                } ?: error("Cannot open destination")
            }.onSuccess {
                onNotify("Saved shader snapshot PNG!")
            }.onFailure { err ->
                onNotify(err.localizedMessage ?: "Failed to save snapshot.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shader GPU Snapshot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Captured preview of $shaderTitle",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 10f)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                )
                Text(
                    text = shaderTitle,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val slug = shaderTitle.lowercase(Locale.US)
                                .replace(Regex("[^a-z0-9]+"), "_")
                                .trim('_')
                                .ifBlank { "lumina_shader" }
                            saveLauncher.launch("${slug}_snapshot.png")
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("save_snapshot_png_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save PNG")
                    }
                    OutlinedButton(
                        onClick = { shareSnapshotBitmap(context, bitmap, shaderTitle, onNotify) },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("share_snapshot_png_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Share")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun shareSnapshotBitmap(
    context: Context,
    bitmap: Bitmap,
    title: String,
    onNotify: (String) -> Unit
) {
    runCatching {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "lumina_snapshot_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "$title Shader Preview")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Shader Preview"))
    }.onFailure { err ->
        onNotify(err.localizedMessage ?: "Failed to share snapshot.")
    }
}

@Composable
fun ShaderSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(Locale.US, "%.2f", value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        )
    }
}

private fun parseSafeColor(hex: String, fallback: Color): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}
