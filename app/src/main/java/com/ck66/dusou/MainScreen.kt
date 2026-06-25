package com.ck66.dusou

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navItems = listOf(
        BottomNavItem("搜题", Icons.Default.Search),
        BottomNavItem("题库", Icons.Default.LibraryBooks),
        BottomNavItem("我的", Icons.Default.Person),
    )
    var selectedIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("读屏搜题") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedIndex) {
            0 -> SearchScreen(modifier = Modifier.padding(innerPadding))
            1 -> QuestionBankScreen(modifier = Modifier.padding(innerPadding))
            2 -> ProfileScreen(modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    // TODO: 搜题页面（读屏搜题 + 拍照搜题）
}

@Composable
fun QuestionBankScreen(modifier: Modifier = Modifier) {
    // TODO: 题库页面（导入题库 + 搜索题目 + 题库练习）
}

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    // TODO: 我的页面（设置 + 关于）
}
