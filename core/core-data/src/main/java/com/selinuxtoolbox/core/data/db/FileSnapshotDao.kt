package com.selinuxtoolbox.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FileSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: FileSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<FileSnapshotEntity>)

    @Query("SELECT * FROM file_snapshots WHERE actionId = :actionId")
    fun getSnapshotsForAction(actionId: Long): Flow<List<FileSnapshotEntity>>

    @Query("SELECT * FROM file_snapshots WHERE actionId = :actionId")
    suspend fun getSnapshotsForActionOnce(actionId: Long): List<FileSnapshotEntity>

    @Query("SELECT * FROM file_snapshots WHERE filePath = :filePath")
    suspend fun getSnapshotsForFile(filePath: String): List<FileSnapshotEntity>

    @Query("DELETE FROM file_snapshots WHERE actionId = :actionId")
    suspend fun deleteForAction(actionId: Long)
}
