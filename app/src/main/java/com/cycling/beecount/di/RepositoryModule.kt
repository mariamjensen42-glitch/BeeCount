package com.cycling.beecount.di

import com.cycling.beecount.data.repository.DataStoreAiKeyRepository
import com.cycling.beecount.data.repository.DataStoreQuickTemplateRepository
import com.cycling.beecount.data.repository.RoomBudgetRepository
import com.cycling.beecount.data.repository.RoomCategoryRepository
import com.cycling.beecount.data.repository.RoomEntryRepository
import com.cycling.beecount.data.repository.RoomTagRepository
import com.cycling.beecount.data.ml.TfliteCategoryClassifier
import com.cycling.beecount.data.repository.WidgetAwareEntryRepository
import com.cycling.beecount.data.notify.Notifier
import com.cycling.beecount.domain.repository.AiKeyRepository
import com.cycling.beecount.domain.repository.BudgetRepository
import com.cycling.beecount.domain.repository.CategoryRepository
import com.cycling.beecount.domain.repository.EntryRepository
import com.cycling.beecount.domain.repository.QuickTemplateRepository
import com.cycling.beecount.domain.repository.TagRepository
import com.cycling.beecount.domain.usecase.MlKitOcrTextRecognizer
import com.cycling.beecount.domain.usecase.OcrTextRecognizer
import com.cycling.beecount.domain.usecase.OnDeviceSpeechToText
import com.cycling.beecount.domain.usecase.AnomalyNotifier
import com.cycling.beecount.domain.usecase.CategoryClassifier
import com.cycling.beecount.domain.usecase.SpeechToText
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
    abstract fun bindAnomalyNotifier(impl: Notifier): AnomalyNotifier

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
    abstract fun bindBudgetRepository(impl: RoomBudgetRepository): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindQuickTemplateRepository(impl: DataStoreQuickTemplateRepository): QuickTemplateRepository

    @Binds
    abstract fun bindOcrTextRecognizer(impl: MlKitOcrTextRecognizer): OcrTextRecognizer

    @Binds
    abstract fun bindSpeechRecognizer(impl: OnDeviceSpeechToText): SpeechToText

    @Binds
    @Singleton
    abstract fun bindCategoryClassifier(impl: TfliteCategoryClassifier): CategoryClassifier
}
