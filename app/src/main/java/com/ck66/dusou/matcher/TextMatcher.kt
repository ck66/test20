package com.ck66.dusou.matcher

import com.ck66.dusou.data.repository.QuestionRepository
import com.ck66.dusou.data.repository.QuestionRepositoryProvider
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.entity.QuestionBank
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.apache.commons.text.similarity.LevenshteinDistance

class TextMatcher(
    private val repository: QuestionRepository = QuestionRepositoryProvider.get()
) {

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
        if (ocrText.isBlank()) {
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
                repository.searchQuestions(query).take(FTS_TOP_N)
            }

            val ftsQuestions = ftsResults.await()

            if (ftsQuestions.isEmpty()) {
                return@coroutineScope MatchResult(
                    question = null,
                    bank = null,
                    similarity = 0f,
                    matched = false,
                    ocrText = ocrText
                )
            }

            // Level 2: Jaccard 2-gram 精排
            val jaccardScored = ftsQuestions.map { question ->
                val similarity = jaccardSimilarity(ocrText, question.stem)
                question to similarity
            }.sortedByDescending { it.second }
                .take(JACCARD_TOP_N)

            // Level 3: Levenshtein 编辑距离确认
            val bestMatch = jaccardScored.maxByOrNull { (question, _) ->
                levenshteinSimilarity(ocrText, question.stem)
            }

            if (bestMatch != null) {
                val (question, jaccardScore) = bestMatch
                val editSimilarity = levenshteinSimilarity(ocrText, question.stem)
                // Weighted combination: 60% edit distance, 40% Jaccard
                val combinedSimilarity = (editSimilarity * 0.6f + jaccardScore * 0.4f).toFloat()

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
            .take(10)
            .joinToString(" ") { "$it*" }

        return keywords.ifBlank {
            ocrText.take(20).trim().split(" ").joinToString(" ") { "$it*" }
        }
    }
}

data class MatchResult(
    val question: Question?,
    val bank: QuestionBank?,
    val similarity: Float,
    val matched: Boolean,
    val ocrText: String
)
