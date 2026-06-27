package com.ck66.dusou.matcher

import com.ck66.dusou.data.repository.QuestionRepository
import com.ck66.dusou.data.repository.QuestionRepositoryProvider
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.entity.QuestionBank
import com.ck66.dusou.util.FileLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.apache.commons.text.similarity.LevenshteinDistance

class TextMatcher(
    // 允许外部注入 Repository；默认通过 lazy 延迟获取，避免构造时 Provider.get() 未初始化崩溃
    private val _repository: QuestionRepository? = null
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
        // LevenshteinDistance(threshold) 在距离超出阈值时返回 -1
        val distance = levenshtein.apply(a, b)
        return if (distance < 0) 0.0 else 1.0 - distance.toDouble() / maxLen
    }

    private fun buildFtsQuery(ocrText: String): String {
        val keywords = ocrText
            .replace(Regex("[\\p{Punct}\\s]+"), " ")
            .trim()
            .split(" ")
            .filter { it.length >= 2 }
            .distinct()
            .take(10)
            .joinToString(" OR ") { "\"$it\"*" }  // OR 语义：匹配任意关键词即可

        return keywords.ifBlank {
            ocrText.take(20).trim()
                .replace(Regex("[\"*]"), "")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" OR ") { "\"$it\"*" }
        }
    }

    /**
     * 从 OCR 文本中提取题干部分（去掉选项文字）。
     * 
     * 策略：找到第一个选项行（A. / A、/ A）的位置，
     * 取该行之前的所有文字作为题干。
     */
    private fun extractStemFromOcr(ocrText: String): String {
        // 匹配选项行开头的模式：A. A、 A）等
        val optionPattern = Regex("""(?m)^\s*[A-Da-d][.、)．]\s*""")
        val match = optionPattern.find(ocrText)

        val stemText = if (match != null) {
            ocrText.substring(0, match.range.first).trim()
        } else {
            ocrText.trim()
        }

        // 过滤掉"单选题""多选题""判断题"等题型前缀行
        return stemText.lines()
            .filter { line ->
                !line.matches(Regex("""^\s*(单选题|多选题|判断题|填空题|简答题)\s*$"""))
            }
            .joinToString("")
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
