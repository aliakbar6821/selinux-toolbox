package com.selinuxtoolbox.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.selinuxtoolbox.core.model.ActionType
import com.selinuxtoolbox.core.model.FileOperation
import com.selinuxtoolbox.core.model.ProjectStatus

@Database(
    entities = [
        ProjectEntity::class,
        ActionEntity::class,
        NoteEntity::class,
        FileSnapshotEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
    abstract fun actionDao(): ActionDao
    abstract fun noteDao(): NoteDao
}

class AppTypeConverters {

    @TypeConverter
    fun fromProjectStatus(value: ProjectStatus): String = value.name

    @TypeConverter
    fun toProjectStatus(value: String): ProjectStatus =
        ProjectStatus.valueOf(value)

    @TypeConverter
    fun fromActionType(value: ActionType): String = value.name

    @TypeConverter
    fun toActionType(value: String): ActionType =
        ActionType.valueOf(value)

    @TypeConverter
    fun fromFileOperation(value: FileOperation): String = value.name

    @TypeConverter
    fun toFileOperation(value: String): FileOperation =
        FileOperation.valueOf(value)
}
