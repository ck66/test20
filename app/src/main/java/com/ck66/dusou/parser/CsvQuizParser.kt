package com.ck66.dusou.parser

import com.ck66.dusou.parser.model.ParsedQuestion
import org.json.JSONArray

class CsvQuizParser : QuizParser {

    override fun supportedFormats(): List<String> = listOf("csv")

    override fun parse(content: String): List<ParsedQuestion> {
        val questions = mutableListOf<ParsedQuestion>()
        val lines = splitCsvLines(content)

        if (lines.size < 2) return questions

        // 第一行为表头
        val header = parseCsvLine(lines[0])

        // 查找列索引
        val typeIdx = header.indexOfFirst { it.trim() == "题型" }
        val stemIdx = header.indexOfFirst { it.trim() == "题目" }
        val optionAIdx = header.indexOfFirst { it.trim() == "选项A" }
        val optionBIdx = header.indexOfFirst { it.trim() == "选项B" }
        val optionCIdx = header.indexOfFirst { it.trim() == "选项C" }
        val optionDIdx = header.indexOfFirst { it.trim() == "选项D" }
        val answerIdx = header.indexOfFirst { it.trim() == "答案" }
        val analysisIdx = header.indexOfFirst { it.trim() == "解析" }

        if (stemIdx < 0) return questions

        for (i in 1 until lines.size) {
            val cols = parseCsvLine(lines[i])
            val question = buildQuestion(
                cols,
                typeIdx, stemIdx,
                optionAIdx, optionBIdx, optionCIdx, optionDIdx,
                answerIdx, analysisIdx
            )
            if (question != null) {
                questions.add(question)
            }
        }
        return questions
    }

    private fun buildQuestion(
        cols: List<String>,
        typeIdx: Int, stemIdx: Int,
        optionAIdx: Int, optionBIdx: Int, optionCIdx: Int, optionDIdx: Int,
        answerIdx: Int, analysisIdx: Int
    ): ParsedQuestion? {
        if (stemIdx >= cols.size) return null

        val stem = cols[stemIdx].trim()
        if (stem.isEmpty()) return null

        val type = if (typeIdx in cols.indices) {
            mapQuestionType(cols[typeIdx].trim())
        } else "single"

        // 构建选项列表
        val optionIndices = listOf(optionAIdx, optionBIdx, optionCIdx, optionDIdx)
        val options = optionIndices.mapNotNull { idx ->
            if (idx in cols.indices) {
                val value = cols[idx].trim()
                if (value.isNotEmpty()) value else null
            } else null
        }

        val optionsJson = if (options.isNotEmpty()) {
            val jsonArray = JSONArray()
            options.forEachIndexed { index, option ->
                jsonArray.put("${('A' + index)}. $option")
            }
            jsonArray.toString()
        } else null

        val answer = if (answerIdx in cols.indices) cols[answerIdx].trim() else ""
        val analysis = if (analysisIdx in cols.indices) {
            val v = cols[analysisIdx].trim()
            if (v.isNotEmpty()) v else null
        } else null

        return ParsedQuestion(
            type = type,
            stem = stem,
            options = optionsJson,
            answer = answer,
            analysis = analysis
        )
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

    private fun splitCsvLines(text: String): List<String> {
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (ch in text) {
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                    current.append(ch)
                }
                ch == '\n' && !inQuotes -> {
                    lines.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        if (current.isNotEmpty()) {
            lines.add(current.toString())
        }
        return lines
    }

    private fun parseCsvLine(line: String): List<String> {
        val cols = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false

        for (ch in line) {
            when {
                ch == '"' -> {
                    inQuotes = !inQuotes
                }
                ch == ',' && !inQuotes -> {
                    cols.add(current.toString())
                    current = StringBuilder()
                }
                else -> current.append(ch)
            }
        }
        cols.add(current.toString())
        return cols
    }
}
