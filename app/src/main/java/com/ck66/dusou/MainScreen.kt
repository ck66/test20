package com.ck66.dusou

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.ck66.dusou.repository.QuestionRepositoryProvider
import com.ck66.dusou.ui.bank.BankViewModel
import com.ck66.dusou.ui.bank.QuestionBankContent

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
    val context = LocalContext.current
    val repository = remember { QuestionRepositoryProvider.get() }
    val viewModel = remember { BankViewModel(repository) }
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.importBank(context, it) }
    }

    QuestionBankContent(
        uiState = uiState,
        onImportClick = { filePickerLauncher.launch(arrayOf("*/*")) },
        onDeleteBank = { viewModel.deleteBank(it) },
        onConfirmDelete = { viewModel.confirmDeleteBank(it) },
        onDismissDelete = { viewModel.dismissDeleteDialog() },
        onSearch = { viewModel.searchQuestions(it) },
        onClearSearch = { viewModel.clearSearch() },
        onDismissImport = { viewModel.dismissImport() },
        onStartPractice = { bankId -> /* TODO: 导航到练习页面 */ },
        modifier = modifier
    )
}

@Composable
fun ProfileScreen(modifier: Modifier = Modifier) {
    // TODO: 我的页面（设置 + 关于）
}
