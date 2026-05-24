package com.selinuxtoolbox.core.data.di

import android.content.Context
import androidx.room.Room
import com.selinuxtoolbox.core.data.db.ActionDao
import com.selinuxtoolbox.core.data.db.AppDatabase
import com.selinuxtoolbox.core.data.db.FileSnapshotDao
import com.selinuxtoolbox.core.data.db.NoteDao
import com.selinuxtoolbox.core.data.db.ProjectDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "selinux_toolbox.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideActionDao(db: AppDatabase): ActionDao = db.actionDao()

    @Provides
    fun provideNoteDao(db: AppDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideFileSnapshotDao(db: AppDatabase): FileSnapshotDao = db.fileSnapshotDao()
}
