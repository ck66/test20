package com.ck66.dusou.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ck66.dusou.data.repository.QuestionRepository
import com.ck66.dusou.database.entity.PracticeRecord
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.model.PracticeStats
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PracticeMode { SEQUENTIAL, RANDOM, WRONG, FAVORITE }

class PracticeViewModelFactory(
    private val repository: QuestionRepository,
    private val bankId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(
        modelClass: Class<T>,
        extras: CreationExtras
    ): T {
        if (modelClass.isAssignableFrom(PracticeViewModel::class.java)) {
            return PracticeViewModel(repository, bankId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

sealed interface PracticeUiState {
    data object Loading : PracticeUiState
    data class Active(
        val questions: List<Question>,
        val currentIndex: Int,
        val correctCount: Int,
        val wrongCount: Int,
        val showAnswer: Boolean,
        val selectedAnswer: String?,
        val isCorrect: Boolean?,
        val stats: PracticeStats?
    ) : PracticeUiState
    data class Finished(
        val correctCount: Int,
        val wrongCount: Int,
        val totalCount: Int,
        val accuracy: Float
    ) : PracticeUiState
    data object Empty : PracticeUiState
    data class Error(val message: String) : PracticeUiState
}

class PracticeViewModel(
    private val repository: QuestionRepository,
    private val bankId: Long
) : ViewModel() {

    companion object {
        /** 随机练习默认抽取题目数 */
        const val RANDOM_QUESTION_COUNT = 20
    }

    private val _uiState = MutableStateFlow<PracticeUiState>(PracticeUiState.Loading)
    val uiState: StateFlow<PracticeUiState> = _uiState.asStateFlow()

    private var questions: List<Question> = emptyList()
    private var currentIndex: Int = 0
    private var correctCount: Int = 0
    private var wrongCount: Int = 0
    private var currentMode: PracticeMode? = null
    private var currentFavoriteIds: List<Long> = emptyList()

    fun loadQuestions(bankId: Long, mode: PracticeMode, favoriteIds: List<Long> = emptyList()) {
        viewModelScope.launch {
            _uiState.value = PracticeUiState.Loading
            try {
                when (mode) {
                    PracticeMode.SEQUENTIAL -> {
                        questions = repository.getQuestions(bankId, Int.MAX_VALUE, 0).first()
                    }
                    PracticeMode.RANDOM -> {
                        questions = repository.getRandomQuestions(bankId, RANDOM_QUESTION_COUNT)
                    }
                    PracticeMode.WRONG -> {
                        questions = repository.getWrongQuestions(bankId).first()
                    }
                    PracticeMode.FAVORITE -> {
                        questions = repository.getFavoriteQuestions(bankId, favoriteIds).first()
                    }
                }
                currentMode = mode
                currentFavoriteIds = favoriteIds
                currentIndex = 0
                correctCount = 0
                wrongCount = 0

                if (questions.isEmpty()) {
                    _uiState.value = PracticeUiState.Empty
                } else {
                    emitActiveState()
                }
            } catch (e: Exception) {
                _uiState.value = PracticeUiState.Error("加载题目失败: ${e.message}")
            }
        }
    }

    fun submitAnswer(questionId: Long, bankId: Long, userAnswer: String) {
        viewModelScope.launch {
            val question = questions.getOrNull(currentIndex) ?: return@launch

            val normalizedUser = normalizeAnswer(userAnswer, question.type)
            val normalizedCorrect = normalizeAnswer(question.answer, question.type)
            val isCorrect = normalizedUser == normalizedCorrect

            val record = PracticeRecord(
                questionId = questionId,
                bankId = bankId,
                userAnswer = userAnswer,
                isCorrect = isCorrect,
                practiceTime = System.currentTimeMillis()
            )

            try {
                repository.submitAnswer(record)

                if (isCorrect) {
                    correctCount++
                } else {
                    wrongCount++
                }

                if (currentIndex + 1 >= questions.size) {
                    emitFinishedState()
                } else {
                    emitActiveState(showAnswer = true, selectedAnswer = userAnswer, isCorrect = isCorrect)
                }
            } catch (e: Exception) {
                _uiState.value = PracticeUiState.Error("提交答案失败: ${e.message}")
            }
        }
    }

    fun nextQuestion() {
        if (currentIndex + 1 >= questions.size) {
            _uiState.value = PracticeUiState.Finished(
                correctCount = correctCount,
                wrongCount = wrongCount,
                totalCount = questions.size,
                accuracy = if (questions.isNotEmpty()) correctCount.toFloat() / questions.size else 0f
            )
        } else {
            currentIndex++
            emitActiveState()
        }
    }

    fun restart(bankId: Long, mode: PracticeMode, favoriteIds: List<Long> = emptyList()) {
        val favs = if (favoriteIds.isNotEmpty()) favoriteIds else currentFavoriteIds
        loadQuestions(bankId, mode, favs)
    }

    fun loadStats(bankId: Long) {
        viewModelScope.launch {
            try {
                val stats = repository.getPracticeStats(bankId)
                val currentState = _uiState.value
                if (currentState is PracticeUiState.Active) {
                    _uiState.value = currentState.copy(stats = stats)
                }
            } catch (e: Exception) {
                _uiState.value = PracticeUiState.Error("加载统计失败: ${e.message}")
            }
        }
    }

    /**
     * 标准化答案，处理多选顺序问题和判断题映射问题
     * - 多选题：排序后比较，避免顺序不同被判错
     * - 判断题：将"正确"/"错误"映射到"A"/"B"（根据实际题库格式）
     */
    private fun normalizeAnswer(answer: String, type: String): String {
        return when {
            type == "多选" || type == "多选题" -> {
                answer.trim()
                    .replace(Regex("[,，、\\s]"), "")  // 移除所有分隔符
                    .toCharArray().sorted().joinToString("").uppercase()
            }
            type == "判断" || type == "判断题" -> {
                when (answer.trim()) {
                    "正确", "对", "A", "a" -> "A"
                    "错误", "错", "B", "b" -> "B"
                    else -> answer.trim().uppercase()
                }
            }
            else -> answer.trim().uppercase()
        }
    }

    private fun emitActiveState(
        showAnswer: Boolean = false,
        selectedAnswer: String? = null,
        isCorrect: Boolean? = null
    ) {
        _uiState.value = PracticeUiState.Active(
            questions = questions,
            currentIndex = currentIndex,
            correctCount = correctCount,
            wrongCount = wrongCount,
            showAnswer = showAnswer,
            selectedAnswer = selectedAnswer,
            isCorrect = isCorrect,
            stats = null
        )
    }

    private fun emitFinishedState() {
        _uiState.value = PracticeUiState.Finished(
            correctCount = correctCount,
            wrongCount = wrongCount,
            totalCount = questions.size,
            accuracy = if (questions.isNotEmpty()) correctCount.toFloat() / questions.size else 0f
        )
    }
}
