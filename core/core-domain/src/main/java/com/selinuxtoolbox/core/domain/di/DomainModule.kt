package com.selinuxtoolbox.core.domain.di

import com.selinuxtoolbox.core.data.root.RootFileReader
import com.selinuxtoolbox.core.data.root.RootShell
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
}
