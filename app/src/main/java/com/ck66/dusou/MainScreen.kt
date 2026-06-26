package com.ck66.dusou

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ck66.dusou.data.repository.QuestionRepositoryProvider
import com.ck66.dusou.matcher.TextMatcher
import com.ck66.dusou.ocr.OcrEngineProvider
import com.ck66.dusou.ui.bank.BankViewModel
import com.ck66.dusou.ui.bank.QuestionBankContent
import com.ck66.dusou.ui.practice.PracticeMode
import com.ck66.dusou.ui.practice.PracticeModeDialog
import com.ck66.dusou.ui.practice.PracticeScreen
import com.ck66.dusou.ui.practice.WrongQuestionScreen
import com.ck66.dusou.ui.profile.ProfileScreen
import com.ck66.dusou.ui.search.PhotoSearchScreen
import com.ck66.dusou.ui.search.SearchUiState
import com.ck66.dusou.ui.search.SearchViewModel

private data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
)

private sealed class Screen {
    data class Practice(val bankId: Long, val bankName: String, val mode: PracticeMode) : Screen()
    data class WrongBook(val bankId: Long) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navItems = listOf(
        BottomNavItem("搜题", Icons.Default.Search),
        BottomNavItem("题库", Icons.Default.LibraryBooks),
        BottomNavItem("我的", Icons.Default.Person),
    )
    var selectedIndex by remember { mutableIntStateOf(0) }

    // Search tab state
    var showCamera by remember { mutableStateOf(false) }

    // Practice navigation state
    var currentScreen by remember { mutableStateOf<Screen?>(null) }
    var practiceModeTarget by remember { mutableStateOf<com.ck66.dusou.database.entity.QuestionBank?>(null) }

    Scaffold(
        topBar = {
            if (!showCamera || selectedIndex != 0) {
                if (currentScreen == null) {
                    TopAppBar(
                        title = { Text("读屏搜题") },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        },
        bottomBar = {
            if ((!showCamera || selectedIndex != 0) && currentScreen == null) {
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
        }
    ) { innerPadding ->
        // Check for practice/wrong book navigation
        when (val screen = currentScreen) {
            is Screen.Practice -> {
                val repository = remember { QuestionRepositoryProvider.get() }
                PracticeScreen(
                    bankId = screen.bankId,
                    bankName = screen.bankName,
                    mode = screen.mode,
                    repository = repository,
                    onNavigateBack = { currentScreen = null },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            is Screen.WrongBook -> {
                val repository = remember { QuestionRepositoryProvider.get() }
                WrongQuestionScreen(
                    bankId = screen.bankId,
                    repository = repository,
                    onStartRetry = {
                        currentScreen = Screen.Practice(
                            bankId = screen.bankId,
                            bankName = "错题重练",
                            mode = PracticeMode.WRONG
                        )
                    },
                    onNavigateBack = { currentScreen = null },
                    modifier = Modifier.padding(innerPadding)
                )
            }

            null -> {
                when (selectedIndex) {
                    0 -> {
                        if (showCamera) {
                            val ocrEngine = remember { OcrEngineProvider.get() }
                            val textMatcher = remember { TextMatcher() }
                            val searchViewModel = remember {
                                SearchViewModel(ocrEngine, textMatcher)
                            }
                            PhotoSearchScreen(
                                viewModel = searchViewModel,
                                onNavigateBack = { showCamera = false },
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else {
                            TextSearchScreen(
                                onOpenCamera = { showCamera = true },
                                modifier = Modifier.padding(innerPadding)
                            )
                        }
                    }
                    1 -> QuestionBankScreen(
                        onStartPractice = { bankId, bank ->
                            practiceModeTarget = bank
                        },
                        onWrongBook = { bankId ->
                            currentScreen = Screen.WrongBook(bankId)
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                    2 -> {
                        val profileRepository = remember { QuestionRepositoryProvider.get() }
                        ProfileScreen(
                            repository = profileRepository,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }

        // Practice mode dialog
        practiceModeTarget?.let { bank ->
            PracticeModeDialog(
                bank = bank,
                onDismiss = { practiceModeTarget = null },
                onStartPractice = { bankId, mode ->
                    currentScreen = Screen.Practice(
                        bankId = bankId,
                        bankName = bank.name,
                        mode = mode
                    )
                    practiceModeTarget = null
                }
            )
        }
    }
}

@Composable
fun TextSearchScreen(
    onOpenCamera: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ocrEngine = remember { OcrEngineProvider.get() }
    val textMatcher = remember { TextMatcher() }
    val viewModel = remember { SearchViewModel(ocrEngine, textMatcher) }
    val uiState by viewModel.uiState.collectAsState()

    var searchText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Search input with camera button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text("输入题目关键词搜索") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = {
                        viewModel.searchFromText(searchText)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索"
                        )
                    }
                }
            )

            IconButton(onClick = onOpenCamera) {
                Icon(
                    imageVector = Icons.Default.Camera,
                    contentDescription = "拍照搜题",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content area
        Box(modifier = Modifier.weight(1f)) {
            when (val state = uiState) {
                is SearchUiState.Idle -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Two big action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Read screen search button
                            Card(
                                onClick = {
                                    context.startActivity(Intent(context, com.ck66.dusou.overlay.ScreenSearchActivity::class.java))
                                },
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "读屏搜题",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // Photo search button
                            Card(
                                onClick = onOpenCamera,
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "拍照搜题",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "或在上方输入题目关键词搜索",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                is SearchUiState.Matching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                is SearchUiState.Result -> {
                    val match = state.match
                    val question = match.question
                    if (question != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "匹配成功 · 相似度 ${"%.0f".format(match.similarity * 100)}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "题目",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = question.stem, style = MaterialTheme.typography.bodyLarge)
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "答案",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = question.answer,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (!question.analysis.isNullOrBlank()) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(
                                            text = "解析",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = question.analysis ?: "",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                is SearchUiState.NotFound -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "未找到匹配题目",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (searchText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "\"${searchText}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                is SearchUiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                is SearchUiState.Recognizing -> {}
            }
        }
    }
}

@Composable
fun QuestionBankScreen(
    onStartPractice: (Long, com.ck66.dusou.database.entity.QuestionBank) -> Unit,
    onWrongBook: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember { QuestionRepositoryProvider.get() }
    val viewModel = remember { BankViewModel(repository) }
    val uiState by viewModel.uiState.collectAsState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val bankName = getFileName(context, it) ?: it.lastPathSegment ?: "未命名题库"
            viewModel.importBank(context, it, bankName)
        }
    }

    QuestionBankContent(
        uiState = uiState,
        onImportClick = { filePickerLauncher.launch(arrayOf("*/*")) },
        onDeleteBank = { viewModel.deleteBank(it) },
        onSearch = { viewModel.searchQuestions(it) },
        onClearSearch = { viewModel.clearSearch() },
        onDismissImport = { viewModel.dismissImportState() },
        onStartPractice = onStartPractice,
        onWrongBook = onWrongBook,
        modifier = modifier
    )
}

/**
 * 从 Content URI 获取真实文件名，避免 lastPathSegment 返回 id 而非文件名
 */
private fun getFileName(context: android.content.Context, uri: Uri): String? {
    var name: String? = null
    try {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx)
                }
            }
    } catch (_: Exception) {}
    return name
}
