package com.ck66.dusou.parser

import com.ck66.dusou.parser.model.ParsedQuestion
import org.json.JSONArray
import org.json.JSONObject

/**
 * NDJSON / JSONL 格式 Parser。
 * 每行一个独立 JSON 对象，字段兼容多种命名：
 *   q / question / 题目 → 题干
 *   a / options / 选项  → 选项数组
 *   ans / answer / 答案 → 答案
 *   analysis / 解析     → 解析
 */
class NdJsonQuizParser : QuizParser {

    override fun supportedFormats(): List<String> = listOf("txt", "jsonl", "ndjson")

    override fun parse(content: String): List<ParsedQuestion> {
        val questions = mutableListOf<ParsedQuestion>()

        content.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("//") }
            .forEach { line ->
                try {
                    val obj = JSONObject(line)
                    parseLine(obj)?.let { questions.add(it) }
                } catch (_: Exception) {
                    // 跳过无法解析的行
                }
            }

        return questions
    }

    private fun parseLine(obj: JSONObject): ParsedQuestion? {
        val stem = obj.optString("q", obj.optString("question", obj.optString("题目", "")))
        if (stem.isBlank()) return null

        val optionsArray = obj.optJSONArray("a")
            ?: obj.optJSONArray("options")
            ?: obj.optJSONArray("选项")

        val optionsJson: String? = if (optionsArray != null && optionsArray.length() > 0) {
            val formatted = JSONArray()
            for (i in 0 until optionsArray.length()) {
                val letter = 'A' + i
                formatted.put("$letter. ${optionsArray.optString(i, "")}")
            }
            formatted.toString()
        } else null

        val ans = obj.optString("ans", obj.optString("answer", obj.optString("答案", "")))

        val type = inferType(optionsArray, ans)

        // 判断题：A/B → 正确/错误
        val answer = if (type == "judge" && optionsArray != null) {
            when (ans.trim().uppercase()) {
                "A", "1" -> optionsArray.optString(0, "正确")
                "B", "2" -> optionsArray.optString(1, "错误")
                else -> ans
            }
        } else {
            ans
        }

        val analysis = obj.optString("analysis", obj.optString("解析", "")).ifBlank { null }

        return ParsedQuestion(
            type = type,
            stem = stem,
            options = optionsJson,
            answer = answer,
            analysis = analysis
        )
    }

    private fun inferType(optionsArray: JSONArray?, ans: String): String {
        if (optionsArray != null) {
            if (optionsArray.length() == 2) {
                val opt0 = optionsArray.optString(0, "")
                val opt1 = optionsArray.optString(1, "")
                if ((opt0 == "正确" || opt0 == "对" || opt0 == "√") &&
                    (opt1 == "错误" || opt1 == "错" || opt1 == "×")) {
                    return "judge"
                }
            }
            if (ans.length > 1 && ans.all { it.isLetter() }) {
                return "multi"
            }
            return "single"
        }
        return "fill"
    }
}
