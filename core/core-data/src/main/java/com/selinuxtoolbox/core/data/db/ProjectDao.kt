package com.selinuxtoolbox.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)

    @Query("SELECT * FROM projects ORDER BY lastModified DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): ProjectEntity?

    @Query("SELECT * FROM projects WHERE status = 'ACTIVE' ORDER BY lastModified DESC LIMIT 1")
    fun getActiveProject(): Flow<ProjectEntity?>

    @Query("UPDATE projects SET lastModified = :timestamp WHERE id = :id")
    suspend fun updateLastModified(id: Long, timestamp: Long)

    @Query("UPDATE projects SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)
}
