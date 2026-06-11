package com.companion.chat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.companion.chat.ui.chat.ChatScreen
import com.companion.chat.ui.chat.ChatViewModel
import com.companion.chat.ui.home.DiscoverViewModel
import com.companion.chat.ui.home.DiscoverRoleDetailScreen
import com.companion.chat.ui.home.HomeScreen
import com.companion.chat.ui.memory.MemoryScreen
import com.companion.chat.ui.navigation.DiscoverRoutes
import com.companion.chat.ui.navigation.Screen
import com.companion.chat.ui.navigation.SettingsRoutes
import com.companion.chat.ui.settings.AboutScreen
import com.companion.chat.ui.settings.CharacterManagementScreen
import com.companion.chat.ui.settings.DarkModeSettingsScreen
import com.companion.chat.ui.settings.LanguageSettingsScreen
import com.companion.chat.ui.settings.ModelConfigScreen
import com.companion.chat.ui.settings.ModelConfigScrollTarget
import com.companion.chat.ui.settings.SettingsScreen
import com.companion.chat.ui.settings.SkillsManagementScreen
import com.companion.chat.ui.settings.UserProfileScreen
import com.companion.chat.ui.settings.VoiceSettingsScreen
import com.companion.chat.ui.theme.CompanionChatTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            var darkModePref by remember { mutableStateOf(
                context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("dark_mode", "system") ?: "system"
            ) }

            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        darkModePref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                            .getString("dark_mode", "system") ?: "system"
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (darkModePref) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }

            CompanionChatTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainApp()
                }
            }
        }
    }
}

@Composable
fun MainApp() {
    val application = LocalContext.current.applicationContext as CompanionChatApplication
    val viewModelFactory = remember(application) {
        AppViewModelFactory(application, application.appContainer)
    }
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val chatViewModel: ChatViewModel = viewModel(factory = viewModelFactory)
    val discoverViewModel: DiscoverViewModel = viewModel(factory = viewModelFactory)
    val coroutineScope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val screens = Screen.entries.toList()
    val showBottomBar = screens.any { screen ->
        currentRoute == screen.route
    }

    DisposableEffect(lifecycleOwner, chatViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                chatViewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                NavigationBar {
                    screens.forEach { screen ->
                        val selected = currentRoute == screen.route

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon
                                    else screen.unselectedIcon,
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        val chatBottomBarHeight = innerPadding.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = Screen.HOME.route,
            modifier = if (currentRoute == Screen.CHAT.route) {
                Modifier.padding(top = innerPadding.calculateTopPadding())
            } else {
                Modifier.padding(innerPadding)
            }
        ) {
            composable(Screen.HOME.route) {
                HomeScreen(
                    viewModel = discoverViewModel,
                    onOpenRole = { roleId -> navController.navigate(DiscoverRoutes.detail(roleId)) },
                    onCreateRole = { navController.navigate(SettingsRoutes.CHARACTER) }
                )
            }
            composable(
                route = DiscoverRoutes.DETAIL,
                arguments = listOf(navArgument("roleId") { type = NavType.StringType })
            ) { entry ->
                val roleId = entry.arguments?.getString("roleId").orEmpty()
                DiscoverRoleDetailScreen(
                    roleId = roleId,
                    viewModel = discoverViewModel,
                    onBack = { navController.popBackStack() },
                    onEditRole = { importedRoleId ->
                        navController.navigate(SettingsRoutes.editCharacter(importedRoleId))
                    },
                    onStartChat = { importedRoleId ->
                        coroutineScope.launch {
                            chatViewModel.startRoleConversation(importedRoleId)
                            navController.navigate(Screen.CHAT.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable(Screen.CHAT.route) { ChatScreen(viewModel = chatViewModel, bottomBarHeight = chatBottomBarHeight) }
            composable(Screen.MEMORY.route) {
                MemoryScreen(memoryViewModel = viewModel(factory = viewModelFactory))
            }
            composable(Screen.SETTINGS.route) {
                SettingsScreen(
                    onNavigateToCharacter = { navController.navigate(SettingsRoutes.CHARACTER) },
                    onNavigateToSkills = { navController.navigate(SettingsRoutes.SKILLS) },
                    onNavigateToMemory = { navController.navigate(Screen.MEMORY.route) },
                    onNavigateToModel = { target ->
                        navController.navigate(SettingsRoutes.modelWithTarget(target.name))
                    },
                    onNavigateToVoice = { navController.navigate(SettingsRoutes.VOICE) },
                    onNavigateToLanguage = { navController.navigate(SettingsRoutes.LANGUAGE) },
                    onNavigateToDarkMode = { navController.navigate(SettingsRoutes.DARK_MODE) },
                    onNavigateToAbout = { navController.navigate(SettingsRoutes.ABOUT) },
                    onNavigateToProfile = { navController.navigate(SettingsRoutes.PROFILE) }
                )
            }
            composable(SettingsRoutes.CHARACTER) {
                CharacterManagementScreen(
                    onBack = { navController.popBackStack() },
                    onActivateRoleCard = { roleId -> chatViewModel.activateRoleCard(roleId) },
                    roleManagementViewModel = viewModel(factory = viewModelFactory),
                    onStartChat = { roleId ->
                        coroutineScope.launch {
                            chatViewModel.startRoleConversation(roleId)
                            navController.navigate(Screen.CHAT.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
            composable(
                route = SettingsRoutes.EDIT_CHARACTER,
                arguments = listOf(navArgument("roleId") { type = NavType.LongType })
            ) { entry ->
                val editRoleId = entry.arguments?.getLong("roleId") ?: 0L
                CharacterManagementScreen(
                    onBack = { navController.popBackStack() },
                    onActivateRoleCard = { roleId -> chatViewModel.activateRoleCard(roleId) },
                    roleManagementViewModel = viewModel(factory = viewModelFactory),
                    onStartChat = { roleId ->
                        coroutineScope.launch {
                            chatViewModel.startRoleConversation(roleId)
                            navController.navigate(Screen.CHAT.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    editRoleId = editRoleId
                )
            }
            composable(SettingsRoutes.SKILLS) {
                SkillsManagementScreen(
                    onBack = { navController.popBackStack() },
                    onActivateSkill = { skillId -> chatViewModel.activateSkill(skillId) },
                    skillsManagementViewModel = viewModel(factory = viewModelFactory)
                )
            }
            composable(
                SettingsRoutes.MODEL_WITH_TARGET,
                arguments = listOf(
                    navArgument("scrollTarget") {
                        type = NavType.StringType
                        defaultValue = "DEFAULT"
                    }
                )
            ) { backStackEntry ->
                val scrollTargetName = backStackEntry.arguments?.getString("scrollTarget") ?: "DEFAULT"
                val scrollTarget = try {
                    ModelConfigScrollTarget.valueOf(scrollTargetName)
                } catch (e: Exception) {
                    ModelConfigScrollTarget.DEFAULT
                }
                ModelConfigScreen(
                    scrollTarget = scrollTarget,
                    onBack = { navController.popBackStack() },
                    onModelConfigChanged = { chatViewModel.initializeEngine() },
                    viewModel = viewModel(factory = viewModelFactory)
                )
            }
            composable(SettingsRoutes.VOICE) {
                VoiceSettingsScreen(
                    onBack = { navController.popBackStack() },
                    viewModel = viewModel(factory = viewModelFactory)
                )
            }
            composable(SettingsRoutes.LANGUAGE) {
                LanguageSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoutes.DARK_MODE) {
                DarkModeSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoutes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(SettingsRoutes.PROFILE) {
                UserProfileScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
