package com.ck66.dusou.database.model

data class PracticeStats(
    val totalQuestions: Int,
    val answeredQuestions: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val accuracy: Float,
)
