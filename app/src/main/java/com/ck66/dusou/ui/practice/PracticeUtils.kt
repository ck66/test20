package com.ck66.dusou.ui.practice

/**
 * 练习模块共享工具函数，避免在 PracticeScreen 和 WrongQuestionScreen 中重复定义。
 */
object PracticeUtils {

    /** 选项标签 A~F */
    val optionLabels = listOf("A", "B", "C", "D", "E", "F")

    /** 题型显示名称 */
    fun typeLabel(type: String): String = when (type) {
        "单选", "单选题" -> "单选题"
        "多选", "多选题" -> "多选题"
        "判断", "判断题" -> "判断题"
        "填空", "填空题" -> "填空题"
        else -> "单选题"
    }

    /** 格式化答案显示：将字母以空格分隔（如 "AB" → "A B"） */
    fun formatAnswerDisplay(answer: String): String {
        return answer.map { c ->
            if (c in 'A'..'Z') "$c" else c.toString()
        }.joinToString("")
    }

    /** 解析选项字符串为列表 */
    fun parseOptions(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            if (raw.trim().startsWith("[")) {
                val cleaned = raw.trim().removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                cleaned
            } else {
                raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            }
        } catch (e: Exception) {
            raw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }
}
