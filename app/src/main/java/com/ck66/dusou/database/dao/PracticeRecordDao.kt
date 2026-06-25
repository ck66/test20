package com.ck66.dusou.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ck66.dusou.database.entity.PracticeRecord
import com.ck66.dusou.database.model.PracticeStats
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: PracticeRecord): Long

    @Query("SELECT * FROM practice_records WHERE bankId = :bankId ORDER BY practiceTime DESC")
    fun getAllByBankId(bankId: Long): Flow<List<PracticeRecord>>

    @Query("""
        SELECT
            COALESCE((SELECT COUNT(DISTINCT q.id) FROM questions q WHERE q.bankId = :bankId), 0) AS totalQuestions,
            COALESCE((SELECT COUNT(*) FROM practice_records pr WHERE pr.bankId = :bankId), 0) AS answeredQuestions,
            COALESCE((SELECT COUNT(*) FROM practice_records pr WHERE pr.bankId = :bankId AND pr.isCorrect = 1), 0) AS correctCount,
            COALESCE((SELECT COUNT(*) FROM practice_records pr WHERE pr.bankId = :bankId AND pr.isCorrect = 0), 0) AS wrongCount,
            CASE
                WHEN (SELECT COUNT(*) FROM practice_records pr WHERE pr.bankId = :bankId) = 0 THEN 0.0
                ELSE CAST((SELECT COUNT(*) FROM practice_records pr WHERE pr.bankId = :bankId AND pr.isCorrect = 1) AS REAL) /
                     CAST((SELECT COUNT(*) FROM practice_records pr WHERE pr.bankId = :bankId) AS REAL)
            END AS accuracy
    """)
    suspend fun getStats(bankId: Long): PracticeStats

    @Query("SELECT questionId FROM practice_records WHERE bankId = :bankId AND isCorrect = 0")
    suspend fun getWrongRecordIds(bankId: Long): List<Long>

    @Query("DELETE FROM practice_records WHERE bankId = :bankId")
    suspend fun deleteByBankId(bankId: Long)

    @Query("DELETE FROM practice_records")
    suspend fun deleteAll()
}
