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
import com.selinuxtoolbox.core.domain.parser.ContextFileParser
import com.selinuxtoolbox.core.domain.parser.RcFileParser
import com.selinuxtoolbox.core.domain.path.PathResolver
import com.selinuxtoolbox.core.domain.path.WorkspaceValidator
import com.selinuxtoolbox.core.domain.repository.ActionRepository
import com.selinuxtoolbox.core.domain.repository.BackupOrchestrator
import com.selinuxtoolbox.core.domain.repository.PolicyRepository
import com.selinuxtoolbox.core.domain.usecase.AddNoteUseCase
import com.selinuxtoolbox.core.domain.usecase.AnalyzeDenialsUseCase
import com.selinuxtoolbox.core.domain.usecase.ApplyDenialFixesUseCase
import com.selinuxtoolbox.core.domain.usecase.ArchiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.CompilePolicyUseCase
import com.selinuxtoolbox.core.domain.usecase.CreateProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.DeleteProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.DiffPolicyUseCase
import com.selinuxtoolbox.core.domain.usecase.ExportProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.FixMissingAttributesUseCase
import com.selinuxtoolbox.core.domain.usecase.FullComparisonUseCase
import com.selinuxtoolbox.core.domain.usecase.GetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.GetAllProjectsUseCase
import com.selinuxtoolbox.core.domain.usecase.GetCompilationOrderUseCase
import com.selinuxtoolbox.core.domain.usecase.GetLivePolicyUseCase
import com.selinuxtoolbox.core.domain.usecase.GetProjectActionsUseCase
import com.selinuxtoolbox.core.domain.usecase.GetProtectedTypesUseCase
import com.selinuxtoolbox.core.domain.usecase.GetRecentDenialsUseCase
import com.selinuxtoolbox.core.domain.usecase.GetSelinuxStatusUseCase
import com.selinuxtoolbox.core.domain.usecase.ImportLogUseCase
import com.selinuxtoolbox.core.domain.usecase.ImportProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.RunCleanupUseCase
import com.selinuxtoolbox.core.domain.usecase.RunRcSeclabelScanUseCase
import com.selinuxtoolbox.core.domain.usecase.SetActiveProjectUseCase
import com.selinuxtoolbox.core.domain.usecase.SetSelinuxModeUseCase
import com.selinuxtoolbox.core.domain.usecase.StreamAvcDenialsUseCase
import com.selinuxtoolbox.core.domain.usecase.UndoActionUseCase
import com.selinuxtoolbox.core.domain.usecase.ValidateActionsUseCase
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
    fun provideContextFileParser(): ContextFileParser = ContextFileParser()

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
    fun provideDiffPolicyUseCase(
        pathResolver: PathResolver
    ): DiffPolicyUseCase = DiffPolicyUseCase(pathResolver)

    @Provides @Singleton
    fun provideFixMissingAttributesUseCase(
        pathResolver: PathResolver
    ): FixMissingAttributesUseCase = FixMissingAttributesUseCase(pathResolver)

    @Provides @Singleton
    fun provideFullComparisonUseCase(
        pathResolver: PathResolver,
        rootFileReader: RootFileReader,
        contextFileParser: ContextFileParser,
        rcFileParser: RcFileParser
    ): FullComparisonUseCase = FullComparisonUseCase(
        pathResolver, rootFileReader, contextFileParser, rcFileParser
    )

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

    @Provides @Singleton
    fun provideCreateProjectUseCase(actionRepository: ActionRepository): CreateProjectUseCase =
        CreateProjectUseCase(actionRepository)

    @Provides @Singleton
    fun provideGetAllProjectsUseCase(actionRepository: ActionRepository): GetAllProjectsUseCase =
        GetAllProjectsUseCase(actionRepository)

    @Provides @Singleton
    fun provideGetActiveProjectUseCase(actionRepository: ActionRepository): GetActiveProjectUseCase =
        GetActiveProjectUseCase(actionRepository)

    @Provides @Singleton
    fun provideGetProjectActionsUseCase(actionRepository: ActionRepository): GetProjectActionsUseCase =
        GetProjectActionsUseCase(actionRepository)

    @Provides @Singleton
    fun provideDeleteProjectUseCase(actionRepository: ActionRepository): DeleteProjectUseCase =
        DeleteProjectUseCase(actionRepository)

    @Provides @Singleton
    fun provideArchiveProjectUseCase(actionRepository: ActionRepository): ArchiveProjectUseCase =
        ArchiveProjectUseCase(actionRepository)

    @Provides @Singleton
    fun provideExportProjectUseCase(
        actionRepository: ActionRepository,
        backupOrchestrator: BackupOrchestrator
    ): ExportProjectUseCase = ExportProjectUseCase(actionRepository, backupOrchestrator)

    @Provides @Singleton
    fun provideImportProjectUseCase(
        backupOrchestrator: BackupOrchestrator,
        actionRepository: ActionRepository
    ): ImportProjectUseCase = ImportProjectUseCase(backupOrchestrator, actionRepository)

    @Provides @Singleton
    fun provideAddNoteUseCase(actionRepository: ActionRepository): AddNoteUseCase =
        AddNoteUseCase(actionRepository)

    @Provides @Singleton
    fun provideUndoActionUseCase(backupOrchestrator: BackupOrchestrator): UndoActionUseCase =
        UndoActionUseCase(backupOrchestrator)

    @Provides @Singleton
    fun provideValidateActionsUseCase(actionRepository: ActionRepository): ValidateActionsUseCase =
        ValidateActionsUseCase(actionRepository)

    @Provides @Singleton
    fun provideGetCompilationOrderUseCase(policyRepository: PolicyRepository): GetCompilationOrderUseCase =
        GetCompilationOrderUseCase(policyRepository)

    @Provides @Singleton
    fun provideGetLivePolicyUseCase(policyRepository: PolicyRepository): GetLivePolicyUseCase =
        GetLivePolicyUseCase(policyRepository)

    @Provides @Singleton
    fun provideGetProtectedTypesUseCase(policyRepository: PolicyRepository): GetProtectedTypesUseCase =
        GetProtectedTypesUseCase(policyRepository)

    @Provides @Singleton
    fun provideGetRecentDenialsUseCase(
        rootShell: RootShell,
        avcDenialParser: AvcDenialParser
    ): GetRecentDenialsUseCase = GetRecentDenialsUseCase(rootShell, avcDenialParser)

    @Provides @Singleton
    fun provideStreamAvcDenialsUseCase(
        rootShell: RootShell,
        avcDenialParser: AvcDenialParser
    ): StreamAvcDenialsUseCase = StreamAvcDenialsUseCase(rootShell, avcDenialParser)

    @Provides @Singleton
    fun provideGetSelinuxStatusUseCase(rootShell: RootShell): GetSelinuxStatusUseCase =
        GetSelinuxStatusUseCase(rootShell)

    @Provides @Singleton
    fun provideSetSelinuxModeUseCase(rootShell: RootShell): SetSelinuxModeUseCase =
        SetSelinuxModeUseCase(rootShell)

    @Provides @Singleton
    fun provideImportLogUseCase(
        pathResolver: PathResolver,
        avcDenialParser: AvcDenialParser
    ): ImportLogUseCase = ImportLogUseCase(pathResolver, avcDenialParser)

    @Provides @Singleton
    fun provideAnalyzeDenialsUseCase(
        avcDenialParser: AvcDenialParser,
        denialMetadataParser: com.selinuxtoolbox.core.domain.parser.DenialMetadataParser,
        pathResolver: PathResolver
    ): AnalyzeDenialsUseCase = AnalyzeDenialsUseCase(
        avcDenialParser,
        denialMetadataParser,
        pathResolver
    )

    @Provides @Singleton
    fun provideApplyDenialFixesUseCase(
        pathResolver: PathResolver,
        backupOrchestrator: BackupOrchestrator
    ): ApplyDenialFixesUseCase = ApplyDenialFixesUseCase(pathResolver, backupOrchestrator)
}
