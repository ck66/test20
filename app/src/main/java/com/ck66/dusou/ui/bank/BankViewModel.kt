package com.ck66.dusou.ui.bank

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ck66.dusou.data.repository.QuestionRepository
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.entity.QuestionBank
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

sealed interface BankUiState {
    data object Loading : BankUiState
    data class Success(
        val banks: List<QuestionBank>,
        val searchResults: List<Question>,
        val isSearching: Boolean,
        val importState: ImportState
    ) : BankUiState
    data class Error(val message: String) : BankUiState
}

sealed interface ImportState {
    data object Idle : ImportState
    data object Importing : ImportState
    data class Success(val bankName: String) : ImportState
    data class Failed(val error: String) : ImportState
}

class BankViewModel(private val repository: QuestionRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<BankUiState>(BankUiState.Loading)
    val uiState: StateFlow<BankUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")

    private var currentBanks: List<QuestionBank> = emptyList()
    private var currentSearchResults: List<Question> = emptyList()
    private var currentImportState: ImportState = ImportState.Idle

    init {
        viewModelScope.launch {
            repository.getAllBanks().collect { banks ->
                currentBanks = banks
                emitSuccessState()
            }
        }

        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .collectLatest { query ->
                    if (query.isBlank()) {
                        currentSearchResults = emptyList()
                        emitSuccessState(isSearching = false)
                    } else {
                        emitSuccessState(isSearching = true)
                        try {
                            currentSearchResults = repository.searchQuestions(query)
                            emitSuccessState(isSearching = false)
                        } catch (e: Exception) {
                            _uiState.value = BankUiState.Error("搜索失败: ${e.message}")
                        }
                    }
                }
        }
    }

    private fun emitSuccessState(isSearching: Boolean = false) {
        _uiState.value = BankUiState.Success(
            banks = currentBanks,
            searchResults = currentSearchResults,
            isSearching = isSearching,
            importState = currentImportState
        )
    }

    fun importBank(context: Context, uri: Uri, bankName: String) {
        viewModelScope.launch {
            currentImportState = ImportState.Importing
            emitSuccessState()

            repository.importBank(context, uri, bankName)
                .onSuccess {
                    currentImportState = ImportState.Success(bankName)
                    emitSuccessState()
                }
                .onFailure { e ->
                    currentImportState = ImportState.Failed(e.message ?: "导入失败")
                    emitSuccessState()
                }
        }
    }

    fun deleteBank(bankId: Long) {
        viewModelScope.launch {
            try {
                repository.deleteBank(bankId)
            } catch (e: Exception) {
                _uiState.value = BankUiState.Error("删除失败: ${e.message}")
            }
        }
    }

    fun searchQuestions(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun dismissImportState() {
        currentImportState = ImportState.Idle
        emitSuccessState()
    }
}
