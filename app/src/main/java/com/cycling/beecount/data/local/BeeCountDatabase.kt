package com.cycling.beecount.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.cycling.beecount.domain.model.EntryType

@Database(
    entities = [
        EntryEntity::class,
        CategoryEntity::class,
        TagEntity::class,
        EntryTagEntity::class,
        BudgetEntity::class,
        BudgetExceptionEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
@TypeConverters(LocalDateConverter::class)
abstract class BeeCountDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun categoryDao(): CategoryDao
    abstract fun tagDao(): TagDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        /** 预定义类别种子数据：只初始化一次，用户自定义类别在运行时创建 */
        val SEED_CATEGORIES: List<CategoryEntity> = listOf(
            // 支出
            CategoryEntity(name = "餐饮", type = EntryType.EXPENSE, isCustom = false, icon = "🍜", color = 0xFFFF8A80),
            CategoryEntity(name = "交通", type = EntryType.EXPENSE, isCustom = false, icon = "🚗", color = 0xFF4FC3F7),
            CategoryEntity(name = "购物", type = EntryType.EXPENSE, isCustom = false, icon = "🛍️", color = 0xFFF06292),
            CategoryEntity(name = "快递物流", type = EntryType.EXPENSE, isCustom = false, icon = "📦", color = 0xFFA1887F),
            CategoryEntity(name = "居住", type = EntryType.EXPENSE, isCustom = false, icon = "🏠", color = 0xFFFFD54F),
            CategoryEntity(name = "娱乐", type = EntryType.EXPENSE, isCustom = false, icon = "🎮", color = 0xFF7986CB),
            CategoryEntity(name = "医疗", type = EntryType.EXPENSE, isCustom = false, icon = "🩺", color = 0xFFFF8A80),
            CategoryEntity(name = "教育", type = EntryType.EXPENSE, isCustom = false, icon = "📚", color = 0xFFBA68C8),
            CategoryEntity(name = "人情", type = EntryType.EXPENSE, isCustom = false, icon = "🎁", color = 0xFFFFB74D),
            CategoryEntity(name = "其他", type = EntryType.EXPENSE, isCustom = false, icon = "📌", color = 0xFF90A4AE),
            // 收入
            CategoryEntity(name = "工资", type = EntryType.INCOME, isCustom = false, icon = "💼", color = 0xFF4DB6AC),
            CategoryEntity(name = "奖金", type = EntryType.INCOME, isCustom = false, icon = "🏆", color = 0xFFFFD54F),
            CategoryEntity(name = "红包", type = EntryType.INCOME, isCustom = false, icon = "🧧", color = 0xFFF06292),
            CategoryEntity(name = "报销", type = EntryType.INCOME, isCustom = false, icon = "💳", color = 0xFF64B5F6),
            CategoryEntity(name = "理财", type = EntryType.INCOME, isCustom = false, icon = "📈", color = 0xFF81C784),
            CategoryEntity(name = "其他", type = EntryType.INCOME, isCustom = false, icon = "📌", color = 0xFF90A4AE),
        )

        /** 预定义标签种子数据（带颜色，见 ADR 0007）；用户自定义标签在运行时创建 */
        val SEED_TAGS: List<TagEntity> = listOf(
            TagEntity(name = "旅行", color = 0xFF81C784, isCustom = false),
            TagEntity(name = "出差", color = 0xFF64B5F6, isCustom = false),
            TagEntity(name = "宠物", color = 0xFFFFB74D, isCustom = false),
            TagEntity(name = "健身", color = 0xFF4DB6AC, isCustom = false),
            TagEntity(name = "学习", color = 0xFFBA68C8, isCustom = false),
            TagEntity(name = "礼物", color = 0xFFF06292, isCustom = false),
            TagEntity(name = "大额", color = 0xFFD4A35A, isCustom = false),
            TagEntity(name = "订阅", color = 0xFF90A4AE, isCustom = false),
        )
    }
}
