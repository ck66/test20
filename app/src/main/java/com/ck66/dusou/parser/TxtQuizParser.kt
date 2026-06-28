package com.ck66.dusou.parser

import com.ck66.dusou.parser.model.ParsedQuestion

class TxtQuizParser : QuizParser {

    override fun supportedFormats(): List<String> = listOf("txt")

    override fun parse(content: String): List<ParsedQuestion> {
        val questions = mutableListOf<ParsedQuestion>()

        // 按空行分隔题目块
        val blocks = content.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (block in blocks) {
            val question = parseBlock(block)
            if (question != null) {
                questions.add(question)
            }
        }
        return questions
    }

    private fun parseBlock(block: String): ParsedQuestion? {
        if (block.isBlank()) return null

        val type = extractTag(block, "题型") ?: return null
        val mappedType = mapQuestionType(type)

        val stemText = extractTag(block, "题目") ?: return null
        val answerText = extractTagContent(block, "答案")
        val analysisText = extractTagContent(block, "解析")

        // 题干（纯文本，不含选项）
        val (stem, options) = extractStemAndOptions(stemText)

        // 处理答案
        val answer = processAnswer(answerText, mappedType)

        val optionsJson = if (options.isNotEmpty()) {
            val jsonArray = org.json.JSONArray()
            options.forEach { jsonArray.put(it) }
            jsonArray.toString()
        } else null

        return ParsedQuestion(
            type = mappedType,
            stem = stem.trim(),
            options = optionsJson,
            answer = answer,
            analysis = analysisText
        )
    }

    private fun extractStemAndOptions(text: String): Pair<String, List<String>> {
        val lines = text.lines()
        val optionLines = mutableListOf<String>()
        val stemLines = mutableListOf<String>()

        val optionPattern = Regex("^[A-Za-z]\\s*[.、]")

        for (line in lines) {
            if (optionPattern.containsMatchIn(line.trim())) {
                optionLines.add(line.trim())
            } else {
                stemLines.add(line)
            }
        }

        val stem = stemLines.joinToString("\n")
        return Pair(stem, optionLines)
    }

    private fun extractTag(block: String, tag: String): String? {
        val escapedTag = Regex.escape(tag)
        val patterns = listOf(
            "【$escapedTag】",       // 全角括号
            "\\[$escapedTag\\]"      // 半角括号（需转义）
        )
        for (p in patterns) {
            val regex = Regex(p)
            val match = regex.find(block) ?: continue
            // 找到标签后提取内容直到下一个标签或行尾
            val afterTag = block.substring(match.range.last + 1)
            val nextTagIndex = findNextTag(afterTag)
            return if (nextTagIndex >= 0) {
                afterTag.substring(0, nextTagIndex).trim()
            } else {
                afterTag.trim()
            }
        }
        return null
    }

    private fun extractTagContent(block: String, tag: String): String? {
        return extractTag(block, tag)
    }

    private fun findNextTag(afterTag: String): Int {
        val tagRegex = Regex("[\\【\\[]题型[】\\]]|[\\【\\[]题目[】\\]]|[\\【\\[]答案[】\\]]|[\\【\\[]解析[】\\]]")
        val match = tagRegex.find(afterTag)
        return match?.range?.first ?: -1
    }

    private fun mapQuestionType(type: String): String {
        return when {
            type.contains("单选") -> "single"
            type.contains("多选") -> "multi"
            type.contains("判断") -> "judge"
            type.contains("填空") -> "fill"
            else -> "single"
        }
    }

    private fun processAnswer(answer: String?, type: String): String {
        if (answer == null) return ""
        val trimmed = answer.trim()
        return when (type) {
            "judge" -> {
                when {
                    trimmed.contains("正确") || trimmed == "对"|| trimmed == "√" -> "正确"
                    trimmed.contains("错误") || trimmed == "错"|| trimmed == "×" -> "错误"
                    else -> trimmed
                }
            }
            "multi" -> trimmed.filter { it.isLetterOrDigit() }
            "single", "fill" -> trimmed
            else -> trimmed
        }
    }
}
