package com.harisudhan.suras.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PatientDao {

    @Query("SELECT * FROM assessments ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<PatientRecord>>

    @Query("SELECT * FROM assessments ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<PatientRecord>>

    @Query(
        """
        SELECT * FROM assessments
        WHERE name LIKE :pattern ESCAPE '\' OR patientId LIKE :pattern ESCAPE '\'
        ORDER BY createdAt DESC
        """,
    )
    fun observeSearch(pattern: String): Flow<List<PatientRecord>>

    @Query("SELECT * FROM assessments WHERE patientId = :patientId LIMIT 1")
    fun observeById(patientId: String): Flow<PatientRecord?>

    @Query("SELECT * FROM assessments WHERE patientId = :patientId LIMIT 1")
    suspend fun findById(patientId: String): PatientRecord?

    @Query("SELECT * FROM assessments ORDER BY createdAt DESC")
    suspend fun getAll(): List<PatientRecord>

    @Query("SELECT COUNT(*) FROM assessments")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: PatientRecord)

    @Query("DELETE FROM assessments WHERE patientId = :patientId")
    suspend fun deleteById(patientId: String)

    @Query("DELETE FROM assessments")
    suspend fun deleteAll()
}
