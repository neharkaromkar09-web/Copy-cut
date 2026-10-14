package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    data object Templates : Screen("templates", "Templates", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
    data object Create : Screen("create", "Create", Icons.Filled.AddCircle, Icons.Outlined.AddCircleOutline)
    data object Library : Screen("library", "Library", Icons.Filled.Folder, Icons.Outlined.Folder)
    data object Profile : Screen("profile", "Profile", Icons.Filled.Person, Icons.Outlined.Person)

    companion object {
        val bottomNavItems: List<Screen>
            get() = listOf(Home, Templates, Create, Library, Profile)
    }
}
