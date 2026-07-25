package io.zx.password

import kotlinx.coroutines.flow.Flow

class PwdRepository(
    private val dao: PwdDao,
    private val tagDao: TagDao
) {
    fun getAll(): Flow<List<PasswordEntry>> = dao.getAll()
    suspend fun insert(item: PasswordEntry) = dao.insert(item)
    suspend fun update(item: PasswordEntry) = dao.update(item)
    suspend fun delete(item: PasswordEntry) = dao.delete(item)

    fun getAllTags(): Flow<List<Tag>> = tagDao.getAll()
    suspend fun insertTag(name: String): Tag {
        val trimmed = name.trim()
        val existing = tagDao.getByName(trimmed)
        if (existing != null) return existing
        val id = tagDao.insert(Tag(name = trimmed))
        return Tag(id = id, name = trimmed)
    }

    fun getTagsForPassword(passwordId: String): Flow<List<Tag>> = tagDao.getTagsForPassword(passwordId)
    suspend fun addTagToPassword(passwordId: String, tagId: Long) {
        tagDao.insertJoin(PasswordTagJoin(passwordId = passwordId, tagId = tagId))
    }
    suspend fun removeTagFromPassword(passwordId: String, tagId: Long) {
        tagDao.deleteJoin(PasswordTagJoin(passwordId = passwordId, tagId = tagId))
    }
    suspend fun setTagsForPassword(passwordId: String, tagNames: List<String>) {
        tagDao.deleteJoinsForPassword(passwordId)
        tagNames.forEach { name ->
            val tag = insertTag(name)
            addTagToPassword(passwordId, tag.id)
        }
    }
    suspend fun deleteJoinsForPassword(passwordId: String) {
        tagDao.deleteJoinsForPassword(passwordId)
    }
}
