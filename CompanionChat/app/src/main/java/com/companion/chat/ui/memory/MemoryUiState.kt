package com.companion.chat.ui.memory

import com.companion.chat.data.local.entity.Memory
import com.companion.chat.data.local.entity.RoleCard

enum class MemoryFilter(val category: String?) {
    ALL(null),
    FACT("fact"),
    PREFERENCE("preference"),
    EVENT("event"),
    RELATION("relation"),
    TIME("time"),
    OTHER("other")
}

data class MemoryUiState(
    val memories: List<Memory> = emptyList(),
    val filter: MemoryFilter = MemoryFilter.ALL,
    val selectedRoleCardId: Long? = null,
    val isLoading: Boolean = true,
    val roleCards: List<RoleCard> = emptyList()
)
