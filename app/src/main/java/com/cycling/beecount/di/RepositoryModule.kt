package com.cycling.beecount.di

import com.cycling.beecount.data.repository.DataStoreAiKeyRepository
import com.cycling.beecount.data.repository.RoomCategoryRepository
import com.cycling.beecount.data.repository.RoomEntryRepository
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.EntryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindEntryRepository(impl: RoomEntryRepository): EntryRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindAiKeyRepository(impl: DataStoreAiKeyRepository): AiKeyRepository
}
