package com.example.ui

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature.chat.ChatScreen
import com.example.feature.dashboard.DashboardScreen
import com.example.feature.installer.InstallerScreen
import com.example.feature.scan.ScanAndLibraryScreen
import com.example.feature.settings.SettingsScreen
import com.example.feature.voice.VoiceScreen
import com.example.ui.theme.SaffronAmber
import com.example.ui.theme.YyMindTheme
import com.example.viewmodel.YyMindViewModel

enum class YyMindDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("dashboard", "Home", Icons.Filled.Home, Icons.Outlined.Home),
    INSTALLER("installer", "Models", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
    CHAT("chat", "Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline),
    VOICE("voice", "Voice", Icons.Filled.Mic, Icons.Outlined.MicNone),
    LIBRARY("library", "Library", Icons.Filled.FolderCopy, Icons.Outlined.FolderCopy),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings)
}

@Composable
fun YyMindApp(
    viewModel: YyMindViewModel,
    initialRoute: String? = null
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val snackbarMsg by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isLocked by viewModel.isLocked.collectAsStateWithLifecycle()

    val isDark = when (settings.darkThemeOverride) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }

    var currentDestination by remember {
        mutableStateOf(
            when (initialRoute) {
                "voice" -> YyMindDestination.VOICE
                "chat" -> YyMindDestination.CHAT
                else -> YyMindDestination.DASHBOARD
            }
        )
    }
    var installerInitialTab by remember { mutableIntStateOf(0) }
    var libraryInitialSubTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(initialRoute) {
        when (initialRoute) {
            "voice" -> currentDestination = YyMindDestination.VOICE
            "chat" -> currentDestination = YyMindDestination.CHAT
        }
    }

    // BackHandler for secondary screens returning to Home Dashboard
    BackHandler(enabled = currentDestination != YyMindDestination.DASHBOARD) {
        currentDestination = YyMindDestination.DASHBOARD
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(snackbarMsg) {
        snackbarMsg?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    YyMindTheme(darkTheme = isDark, dynamicColor = settings.dynamicColor) {
        if (isLocked) {
            AppLockGateScreen(onUnlocked = { viewModel.unlockApp() })
        } else {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isMediumOrExpanded = maxWidth >= 600.dp
                val isExpanded = maxWidth >= 840.dp

                val primaryNavItems = listOf(
                    YyMindDestination.DASHBOARD,
                    YyMindDestination.INSTALLER,
                    YyMindDestination.CHAT,
                    YyMindDestination.VOICE,
                    YyMindDestination.LIBRARY
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!isMediumOrExpanded) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                primaryNavItems.forEach { dest ->
                                    val selected = currentDestination == dest
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            if (dest == YyMindDestination.LIBRARY) libraryInitialSubTab = 0
                                            currentDestination = dest
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label) },
                                        modifier = Modifier.testTag("nav_${dest.route}")
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
                        if (isMediumOrExpanded) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                Spacer(modifier = Modifier.height(12.dp))
                                YyMindDestination.entries.forEach { dest ->
                                    val selected = currentDestination == dest
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            if (dest == YyMindDestination.LIBRARY) libraryInitialSubTab = 0
                                            currentDestination = dest
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label) },
                                        modifier = Modifier.testTag("rail_${dest.route}")
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            when (currentDestination) {
                                YyMindDestination.DASHBOARD -> DashboardScreen(
                                    viewModel = viewModel,
                                    onNavigateChat = { currentDestination = YyMindDestination.CHAT },
                                    onNavigateVoice = { startTranslate ->
                                        viewModel.toggleTranslationMode(startTranslate)
                                        currentDestination = YyMindDestination.VOICE
                                    },
                                    onNavigateScan = {
                                        libraryInitialSubTab = 1
                                        currentDestination = YyMindDestination.LIBRARY
                                    },
                                    onNavigateLibrary = {
                                        libraryInitialSubTab = 0
                                        currentDestination = YyMindDestination.LIBRARY
                                    },
                                    onNavigateInstaller = {
                                        installerInitialTab = (viewModel.hardwareProfile.value.effectiveTier.level - 1).coerceIn(0, 2)
                                        currentDestination = YyMindDestination.INSTALLER
                                    },
                                    onNavigateSettings = { currentDestination = YyMindDestination.SETTINGS }
                                )

                                YyMindDestination.INSTALLER -> InstallerScreen(
                                    viewModel = viewModel,
                                    initialTab = installerInitialTab
                                )

                                YyMindDestination.CHAT -> ChatScreen(
                                    viewModel = viewModel,
                                    showTwoPaneList = isExpanded,
                                    onNavigateScan = {
                                        libraryInitialSubTab = 1
                                        currentDestination = YyMindDestination.LIBRARY
                                    },
                                    onNavigateInstaller = {
                                        installerInitialTab = 0
                                        currentDestination = YyMindDestination.INSTALLER
                                    }
                                )

                                YyMindDestination.VOICE -> VoiceScreen(
                                    viewModel = viewModel,
                                    onOpenVoiceInstaller = {
                                        installerInitialTab = 4
                                        currentDestination = YyMindDestination.INSTALLER
                                    }
                                )

                                YyMindDestination.LIBRARY -> ScanAndLibraryScreen(
                                    viewModel = viewModel,
                                    initialSubTab = libraryInitialSubTab,
                                    onAskInChat = { currentDestination = YyMindDestination.CHAT }
                                )

                                YyMindDestination.SETTINGS -> SettingsScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLockGateScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val keyguard = remember { context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager }

    val authLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onUnlocked()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Vault Locked",
                tint = SaffronAmber,
                modifier = Modifier.size(56.dp)
            )
            Text("YY Assistant Vault Locked", style = MaterialTheme.typography.headlineSmall)
            Text(
                text = "Your offline models, chats, and private RAG documents are protected locally.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    @Suppress("DEPRECATION")
                    val intent: Intent? = if (keyguard.isDeviceSecure) {
                        keyguard.createConfirmDeviceCredentialIntent("Unlock YY Assistant", "Authenticate locally to access your private AI vault")
                    } else null
                    if (intent != null) {
                        authLauncher.launch(intent)
                    } else {
                        onUnlocked()
                    }
                },
                modifier = Modifier.testTag("unlock_vault_button")
            ) {
                Text("Unlock Privately")
            }
        }
    }
}
