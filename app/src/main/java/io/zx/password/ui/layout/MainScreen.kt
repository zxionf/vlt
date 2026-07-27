package io.zx.password.ui.layout

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import io.zx.password.PasswordEntry
import io.zx.password.PwdViewModel
import io.zx.password.PwdViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var editingItem by remember { mutableStateOf<PasswordEntry?>(null) }
    val viewModel: PwdViewModel = viewModel(factory = PwdViewModelFactory(LocalContext.current))

    Scaffold(
        bottomBar = {
            if (currentRoute in listOf("home", "search", "setting")) {
                BottomNavigationBar(navController)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavigationGraph(navController, editingItem, viewModel) { editingItem = it }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavController) {
    val items = listOf(
        BottomNavItem.Home,
        BottomNavItem.Search,
        BottomNavItem.Setting
    )

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route

        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (currentRoute == item.route) item.selectedIcon else item.icon,
                        contentDescription = item.title
                    )
                },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        // 避免在导航栈中创建多个相同的实例
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        // 避免重新创建已经存在的实例
                        launchSingleTop = true
                        // 恢复之前的状态
                        restoreState = false
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun NavigationGraph(
    navController: NavHostController,
    editingItem: PasswordEntry?,
    viewModel: PwdViewModel,
    setEditingItem: (PasswordEntry?) -> Unit
) {
    NavHost(
        navController = navController,
        startDestination = BottomNavItem.Home.route,
        enterTransition = { fadeIn(animationSpec = tween(durationMillis = 100)) },
        exitTransition = { fadeOut(animationSpec = tween(durationMillis = 100)) },
        popEnterTransition = { fadeIn(animationSpec = tween(durationMillis = 100)) },
        popExitTransition = { fadeOut(animationSpec = tween(durationMillis = 100)) },
    ) {
        composable(BottomNavItem.Home.route) {
            HomeScreen(
                onAddClick = {
                    setEditingItem(null)
                    navController.navigate("create_password")
                },
                onTagManageClick = { navController.navigate("tag_manage") },
                onEditItem = { item ->
                    setEditingItem(item)
                    navController.navigate("create_password")
                },
                viewModel = viewModel
            )
        }
        composable("tag_manage") {
            TagManageScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel
            )
        }
        composable("create_password") {
            CreatePasswordScreen(
                editItem = editingItem,
                onBack = {
                    setEditingItem(null)
                    navController.popBackStack()
                },
                viewModel = viewModel
            )
        }
        composable(BottomNavItem.Search.route) {
            SearchScreen(viewModel = viewModel)
        }
        composable(BottomNavItem.Setting.route) {
            SettingScreen()
        }
    }
}