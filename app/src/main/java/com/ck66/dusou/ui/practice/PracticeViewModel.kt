package com.ck66.dusou.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ck66.dusou.data.repository.QuestionRepository
import com.ck66.dusou.database.entity.PracticeRecord
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.model.PracticeStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class PracticeMode { SEQUENTIAL, RANDOM, WRONG, FAVORITE }

sealed interface PracticeUiState {
    data object Loading : PracticeUiState
    data class Active(
        val questions: List<Question>,
        val currentIndex: Int,
        val correctCount: Int,
        val wrongCount: Int,
        val isFinished: Boolean,
        val showAnswer: Boolean,
        val selectedAnswer: String?,
        val stats: PracticeStats?
    ) : PracticeUiState
    data class Finished(
        val correctCount: Int,
        val wrongCount: Int,
        val totalCount: Int,
        val accuracy: Float
    ) : PracticeUiState
    data class Error(val message: String) : PracticeUiState
}

class PracticeViewModel(
    private val repository: QuestionRepository,
    private val bankId: Long
) : ViewModel() {

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
                        questions = repository.getQuestions(bankId, 50, 0).first()
                    }
                    PracticeMode.RANDOM -> {
                        questions = repository.getRandomQuestions(bankId, 20)
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
                    _uiState.value = PracticeUiState.Error("没有找到题目")
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
            val isCorrect = question.answer.trim().equals(userAnswer.trim(), ignoreCase = true)

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
                    emitActiveState(showAnswer = true, selectedAnswer = userAnswer)
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
        currentMode = mode
        currentFavoriteIds = favoriteIds
        loadQuestions(bankId, mode, favoriteIds)
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

    private fun emitActiveState(
        showAnswer: Boolean = false,
        selectedAnswer: String? = null,
        isFinished: Boolean = false
    ) {
        _uiState.value = PracticeUiState.Active(
            questions = questions,
            currentIndex = currentIndex,
            correctCount = correctCount,
            wrongCount = wrongCount,
            isFinished = isFinished,
            showAnswer = showAnswer,
            selectedAnswer = selectedAnswer,
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
