package com.ck66.dusou.data.repository

import android.content.Context
import com.ck66.dusou.database.AppDatabase

object QuestionRepositoryProvider {
    private lateinit var repository: QuestionRepository

    fun init(context: Context) {
        repository = QuestionRepository(AppDatabase.getInstance(context))
    }

    fun get(): QuestionRepository {
        if (!::repository.isInitialized) throw IllegalStateException("Call init() first")
        return repository
    }
}
