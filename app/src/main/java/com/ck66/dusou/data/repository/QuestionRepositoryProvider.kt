package com.ck66.dusou.data.repository

import android.content.Context
import android.util.Log
import com.ck66.dusou.database.AppDatabase

object QuestionRepositoryProvider {

    private const val TAG = "RepoProvider"

    @Synchronized
    fun init(context: Context) {
        try {
            val app = context.applicationContext
            repository = QuestionRepository(AppDatabase.getInstance(app))
            initialized = true
        } catch (e: Exception) {
            // 初始化失败记录日志，后续 get() 会把原始异常作为 cause 抛出
            Log.e(TAG, "QuestionRepositoryProvider 初始化失败", e)
            lastInitError = e
        }
    }

    @Volatile
    private var repository: QuestionRepository? = null
    @Volatile
    private var initialized = false
    private var lastInitError: Exception? = null

    fun get(): QuestionRepository {
        return repository ?: throw IllegalStateException(
            "QuestionRepositoryProvider 未初始化。请先调用 init()。",
            lastInitError
        )
    }
}
