package io.zx.password.ui.layout

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    data object Home : BottomNavItem(
        route = "home",
        title = "首页",
        icon = Icons.Default.Home,
        selectedIcon = Icons.Default.Home
    )

    data object Search : BottomNavItem(
        route = "search",
        title = "搜索",
        icon = Icons.Default.Search,
        selectedIcon = Icons.Default.Search
    )

    data object Setting : BottomNavItem(
        route = "setting",
        title = "设置",
        icon = Icons.Default.Settings,
        selectedIcon = Icons.Default.Settings
    )
}