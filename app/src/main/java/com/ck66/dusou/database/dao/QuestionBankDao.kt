package com.ck66.dusou.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ck66.dusou.database.entity.QuestionBank
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionBankDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bank: QuestionBank): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(banks: List<QuestionBank>): List<Long>

    @Query("SELECT * FROM question_banks ORDER BY importTime DESC")
    fun getAll(): Flow<List<QuestionBank>>

    @Query("SELECT * FROM question_banks WHERE id = :id")
    suspend fun getById(id: Long): QuestionBank?

    @Delete
    suspend fun delete(bank: QuestionBank)

    @Query("DELETE FROM question_banks")
    suspend fun deleteAll()

    @Update
    suspend fun update(bank: QuestionBank)

    @Query("SELECT COUNT(*) FROM question_banks")
    suspend fun getCount(): Int
}
