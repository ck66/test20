package com.ck66.dusou.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.ck66.dusou.database.entity.Question
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: Question): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<Question>): List<Long>

    @Query("SELECT * FROM questions WHERE bankId = :bankId ORDER BY id LIMIT :limit OFFSET :offset")
    fun getByBankId(bankId: Long, limit: Int, offset: Int): Flow<List<Question>>

    @RawQuery(observedEntities = [Question::class])
    suspend fun searchByFts(query: SupportSQLiteQuery): List<Question>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getById(id: Long): Question?

    @Query("""SELECT * FROM questions 
        WHERE bankId = :bankId 
        AND (:count > 0) -- Workaround: ORDER BY RANDOM() only works with LIMIT 
        ORDER BY RANDOM() LIMIT :count""")
    suspend fun getRandom(bankId: Long, count: Int): List<Question>

    @Query("""
        SELECT DISTINCT q.* FROM questions q
        INNER JOIN practice_records pr ON q.id = pr.questionId AND q.bankId = pr.bankId
        WHERE q.bankId = :bankId AND pr.isCorrect = 0
        ORDER BY q.id
    """)
    fun getWrongQuestions(bankId: Long): Flow<List<Question>>

    @Query("SELECT * FROM questions WHERE bankId = :bankId AND id IN (:favoriteIds)")
    fun getFavoriteQuestions(bankId: Long, favoriteIds: List<Long>): Flow<List<Question>>

    @Query("DELETE FROM questions WHERE bankId = :bankId")
    suspend fun deleteByBankId(bankId: Long)

    @Query("DELETE FROM questions")
    suspend fun deleteAll()
}
