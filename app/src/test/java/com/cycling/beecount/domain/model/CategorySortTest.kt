package com.cycling.beecount.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class CategorySortTest {

    private fun cat(
        id: Long,
        name: String,
        type: EntryType = EntryType.EXPENSE,
        parentId: Long? = null,
        sortOrder: Int = 0,
    ) = Category(id = id, name = name, type = type, parentId = parentId, sortOrder = sortOrder)

    @Test
    fun `子分类跟随父级，未手动排序按使用频率降序`() {
        val categories = listOf(
            cat(1, "娱乐"),
            cat(2, "餐饮"),
            cat(3, "购物"),
            cat(4, "餐饮·外卖", parentId = 2),
            cat(5, "餐饮·堂食", parentId = 2),
            cat(6, "购物·线上", parentId = 3),
        )
        val usage = mapOf<String, Int>(
            "购物" to 5,
            "餐饮" to 10,
            "娱乐" to 1,
            "餐饮·堂食" to 3,
            "餐饮·外卖" to 8,
            "购物·线上" to 2,
        )
        val sorted = sortCategories(categories) { usage[it.name] ?: 0 }
        // 一级：餐饮(10) > 购物(5) > 娱乐(1)
        // 子：餐饮·外卖(8) > 餐饮·堂食(3)；购物·线上
        assertEquals(
            listOf("餐饮", "餐饮·外卖", "餐饮·堂食", "购物", "购物·线上", "娱乐"),
            sorted.map { it.name },
        )
    }

    @Test
    fun `手动排序的分类置顶且按 sortOrder 升序，未手动的按频率后排`() {
        val categories = listOf(
            cat(1, "餐饮", sortOrder = 2),
            cat(2, "交通", sortOrder = 1),
            cat(3, "娱乐"),
            cat(4, "购物"),
        )
        val usage = mapOf("娱乐" to 99, "购物" to 50)
        val sorted = sortCategories(categories) { usage[it.name] ?: 0 }
        // 手动的先：交通(1) < 餐饮(2)；未手动的：娱乐(99) > 购物(50)
        assertEquals(listOf("交通", "餐饮", "娱乐", "购物"), sorted.map { it.name })
    }

    @Test
    fun `displayName 子分类取叶子名，一级取原名`() {
        val parent = cat(1, "餐饮")
        val child = cat(2, "餐饮·外卖", parentId = 1)
        assertEquals("餐饮", parent.displayName)
        assertEquals("外卖", child.displayName)
    }
}
