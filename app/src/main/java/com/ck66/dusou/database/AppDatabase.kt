package com.ck66.dusou.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ck66.dusou.database.dao.PracticeRecordDao
import com.ck66.dusou.database.dao.QuestionBankDao
import com.ck66.dusou.database.dao.QuestionDao
import com.ck66.dusou.database.entity.PracticeRecord
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.entity.QuestionBank

@Database(
    entities = [QuestionBank::class, Question::class, PracticeRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun questionBankDao(): QuestionBankDao
    abstract fun questionDao(): QuestionDao
    abstract fun practiceRecordDao(): PracticeRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** 标记 FTS5 全文搜索是否可用（部分设备可能不支持 FTS5 扩展） */
        @Volatile
        var isFtsAvailable = false
            private set

        /**
         * 数据库 Migration 策略：显式定义每次版本升级的 SQL，避免 fallbackToDestructiveMigration 导致数据丢失。
         * 当前版本 1，无历史迁移；后续版本按需添加 MIGRATION_X_Y 条目。
         */
        @Suppress("unused")
        private val MIGRATIONS = arrayOf<Migration>()

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dusou.db"
                )
                    .addMigrations(*MIGRATIONS)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            try {
                                db.execSQL("""
                                    CREATE VIRTUAL TABLE IF NOT EXISTS questions_fts USING fts5(
                                        stem, options, answer, analysis,
                                        content='questions',
                                        content_rowid='id',
                                        tokenize='unicode61'
                                    )
                                """.trimIndent())
                                isFtsAvailable = true
                            } catch (_: Exception) {
                                // 设备不支持 FTS5，降级为 LIKE 搜索，不影响应用使用
                                isFtsAvailable = false
                            }
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA journal_mode=WAL")
                            db.execSQL("PRAGMA synchronous=NORMAL")
                            db.execSQL("PRAGMA foreign_keys=ON")
                        }
                    })
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
