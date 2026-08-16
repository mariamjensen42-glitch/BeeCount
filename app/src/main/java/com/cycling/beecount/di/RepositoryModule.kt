package com.cycling.beecount.di

import com.cycling.beecount.data.repository.DataStoreAiKeyRepository
import com.cycling.beecount.data.repository.DataStoreAutoEntrySettingsRepository
import com.cycling.beecount.data.repository.RoomCategoryRepository
import com.cycling.beecount.data.repository.RoomEntryRepository
import com.cycling.beecount.data.repository.RoomPendingDraftRepository
import com.cycling.beecount.data.repository.RoomProcessedNotificationRepository
import com.cycling.beecount.data.repository.RoomTagRepository
import com.cycling.beecount.data.repository.WidgetAwareEntryRepository
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.repository.AutoEntrySettingsRepository
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.PendingDraftRepository
import com.cycling.beecount.domain.repository.ProcessedNotificationRepository
import com.cycling.beecount.domain.repository.TagRepository
import com.cycling.beecount.domain.usecase.AutoEntryNotifier
import com.cycling.beecount.domain.usecase.MlKitOcrImageLoader
import com.cycling.beecount.domain.usecase.OcrImageLoader
import com.cycling.beecount.notification.AutoEntryNotificationPoster
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    /** 基础实现绑定为命名依赖，供装饰器委托（ADR 0013：widget 变更即推） */
    @Binds
    @Named("base")
    @Singleton
    abstract fun bindBaseEntryRepository(impl: RoomEntryRepository): EntryRepository

    @Binds
    @Singleton
    abstract fun bindEntryRepository(impl: WidgetAwareEntryRepository): EntryRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindAiKeyRepository(impl: DataStoreAiKeyRepository): AiKeyRepository

    @Binds
    @Singleton
    abstract fun bindTagRepository(impl: RoomTagRepository): TagRepository

    @Binds
    @Singleton
    abstract fun bindPendingDraftRepository(impl: RoomPendingDraftRepository): PendingDraftRepository

    @Binds
    @Singleton
    abstract fun bindProcessedNotificationRepository(
        impl: RoomProcessedNotificationRepository,
    ): ProcessedNotificationRepository

    @Binds
    @Singleton
    abstract fun bindAutoEntrySettingsRepository(
        impl: DataStoreAutoEntrySettingsRepository,
    ): AutoEntrySettingsRepository

    @Binds
    @Singleton
    abstract fun bindAutoEntryNotifier(impl: AutoEntryNotificationPoster): AutoEntryNotifier

    @Binds
    abstract fun bindOcrImageLoader(impl: MlKitOcrImageLoader): OcrImageLoader
}
