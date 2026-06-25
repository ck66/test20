package com.ck66.dusou.database.model

import com.ck66.dusou.database.entity.Question

data class WrongQuestionInfo(
    val question: Question,
    val userAnswer: String,
    val practiceTime: Long,
)
