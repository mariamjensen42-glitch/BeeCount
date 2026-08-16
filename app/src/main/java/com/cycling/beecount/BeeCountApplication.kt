package com.cycling.beecount

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cycling.beecount.data.local.BeeCountDatabase
import com.cycling.beecount.notification.NotificationChannels
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BeeCountApplication : Application() {

    /**
     * 全局唯一数据库实例：App 内由 Hilt 的 AppModule 提供；
     * 桌面小组件（系统实例化 receiver、不走 Hilt 图）通过 Application 复用同一实例，
     * 避免对同一数据库文件开两个 Room 实例（ADR 0013）。
     */
    val database: BeeCountDatabase by lazy { createDatabase(this) }

    override fun onCreate() {
        super.onCreate()
        // 自动记账的确认/失败提醒通道（ADR 0014）
        NotificationChannels.create(this)
    }
}

internal fun createDatabase(context: Context): BeeCountDatabase =
    Room.databaseBuilder(
        context,
        BeeCountDatabase::class.java,
        "beecount.db",
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

/**
 * v4 → v5：自动记账（ADR 0014）新增待确认草稿表与已处理通知去重表。
 * 只建新表与唯一索引，不动已有数据，无损。
 */
private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `pending_drafts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `type` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `amountRaw` TEXT NOT NULL,
                `categoryName` TEXT NOT NULL,
                `date` TEXT NOT NULL,
                `tagsJson` TEXT NOT NULL,
                `note` TEXT,
                `originalText` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `processed_notifications` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `packageName` TEXT NOT NULL,
                `notifyKey` TEXT NOT NULL,
                `text` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_processed_notifications_packageName_notifyKey_text` " +
                "ON `processed_notifications` (`packageName`, `notifyKey`, `text`)"
        )
    }
}
