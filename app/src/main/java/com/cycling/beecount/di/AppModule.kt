package com.cycling.beecount.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cycling.beecount.data.local.BeeCountDatabase
import com.cycling.beecount.data.local.CategoryDao
import com.cycling.beecount.data.local.EntryDao
import com.cycling.beecount.data.local.TagDao
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // 预置类别/标签种子只在数据库首次创建时写入；enum 以 name 字符串存储
                    BeeCountDatabase.SEED_CATEGORIES.forEach { entity ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO categories (name, type, isCustom) VALUES (?, ?, ?)",
                            arrayOf<Any>(entity.name, entity.type.name, if (entity.isCustom) 1 else 0),
                        )
                    }
                    BeeCountDatabase.SEED_TAGS.forEach { entity ->
                        db.execSQL(
                            "INSERT OR IGNORE INTO tags (name, color, isCustom) VALUES (?, ?, ?)",
                            arrayOf<Any>(entity.name, entity.color, if (entity.isCustom) 1 else 0),
                        )
                    }
                }
            })
            .build()

    /** v1 → v2：新增 tags 与 entry_tags 两张表（ADR 0007），不动已有账目数据 */
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `tags` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `color` INTEGER NOT NULL,
                    `isCustom` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `entry_tags` (
                    `entryId` INTEGER NOT NULL,
                    `tagId` INTEGER NOT NULL,
                    PRIMARY KEY(`entryId`, `tagId`),
                    FOREIGN KEY(`entryId`) REFERENCES `entries`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_entry_tags_tagId` ON `entry_tags` (`tagId`)"
            )
            BeeCountDatabase.SEED_TAGS.forEach { entity ->
                db.execSQL(
                    "INSERT OR IGNORE INTO tags (name, color, isCustom) VALUES (?, ?, ?)",
                    arrayOf<Any>(entity.name, entity.color, if (entity.isCustom) 1 else 0),
                )
            }
        }
    }

    /**
     * v2 → v3：账目表新增可空 sourceRef 列并建可空唯一索引（ADR 0012 微信账单导入去重）。
     * 只加列与索引，不动已有数据，无损。
     */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `entries` ADD COLUMN `sourceRef` TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_entries_sourceRef` ON `entries` (`sourceRef`)")
        }
    }

    /**
     * v3 → v4：类别表补预置「快递物流」支出类别（微信账单导入分类映射用，用户反馈运费
     * 不应归购物）。纯数据补种，老库首次升级时 INSERT OR IGNORE，不动已有数据。
     */
    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "INSERT OR IGNORE INTO categories (name, type, isCustom) VALUES (?, ?, ?)",
                arrayOf<Any>("快递物流", "EXPENSE", 0),
            )
        }
    }

    @Provides
    fun provideEntryDao(db: BeeCountDatabase): EntryDao = db.entryDao()

    @Provides
    fun provideCategoryDao(db: BeeCountDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideTagDao(db: BeeCountDatabase): TagDao = db.tagDao()

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
