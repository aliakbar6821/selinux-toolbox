package com.selinuxtoolbox.core.data.di

import android.content.Context
import com.selinuxtoolbox.core.data.binary.BinaryManager
import com.selinuxtoolbox.core.data.root.RootShell
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BinaryModule {

    @Provides
    @Singleton
    fun provideBinaryManager(
        @ApplicationContext context: Context,
        rootShell: RootShell
    ): BinaryManager = BinaryManager(context, rootShell)
}
