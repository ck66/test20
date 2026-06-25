package com.ck66.dusou.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "question_banks")
data class QuestionBank(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val fileName: String,
    val questionCount: Int,
    val importTime: Long,
)
