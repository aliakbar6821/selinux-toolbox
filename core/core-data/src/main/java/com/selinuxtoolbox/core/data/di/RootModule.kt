package com.selinuxtoolbox.core.data.di

import com.selinuxtoolbox.core.data.root.RootFileReader
import com.selinuxtoolbox.core.data.root.RootShell
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RootModule {

    @Provides
    @Singleton
    fun provideRootShell(): RootShell = RootShell()

    @Provides
    @Singleton
    fun provideRootFileReader(rootShell: RootShell): RootFileReader =
        RootFileReader(rootShell)
}
