package com.cycling.beecount.domain.usecase

import com.cycling.beecount.domain.model.Category
import com.cycling.beecount.domain.repository.CategoryRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * 用例：观察全部类别（预定义 + 自定义）
 */
class ObserveCategoriesUseCase @Inject constructor(
    private val categoryRepository: CategoryRepository,
) {
    operator fun invoke(): Flow<List<Category>> = categoryRepository.observeAll()
}
