package io.zx.password

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.Flow

class PwdRepository(
    private val dao: PwdDao,
    private val tagDao: TagDao,
    private val deviceDao: DeviceDao
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

    suspend fun updateTag(tag: Tag) = tagDao.update(tag)

    suspend fun deleteTag(tag: Tag) = tagDao.delete(tag)

    fun getTagsForPassword(passwordId: String): Flow<List<Tag>> =
        tagDao.getTagsForPassword(passwordId)

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

    suspend fun getCurrentDeviceId(): String? {
        return deviceDao.getCurrentDevice()?.deviceId
    }
    suspend fun exportAllData(): String {
        val passwords = dao.getAll().first()
        val tags = tagDao.getAll().first()
        val joins = tagDao.getAllJoins()
        val devices = deviceDao.getAll().first()

        val root = org.json.JSONObject()
        root.put("version", 1)
        root.put("exportedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))
        root.put("passwords", org.json.JSONArray(passwords.map { p ->
            org.json.JSONObject().apply {
                put("id", p.id)
                put("title", p.title)
                put("username", p.username)
                put("encryptedPassword", p.encryptedPassword)
                put("encryptedNotes", p.encryptedNotes ?: org.json.JSONObject.NULL)
                put("url", p.url ?: org.json.JSONObject.NULL)
                put("createdDeviceId", p.createdDeviceId)
                put("lastModifiedDeviceId", p.lastModifiedDeviceId)
                put("createdAt", p.createdAt)
                put("updatedAt", p.updatedAt)
                put("syncVersion", p.syncVersion)
                put("isDeleted", p.isDeleted)
            }
        }))
        root.put("tags", org.json.JSONArray(tags.map { t ->
            org.json.JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
            }
        }))
        root.put("passwordTagJoins", org.json.JSONArray(joins.map { j ->
            org.json.JSONObject().apply {
                put("passwordId", j.passwordId)
                put("tagId", j.tagId)
            }
        }))
        root.put("devices", org.json.JSONArray(devices.map { d ->
            org.json.JSONObject().apply {
                put("deviceId", d.deviceId)
                put("deviceName", d.deviceName)
                put("publicKey", d.publicKey)
                put("encryptedDataKey", d.encryptedDataKey ?: org.json.JSONObject.NULL)
                put("isCurrentDevice", d.isCurrentDevice)
                put("createdAt", d.createdAt)
            }
        }))
        return root.toString(2)
    }

    suspend fun importAllData(json: String): Int {
        val root = org.json.JSONObject(json)
        val passwordsArr = root.optJSONArray("passwords") ?: return 0
        val tagsArr = root.optJSONArray("tags")
        val joinsArr = root.optJSONArray("passwordTagJoins")
        val devicesArr = root.optJSONArray("devices")

        val tagIdMap = mutableMapOf<Long, Long>()
        if (tagsArr != null) {
            for (i in 0 until tagsArr.length()) {
                val t = tagsArr.getJSONObject(i)
                val name = t.getString("name")
                val existing = tagDao.getByName(name)
                if (existing == null) {
                    val id = tagDao.insert(Tag(name = name))
                    tagIdMap[t.getLong("id")] = id
                } else {
                    tagIdMap[t.getLong("id")] = existing.id
                }
            }
        }

        if (devicesArr != null) {
            for (i in 0 until devicesArr.length()) {
                val d = devicesArr.getJSONObject(i)
                val deviceId = d.getString("deviceId")
                val existing = deviceDao.getAll().first().find { it.deviceId == deviceId }
                if (existing == null) {
                    deviceDao.insert(DeviceEntity(
                        deviceId = deviceId,
                        deviceName = d.getString("deviceName"),
                        publicKey = d.getString("publicKey"),
                        encryptedDataKey = if (d.isNull("encryptedDataKey")) null else d.getString("encryptedDataKey"),
                        isCurrentDevice = false,
                        createdAt = d.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
            }
        }

        var count = 0
        for (i in 0 until passwordsArr.length()) {
            val p = passwordsArr.getJSONObject(i)
            val id = p.getString("id")
            val existing = dao.getById(id)
            if (existing != null && !existing.isDeleted) continue
            val entry = PasswordEntry(
                id = id,
                title = p.getString("title"),
                username = p.getString("username"),
                encryptedPassword = p.getString("encryptedPassword"),
                encryptedNotes = if (p.isNull("encryptedNotes")) null else p.getString("encryptedNotes"),
                url = if (p.isNull("url")) null else p.getString("url"),
                createdDeviceId = p.optString("createdDeviceId", ""),
                lastModifiedDeviceId = p.optString("lastModifiedDeviceId", ""),
                createdAt = p.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = p.optLong("updatedAt", System.currentTimeMillis()),
                syncVersion = p.optInt("syncVersion", 1),
                isDeleted = p.optBoolean("isDeleted", false)
            )
            if (existing != null) dao.update(entry) else dao.insert(entry)
            count++
        }

        if (joinsArr != null) {
            for (i in 0 until joinsArr.length()) {
                val j = joinsArr.getJSONObject(i)
                val oldTagId = j.getLong("tagId")
                val newTagId = tagIdMap[oldTagId] ?: continue
                val passwordId = j.getString("passwordId")
                val join = PasswordTagJoin(passwordId = passwordId, tagId = newTagId)
                try { tagDao.insertJoin(join) } catch (_: Exception) {}
            }
        }

        return count
    }
}
