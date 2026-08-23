package com.cycling.beecount.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.cycling.beecount.BeeCountApplication
import com.cycling.beecount.data.local.BeeCountDatabase
import com.cycling.beecount.data.local.BudgetDao
import com.cycling.beecount.data.local.CategoryDao
import com.cycling.beecount.data.local.EntryDao
import com.cycling.beecount.data.local.TagDao
import com.cycling.beecount.data.remote.DeepSeekApi
import com.cycling.beecount.data.remote.DeepSeekAiChatDataSource
import com.cycling.beecount.data.datasource.AiChatDataSource
import com.cycling.beecount.data.repository.WidgetRefresher
import com.cycling.beecount.widget.OverviewWidget
import androidx.glance.appwidget.updateAll
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * 数据库由 Application 持有（创建/迁移逻辑见 BeeCountApplication）：App 内 Hilt 从这里拿，
     * 桌面小组件（不走 Hilt）也拿同一实例，保证全进程只有一个 Room 实例（ADR 0013）。
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BeeCountDatabase =
        (context.applicationContext as BeeCountApplication).database

    @Provides
    fun provideEntryDao(db: BeeCountDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideCategoryDao(db: BeeCountDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTagDao(db: BeeCountDatabase): TagDao = db.tagDao()

    @Provides
    fun provideBudgetDao(db: BeeCountDatabase): BudgetDao = db.budgetDao()

    /** 桌面小组件刷新器：账目写操作后触发全部 widget 实例更新（ADR 0013） */
    @Provides
    @Singleton
    fun provideWidgetRefresher(@ApplicationContext context: Context): WidgetRefresher =
        WidgetRefresher { OverviewWidget().updateAll(context) }

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("settings") },
        )

    @Provides
    @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(DeepSeekApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideDeepSeekApi(retrofit: Retrofit): DeepSeekApi =
        retrofit.create(DeepSeekApi::class.java)

    @Provides
    @Singleton
    fun provideAiChatDataSource(api: DeepSeekApi): AiChatDataSource =
        DeepSeekAiChatDataSource(api)

    @Provides
    fun provideCurrentDate(): () -> java.time.LocalDate = { java.time.LocalDate.now() }
}
