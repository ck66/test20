package com.ck66.dusou.matcher

import android.content.Context
import com.ck66.dusou.data.repository.QuestionRepository
import com.ck66.dusou.data.repository.QuestionRepositoryProvider
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.entity.QuestionBank
import com.ck66.dusou.util.FileLogger
import com.ck66.dusou.util.OcrFilterPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.apache.commons.text.similarity.LevenshteinDistance

class TextMatcher(
    // 允许外部注入 Repository；默认通过 lazy 延迟获取，避免构造时 Provider.get() 未初始化崩溃
    private val _repository: QuestionRepository? = null,
    // 用于 OCR 过滤词配置（可选，传 null 使用硬编码默认过滤）
    private val context: Context? = null
) {

    private val repository by lazy { _repository ?: QuestionRepositoryProvider.get() }

    /**
     * Levenshtein 实例，在构造时设置阈值避免 O(m*n) 全量计算。
     * Commons Text 的 `LevenshteinDistance(threshold)` 构造器在距离超出阈值时返回 -1。
     */
    private val levenshtein = LevenshteinDistance(MAX_EDIT_DISTANCE)

    companion object {
        private const val FTS_TOP_N = 30
        private const val JACCARD_TOP_N = 10
        private const val MATCH_THRESHOLD = 0.65f
        /**
         * 编辑距离计算上限，超出此距离的文本直接判 0 相似度。
         * 在长文本场景下有效降低 O(m*n) 开销。
         */
        private const val MAX_EDIT_DISTANCE = 30
    }

    suspend fun findBestMatch(ocrText: String, bankId: Long? = null): MatchResult {
        FileLogger.i("TextMatcher", "findBestMatch: ocrText='${ocrText.take(200)}', bankId=$bankId")

        if (ocrText.isBlank()) {
            FileLogger.w("TextMatcher", "ocrText is blank, returning no match")
            return MatchResult(
                question = null,
                bank = null,
                similarity = 0f,
                matched = false,
                ocrText = ocrText
            )
        }

        return coroutineScope {
            // Level 1: FTS 粗筛
            val ftsResults = async {
                val query = buildFtsQuery(ocrText)
                FileLogger.i("TextMatcher", "FTS query: '$query'")
                repository.searchQuestions(query, bankId).take(FTS_TOP_N)
            }

            val ftsQuestions = ftsResults.await()
            FileLogger.i("TextMatcher", "FTS results: ${ftsQuestions.size} questions")

            if (ftsQuestions.isEmpty()) {
                FileLogger.w("TextMatcher", "FTS returned empty, no match")
                return@coroutineScope MatchResult(
                    question = null,
                    bank = null,
                    similarity = 0f,
                    matched = false,
                    ocrText = ocrText
                )
            }

            // 从 OCR 文本提取题干，去掉选项文字和题型前缀
            val ocrStem = extractStemFromOcr(ocrText)
            FileLogger.i("TextMatcher", "ocrStem='$ocrStem'")

            // Level 2: Jaccard 2-gram 精排
            val jaccardScored = ftsQuestions.map { question ->
                val similarity = jaccardSimilarity(ocrStem, question.stem)
                question to similarity
            }.sortedByDescending { it.second }
                .take(JACCARD_TOP_N)

            // Level 3: Levenshtein 编辑距离确认
            val bestMatch = jaccardScored.maxByOrNull { (question, _) ->
                levenshteinSimilarity(ocrStem, question.stem)
            }

            if (bestMatch != null) {
                val (question, jaccardScore) = bestMatch
                val editSimilarity = levenshteinSimilarity(ocrStem, question.stem)
                // Weighted combination: 60% edit distance, 40% Jaccard
                val combinedSimilarity = (editSimilarity * 0.6f + jaccardScore * 0.4f).toFloat()

                FileLogger.i("TextMatcher", "Best match: stem='${question.stem.take(80)}', jaccard=$jaccardScore, edit=$editSimilarity, combined=$combinedSimilarity, threshold=$MATCH_THRESHOLD")

                MatchResult(
                    question = question,
                    bank = null,
                    similarity = combinedSimilarity.coerceIn(0f, 1f),
                    matched = combinedSimilarity > MATCH_THRESHOLD,
                    ocrText = ocrText
                )
            } else {
                MatchResult(
                    question = null,
                    bank = null,
                    similarity = 0f,
                    matched = false,
                    ocrText = ocrText
                )
            }
        }
    }

    private fun jaccardSimilarity(a: String, b: String): Double {
        val aGrams = a.windowed(2).toSet()
        val bGrams = b.windowed(2).toSet()
        val intersection = aGrams.intersect(bGrams).size
        val union = aGrams.union(bGrams).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    private fun levenshteinSimilarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val distance = levenshtein.apply(a, b)
        return if (distance < 0) 0.0 else 1.0 - distance.toDouble() / maxLen
    }

    private fun buildFtsQuery(ocrText: String): String {
        val keywords = ocrText
            .replace(Regex("""[\p{Punct}\s\p{S}]+"""), " ")
            .trim()
            .split(" ")
            .filter { it.length >= 2 }
            .distinct()
            .take(10)
            .map { it.replace("\"", "") }

        return if (keywords.isNotEmpty()) {
            keywords.joinToString(" OR ") { "\"$it\"*" }
        } else {
            ocrText.take(20).trim()
                .replace(Regex("""["*]"""), "")
                .split(Regex("""\s+"""))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" OR ") { "\"$it\"*" }
        }
    }

    /**
     * 从 OCR 文本中提取题干部分。
     *
     * 策略：在全文中找到第一个 "选项组起始"（A 选项 + 附近有 B 选项），
     * 取 A 选项之前的文字作为题干，再过滤掉答案行、题型标签等噪声。
     *
     * 支持选项排列方式：
     * - 每行一个：A.xxx\nB.xxx\nC.xxx\nD.xxx
     * - 全部一行：A.xxx B.xxx C.xxx D.xxx
     * - 两个一行：A.xxx B.xxx\nC.xxx D.xxx
     * - 混合排列
     */
    private fun extractStemFromOcr(ocrText: String): String {
        // 匹配选项标记：A. A、 A） 等（行首或空格后）
        val optionPattern = Regex("""(?:^|\n|\s)([A-Da-d])[.、)．]""")

        val matches = optionPattern.findAll(ocrText).toList()
        if (matches.isEmpty()) {
            // 没有任何选项标记，整个文本就是题干
            return cleanStemText(ocrText)
        }

        // 找第一个 "A" 选项，且其后 200 字符内有 "B" 选项
        var optionGroupStart: Int = -1

        for (i in matches.indices) {
            val match = matches[i]
            val letter = match.groupValues[1].uppercase()

            if (letter == "A") {
                // 在后续 matches 中找 B，且距离不超过 50 字符
                val aEndPos = match.range.last
                for (j in (i + 1) until matches.size) {
                    val nextMatch = matches[j]
                    val nextLetter = nextMatch.groupValues[1].uppercase()
                    val distance = nextMatch.range.first - aEndPos

                    if (distance > 50) break  // 太远了，不是同一组

                    if (nextLetter == "B") {
                        // 找到 A+B 组合，确认选项组起始
                        optionGroupStart = match.range.first
                        break
                    }
                }
                if (optionGroupStart >= 0) break
            }
        }

        val stemText = if (optionGroupStart >= 0) {
            ocrText.substring(0, optionGroupStart).trim()
        } else {
            // 找不到 A+B 组合，回退：找第一个选项标记，但有连续 2+ 个选项时才截取
            val firstMatch = matches.first()
            val firstPos = firstMatch.range.first

            // 检查第一个选项标记后 50 字符内是否有其他选项标记
            val hasNearbyOption = matches.any { m ->
                m.range.first != firstMatch.range.first &&
                m.range.first - firstPos in 1..50
            }

            if (hasNearbyOption) {
                ocrText.substring(0, firstPos).trim()
            } else {
                // 没有选项组，整个文本作为题干
                ocrText.trim()
            }
        }

        return cleanStemText(stemText)
    }

    /**
     * 清理题干文本：过滤答案行、题型标签、进度指示等噪声。
     */
    private fun cleanStemText(text: String): String {
        return if (context != null) {
            cleanStemTextWithPrefs(text)
        } else {
            cleanStemTextDefault(text)
        }
    }

    /**
     * 使用 OcrFilterPreferences 过滤。
     */
    private fun cleanStemTextWithPrefs(text: String): String {
        val filters = OcrFilterPreferences.getEffectiveFilters(context!!)
        val isFilterEnabled = OcrFilterPreferences.isFilterEnabled(context!!)

        return text.lines()
            .filter { line ->
                if (!isFilterEnabled) return@filter true
                line.isNotBlank() && !filters.any { filter -> line.matches(Regex(filter)) }
            }
            .joinToString("\n")
            .trim()
    }

    /**
     * 硬编码默认过滤（向后兼容，context 为 null 时使用）。
     */
    private fun cleanStemTextDefault(text: String): String {
        return text.lines()
            .filter { line ->
                line.isNotBlank() &&
                !line.matches(Regex("""^\s*(单选题|多选题|判断题|填空题|简答题)\s*$""")) &&
                !line.matches(Regex("""^\s*第\s*\d+\s*/\s*\d+\s*题\s*$""")) &&
                !line.matches(Regex("""^\s*\d*正确\d*错误\s*$""")) &&
                !line.matches(Regex("""^\s*O正\s*$""")) &&
                !line.matches(Regex("""^\s*☆\s*$""")) &&
                !line.matches(Regex("""^\s*(屏幕共享|共享中|顺序练习|随机练习)\s*$""")) &&
                !line.matches(Regex("""^\s*题库\d*\.txt\s*$""")) &&
                !line.matches(Regex("""^\s*答案[:：]\s*[A-Da-d]+\s*$""")) &&
                !line.matches(Regex("""^\s*[A-Da-d][.、)．]\s*.+$"""))
            }
            .joinToString("\n")
            .trim()
    }
}

data class MatchResult(
    val question: Question?,
    val bank: QuestionBank?,
    val similarity: Float,
    val matched: Boolean,
    val ocrText: String
)
