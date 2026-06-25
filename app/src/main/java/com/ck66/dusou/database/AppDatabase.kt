package com.ck66.dusou.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "dusou.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            db.execSQL("""
                                CREATE VIRTUAL TABLE IF NOT EXISTS questions_fts USING fts5(
                                    stem, options, answer, analysis,
                                    content='questions',
                                    content_rowid='id',
                                    tokenize='unicode61'
                                )
                            """.trimIndent())
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
