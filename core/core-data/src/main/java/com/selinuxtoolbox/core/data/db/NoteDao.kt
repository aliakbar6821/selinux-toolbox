package com.selinuxtoolbox.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("SELECT * FROM notes WHERE projectId = :projectId ORDER BY timestamp DESC")
    fun getNotesForProject(projectId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE projectId = :projectId AND actionId = :actionId")
    fun getNotesForAction(projectId: Long, actionId: Long): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE projectId = :projectId AND actionId IS NULL ORDER BY timestamp DESC")
    fun getProjectLevelNotes(projectId: Long): Flow<List<NoteEntity>>

    @Query("DELETE FROM notes WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: Long)
}
