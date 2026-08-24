package com.cycling.beecount

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cycling.beecount.data.local.BeeCountDatabase
import com.cycling.beecount.domain.model.DEFAULT_CATEGORY_COLOR
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class BeeCountApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initLogging()
        installUncaughtExceptionHandler()
    }

    /**
     * 全局唯一数据库实例：App 内由 Hilt 的 AppModule 提供；
     * 桌面小组件（系统实例化 receiver、不走 Hilt 图）通过 Application 复用同一实例，
     * 避免对同一数据库文件开两个 Room 实例（ADR 0013）。
     */
    val database: BeeCountDatabase by lazy { createDatabase(this) }

    private fun initLogging() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(ReleaseTree())
        }
        Timber.d("BeeCount 启动，debug=%s", BuildConfig.DEBUG)
    }

    private fun installUncaughtExceptionHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Timber.e(throwable, "未捕获异常 @ thread=%s", thread.name)
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** release 版只记录 WARN/ERROR，过滤冗余 debug 日志 */
    private class ReleaseTree : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority < Log.WARN) return
            if (priority == Log.ERROR) {
                Log.e(tag ?: "BeeCount", message, t)
            } else {
                Log.w(tag ?: "BeeCount", message, t)
            }
        }
    }
}

internal fun createDatabase(context: Context): BeeCountDatabase =
    Room.databaseBuilder(
        context,
        BeeCountDatabase::class.java,
        "beecount.db",
    )
        .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
        )
        .addCallback(object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                Timber.i("Room 数据库首次创建，开始写入种子类别/标签")
                // 预置类别/标签种子只在数据库首次创建时写入；enum 以 name 字符串存储
                BeeCountDatabase.SEED_CATEGORIES.forEach { entity ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO categories (name, type, isCustom, parentId, icon, color, sortOrder, isHidden) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        arrayOf<Any?>(
                            entity.name,
                            entity.type.name,
                            if (entity.isCustom) 1 else 0,
                            null,
                            entity.icon,
                            entity.color,
                            entity.sortOrder,
                            if (entity.isHidden) 1 else 0,
                        ),
                    )
                }
                BeeCountDatabase.SEED_TAGS.forEach { entity ->
                    db.execSQL(
                        "INSERT OR IGNORE INTO tags (name, color, isCustom) VALUES (?, ?, ?)",
                        arrayOf<Any>(entity.name, entity.color, if (entity.isCustom) 1 else 0),
                    )
                }
                Timber.i("Room 种子数据写入完成：类别 %d 个、标签 %d 个",
                    BeeCountDatabase.SEED_CATEGORIES.size, BeeCountDatabase.SEED_TAGS.size)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                Timber.d("Room 数据库已打开（version=%s）", db.version)
            }
        })
        .build()

/** v1 → v2：新增 tags 与 entry_tags 两张表（ADR 0007），不动已有账目数据 */
private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.i("数据库迁移 1→2：新增 tags 与 entry_tags")
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
        Timber.i("数据库迁移 2→3：entries 新增 sourceRef 列与唯一索引")
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
        Timber.i("数据库迁移 3→4：补种「快递物流」支出类别")
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
        Timber.i("数据库迁移 4→5：新增 pending_drafts 与 processed_notifications")
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

/** v5 → v6：自动记账下线（ADR 0014 弃用），删除两张表及其唯一索引 */
private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.i("数据库迁移 5→6：删除自动记账两张表")
        db.execSQL("DROP TABLE IF EXISTS `pending_drafts`")
        db.execSQL("DROP TABLE IF EXISTS `processed_notifications`")
    }
}

/**
 * v6 → v7：类别二级层级与展示属性（新增子分类、图标 Emoji、颜色、手动拖拽排序、隐藏）。
 * 为 categories 表新增列（无损），并为预置分类补默认 icon/color。
 * 老库升级时只加列与补种，不动已有账目数据与用户自定义分类。
 */
private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.i("数据库迁移 6→7：categories 新增层级与展示属性列")
        db.execSQL(
            "ALTER TABLE `categories` ADD COLUMN `parentId` INTEGER DEFAULT NULL"
        )
        db.execSQL(
            "ALTER TABLE `categories` ADD COLUMN `icon` TEXT NOT NULL DEFAULT ''"
        )
        db.execSQL(
            "ALTER TABLE `categories` ADD COLUMN `color` INTEGER NOT NULL DEFAULT ${DEFAULT_CATEGORY_COLOR}"
        )
        db.execSQL(
            "ALTER TABLE `categories` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0"
        )
        db.execSQL(
            "ALTER TABLE `categories` ADD COLUMN `isHidden` INTEGER NOT NULL DEFAULT 0"
        )
        // 预置分类补默认图标与颜色；只匹配「同类型同名」的预置行，不影响用户自定义分类。
        BeeCountDatabase.SEED_CATEGORIES.forEach { entity ->
            db.execSQL(
                "UPDATE categories SET icon = ?, color = ? WHERE name = ? AND type = ? AND isCustom = 0",
                arrayOf<Any>(entity.icon, entity.color, entity.name, entity.type.name),
            )
        }
    }
}

/**
 * v7 → v8：预算系统。新增 budgets 表与 budget_exceptions 表。
 * - budgets：一条 = 周期 × 维度 × 金额；cycle 存 enum name；categoryName 为 null 表示总预算。
 * - budget_exceptions：预算例外日，该日账目不计入预算消费。均为新表，无损新增。
 */
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.i("数据库迁移 7→8：新增 budgets 与 budget_exceptions")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budgets` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `cycle` TEXT NOT NULL,
                `lengthDays` INTEGER NOT NULL DEFAULT 30,
                `customAnchor` TEXT,
                `categoryName` TEXT,
                `amount` REAL NOT NULL,
                `carryOver` INTEGER NOT NULL DEFAULT 1,
                `enabled` INTEGER NOT NULL DEFAULT 1,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `budget_exceptions` (
                `date` TEXT NOT NULL PRIMARY KEY
            )
            """.trimIndent()
        )
    }
}

/**
 * v8 → v9：entries 表新增可空 counterparty 列（交易对方，用于账本页按对方筛选）。
 * 只加列，不动已有数据，无损。
 */
private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.i("数据库迁移 8→9：entries 新增 counterparty 列")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `counterparty` TEXT")
    }
}

/**
 * v9 → v10：entries 表新增报销标记 isReimbursed 列（支持「记录是否已报销」）。
 * 只加列（默认 0 = 未报销），不动已有数据，无损。
 */
private val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        Timber.i("数据库迁移 9→10：entries 新增 isReimbursed 列")
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `isReimbursed` INTEGER NOT NULL DEFAULT 0")
    }
}
