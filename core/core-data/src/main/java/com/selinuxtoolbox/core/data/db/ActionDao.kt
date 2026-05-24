package com.selinuxtoolbox.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ActionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: ActionEntity): Long

    @Query("SELECT * FROM actions WHERE projectId = :projectId ORDER BY timestamp ASC")
    fun getActionsForProject(projectId: Long): Flow<List<ActionEntity>>

    @Query("SELECT * FROM actions WHERE projectId = :projectId ORDER BY timestamp ASC")
    suspend fun getActionsForProjectOnce(projectId: Long): List<ActionEntity>

    @Query("SELECT * FROM actions WHERE id = :id")
    suspend fun getById(id: Long): ActionEntity?

    @Query("""
        SELECT * FROM actions 
        WHERE projectId = :projectId 
        AND timestamp > :afterTimestamp 
        AND undone = 0
        ORDER BY timestamp ASC
    """)
    suspend fun getActionsAfter(projectId: Long, afterTimestamp: Long): List<ActionEntity>

    @Query("UPDATE actions SET undone = 1, undoneAt = :timestamp WHERE id = :id")
    suspend fun markUndone(id: Long, timestamp: Long)

    @Query("UPDATE actions SET changedFilesJson = :json WHERE id = :id")
    suspend fun updateChangedFiles(id: Long, json: String)

    @Query("DELETE FROM actions WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: Long)
}
