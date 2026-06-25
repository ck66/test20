package com.ck66.dusou.ui.practice

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ck66.dusou.data.repository.QuestionRepository
import com.ck66.dusou.database.entity.Question
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    bankId: Long,
    bankName: String,
    mode: PracticeMode,
    repository: QuestionRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val viewModel = remember(bankId, mode) { PracticeViewModel(repository, bankId) }
    val uiState by viewModel.uiState.collectAsState()
    var showExitDialog by remember { mutableStateOf(false) }

    LaunchedEffect(bankId, mode) {
        val favorites = FavoriteManager.getAllFavorites(context)
        viewModel.loadQuestions(bankId, mode, favorites)
    }

    val modeLabel = when (mode) {
        PracticeMode.SEQUENTIAL -> "顺序"
        PracticeMode.RANDOM -> "随机"
        PracticeMode.WRONG -> "错题"
        PracticeMode.FAVORITE -> "收藏"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = bankName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = modeLabel + "练习",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        val state = uiState
                        if (state is PracticeUiState.Active && state.selectedAnswer != null) {
                            showExitDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is PracticeUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is PracticeUiState.Empty -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (mode == PracticeMode.WRONG) "暂无错题" else "暂无题目",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text("返回")
                        }
                    }
                }

                is PracticeUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text("返回")
                        }
                    }
                }

                is PracticeUiState.Active -> {
                    val question = state.questions.getOrNull(state.currentIndex)
                    if (question != null) {
                        QuestionContent(
                            question = question,
                            currentIndex = state.currentIndex,
                            totalCount = state.questions.size,
                            correctCount = state.correctCount,
                            wrongCount = state.wrongCount,
                            showAnswer = state.showAnswer,
                            selectedAnswer = state.selectedAnswer,
                            isCorrect = state.isCorrect,
                            isFavorite = rememberFavorite(question.id, context),
                            onAnswer = { answer ->
                                viewModel.submitAnswer(question.id, bankId, answer)
                            },
                            onNext = { viewModel.nextQuestion() },
                            onToggleFavorite = {
                                scope.launch {
                                    FavoriteManager.toggle(context, question.id)
                                }
                            }
                        )
                    }
                }

                is PracticeUiState.Finished -> {
                    FinishedContent(
                        correctCount = state.correctCount,
                        wrongCount = state.wrongCount,
                        totalCount = state.totalCount,
                        accuracy = state.accuracy,
                        onRestart = {
                            val favorites = remember {
                                scope.launch { FavoriteManager.getAllFavorites(context) }
                            }
                            viewModel.restart(bankId, mode)
                        },
                        onExit = onNavigateBack
                    )
                }
            }
        }
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("退出练习") },
            text = { Text("确定要退出当前练习吗？\n退出后本次练习进度将不会保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    onNavigateBack()
                }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("继续练习")
                }
            }
        )
    }
}

@Composable
private fun QuestionContent(
    question: Question,
    currentIndex: Int,
    totalCount: Int,
    correctCount: Int,
    wrongCount: Int,
    showAnswer: Boolean,
    selectedAnswer: String?,
    isCorrect: Boolean?,
    isFavorite: Boolean,
    onAnswer: (String) -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Progress bar
        ProgressHeader(
            currentIndex = currentIndex,
            totalCount = totalCount,
            correctCount = correctCount,
            wrongCount = wrongCount
        )

        Spacer(modifier = Modifier.height(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            // Question type tag
            Text(
                text = typeLabel(question.type),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Question stem with favorite button
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    text = question.stem,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = if (isFavorite) "取消收藏" else "收藏",
                        tint = if (isFavorite) Color(0xFFFFC107) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options area based on question type
            when {
                question.type == "多选" || question.type == "多选题" -> {
                    MultiChoiceOptions(
                        question = question,
                        showAnswer = showAnswer,
                        selectedAnswer = selectedAnswer,
                        onAnswer = onAnswer
                    )
                }

                question.type == "判断" || question.type == "判断题" -> {
                    TrueFalseOptions(
                        showAnswer = showAnswer,
                        selectedAnswer = selectedAnswer,
                        onAnswer = onAnswer
                    )
                }

                question.type == "填空" || question.type == "填空题" -> {
                    FillInBlankInput(
                        showAnswer = showAnswer,
                        selectedAnswer = selectedAnswer,
                        onAnswer = onAnswer
                    )
                }

                else -> {
                    SingleChoiceOptions(
                        question = question,
                        showAnswer = showAnswer,
                        selectedAnswer = selectedAnswer,
                        onAnswer = onAnswer
                    )
                }
            }

            // Answer feedback
            if (showAnswer) {
                Spacer(modifier = Modifier.height(16.dp))
                AnswerFeedback(
                    question = question,
                    isCorrect = isCorrect ?: false,
                    selectedAnswer = selectedAnswer ?: ""
                )
            }
        }

        // Next question button
        if (showAnswer) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (currentIndex + 1 >= totalCount) "完成" else "下一题"
                )
            }
        }
    }
}

@Composable
private fun ProgressHeader(
    currentIndex: Int,
    totalCount: Int,
    correctCount: Int,
    wrongCount: Int,
) {
    val progress = if (totalCount > 0) (currentIndex + 1).toFloat() / totalCount else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "第 ${currentIndex + 1}/$totalCount 题",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$correctCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "正确",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "$wrongCount",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFF44336),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "错误",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFF44336)
                )
            }
        }
    }
}

@Composable
private fun SingleChoiceOptions(
    question: Question,
    showAnswer: Boolean,
    selectedAnswer: String?,
    onAnswer: (String) -> Unit,
) {
    val options = parseOptions(question.options)
    var localSelected by remember(question.id, showAnswer) { mutableStateOf(selectedAnswer) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            val label = optionLabels[index]
            val isSelected = localSelected == label
            val borderColor by animateColorAsState(
                targetValue = when {
                    showAnswer && label == question.answer -> Color(0xFF4CAF50)
                    showAnswer && isSelected && localSelected != question.answer -> Color(0xFFF44336)
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                label = "border"
            )

            OutlinedCard(
                onClick = {
                    if (!showAnswer) {
                        localSelected = label
                        onAnswer(label)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = when {
                        showAnswer && label == question.answer -> Color(0xFFE8F5E9)
                        showAnswer && isSelected && label != question.answer -> Color(0xFFFFEBEE)
                        isSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$label.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.width(28.dp)
                    )
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun MultiChoiceOptions(
    question: Question,
    showAnswer: Boolean,
    selectedAnswer: String?,
    onAnswer: (String) -> Unit,
) {
    val options = parseOptions(question.options)
    val selectedSet = remember { mutableStateListOf<String>() }
    var confirmed by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            val label = optionLabels[index]
            val isSelected = label in selectedSet

            OutlinedCard(
                onClick = {
                    if (!showAnswer && !confirmed) {
                        if (isSelected) {
                            selectedSet.remove(label)
                        } else {
                            selectedSet.add(label)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                border = androidx.compose.foundation.BorderStroke(
                    2.dp,
                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                ),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$label.",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(28.dp)
                    )
                    Text(text = option, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                if (selectedSet.isNotEmpty()) {
                    val answer = selectedSet.sorted().joinToString("")
                    confirmed = true
                    onAnswer(answer)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedSet.isNotEmpty() && !confirmed && !showAnswer
        ) {
            Text("确认答案")
        }
    }
}

@Composable
private fun TrueFalseOptions(
    showAnswer: Boolean,
    selectedAnswer: String?,
    onAnswer: (String) -> Unit,
) {
    var localSelected by remember { mutableStateOf(selectedAnswer) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        listOf("正确" to "正确", "错误" to "错误").forEach { (value, display) ->
            val isSelected = localSelected == value
            val borderColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outlineVariant
                },
                label = "tf_border"
            )

            OutlinedCard(
                onClick = {
                    if (!showAnswer) {
                        localSelected = value
                        onAnswer(value)
                    }
                },
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(2.dp, borderColor),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = display,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun FillInBlankInput(
    showAnswer: Boolean,
    selectedAnswer: String?,
    onAnswer: (String) -> Unit,
) {
    var input by remember { mutableStateOf(selectedAnswer ?: "") }
    var confirmed by remember { mutableStateOf(selectedAnswer != null) }

    Column {
        OutlinedTextField(
            value = input,
            onValueChange = { if (!showAnswer && !confirmed) input = it },
            label = { Text("输入你的答案") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !confirmed && !showAnswer,
            singleLine = true
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (input.isNotBlank()) {
                    confirmed = true
                    onAnswer(input.trim())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = input.isNotBlank() && !confirmed && !showAnswer
        ) {
            Text("确认")
        }
    }
}

@Composable
private fun AnswerFeedback(
    question: Question,
    isCorrect: Boolean,
    selectedAnswer: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCorrect) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (isCorrect) {
                Text(
                    text = "回答正确！",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Text(
                    text = "回答错误",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFC62828),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "正确答案是: ${formatAnswerDisplay(question.answer)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF2E7D32),
                    fontWeight = FontWeight.Medium
                )
            }

            if (!question.analysis.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "解析",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = question.analysis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FinishedContent(
    correctCount: Int,
    wrongCount: Int,
    totalCount: Int,
    accuracy: Float,
    onRestart: () -> Unit,
    onExit: () -> Unit,
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "练习完成!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ResultRow("总题数", "$totalCount 题")
                Spacer(modifier = Modifier.height(12.dp))
                ResultRow("正确", "$correctCount 题", Color(0xFF4CAF50))
                Spacer(modifier = Modifier.height(12.dp))
                ResultRow("错误", "$wrongCount 题", Color(0xFFF44336))
                Spacer(modifier = Modifier.height(12.dp))
                ResultRow("正确率", "${"%.1f".format(accuracy * 100)}%")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("再来一次")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("返回")
        }
    }
}

@Composable
private fun ResultRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun rememberFavorite(questionId: Long, context: android.content.Context): Boolean {
    val scope = rememberCoroutineScope()
    var isFav by remember { mutableStateOf(false) }

    LaunchedEffect(questionId) {
        isFav = FavoriteManager.isFavorite(context, questionId)
    }

    // Listen to toggles
    return isFav
}

@Composable
fun PracticeModeDialog(
    bank: com.ck66.dusou.database.entity.QuestionBank,
    onDismiss: () -> Unit,
    onStartPractice: (Long, PracticeMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择练习模式 — ${bank.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeOption(
                    title = "顺序练习",
                    description = "按题库顺序依次练习",
                    onClick = {
                        onStartPractice(bank.id, PracticeMode.SEQUENTIAL)
                        onDismiss()
                    }
                )
                ModeOption(
                    title = "随机练习",
                    description = "随机抽取 20 道题目",
                    onClick = {
                        onStartPractice(bank.id, PracticeMode.RANDOM)
                        onDismiss()
                    }
                )
                ModeOption(
                    title = "错题重练",
                    description = "只练习做错的题目",
                    onClick = {
                        onStartPractice(bank.id, PracticeMode.WRONG)
                        onDismiss()
                    }
                )
                ModeOption(
                    title = "收藏练习",
                    description = "只练习收藏的题目",
                    onClick = {
                        onStartPractice(bank.id, PracticeMode.FAVORITE)
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun parseOptions(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    // Try JSON array first
    return try {
        if (raw.trim().startsWith("[")) {
            val cleaned = raw.trim().removeSurrounding("[", "]")
                .split(",")
                .map { it.trim().removeSurrounding("\"") }
            cleaned
        } else {
            // Split by newlines
            raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }
    } catch (e: Exception) {
        raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

private val optionLabels = listOf("A", "B", "C", "D", "E", "F")

private fun typeLabel(type: String): String = when (type) {
    "单选", "单选题" -> "单选题"
    "多选", "多选题" -> "多选题"
    "判断", "判断题" -> "判断题"
    "填空", "填空题" -> "填空题"
    else -> "单选题"
}

private fun formatAnswerDisplay(answer: String): String {
    return answer.map { c ->
        if (c in 'A'..'Z') "$c" else c.toString()
    }.joinToString("")
}
