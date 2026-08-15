package com.cycling.beecount.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cycling.beecount.ui.assistant.AssistantRoute
import com.cycling.beecount.ui.ledger.LedgerRoute

/**
 * 应用根导航：底部「今日 / 账本」两个 tab（ADR 0006）。
 * 用 saveState/restoreState 保留各 tab 的页面状态。
 */
@Composable
fun BeeCountApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == "assistant",
                    onClick = {
                        navController.navigate("assistant") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("今日") },
                )
                NavigationBarItem(
                    selected = currentRoute == "ledger",
                    onClick = {
                        navController.navigate("ledger") {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("账本") },
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "assistant",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("assistant") { AssistantRoute() }
            composable("ledger") { LedgerRoute() }
        }
    }
}
