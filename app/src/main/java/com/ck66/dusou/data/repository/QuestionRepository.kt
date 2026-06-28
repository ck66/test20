package com.ck66.dusou.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.ck66.dusou.database.AppDatabase
import com.ck66.dusou.database.dao.PracticeRecordDao
import com.ck66.dusou.database.dao.QuestionBankDao
import com.ck66.dusou.database.dao.QuestionDao
import com.ck66.dusou.database.entity.PracticeRecord
import com.ck66.dusou.database.entity.Question
import com.ck66.dusou.database.entity.QuestionBank
import com.ck66.dusou.database.model.PracticeStats
import com.ck66.dusou.database.model.WrongQuestionInfo
import com.ck66.dusou.parser.QuizParserFactory
import com.ck66.dusou.util.FileLogger
import kotlinx.coroutines.flow.Flow

class QuestionRepository(private val database: AppDatabase) {

    private val questionBankDao: QuestionBankDao = database.questionBankDao()
    private val questionDao: QuestionDao = database.questionDao()
    private val practiceRecordDao: PracticeRecordDao = database.practiceRecordDao()

    suspend fun importBank(context: Context, uri: Uri, bankName: String): Result<Long> {
        return try {
            // 大文件检查：超过 50MB 提示用户拆分
            val fileSize = context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0
            if (fileSize > 50 * 1024 * 1024) {
                throw Exception("文件过大（${fileSize / 1024 / 1024}MB），请拆分后导入")
            }

            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw Exception("无法读取文件")

            val fileName = uri.lastPathSegment ?: "unknown"
            val parser = QuizParserFactory.getParserForFile(fileName)
                ?: throw Exception("不支持的文件格式")

            val parsedQuestions = parser.parse(content)
            if (parsedQuestions.isEmpty()) {
                throw Exception("文件中没有解析到题目")
            }

            // 去重：相同题目+答案的视为重复（题目 stem 相同但答案不同的保留）
            val uniqueQuestions = parsedQuestions.distinctBy { "${it.stem.trim()}|${it.answer.trim()}" }

            val questions = uniqueQuestions.map { pq ->
                Question(
                    bankId = 0,
                    type = pq.type,
                    stem = pq.stem,
                    options = pq.options,
                    answer = pq.answer,
                    analysis = pq.analysis
                )
            }

            val bank = QuestionBank(
                name = bankName,
                fileName = fileName,
                questionCount = uniqueQuestions.size,
                importTime = System.currentTimeMillis()
            )

            val bankId = database.withTransaction {
                val id = questionBankDao.insert(bank)
                val questionsWithBankId = questions.map { it.copy(bankId = id) }
                questionDao.insertAll(questionsWithBankId)

                if (AppDatabase.isFtsAvailable) {
                    try {
                        database.openHelper.writableDatabase.execSQL(
                            "INSERT INTO questions_fts(questions_fts) VALUES('rebuild')"
                        )
                        // 验证 rebuild 后的索引行数
                        val cursor = database.openHelper.readableDatabase
                            .query("SELECT COUNT(*) FROM questions_fts")
                        cursor.use {
                            if (it.moveToFirst()) {
                                FileLogger.i("QuestionRepository", "FTS rebuild done, fts_rows=${it.getInt(0)}")
                            }
                        }
                        val cursor2 = database.openHelper.readableDatabase
                            .query("SELECT COUNT(*) FROM questions")
                        cursor2.use {
                            if (it.moveToFirst()) {
                                FileLogger.i("QuestionRepository", "questions table rows=${it.getInt(0)}")
                            }
                        }
                    } catch (e: Exception) {
                        FileLogger.e("QuestionRepository", "FTS rebuild failed: ${e.message}")
                    }
                }

                id
            }

            Result.success(bankId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAllBanks(): Flow<List<QuestionBank>> {
        return questionBankDao.getAll()
    }

    suspend fun deleteBank(bankId: Long) {
        database.withTransaction {
            practiceRecordDao.deleteByBankId(bankId)
            questionDao.deleteByBankId(bankId)
            val bank = questionBankDao.getById(bankId)
            if (bank != null) {
                questionBankDao.delete(bank)
            }

            if (AppDatabase.isFtsAvailable) {
                database.openHelper.writableDatabase.execSQL(
                    "INSERT INTO questions_fts(questions_fts) VALUES('rebuild')"
                )
            }
        }
    }

    fun getQuestions(bankId: Long, limit: Int = 50, offset: Int = 0): Flow<List<Question>> {
        return questionDao.getByBankId(bankId, limit, offset)
    }

    suspend fun searchQuestions(query: String, bankId: Long? = null): List<Question> {
        if (query.isBlank()) return emptyList()

        FileLogger.i("QuestionRepository", "searchQuestions: query='$query', bankId=$bankId, isFtsAvailable=${AppDatabase.isFtsAvailable}")

        val bankFilter = if (bankId != null) " AND bankId = $bankId" else ""

        if (!AppDatabase.isFtsAvailable) {
            // 降级 LIKE 搜索：query 可能是 FTS5 OR 语法，需提取纯关键词
            val plainKeywords = query.split(" OR ").mapNotNull {
                it.trim().trim('"', '*', ' ').takeIf { w -> w.length >= 2 }
            }
            if (plainKeywords.isEmpty()) return emptyList()
            val likeCondition = plainKeywords.joinToString(" OR ") {
                "(stem LIKE ? ESCAPE ? OR options LIKE ? ESCAPE ? OR answer LIKE ? ESCAPE ?)"
            }
            // 参数化查询：每个关键词对应 3 个 LIKE + 一个 ESCAPE
            val args = plainKeywords.flatMap { kw ->
                listOf("%$kw%", "\\", "%$kw%", "\\", "%$kw%", "\\")
            }.toTypedArray()
            FileLogger.i("QuestionRepository", "LIKE fallback: $likeCondition")
            return questionDao.searchByFts(SimpleSQLiteQuery(
                "SELECT * FROM questions WHERE ($likeCondition)$bankFilter", args
            ))
        }

        // 如果 query 已经由 TextMatcher.buildFtsQuery() 生成（包含 * 通配符），直接使用
        // 避免二次处理产生 "关键词**" 等双重星号
        val ftsQuery = if (query.contains("*")) {
            query.trim()
        } else {
            query.trim().split("\\s+".toRegex())
                .filter { it.isNotBlank() }
                .joinToString(" ") { "$it*" }
        }

        val sql = """
            SELECT * FROM questions
            WHERE id IN (SELECT rowid FROM questions_fts WHERE questions_fts MATCH ?)$bankFilter
        """.trimIndent()

        FileLogger.i("QuestionRepository", "FTS SQL param: $ftsQuery")
        val results = questionDao.searchByFts(SimpleSQLiteQuery(sql, arrayOf(ftsQuery)))
        FileLogger.i("QuestionRepository", "FTS search returned ${results.size} results")
        return results
    }

    suspend fun getRandomQuestions(bankId: Long, count: Int): List<Question> {
        if (count <= 0) return emptyList()
        return questionDao.getRandom(bankId, count)
    }

    fun getWrongQuestions(bankId: Long): Flow<List<Question>> {
        return questionDao.getWrongQuestions(bankId)
    }

    fun getFavoriteQuestions(bankId: Long, ids: List<Long>): Flow<List<Question>> {
        return questionDao.getFavoriteQuestions(bankId, ids)
    }

    suspend fun submitAnswer(record: PracticeRecord) {
        practiceRecordDao.insert(record)
    }

    suspend fun getPracticeStats(bankId: Long): PracticeStats {
        return practiceRecordDao.getStats(bankId)
    }

    suspend fun getWrongQuestionsWithInfo(bankId: Long): List<WrongQuestionInfo> {
        val wrongQuestions = questionDao.getWrongQuestionsList(bankId)
        val records = practiceRecordDao.getAllByBankIdList(bankId)
        val recordMap = records.associateBy { it.questionId }
        return wrongQuestions.map { q ->
            val record = recordMap[q.id]
            WrongQuestionInfo(
                question = q,
                userAnswer = record?.userAnswer ?: "",
                practiceTime = record?.practiceTime ?: 0L
            )
        }
    }
}
