package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.prefs.AppLanguage
import com.example.i18n.StringsCatalog
import com.example.ui.components.HudTelemetryStrip
import com.example.ui.screens.AppsSystemsScreen
import com.example.ui.screens.CoreChatScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.screens.WorkspaceScreen
import com.example.ui.theme.JarvisAiTheme
import com.example.ui.theme.LocalJarvisHudColors
import com.example.viewmodel.JarvisViewModel
import kotlinx.coroutines.launch

object JarvisRoutes {
    const val CORE = "core"
    const val WORKSPACE = "workspace"
    const val SYSTEMS = "systems"
    const val CONFIG = "config"
}

private data class NavDestinationItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

@Composable
fun JarvisApp(viewModel: JarvisViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val messages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val aiUiState by viewModel.aiUiState.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val rmsLevel by viewModel.rmsLevel.collectAsStateWithLifecycle()
    val partialTranscript by viewModel.partialTranscript.collectAsStateWithLifecycle()
    val voiceError by viewModel.voiceError.collectAsStateWithLifecycle()
    val permissionStatus by viewModel.permissionStatus.collectAsStateWithLifecycle()

    val strings = remember(settings.language) { StringsCatalog.get(settings.language) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPermissions()
        if (granted) {
            viewModel.toggleVoiceListening()
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Microphone permission is needed for voice input.")
            }
        }
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPermissions()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    JarvisAiTheme(hudTheme = settings.hudTheme) {
        val hudColors = LocalJarvisHudColors.current
        val glow = hudColors.glowPrimary

        val destinations = remember(strings) {
            listOf(
                NavDestinationItem(JarvisRoutes.CORE, strings.navCore, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
                NavDestinationItem(JarvisRoutes.WORKSPACE, strings.navTasks, Icons.Filled.Checklist, Icons.Outlined.Checklist),
                NavDestinationItem(JarvisRoutes.SYSTEMS, strings.navSystems, Icons.Filled.Apps, Icons.Outlined.Apps),
                NavDestinationItem(JarvisRoutes.CONFIG, strings.navConfig, Icons.Filled.Settings, Icons.Outlined.Settings)
            )
        }

        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route ?: JarvisRoutes.CORE

        val navigateTo: (String) -> Unit = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        val handleMicClick: () -> Unit = {
            if (permissionStatus.microphoneGranted) {
                viewModel.toggleVoiceListening()
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        val handleCycleLanguage: () -> Unit = {
            val next = when (settings.language) {
                AppLanguage.ENGLISH -> AppLanguage.BENGALI
                AppLanguage.BENGALI -> AppLanguage.HINDI
                AppLanguage.HINDI -> AppLanguage.ENGLISH
            }
            viewModel.setLanguage(next)
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            val useNavigationRail = maxWidth >= 680.dp

            val bgColor = MaterialTheme.colorScheme.background

            // Subtle cyber-HUD radial backdrop & grid
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            glow.copy(alpha = 0.08f),
                            hudColors.glowSecondary.copy(alpha = 0.04f),
                            bgColor
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.22f),
                        radius = size.maxDimension * 0.75f
                    )
                )
                val step = 48.dp.toPx()
                val gridColor = glow.copy(alpha = 0.035f)
                var x = 0f
                while (x < size.width) {
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    x += step
                }
                var y = 0f
                while (y < size.height) {
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    y += step
                }
            }

            Scaffold(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                            tonalElevation = 0.dp
                        ) {
                            destinations.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = glow,
                                        indicatorColor = glow,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${item.route}")
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
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            modifier = Modifier
                                .fillMaxHeight()
                                .windowInsetsPadding(WindowInsets(0))
                        ) {
                            destinations.forEach { item ->
                                val selected = currentRoute == item.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label
                                        )
                                    },
                                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                        selectedTextColor = glow,
                                        indicatorColor = glow,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag("nav_${item.route}")
                                )
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f).fillMaxSize()) {
                        HudTelemetryStrip(
                            strings = strings,
                            telemetry = telemetry,
                            language = settings.language,
                            localOnlyPrivacy = settings.localOnlyPrivacyMode,
                            onCycleLanguage = handleCycleLanguage
                        )

                        Box(modifier = Modifier.weight(1f)) {
                            NavHost(
                                navController = navController,
                                startDestination = JarvisRoutes.CORE
                            ) {
                                composable(JarvisRoutes.CORE) {
                                    CoreChatScreen(
                                        strings = strings,
                                        settings = settings,
                                        messages = messages,
                                        aiUiState = aiUiState,
                                        isListening = isListening,
                                        isSpeaking = isSpeaking,
                                        rmsLevel = rmsLevel,
                                        partialTranscript = partialTranscript,
                                        voiceError = voiceError,
                                        onSendMessage = viewModel::sendMessage,
                                        onMicClick = handleMicClick,
                                        onStopSpeaking = viewModel::stopSpeaking,
                                        onSpeakMessage = viewModel::speakText,
                                        onClearChat = viewModel::clearChatHistory,
                                        onDismissVoiceError = viewModel::clearVoiceError,
                                        onDismissAiError = viewModel::dismissAiError,
                                        onCopiedNotice = { notice ->
                                            scope.launch { snackbarHostState.showSnackbar(notice) }
                                        }
                                    )
                                }

                                composable(JarvisRoutes.WORKSPACE) {
                                    WorkspaceScreen(
                                        strings = strings,
                                        notes = notes,
                                        reminders = reminders,
                                        alarms = alarms,
                                        onCreateNote = viewModel::createNote,
                                        onTogglePinNote = viewModel::togglePinNote,
                                        onDeleteNote = viewModel::deleteNote,
                                        onCreateReminder = { title, mins ->
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                                !permissionStatus.notificationsGranted
                                            ) {
                                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            viewModel.createReminder(title, mins)
                                        },
                                        onToggleReminder = viewModel::toggleReminder,
                                        onDeleteReminder = viewModel::deleteReminder,
                                        onCreateAlarm = viewModel::createAlarm,
                                        onToggleAlarm = viewModel::toggleAlarm,
                                        onDeleteAlarm = viewModel::deleteAlarm
                                    )
                                }

                                composable(JarvisRoutes.SYSTEMS) {
                                    AppsSystemsScreen(
                                        strings = strings,
                                        telemetry = telemetry,
                                        installedApps = installedApps,
                                        onOpenCamera = viewModel::openCamera,
                                        onOpenSettings = viewModel::openSettings,
                                        onOpenWifi = viewModel::openWifiSettings,
                                        onOpenMaps = viewModel::openMaps,
                                        onLaunchApp = viewModel::launchInstalledApp,
                                        onRefreshApps = viewModel::refreshInstalledApps
                                    )
                                }

                                composable(JarvisRoutes.CONFIG) {
                                    SettingsPrivacyScreen(
                                        strings = strings,
                                        settings = settings,
                                        permissionStatus = permissionStatus,
                                        onSelectLanguage = viewModel::setLanguage,
                                        onSelectTheme = viewModel::setHudTheme,
                                        onSetAutoSpeak = viewModel::setAutoSpeak,
                                        onSetSpeechRate = viewModel::setSpeechRate,
                                        onSetVoicePitch = viewModel::setVoicePitch,
                                        onTestVoice = viewModel::testVoice,
                                        onRequestMicPermission = {
                                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        },
                                        onRequestNotifPermission = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                        },
                                        onOpenAppSettings = viewModel::openAppPermissionsSettings,
                                        onSetLocalOnlyPrivacy = viewModel::setLocalOnlyPrivacyMode,
                                        onSetSaveChatHistory = viewModel::setSaveChatHistory,
                                        onSetShareTelemetry = viewModel::setShareTelemetryWithAi,
                                        onSetHighIntensityAnimations = viewModel::setHighIntensityAnimations,
                                        onPurgeAllData = viewModel::purgeAllLocalData
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
