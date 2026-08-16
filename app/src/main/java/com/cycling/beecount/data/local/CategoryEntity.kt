package com.cycling.beecount.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.model.EntryType

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val type: EntryType,
    val isCustom: Boolean,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    type = type,
    isCustom = isCustom,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    isCustom = isCustom,
)
