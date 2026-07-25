package io.zx.password.crypto

import android.content.Context
import io.zx.password.PasswordEntry
import io.zx.password.PasswordTagJoin
import io.zx.password.PwdDB
import io.zx.password.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object BackupHelper {
    private const val VERSION = 1
    private const val EXT = ".pwdbackup"

    data class BackupData(
        val version: Int,
        val entries: List<PasswordEntry>,
        val tags: List<Tag>,
        val joins: List<PasswordTagJoin>
    )

    /** 导出加密备份 */
    fun export(context: Context, outputFile: File, onProgress: (String) -> Unit = {}) {
        onProgress("读取数据库...")
        val db = PwdDB.getInstance(context)
        val entries = runBlocking(Dispatchers.IO) {
            db.PwdDao().getAll().let { flow ->
                var list: List<PasswordEntry> = emptyList()
                kotlinx.coroutines.runBlocking { flow.collect { list = it } }
                list
            }
        }
        val tags = runBlocking(Dispatchers.IO) {
            db.TagDao().getAll().let { flow ->
                var list: List<Tag> = emptyList()
                kotlinx.coroutines.runBlocking { flow.collect { list = it } }
                list
            }
        }
        val joins = mutableListOf<PasswordTagJoin>()
        // 从 tagMap 重建 joins
        entries.forEach { entry ->
            runBlocking(Dispatchers.IO) {
                db.TagDao().getTagsForPassword(entry.id).collect { tagList ->
                    tagList.forEach { tag ->
                        joins.add(PasswordTagJoin(passwordId = entry.id, tagId = tag.id))
                    }
                    return@collect
                }
            }
        }

        onProgress("序列化...")
        val json = toJson(BackupData(VERSION, entries, tags, joins))
        val plaintext = json.toByteArray(Charsets.UTF_8)

        onProgress("加密...")
        val result = CryptoManager.encryptBytes(plaintext, SessionManager.dataKey!!)
        val ivB64 = CryptoManager.bytesToBase64(result.iv)
        val ctB64 = CryptoManager.bytesToBase64(result.ciphertext)

        onProgress("写入文件...")
        FileOutputStream(outputFile).use { out ->
            out.write("PWD_BAK\n".toByteArray())
            out.write("$ivB64\n".toByteArray())
            out.write(ctB64.toByteArray())
        }
        onProgress("完成: ${outputFile.absolutePath}")
    }

    /** 导入加密备份 */
    fun import(context: Context, inputFile: File, onProgress: (String) -> Unit = {}) {
        onProgress("读取文件...")
        val lines = FileInputStream(inputFile).bufferedReader().readLines()
        if (lines.size < 3 || lines[0] != "PWD_BAK") throw Exception("无效备份文件")
        val iv = CryptoManager.base64ToBytes(lines[1])
        val ct = lines[2].toByteArray()

        onProgress("解密...")
        val plaintext = CryptoManager.decryptBytes(iv, ct, SessionManager.dataKey!!)

        onProgress("解析数据...")
        val json = String(plaintext, Charsets.UTF_8)
        val backup = fromJson(json)

        onProgress("写入数据库 (${backup.entries.size} 条)...")
        val db = PwdDB.getInstance(context)
        runBlocking(Dispatchers.IO) {
            backup.entries.forEach { db.PwdDao().insert(it) }
            backup.tags.forEach { db.TagDao().insert(it) }
            backup.joins.forEach { db.TagDao().insertJoin(it) }
        }
        onProgress("导入完成")
    }

    private fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("version", data.version)
        root.put("entries", JSONArray().apply {
            data.entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("title", e.title)
                    put("username", e.username)
                    put("encryptedPassword", e.encryptedPassword)
                    put("encryptedNotes", e.encryptedNotes ?: "")
                    put("url", e.url ?: "")
                    put("createdDeviceId", e.createdDeviceId)
                    put("lastModifiedDeviceId", e.lastModifiedDeviceId)
                    put("createdAt", e.createdAt)
                    put("updatedAt", e.updatedAt)
                    put("syncVersion", e.syncVersion)
                    put("isDeleted", e.isDeleted)
                })
            }
        })
        root.put("tags", JSONArray().apply {
            data.tags.forEach { t ->
                put(JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                })
            }
        })
        root.put("joins", JSONArray().apply {
            data.joins.forEach { j ->
                put(JSONObject().apply {
                    put("passwordId", j.passwordId)
                    put("tagId", j.tagId)
                })
            }
        })
        return root.toString(2)
    }

    private fun fromJson(json: String): BackupData {
        val root = JSONObject(json)
        val entries = mutableListOf<PasswordEntry>()
        root.getJSONArray("entries").let { arr ->
            for (i in 0 until arr.length()) {
                val e = arr.getJSONObject(i)
                entries.add(PasswordEntry(
                    id = e.getString("id"),
                    title = e.getString("title"),
                    username = e.getString("username"),
                    encryptedPassword = e.getString("encryptedPassword"),
                    encryptedNotes = e.getString("encryptedNotes").ifBlank { null },
                    url = e.getString("url").ifBlank { null },
                    createdDeviceId = e.getString("createdDeviceId"),
                    lastModifiedDeviceId = e.getString("lastModifiedDeviceId"),
                    createdAt = e.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = e.optLong("updatedAt", System.currentTimeMillis()),
                    syncVersion = e.optInt("syncVersion", 1),
                    isDeleted = e.optBoolean("isDeleted", false)
                ))
            }
        }
        val tags = mutableListOf<Tag>()
        root.getJSONArray("tags").let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                tags.add(Tag(id = t.getLong("id"), name = t.getString("name")))
            }
        }
        val joins = mutableListOf<PasswordTagJoin>()
        root.getJSONArray("joins").let { arr ->
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                joins.add(PasswordTagJoin(
                    passwordId = j.getString("passwordId"),
                    tagId = j.getLong("tagId")
                ))
            }
        }
        return BackupData(VERSION, entries, tags, joins)
    }
}
