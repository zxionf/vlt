package io.zx.password

import kotlinx.coroutines.flow.Flow

class PwdRepository(private val dao: PwdDao) {
    fun getAll(): Flow<List<Pwd>> = dao.getAll()
    suspend fun insert(item: Pwd) = dao.insert(item)
    suspend fun update(item: Pwd) = dao.update(item)
    suspend fun delete(item: Pwd) = dao.delete(item)
}