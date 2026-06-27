package com.ck66.dusou.parser

import com.ck66.dusou.parser.model.ParsedQuestion

/**
 * .txt 文件智能分发 Parser。
 *
 * 嗅探内容特征，自动选择最合适的子 Parser：
 * 1. 含 【题型】/【题目】 标签 → TxtQuizParser（原有标签格式）
 * 2. 第一行是 {...}              → NdJsonQuizParser（NDJSON 格式）
 * 3. 其他                         → StructuredTxtParser（结构特征识别）
 */
class SmartTxtParser : QuizParser {

    override fun supportedFormats(): List<String> = listOf("txt")

    override fun parse(content: String): List<ParsedQuestion> {
        return detectParser(content).parse(content)
    }

    private fun detectParser(content: String): QuizParser {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return StructuredTxtParser()

        val firstLine = trimmed.lines().firstOrNull()?.trim() ?: return StructuredTxtParser()

        // NDJSON：第一行是 {...}
        if (firstLine.startsWith("{") && firstLine.endsWith("}")) {
            return NdJsonQuizParser()
        }

        // 标签格式：包含 【题型】 或 【题目】
        if (trimmed.contains("【题型】") || trimmed.contains("【题目】") ||
            trimmed.contains("[题型]") || trimmed.contains("[题目]")) {
            return TxtQuizParser()
        }

        // 其他：结构特征识别
        return StructuredTxtParser()
    }
}
