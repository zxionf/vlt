package io.zx.password

import kotlinx.coroutines.flow.Flow

class PwdRepository(private val dao: PwdDao) {
    fun getAll(): Flow<List<PasswdEntity>> = dao.getAll()
    suspend fun insert(item: PasswdEntity) = dao.insert(item)
    suspend fun update(item: PasswdEntity) = dao.update(item)
    suspend fun delete(item: PasswdEntity) = dao.delete(item)
}
