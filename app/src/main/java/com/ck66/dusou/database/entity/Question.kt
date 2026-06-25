package com.ck66.dusou.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "questions",
    indices = [Index(value = ["bankId"])],
    foreignKeys = [ForeignKey(
        entity = QuestionBank::class,
        parentColumns = ["id"],
        childColumns = ["bankId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Question(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bankId: Long,
    val type: String,
    val stem: String,
    val options: String?,
    val answer: String,
    val analysis: String?,
)
