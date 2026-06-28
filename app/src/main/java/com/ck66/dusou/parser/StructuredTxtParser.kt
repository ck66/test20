package com.ck66.dusou.parser

import com.ck66.dusou.parser.model.ParsedQuestion
import org.json.JSONArray

/**
 * 基于结构特征识别的通用纯文本题库 Parser。
 *
 * 不依赖固定前缀格式，而是通过"题干行 + 连续选项行 + 答案行"的
 * 上下文结构来识别题目。
 *
 * 支持的格式变体：
 * - 无序号纯文本
 * - 数字序号（1. 2. 3、）
 * - Markdown 加粗序号（**1.** **2.**）
 * - 括号序号（(1) （1） (一)）
 * - 有无"答案："前缀
 * - 有无解析
 * - 判断题（选项为"正确"/"错误"）
 * - 单选题、多选题
 * - 一行多选项（ABCD 在一行 / AB一行CD一行）
 */
class StructuredTxtParser : QuizParser {

    override fun supportedFormats(): List<String> = listOf("txt")

    override fun parse(content: String): List<ParsedQuestion> {
        val questions = mutableListOf<ParsedQuestion>()

        // 按空行分块（两个及以上连续换行 = 块分隔）
        val blocks = content.split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (block in blocks) {
            val question = analyzeBlock(block)
            if (question != null) {
                questions.add(question)
            }
        }

        return questions
    }

    // ==================== 结构分析 ====================

    private fun analyzeBlock(block: String): ParsedQuestion? {
        val rawLines = block.lines()
        // 分类后的行：(角色, 内容)
        val classified = classifyLines(rawLines)

        if (classified.isEmpty()) return null

        // 提取各部分
        val stemLines = classified.filter { it.first == LineRole.STEM }
        val optionLines = classified.filter { it.first == LineRole.OPTION }
        val answerLines = classified.filter { it.first == LineRole.ANSWER }
        val analysisLines = classified.filter { it.first == LineRole.ANALYSIS }

        // 必须有题干；选项和答案至少有其一
        if (stemLines.isEmpty()) return null
        if (optionLines.isEmpty() && answerLines.isEmpty()) return null

        val stem = stemLines.joinToString("\n") { it.second }
        val answer = answerLines.firstOrNull()?.second ?: ""
        val analysis = analysisLines.joinToString("\n") { it.second }.ifBlank { null }

        // 推断题型
        val type = inferType(optionLines, answer)

        // 构建选项 JSON
        val optionsJson = buildOptionsJson(optionLines)

        // 判断题答案转换
        val finalAnswer = normalizeAnswer(answer, type, optionLines)

        return ParsedQuestion(
            type = type,
            stem = stem,
            options = optionsJson,
            answer = finalAnswer,
            analysis = analysis
        )
    }

    // ==================== 逐行角色判定（片段级） ====================

    private enum class LineRole { STEM, OPTION, ANSWER, ANALYSIS }

    /**
     * 选项片段：一行中的单个选项。
     * 例如 "A.1 B.2" 会拆成 OptionSegment('A', "1") 和 OptionSegment('B', "2")
     */
    private data class OptionSegment(val letter: Char, val content: String)

    /**
     * 一行文本及其选项片段。
     */
    private class LineSegments(
        val original: String,
        val segments: List<OptionSegment>
    ) {
        var isOption = false
    }

    /**
     * 将一行文本拆分为多个选项片段。
     *
     * 匹配模式：
     * - A. xxx B. xxx C. xxx D. xxx（一行多选）
     * - A.xxx B.xxx（无空格）
     *
     * 返回每行的选项片段列表。
     */
    private fun splitOptionsInLine(line: String): List<OptionSegment> {
        // 匹配选项片段：行首或空格后接 A-D + 分隔符 + 内容
        val pattern = Regex("""(?:^|(?<=\s))([A-Da-d])\s*[.、．:：]\s*(\S[^\n]*)""")
        val matches = pattern.findAll(line).toList()

        return matches.mapNotNull { match ->
            val letter = match.groupValues[1].uppercase().first()
            var content = match.groupValues[2].trim()
            // 去掉该片段后可能紧跟着的下一个选项字母内容
            // 例如 "A.1 B.2" 中 A 的内容被匹配为 "1 B.2"，需要截断到下一个 B. 之前
            val nextOptionMarker = Regex("""\s+[B-Db-d]\s*[.、．:：]""").find(content)
            if (nextOptionMarker != null) {
                content = content.substring(0, nextOptionMarker.range.first).trim()
            }
            if (letter in 'A'..'D' && content.length >= 1) {
                OptionSegment(letter, content)
            } else null
        }
    }

    /**
     * 对每一行进行角色判定。
     * 判定优先级：ANSWER > ANALYSIS > OPTION > STEM
     *
     * OPTION 判定采用片段级"连续出现"增强：
     * - 一行内含 2+ 片段（如 "A.1 B.2"）→ 直接确认选项行
     * - 一行内含 1 个片段 + 相邻行也有选项 → 确认为选项组
     */
    private fun classifyLines(rawLines: List<String>): List<Pair<LineRole, String>> {
        val lines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        // 预拆分：每行拆成选项片段列表
        val lineSegments = lines.map { line ->
            LineSegments(
                original = line,
                segments = splitOptionsInLine(line)
            )
        }

        // 确认选项组（片段级）
        confirmOptionGroupsWithSegments(lineSegments)

        // 根据确认结果生成最终分类
        return buildFinalClassification(lineSegments)
    }

    /**
     * 选项组确认逻辑（片段级）：
     *
     * 遍历每行，检查该行是否包含选项片段。
     * 如果一行包含 2+ 个片段（如 "A.1 B.2"），直接确认为选项行。
     * 如果一行只有 1 个片段，则需要周围有其他选项行（连续 2+ 行有选项）才确认。
     *
     * 这样可以同时处理：
     * - 逐行一个：A.xxx\nB.xxx\n → 连续 2+ 行确认
     * - AB一行：A.xxx B.xxx\nC.xxx D.xxx → 每行 2+ 片段直接确认
     * - AB一行 CD一行：A.xxx B.xxx\nC.xxx D.xxx → 每行 2+ 片段直接确认
     */
    private fun confirmOptionGroupsWithSegments(lineSegments: List<LineSegments>) {
        // 先把含 2+ 片段的行都标记为选项行
        for (ls in lineSegments) {
            if (ls.segments.size >= 2) {
                ls.isOption = true
            }
        }

        // 再处理含 1 个片段的行：需要连续 2+ 行都有选项才确认
        var i = 0
        while (i < lineSegments.size) {
            val ls = lineSegments[i]

            if (ls.segments.size == 1 && !ls.isOption) {
                // 找连续包含选项片段的行
                var j = i
                while (j < lineSegments.size && lineSegments[j].segments.isNotEmpty()) {
                    j++
                }
                val groupSize = j - i

                if (groupSize >= 2) {
                    for (k in i until j) {
                        lineSegments[k].isOption = true
                    }
                }

                // 跳过已处理的行（1片段组或非选项连续块）
                i = j
            } else {
                i++
            }
        }
    }

    /**
     * 根据片段级分类结果构建最终 (角色, 内容) 列表。
     *
     * 关键：多片段选项行（如 "A.1 B.2"）展开为多个独立选项行，
     * 每个选项内容不含字母前缀，由 buildOptionsJson 统一加回。
     */
    private fun buildFinalClassification(lineSegments: List<LineSegments>): List<Pair<LineRole, String>> {
        val result = mutableListOf<Pair<LineRole, String>>()

        for (ls in lineSegments) {
            val role = when {
                ls.isOption -> LineRole.OPTION
                isAnswerLike(ls.original) -> LineRole.ANSWER
                isAnalysisLike(ls.original) -> LineRole.ANALYSIS
                else -> LineRole.STEM
            }

            if (role == LineRole.OPTION) {
                // 选项行：如果是多片段，每片段展开为一行
                if (ls.segments.size >= 2) {
                    for (seg in ls.segments) {
                        val content = seg.content.trim()
                        if (content.isNotBlank()) {
                            result.add(role to content)
                        }
                    }
                } else {
                    // 单片段：直接清理原行
                    val content = cleanOptionLine(ls.original)
                    if (content.isNotBlank()) {
                        result.add(role to content)
                    }
                }
            } else {
                val content = if (role == LineRole.STEM) {
                    cleanStemLine(ls.original)
                } else {
                    ls.original
                }
                if (content.isNotBlank()) {
                    result.add(role to content)
                }
            }
        }

        return result
    }

    // ==================== 特征检测 ====================

    /**
     * 答案行特征检测：
     * 包含"答案"/"正确答案"/"参考答案" + 分隔符(: ：) + 答案内容
     */
    private fun isAnswerLike(line: String): Boolean {
        val patterns = listOf(
            Regex("^(答案|正确答案|参考答案|标准答案)\\s*[:：]\\s*(.+)"),
            // 也匹配 "答案D"（无分隔符）
            Regex("^(答案|正确答案|参考答案)\\s*([A-Da-d]+|正确|错误|对|错|√|×)")
        )
        return patterns.any { it.containsMatchIn(line) }
    }

    /**
     * 解析行特征检测：
     * 包含"解析"/"分析"/"答案解析" + 分隔符
     */
    private fun isAnalysisLike(line: String): Boolean {
        return Regex("^(解析|分析|答案解析|试题解析)\\s*[:：]").containsMatchIn(line)
    }

    // ==================== 内容清理 ====================

    /**
     * 清理题干行：去掉题号前缀、Markdown 标记
     */
    private fun cleanStemLine(line: String): String {
        var s = line

        // 去掉题号前缀：**1.** / 1. / 1、 / (1) / （1） / (一) 等
        s = s.replaceFirst(
            Regex("^\\*{0,2}\\s*[(（]?\\s*[\\d一二三四五六七八九十]+\\s*[)）]?\\s*[.、．:：]?\\s*\\*{0,2}"),
            ""
        )

        // 去掉残留的 **（Markdown 加粗）
        s = s.replace("**", "")

        return s.trim()
    }

    /**
     * 清理选项行：提取字母后的内容
     * "A. 非互联网电脑屏幕内容" → "非互联网电脑屏幕内容"
     */
    private fun cleanOptionLine(line: String): String {
        val s = line.trimStart().trimStart('*').trimStart()
        // 去掉 "A." / "A、" / "A：" 等前缀，提取内容
        val content = s.replaceFirst(Regex("^[A-Da-d]\\s*[.、．:：)）]?\\s*"), "")
        return content.trim()
    }

    // ==================== 构建结果 ====================

    private fun buildOptionsJson(optionLines: List<Pair<LineRole, String>>): String? {
        if (optionLines.isEmpty()) return null
        val jsonArray = JSONArray()
        optionLines.forEach { (_, content) ->
            jsonArray.put(content)
        }
        return jsonArray.toString()
    }

    private fun inferType(
        optionLines: List<Pair<LineRole, String>>,
        answer: String
    ): String {
        val options = optionLines.map { it.second }

        // 判断题：2 个选项且内容是"正确"/"错误"
        if (options.size == 2) {
            val opt0 = options[0]
            val opt1 = options[1]
            if ((opt0.contains("正确") || opt0 == "对" || opt0 == "√") &&
                (opt1.contains("错误") || opt1 == "错" || opt1 == "×")) {
                return "判断"
            }
        }

        // 多选题：答案长度 > 1 且全是字母
        if (answer.length > 1 && answer.all { it.isLetter() }) {
            return "多选"
        }

        // 有选项 → 单选题；无选项 → 填空题
        return if (options.isNotEmpty()) "单选" else "填空"
    }

    private fun normalizeAnswer(
        answer: String,
        type: String,
        optionLines: List<Pair<LineRole, String>>
    ): String {
        val trimmed = answer.trim()

        return when (type) {
            "判断", "judge" -> {
                val ansValue = extractAnswerValue(trimmed)
                when (ansValue.uppercase()) {
                    "A", "1" -> "正确"
                    "B", "2" -> "错误"
                    else -> when {
                        ansValue.contains("正确") || ansValue == "对" || ansValue == "√" -> "正确"
                        ansValue.contains("错误") || ansValue == "错" || ansValue == "×" -> "错误"
                        else -> ansValue
                    }
                }
            }
            "多选", "multi" -> extractAnswerValue(trimmed).filter { it.isLetterOrDigit() }
            else -> extractAnswerValue(trimmed)
        }
    }

    /**
     * 从答案行中提取答案值
     * "答案：D" → "D"
     * "答案D" → "D"
     */
    private fun extractAnswerValue(line: String): String {
        val patterns = listOf(
            Regex("^(?:答案|正确答案|参考答案|标准答案)\\s*[:：]\\s*(.+)"),
            Regex("^(?:答案|正确答案|参考答案)\\s*(.+)")
        )
        for (p in patterns) {
            val match = p.find(line)
            if (match != null) {
                return match.groupValues[1].trim()
            }
        }
        return line
    }
}
