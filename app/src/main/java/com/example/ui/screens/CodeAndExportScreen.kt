package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.export.ShaderPackExporter
import com.example.model.ShaderPreset
import com.example.model.TargetPlatform
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.SuccessEmerald
import com.example.ui.viewmodel.CompileStatus

@Composable
fun CodeAndExportScreen(
    activeShader: ShaderPreset,
    compileStatus: CompileStatus,
    onUpdateCode: (String) -> Unit,
    onRestoreDefaultKernel: () -> Unit,
    onNotify: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var pendingExportPlatform by remember { mutableStateOf(TargetPlatform.BEDROCK_VIBRANT) }

    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ShaderPackExporter.writeToOutputStream(activeShader, pendingExportPlatform, out)
                }
                onNotify("Exported ${ShaderPackExporter.suggestedFileName(activeShader, pendingExportPlatform)}")
            } catch (e: Exception) {
                onNotify(e.localizedMessage ?: "Failed to export shader pack.")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Export Packs Section
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Export Shader Pack",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Package \"${activeShader.title}\" as a real installable archive—compatible with 32-bit & 64-bit devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    ExportFormatCard(
                        title = "Bedrock Vibrant Visuals / PBR (.mcpack)",
                        subtitle = "Generates manifest.json (pbr capability), lighting/global.json, atmospherics, water, shadows, and color grading.",
                        saveTag = "export_mcpack_button",
                        shareTag = "share_mcpack_button",
                        onSave = {
                            pendingExportPlatform = TargetPlatform.BEDROCK_VIBRANT
                            saveDocumentLauncher.launch(
                                ShaderPackExporter.suggestedFileName(activeShader, TargetPlatform.BEDROCK_VIBRANT)
                            )
                        },
                        onShare = {
                            val intent = ShaderPackExporter.buildShareIntent(
                                context,
                                activeShader,
                                TargetPlatform.BEDROCK_VIBRANT
                            )
                            context.startActivity(
                                android.content.Intent.createChooser(intent, "Share Bedrock .mcpack")
                            )
                        }
                    )

                    ExportFormatCard(
                        title = "Java Edition Iris / OptiFine (.zip)",
                        subtitle = "Generates shaders.properties, gbuffers_terrain, gbuffers_water, and ACES final.fsh pipeline.",
                        saveTag = "export_java_zip_button",
                        shareTag = "share_java_zip_button",
                        onSave = {
                            pendingExportPlatform = TargetPlatform.JAVA_OPTIFINE
                            saveDocumentLauncher.launch(
                                ShaderPackExporter.suggestedFileName(activeShader, TargetPlatform.JAVA_OPTIFINE)
                            )
                        },
                        onShare = {
                            val intent = ShaderPackExporter.buildShareIntent(
                                context,
                                activeShader,
                                TargetPlatform.JAVA_OPTIFINE
                            )
                            context.startActivity(
                                android.content.Intent.createChooser(intent, "Share Java Shader .zip")
                            )
                        }
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    ) {
                        Text(
                            text = ShaderPackExporter.buildBedrockPreviewSummary(activeShader),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // GLSL Compiler Diagnostics & Code Lab
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                imageVector = Icons.Filled.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "GLSL ES Fragment Kernel",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(activeShader.fragmentShader))
                                    onNotify("Copied GLSL code to clipboard.")
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("copy_glsl_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ContentCopy,
                                    contentDescription = "Copy GLSL code",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy")
                            }
                            TextButton(
                                onClick = onRestoreDefaultKernel,
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("restore_glsl_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Restore,
                                    contentDescription = "Restore built-in ray-marched GLSL kernel",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset")
                            }
                        }
                    }

                    // Live OpenGL Compiler Status Card
                    Surface(
                        color = if (compileStatus.isSuccess) {
                            SuccessEmerald.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (compileStatus.isSuccess) {
                                    Icons.Filled.CheckCircle
                                } else {
                                    Icons.Filled.Warning
                                },
                                contentDescription = null,
                                tint = if (compileStatus.isSuccess) {
                                    SuccessEmerald
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = compileStatus.log,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (compileStatus.isSuccess) {
                                    SuccessEmerald
                                } else {
                                    MaterialTheme.colorScheme.onErrorContainer
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = activeShader.fragmentShader,
                        onValueChange = onUpdateCode,
                        textStyle = TextStyle(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        minLines = 12,
                        maxLines = 22,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("glsl_code_editor")
                    )

                    OutlinedButton(
                        onClick = {
                            pendingExportPlatform = TargetPlatform.UNIVERSAL_GLSL
                            saveDocumentLauncher.launch(
                                ShaderPackExporter.suggestedFileName(activeShader, TargetPlatform.UNIVERSAL_GLSL)
                            )
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("export_fsh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save Raw Fragment Shader (.fsh)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ExportFormatCard(
    title: String,
    subtitle: String,
    saveTag: String,
    shareTag: String,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSave,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag(saveTag)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Save File")
                }
                OutlinedButton(
                    onClick = onShare,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag(shareTag)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share / Open")
                }
            }
        }
    }
}
