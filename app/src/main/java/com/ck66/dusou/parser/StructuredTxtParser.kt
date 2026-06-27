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

    // ==================== 逐行角色判定 ====================

    private enum class LineRole { STEM, OPTION, ANSWER, ANALYSIS }

    /**
     * 对每一行进行角色判定。
     * 判定优先级：ANSWER > ANALYSIS > OPTION > STEM
     *
     * OPTION 判定采用"连续出现"增强：
     * 如果一行疑似选项（A-D 开头 + 分隔符），且后续行也是 A-D 开头，
     * 则确认是选项组。单独出现的 "A. xxx" 可能只是题干的一部分。
     */
    private fun classifyLines(rawLines: List<String>): List<Pair<LineRole, String>> {
        val lines = rawLines.map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return emptyList()

        // 先标记每行的"候选角色"
        val candidates = lines.map { line -> LineCandidate(line) }

        // 增强 OPTION 判定：连续 2+ 行都是候选选项 → 确认为选项组
        confirmOptionGroups(candidates)

        // 根据候选角色生成最终分类
        return candidates.mapNotNull { c ->
            val role = when {
                c.isAnswer -> LineRole.ANSWER
                c.isAnalysis -> LineRole.ANALYSIS
                c.isOption -> LineRole.OPTION
                else -> LineRole.STEM
            }
            val content = if (role == LineRole.STEM) {
                cleanStemLine(c.line)
            } else if (role == LineRole.OPTION) {
                cleanOptionLine(c.line)
            } else {
                c.line
            }
            if (content.isBlank()) null else role to content
        }
    }

    private inner class LineCandidate(val line: String) {
        var isOptionCandidate = false
        var isOption = false       // 确认是选项（经连续性验证）
        var isAnswer = false
        var isAnalysis = false

        init {
            isOptionCandidate = isOptionLike(line)
            isAnswer = isAnswerLike(line)
            isAnalysis = isAnalysisLike(line)
        }
    }

    /**
     * 选项行特征检测（宽松）：
     * - 行首（忽略空格、* 号）第一个字符是 A-D / a-d
     * - 紧跟分隔符（. 、 ． : ： ） )）或空格
     * - 后面有实际内容（长度 > 2）
     *
     * 但单独一行匹配不一定是选项（题干可能以 A 开头），
     * 需要连续性验证。
     */
    private fun isOptionLike(line: String): Boolean {
        // 去掉行首空格和星号
        val s = line.trimStart().trimStart('*').trimStart()
        if (s.length < 3) return false

        val first = s[0]
        if (first !in 'A'..'D' && first !in 'a'..'d') return false

        // 第二个字符应该是分隔符
        if (s.length < 2) return false
        val sep = s[1]
        val isSep = sep in setOf('.', '、', '．', ':', '：', ')', '）', ' ')
        if (!isSep) return false

        // 如果是空格，后面必须有非字母内容（排除 "A brief..." 这种正常句子）
        if (sep == ' ') {
            val afterSpace = s.drop(2).trimStart()
            if (afterSpace.isEmpty()) return false
            return true
        }

        return true
    }

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

    /**
     * 增强 OPTION 判定：连续 2+ 行都是候选选项 → 确认为选项组。
     *
     * 这是核心的结构识别逻辑：不是看某一行是否像选项，
     * 而是看是否有一组连续的行，每行都以递增的 A/B/C/D 开头。
     */
    private fun confirmOptionGroups(candidates: List<LineCandidate>) {
        var i = 0
        while (i < candidates.size) {
            if (!candidates[i].isOptionCandidate) {
                i++
                continue
            }

            // 从 i 开始，找连续的候选选项行
            val groupStart = i
            var j = i + 1
            while (j < candidates.size && candidates[j].isOptionCandidate) {
                j++
            }
            val groupSize = j - groupStart

            if (groupSize >= 2) {
                // 连续 2+ 行 → 确认为选项组
                for (k in groupStart until j) {
                    candidates[k].isOption = true
                }
            }
            // 即使只有 1 行候选选项，但如果前后有题干行和答案行，
            // 也可能是单选项的判断题，暂不确认（留给后续逻辑处理）
            i = j
        }
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
        optionLines.forEachIndexed { index, (_, content) ->
            val letter = 'A' + index
            jsonArray.put("$letter. $content")
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
                return "judge"
            }
        }

        // 多选题：答案长度 > 1 且全是字母
        if (answer.length > 1 && answer.all { it.isLetter() }) {
            return "multi"
        }

        // 有选项 → 单选题；无选项 → 填空题
        return if (options.isNotEmpty()) "single" else "fill"
    }

    private fun normalizeAnswer(
        answer: String,
        type: String,
        optionLines: List<Pair<LineRole, String>>
    ): String {
        val trimmed = answer.trim()

        return when (type) {
            "judge" -> {
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
            "multi" -> extractAnswerValue(trimmed).filter { it.isLetterOrDigit() }
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
