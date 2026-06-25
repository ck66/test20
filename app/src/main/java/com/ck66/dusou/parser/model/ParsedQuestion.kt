package com.ck66.dusou.parser.model

data class ParsedQuestion(
    val type: String,        // single / multi / judge / fill
    val stem: String,        // 题干
    val options: String?,    // 选项JSON: ["A.xxx","B.xxx","C.xxx","D.xxx"]
    val answer: String,      // 答案
    val analysis: String?,   // 解析
)
