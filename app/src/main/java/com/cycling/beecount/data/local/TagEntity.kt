package com.cycling.beecount.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cycling.beecount.domain.model.Tag

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    /** 0xAARRGGBB，存 Long 避免 Int 符号问题 */
    val color: Long,
    val isCustom: Boolean,
)

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    color = color,
    isCustom = isCustom,
)

fun Tag.toEntity(): TagEntity = TagEntity(
    id = id,
    name = name,
    color = color,
    isCustom = isCustom,
)
