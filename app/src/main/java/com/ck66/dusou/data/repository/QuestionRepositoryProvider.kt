package com.ck66.dusou.data.repository

import android.content.Context
import com.ck66.dusou.database.AppDatabase

object QuestionRepositoryProvider {

    fun init(context: Context) {
        // 主动触发 AppDatabase 和 QuestionRepository 的初始化，确保后台线程完成准备工作
        try {
            val app = context.applicationContext
            repository = QuestionRepository(AppDatabase.getInstance(app))
            initialized = true
        } catch (e: Exception) {
            // 初始化失败记录日志，后续 get() 会抛出更明确的错误
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
