package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.ShaderAiRepository
import com.example.data.ShaderRepository
import com.example.data.local.ShaderDatabase
import com.example.ui.screens.CodeAndExportScreen
import com.example.ui.screens.ForgeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.viewmodel.ShaderStudioViewModel

private enum class StudioTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FORGE("forge", "Forge", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    CODE("code_export", "Code & Pack", Icons.Filled.Code, Icons.Outlined.Code),
    LIBRARY("library", "Vault", Icons.Filled.Bookmarks, Icons.Outlined.Bookmarks)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val viewModel: ShaderStudioViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val dao = ShaderDatabase.getInstance(app).shaderDao()
                ShaderStudioViewModel(
                    repository = ShaderRepository(dao),
                    aiRepository = ShaderAiRepository()
                )
            }
        }
    )

    val activeShader by viewModel.activeShader.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val compileStatus by viewModel.compileStatus.collectAsStateWithLifecycle()
    val fps by viewModel.fps.collectAsStateWithLifecycle()
    val compareMode by viewModel.compareMode.collectAsStateWithLifecycle()
    val qualityTier by viewModel.qualityTier.collectAsStateWithLifecycle()
    val capturedSnapshot by viewModel.capturedSnapshot.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val libraryState by viewModel.libraryState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: StudioTab.FORGE.route

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeMessage()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 840.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Lumina Shader Forge",
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_tab_${tab.route}")
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        StudioTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_rail_${tab.route}")
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = StudioTab.FORGE.route
                    ) {
                        composable(StudioTab.FORGE.route) {
                            ForgeScreen(
                                activeShader = activeShader,
                                generationState = generationState,
                                compileStatus = compileStatus,
                                fps = fps,
                                compareMode = compareMode,
                                qualityTier = qualityTier,
                                capturedSnapshot = capturedSnapshot,
                                isSaving = isSaving,
                                onGenerateShader = viewModel::generateShader,
                                onUpdateShader = viewModel::updateActiveShader,
                                onApplyStarter = viewModel::applyStarterPreset,
                                onApplyTimeOfDay = viewModel::applyTimeOfDay,
                                onToggleCompare = viewModel::toggleCompareMode,
                                onCycleQualityTier = viewModel::cycleQualityTier,
                                onSnapshotCaptured = viewModel::onSnapshotCaptured,
                                onDismissSnapshot = viewModel::dismissSnapshot,
                                onSaveShader = viewModel::saveActiveShader,
                                onCompileStatus = viewModel::updateCompileStatus,
                                onFpsUpdated = viewModel::updateFps,
                                onSoftwareGpuDetected = viewModel::onSoftwareGpuDetected,
                                onClearError = viewModel::clearGenerationError,
                                onNotify = viewModel::showMessage
                            )
                        }

                        composable(StudioTab.CODE.route) {
                            CodeAndExportScreen(
                                activeShader = activeShader,
                                compileStatus = compileStatus,
                                onUpdateCode = { newCode ->
                                    viewModel.updateActiveShader { it.copy(fragmentShader = newCode) }
                                },
                                onRestoreDefaultKernel = viewModel::resetToDefaultGlsl,
                                onNotify = viewModel::showMessage
                            )
                        }

                        composable(StudioTab.LIBRARY.route) {
                            LibraryScreen(
                                libraryState = libraryState,
                                onLoadShader = { preset ->
                                    viewModel.loadSavedPreset(preset)
                                    navController.navigate(StudioTab.FORGE.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onDeleteShader = viewModel::deleteShader,
                                onLoadStarterPreset = { starter ->
                                    viewModel.applyStarterPreset(starter)
                                    navController.navigate(StudioTab.FORGE.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                onSeedStarterPresets = viewModel::seedStarterPresetsToVault
                            )
                        }
                    }
                }
            }
        }
    }
}
