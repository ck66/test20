package com.ck66.dusou.data.repository

import android.content.Context
import com.ck66.dusou.DusouApplication
import com.ck66.dusou.database.AppDatabase

object QuestionRepositoryProvider {

    fun init(context: Context) {
        // Kept for backward compatibility; lazy init now uses DusouApplication.instance
    }

    private val repository: QuestionRepository by lazy {
        val app = try {
            DusouApplication.instance
        } catch (e: UninitializedPropertyAccessException) {
            throw IllegalStateException(
                "QuestionRepositoryProvider not initialized. Call init() first.",
                e
            )
        }
        QuestionRepository(AppDatabase.getInstance(app))
    }

    fun get(): QuestionRepository = repository
}
