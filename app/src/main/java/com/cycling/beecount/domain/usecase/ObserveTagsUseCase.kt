package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Tag
import com.cycling.beecount.domain.repository.TagRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 用例：观察全部标签（含颜色，供确认卡片与筛选使用）。
 */
class ObserveTagsUseCase @Inject constructor(
    private val tagRepository: TagRepository,
) {
    operator fun invoke(): Flow<List<Tag>> = tagRepository.observeAll()
}
