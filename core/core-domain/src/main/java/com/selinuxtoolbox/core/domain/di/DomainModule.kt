package com.selinuxtoolbox.core.domain.di

import com.selinuxtoolbox.core.data.binary.BinaryManager
import com.selinuxtoolbox.core.data.db.ActionDao
import com.selinuxtoolbox.core.data.db.NoteDao
import com.selinuxtoolbox.core.data.db.ProjectDao
import com.selinuxtoolbox.core.data.prefs.AppPreferences
import com.selinuxtoolbox.core.data.root.RootFileReader
import com.selinuxtoolbox.core.data.root.RootShell
import com.selinuxtoolbox.core.domain.analyzer.CleanupEngine
import com.selinuxtoolbox.core.domain.analyzer.RcSeclabelScanner
import com.selinuxtoolbox.core.domain.parser.AvcDenialParser
import com.selinuxtoolbox.core.domain.parser.RcFileParser
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.WorkspaceValidator
import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.domain.repository.BackupOrchestrator
import com.selinuxtoolbox.core.domain.repository.PolicyRepository
import com.selinuxtoolbox.core.domain.usecase.CompilePolicyUseCase
import com.selinuxtoolbox.core.domain.usecase.RunCleanupUseCase
import com.selinuxtoolbox.core.domain.usecase.RunRcSeclabelScanUseCase
import com.selinuxtoolbox.core.domain.usecase.SetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.ValidateCilUseCase
import com.selinuxtoolbox.core.domain.usecase.ValidateSeclabelsUseCase
import com.selinuxtoolbox.core.domain.usecase.ValidateWorkspaceUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {

    @Provides @Singleton
    fun provideAvcDenialParser(): AvcDenialParser = AvcDenialParser()

    @Provides @Singleton
    fun provideRcFileParser(): RcFileParser = RcFileParser()

    @Provides @Singleton
    fun provideCleanupEngine(): CleanupEngine = CleanupEngine()

    @Provides @Singleton
    fun providePathResolver(): PathResolver = PathResolver()

    @Provides @Singleton
    fun provideWorkspaceValidator(pathResolver: PathResolver): WorkspaceValidator =
        WorkspaceValidator(pathResolver)

    @Provides @Singleton
    fun provideRcSeclabelScanner(pathResolver: PathResolver): RcSeclabelScanner =
        RcSeclabelScanner(pathResolver)

    @Provides @Singleton
    fun provideValidateWorkspaceUseCase(validator: WorkspaceValidator): ValidateWorkspaceUseCase =
        ValidateWorkspaceUseCase(validator)

    @Provides @Singleton
    fun provideRunRcSeclabelScanUseCase(scanner: RcSeclabelScanner): RunRcSeclabelScanUseCase =
        RunRcSeclabelScanUseCase(scanner)

    @Provides @Singleton
    fun provideValidateCilUseCase(
        binaryManager: BinaryManager,
        pathResolver: PathResolver
    ): ValidateCilUseCase = ValidateCilUseCase(binaryManager, pathResolver)

    @Provides @Singleton
    fun provideValidateSeclabelsUseCase(
        rcFileParser: RcFileParser,
        pathResolver: PathResolver
    ): ValidateSeclabelsUseCase = ValidateSeclabelsUseCase(rcFileParser, pathResolver)

    @Provides @Singleton
    fun provideCompilePolicyUseCase(
        binaryManager: BinaryManager,
        pathResolver: PathResolver
    ): CompilePolicyUseCase = CompilePolicyUseCase(binaryManager, pathResolver)

    @Provides @Singleton
    fun providePolicyRepository(
        rootFileReader: RootFileReader,
        rootShell: RootShell
    ): PolicyRepository = PolicyRepository(rootFileReader, rootShell)

    @Provides @Singleton
    fun provideActionRepository(
        projectDao: ProjectDao,
        actionDao: ActionDao,
        noteDao: NoteDao
    ): ActionRepository = ActionRepository(projectDao, actionDao, noteDao)

    @Provides @Singleton
    fun provideBackupOrchestrator(
        actionRepository: ActionRepository
    ): BackupOrchestrator = BackupOrchestrator(actionRepository)

    @Provides @Singleton
    fun provideSetActiveProjectUseCase(
        actionRepository: ActionRepository,
        appPreferences: AppPreferences
    ): SetActiveProjectUseCase = SetActiveProjectUseCase(actionRepository, appPreferences)

    @Provides @Singleton
    fun provideRunCleanupUseCase(
        cleanupEngine: CleanupEngine,
        backupOrchestrator: BackupOrchestrator,
        actionRepository: ActionRepository,
        appPreferences: AppPreferences
    ): RunCleanupUseCase = RunCleanupUseCase(
        cleanupEngine, backupOrchestrator, actionRepository, appPreferences
    )
}
