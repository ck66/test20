package com.ck66.dusou.ui.search

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ck66.dusou.matcher.TextMatcher
import com.ck66.dusou.matcher.MatchResult
import com.ck66.dusou.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Recognizing : SearchUiState
    data object Matching : SearchUiState
    data class Cropping(val bitmap: Bitmap) : SearchUiState
    data class Result(val match: MatchResult) : SearchUiState
    data class NotFound(val ocrText: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

class SearchViewModel(
    private val ocrEngine: OcrEngine,
    private val textMatcher: TextMatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap: StateFlow<Bitmap?> = _capturedBitmap.asStateFlow()

    fun setCapturedBitmap(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _uiState.value = SearchUiState.Cropping(bitmap)
    }

    fun searchFromCroppedBitmap(originalBitmap: Bitmap, cropRect: com.ck66.dusou.util.CropRect) {
        val cropped = Bitmap.createBitmap(
            originalBitmap,
            cropRect.x.coerceIn(0, originalBitmap.width),
            cropRect.y.coerceIn(0, originalBitmap.height),
            cropRect.width.coerceAtMost(originalBitmap.width - cropRect.x),
            cropRect.height.coerceAtMost(originalBitmap.height - cropRect.y)
        )
        _capturedBitmap.value = cropped
        searchFromBitmap(cropped)
    }

    fun searchFromBitmapDirect(bitmap: Bitmap) {
        // 跳过裁剪，直接 OCR
        _capturedBitmap.value = bitmap
        searchFromBitmap(bitmap)
    }

    fun searchFromBitmap(bitmap: Bitmap) {
        _capturedBitmap.value = bitmap
        _uiState.value = SearchUiState.Recognizing

        viewModelScope.launch {
            try {
                val ocrResult = withContext(Dispatchers.IO) {
                    ocrEngine.recognize(bitmap)
                }

                if (ocrResult.text.isBlank()) {
                    _uiState.value = SearchUiState.Error("未能识别到文字，请重试")
                    return@launch
                }

                _uiState.value = SearchUiState.Matching

                val match = withContext(Dispatchers.IO) {
                    textMatcher.findBestMatch(ocrResult.text)
                }

                if (match.matched && match.question != null) {
                    _uiState.value = SearchUiState.Result(match)
                } else {
                    _uiState.value = SearchUiState.NotFound(ocrResult.text)
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error("识别失败: ${e.message}")
            }
        }
    }

    fun searchFromText(text: String) {
        if (text.isBlank()) {
            _uiState.value = SearchUiState.Error("请输入搜索内容")
            return
        }

        _uiState.value = SearchUiState.Matching

        viewModelScope.launch {
            try {
                val match = withContext(Dispatchers.IO) {
                    textMatcher.findBestMatch(text)
                }

                if (match.matched && match.question != null) {
                    _uiState.value = SearchUiState.Result(match)
                } else {
                    _uiState.value = SearchUiState.NotFound(text)
                }
            } catch (e: Exception) {
                _uiState.value = SearchUiState.Error("搜索失败: ${e.message}")
            }
        }
    }

    fun clearResult() {
        _uiState.value = SearchUiState.Idle
        _capturedBitmap.value = null
    }

    fun clearCapturedBitmap() {
        _capturedBitmap.value = null
    }
}

/**
 * Factory for SearchViewModel that binds to Compose ViewModelStoreOwner lifecycle.
 * Using viewModel(factory = ...) ensures the ViewModel survives recomposition
 * and tab switches, preventing coroutine leaks.
 */
class SearchViewModelFactory(
    private val ocrEngine: OcrEngine,
    private val textMatcher: TextMatcher
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SearchViewModel::class.java)) {
            return SearchViewModel(ocrEngine, textMatcher) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
