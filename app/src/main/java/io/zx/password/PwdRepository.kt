package io.zx.password

import kotlinx.coroutines.flow.Flow

class PwdRepository(
    private val dao: PwdDao,
    private val tagDao: TagDao
) {
    // Password CRUD
    fun getAll(): Flow<List<PasswdEntity>> = dao.getAll()
    suspend fun insert(item: PasswdEntity) = dao.insert(item)
    suspend fun update(item: PasswdEntity) = dao.update(item)
    suspend fun delete(item: PasswdEntity) = dao.delete(item)

    // Tag CRUD
    fun getAllTags(): Flow<List<Tag>> = tagDao.getAll()
    suspend fun insertTag(name: String): Tag {
        val trimmed = name.trim()
        val existing = tagDao.getByName(trimmed)
        if (existing != null) return existing
        val id = tagDao.insert(Tag(name = trimmed))
        return Tag(id = id, name = trimmed)
    }

    // Tag-Password association
    fun getTagsForPassword(passwdId: Long): Flow<List<Tag>> = tagDao.getTagsForPassword(passwdId)
    suspend fun addTagToPassword(passwdId: Long, tagId: Long) {
        tagDao.insertJoin(PasswordTagJoin(passwdId = passwdId, tagId = tagId))
    }
    suspend fun removeTagFromPassword(passwdId: Long, tagId: Long) {
        tagDao.deleteJoin(PasswordTagJoin(passwdId = passwdId, tagId = tagId))
    }
    suspend fun setTagsForPassword(passwdId: Long, tagNames: List<String>) {
        // Remove all existing joins
        tagDao.deleteJoinsForPassword(passwdId)
        // Add new joins
        tagNames.forEach { name ->
            val tag = insertTag(name)
            addTagToPassword(passwdId, tag.id)
        }
    }
    suspend fun deleteJoinsForPassword(passwdId: Long) {
        tagDao.deleteJoinsForPassword(passwdId)
    }
}
