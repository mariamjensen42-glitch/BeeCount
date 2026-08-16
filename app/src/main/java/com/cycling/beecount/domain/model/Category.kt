package com.cycling.beecount.domain.model

/**
 * 账目类别：由预定义类别与用户自定义类别共同构成。
 */
data class Category(
    val id: Long = 0L,
    val name: String,
    val type: EntryType,
    val isCustom: Boolean = false,
)
