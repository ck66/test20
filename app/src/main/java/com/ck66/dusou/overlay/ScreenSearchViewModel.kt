package com.ck66.dusou.overlay

import android.graphics.Bitmap
import com.ck66.dusou.matcher.MatchResult
import com.ck66.dusou.matcher.TextMatcher
import com.ck66.dusou.ocr.OcrEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScreenSearchState {
    data object Idle : ScreenSearchState
    data object Capturing : ScreenSearchState
    data object Recognizing : ScreenSearchState
    data object Matching : ScreenSearchState
    data class Result(val match: MatchResult) : ScreenSearchState
    data class NotFound(val ocrText: String) : ScreenSearchState
    data class Error(val message: String) : ScreenSearchState
}

class ScreenSearchViewModel(
    private val ocrEngine: OcrEngine,
    private val textMatcher: TextMatcher
) {

    // 自管理 CoroutineScope，不依赖 Activity/viewModelScope 生命周期
    // 因为 FloatingBallService 持有此 ViewModel，Service 的 onCreate/destroy 负责起停
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<ScreenSearchState>(ScreenSearchState.Idle)
    val state: StateFlow<ScreenSearchState> = _state.asStateFlow()

    fun searchFromScreenCapture(bitmap: Bitmap) {
        _state.value = ScreenSearchState.Recognizing

        scope.launch {
            try {
                val ocrResult = withContext(Dispatchers.IO) {
                    ocrEngine.recognize(bitmap)
                }

                if (ocrResult.text.isBlank()) {
                    _state.value = ScreenSearchState.Error("未能识别到文字，请重试")
                    return@launch
                }

                _state.value = ScreenSearchState.Matching

                val match = withContext(Dispatchers.IO) {
                    textMatcher.findBestMatch(ocrResult.text)
                }

                if (match.matched && match.question != null) {
                    _state.value = ScreenSearchState.Result(match)
                } else {
                    _state.value = ScreenSearchState.NotFound(ocrText = ocrResult.text)
                }
            } catch (e: Exception) {
                _state.value = ScreenSearchState.Error("识别失败: ${e.message}")
            }
        }
    }

    fun reset() {
        _state.value = ScreenSearchState.Idle
    }

    fun destroy() {
        scope.cancel()
    }
}
