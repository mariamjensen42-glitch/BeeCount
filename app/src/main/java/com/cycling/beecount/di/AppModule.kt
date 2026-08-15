package com.cycling.beecount.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cycling.beecount.data.local.BeeCountDatabase
import com.cycling.beecount.data.local.CategoryDao
import com.cycling.beecount.data.local.EntryDao
import com.cycling.beecount.data.remote.DeepSeekApi
import com.cycling.beecount.data.remote.DeepSeekAiChatDataSource
import com.cycling.beecount.data.datasource.AiChatDataSource
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BeeCountDatabase =
        Room.databaseBuilder(
            context,
            BeeCountDatabase::class.java,
            "beecount.db",
        )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // 预置类别种子只在数据库首次创建时写入；enum 以 name 字符串存储
                    BeeCountDatabase.SEED_CATEGORIES.forEach { entity ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO categories (name, type, isCustom) VALUES (?, ?, ?)",
                            arrayOf<Any>(entity.name, entity.type.name, if (entity.isCustom) 1 else 0),
                        )
                    }
                }
            })
            .build()

    @Provides
    fun provideEntryDao(db: BeeCountDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideCategoryDao(db: BeeCountDatabase): CategoryDao = db.categoryDao()

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
