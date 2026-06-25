package com.ck66.dusou.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "practice_records",
    indices = [Index(value = ["questionId", "bankId"])]
)
data class PracticeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val bankId: Long,
    val userAnswer: String,
    val isCorrect: Boolean,
    val practiceTime: Long,
)
