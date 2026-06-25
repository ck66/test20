package com.ck66.dusou.parser

import com.ck66.dusou.parser.model.ParsedQuestion
import org.json.JSONArray
import org.json.JSONObject

class JsonQuizParser : QuizParser {

    override fun supportedFormats(): List<String> = listOf("json")

    override fun parse(content: String): List<ParsedQuestion> {
        val questions = mutableListOf<ParsedQuestion>()

        val root = JSONObject(content.trim())
        val questionsArray: JSONArray? = root.optJSONArray("questions")

        if (questionsArray == null || questionsArray.length() == 0) {
            return questions
        }

        for (i in 0 until questionsArray.length()) {
            val obj = questionsArray.getJSONObject(i)
            val question = parseQuestionObject(obj)
            if (question != null) {
                questions.add(question)
            }
        }
        return questions
    }

    private fun parseQuestionObject(obj: JSONObject): ParsedQuestion? {
        val stem = obj.optString("stem", null) ?: return null
        val type = obj.optString("type", "single")

        // 处理 options：支持 JSON数组 或 null
        val optionsJson: String? = run {
            val optArray = obj.optJSONArray("options")
            if (optArray != null && optArray.length() > 0) {
                optArray.toString()
            } else {
                obj.optString("options", null)
            }
        }

        val answer = obj.optString("answer", "")
        val analysis = obj.optString("analysis", null)

        return ParsedQuestion(
            type = type,
            stem = stem,
            options = optionsJson,
            answer = answer,
            analysis = analysis
        )
    }
}
