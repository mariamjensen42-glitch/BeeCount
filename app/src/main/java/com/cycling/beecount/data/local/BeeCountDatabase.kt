package com.cycling.beecount.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cycling.beecount.domain.model.EntryType

@Database(
    entities = [EntryEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(LocalDateConverter::class)
abstract class BeeCountDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        /** 预定义类别种子数据：只初始化一次，用户自定义类别在运行时创建 */
        val SEED_CATEGORIES: List<CategoryEntity> = listOf(
            // 支出
            CategoryEntity(name = "餐饮", type = EntryType.EXPENSE, isCustom = false),
            CategoryEntity(name = "交通", type = EntryType.EXPENSE, isCustom = false),
            CategoryEntity(name = "购物", type = EntryType.EXPENSE, isCustom = false),
            CategoryEntity(name = "居住", type = EntryType.EXPENSE, isCustom = false),
            CategoryEntity(name = "娱乐", type = EntryType.EXPENSE, isCustom = false),
            CategoryEntity(name = "医疗", type = EntryType.EXPENSE, isCustom = false),
            CategoryEntity(name = "教育", type = EntryType.EXPENSE, isCustom = false),
            CategoryEntity(name = "人情", type = EntryType.EXPENSE, isCustom = false),
            CategoryEntity(name = "其他", type = EntryType.EXPENSE, isCustom = false),
            // 收入
            CategoryEntity(name = "工资", type = EntryType.INCOME, isCustom = false),
            CategoryEntity(name = "奖金", type = EntryType.INCOME, isCustom = false),
            CategoryEntity(name = "红包", type = EntryType.INCOME, isCustom = false),
            CategoryEntity(name = "报销", type = EntryType.INCOME, isCustom = false),
            CategoryEntity(name = "理财", type = EntryType.INCOME, isCustom = false),
            CategoryEntity(name = "其他", type = EntryType.INCOME, isCustom = false),
        )
    }
}
