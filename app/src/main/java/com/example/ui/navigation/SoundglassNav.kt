package com.example.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.AnalyzerScreen
import com.example.ui.screens.DspDocsScreen
import com.example.ui.screens.GeneratorScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.MeterScreen
import com.example.ui.screens.PrivacyAndSourceScreen
import com.example.ui.theme.CyanAccentDark
import com.example.ui.theme.PurplePrimaryDark
import com.example.ui.viewmodel.AnalyzerViewModel
import com.example.ui.viewmodel.GeneratorViewModel
import com.example.ui.viewmodel.HistoryViewModel
import com.example.ui.viewmodel.MeterViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Meter : Screen("meter", "Meter", Icons.Default.VolumeUp)
    object Analyzer : Screen("analyzer", "Analyzer", Icons.Default.GraphicEq)
    object Generator : Screen("generator", "Generator", Icons.Default.Waves)
    object History : Screen("history", "History", Icons.Default.History)
    object Privacy : Screen("privacy", "Privacy", Icons.Default.Shield)
    object Docs : Screen("docs", "DSP Info", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundglassNav(
    meterViewModel: MeterViewModel,
    analyzerViewModel: AnalyzerViewModel,
    generatorViewModel: GeneratorViewModel,
    historyViewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Meter.route

    val bottomNavItems = listOf(
        Screen.Meter,
        Screen.Analyzer,
        Screen.Generator,
        Screen.History
    )

    val showBottomBar = currentRoute in bottomNavItems.map { it.route }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            if (showBottomBar) {
                TopAppBar(
                    title = {
                        Text(
                            text = "Soundglass",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(
                            onClick = { navController.navigate(Screen.Docs.route) },
                            modifier = Modifier.testTag("nav_docs_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "DSP Info",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { navController.navigate(Screen.Privacy.route) },
                            modifier = Modifier.testTag("nav_privacy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Privacy & Source",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("bottom_nav_bar")
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = (currentRoute == screen.route)
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (currentRoute != screen.route) {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.testTag("nav_tab_${screen.route}"),
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = PurplePrimaryDark,
                                selectedTextColor = PurplePrimaryDark,
                                indicatorColor = PurplePrimaryDark.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Meter.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Meter.route) {
                MeterScreen(
                    viewModel = meterViewModel,
                    onNavigateToDocs = { navController.navigate(Screen.Docs.route) }
                )
            }

            composable(Screen.Analyzer.route) {
                AnalyzerScreen(viewModel = analyzerViewModel)
            }

            composable(Screen.Generator.route) {
                GeneratorScreen(viewModel = generatorViewModel)
            }

            composable(Screen.History.route) {
                HistoryScreen(viewModel = historyViewModel)
            }

            composable(Screen.Privacy.route) {
                PrivacyAndSourceScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.Docs.route) {
                DspDocsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
