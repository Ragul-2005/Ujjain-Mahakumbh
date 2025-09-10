package com.mahakumbh.crowdsafety.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController

data class BottomNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun BottomNavigationBar(navController: NavHostController, currentRoute: String, items: List<BottomNavItem>) {
	NavigationBar {
		items.forEach { item ->
			NavigationBarItem(
				selected = currentRoute == item.route,
				onClick = {
					if (currentRoute != item.route) {
						navController.navigate(item.route) {
							popUpTo(navController.graph.startDestinationId) { saveState = true }
							launchSingleTop = true
							restoreState = true
						}
					}
				},
				icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
				label = { Text(item.label) }
			)
		}
	}
}
