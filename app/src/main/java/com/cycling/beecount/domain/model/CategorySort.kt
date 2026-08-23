package com.cycling.beecount.domain.model

/**
 * 类别排序：同类型内一级分类置顶、其子分类紧随其后。
 * 同一层级内：手动排序（sortOrder > 0）靠前按 sortOrder 升序；
 * 未手动的（sortOrder == 0）按使用频率 [usageCount] 降序，频率再相同按 id 兜底保持稳定。
 */
fun sortCategories(categories: List<Category>, usageCount: (Category) -> Int = { 0 }): List<Category> {
    val byGroup: Comparator<Category> =
        compareBy<Category> { if (it.sortOrder > 0) 0 else 1 }
            .thenBy { it.sortOrder }
            .thenByDescending { usageCount(it) }
            .thenBy { it.id }

    val result = mutableListOf<Category>()
    val parents = categories.filter { it.parentId == null }.sortedWith(byGroup)
    parents.forEach { parent ->
        result += parent
        result += categories.filter { it.parentId == parent.id }.sortedWith(byGroup)
    }
    // 兜底：父级已被删除但未清理的孤立子分类也展示
    val handled = result.mapTo(mutableSetOf()) { it.id }
    categories.filter { it.parentId != null && it.id !in handled }.sortedWith(byGroup).forEach { result += it }
    return result
}
