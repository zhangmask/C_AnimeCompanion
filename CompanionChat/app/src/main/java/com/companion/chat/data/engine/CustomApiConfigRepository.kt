package com.companion.chat.data.engine

import com.companion.chat.data.local.dao.CustomApiConfigDao
import com.companion.chat.data.local.entity.CustomApiConfig

class CustomApiConfigRepository(
    private val dao: CustomApiConfigDao
) {
    suspend fun getAll(): List<CustomApiConfig> = dao.getAll()

    suspend fun getById(id: Long): CustomApiConfig? = dao.getById(id)

    suspend fun getActive(): CustomApiConfig? = dao.getActive()

    suspend fun upsert(config: CustomApiConfig): Long {
        return if (config.id == 0L) {
            dao.insert(config)
        } else {
            dao.update(config)
            config.id
        }
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun activate(id: Long) {
        dao.deactivateAll()
        dao.activate(id)
    }
}
