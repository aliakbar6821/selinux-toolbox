package com.selinuxtoolbox.core.domain.di

import com.selinuxtoolbox.core.data.db.ActionDao
import com.selinuxtoolbox.core.data.db.NoteDao
import com.selinuxtoolbox.core.data.db.ProjectDao
import com.selinuxtoolbox.core.data.root.RootFileReader
import com.selinuxtoolbox.core.data.root.RootShell
import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.domain.repository.BackupOrchestrator
import com.selinuxtoolbox.core.domain.repository.PolicyRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides
    @Singleton
    fun providePolicyRepository(
        rootFileReader: RootFileReader,
        rootShell: RootShell
    ): PolicyRepository = PolicyRepository(rootFileReader, rootShell)

    @Provides
    @Singleton
    fun provideActionRepository(
        projectDao: ProjectDao,
        actionDao: ActionDao,
        noteDao: NoteDao
    ): ActionRepository = ActionRepository(projectDao, actionDao, noteDao)

    @Provides
    @Singleton
    fun provideBackupOrchestrator(
        actionRepository: ActionRepository
    ): BackupOrchestrator = BackupOrchestrator(actionRepository)
}
