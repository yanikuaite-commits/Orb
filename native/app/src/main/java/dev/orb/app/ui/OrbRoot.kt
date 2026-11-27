package dev.orb.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.orb.app.AppContainer
import dev.orb.app.ui.chat.ChatScreen
import dev.orb.app.ui.components.FileApprovalDialog
import dev.orb.app.ui.components.OrbMark
import dev.orb.app.ui.memory.MemoryScreen
import dev.orb.app.ui.reminders.RemindersScreen
import dev.orb.app.ui.settings.SettingsScreen
import dev.orb.app.ui.settings.SetupScreen
import dev.orb.app.ui.theme.OrbBg
import dev.orb.app.ui.theme.OrbMuted
import dev.orb.app.ui.theme.OrbPanel
import dev.orb.app.ui.theme.OrbRed

private data class Tab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    Tab("chat", "Chat", Icons.Filled.ChatBubble),
    Tab("memory", "Memória", Icons.Filled.Psychology),
    Tab("reminders", "Lembretes", Icons.Filled.Alarm),
)

@Composable
fun OrbRoot(container: AppContainer) {
    val vm: OrbViewModel = viewModel(factory = OrbViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    if (!state.ready) {
        Box(Modifier.fillMaxSize().background(OrbBg), contentAlignment = Alignment.Center) {
            OrbMark(size = 72.dp)
        }
        return
    }

    if (state.setupRequired) {
        SetupScreen(state = state, onActivate = vm::activate)
        return
    }

    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: "chat"
    val snackbarHost = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHost.showSnackbar(it)
            vm.dismissSnackbar()
        }
    }

    Scaffold(
        containerColor = OrbBg,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (route != "settings") {
                NavigationBar(containerColor = OrbPanel, tonalElevation = 0.dp) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = OrbRed,
                                selectedTextColor = OrbRed,
                                indicatorColor = OrbRed.copy(alpha = 0.14f),
                                unselectedIconColor = OrbMuted,
                                unselectedTextColor = OrbMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(
                navController = nav,
                startDestination = "chat",
            ) {
                composable("chat") {
                    ChatScreen(
                        state = state,
                        onSend = vm::send,
                        onInput = vm::setInput,
                        onToggleTts = vm::toggleTts,
                        onClearHistory = vm::clearHistory,
                        onOpenSettings = { nav.navigate("settings") },
                    )
                }
                composable("memory") {
                    MemoryScreen(
                        state = state,
                        onLoad = vm::loadMemory,
                        onDelete = vm::deleteMemory,
                        onClear = vm::clearMemory,
                    )
                }
                composable("reminders") {
                    RemindersScreen(state = state, onLoad = vm::loadReminders)
                }
                composable("settings") {
                    SettingsScreen(
                        state = state,
                        onSave = vm::saveSettings,
                        onRemoveGeminiKey = vm::removeGeminiKey,
                        onRemoveGroqKey = vm::removeGroqKey,
                        onTest = vm::testModel,
                        onRefreshFileAccess = vm::refreshFileAccess,
                        onBack = { nav.popBackStack() },
                    )
                }
            }
            state.pendingFileApproval?.let { approval ->
                FileApprovalDialog(
                    approval = approval,
                    onApprove = vm::approveFileAction,
                    onDeny = vm::denyFileAction,
                )
            }
        }
    }
}
