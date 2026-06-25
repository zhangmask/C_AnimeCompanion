package com.companion.chat.data.memory

/**
 * 提取结果数据类。
 *
 * 改造后统一使用 strength=0.6 初始值，不再需要 layer/expiresAt。
 * 新增 entityName 用于图构建（链接信息通过 ExtractedLink 传递）。
 */
data class ExtractedMemory(
    val content: String,
    val category: String,           // fact / preference / event / behavior / knowledge / skill
    val source: String,
    val entityName: String? = null     // 提取时附带的实体名
)
