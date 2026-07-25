package io.zx.password

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SyncManager {
    var baseUrl: String = "http://10.0.2.2:8080" // 模拟器默认宿主机地址

    // ─── 设备管理 ───

    /** 注册当前设备到服务器 */
    suspend fun registerDevice(
        deviceId: String,
        deviceName: String,
        publicKey: String,
        encryptedDataKey: String
    ): Result<String> = apiPost("/api/devices/register", JSONObject().apply {
        put("device_id", deviceId)
        put("device_name", deviceName)
        put("public_key", publicKey)
        put("encrypted_data_key", encryptedDataKey)
    })

    /** 查询指定设备 */
    suspend fun getDevice(deviceId: String): Result<JSONObject> =
        apiGet("/api/devices/$deviceId")

    /** 获取待授权设备列表 */
    suspend fun getPendingDevices(): Result<JSONArray> {
        val response = apiGet("/api/devices/pending")
        return response.map { it.getJSONArray("devices") }
    }

    /** 授权设备 */
    suspend fun authorizeDevice(
        fromDeviceId: String,
        toDeviceId: String,
        deviceName: String,
        encryptedDataKey: String
    ): Result<String> = apiPost("/api/devices/authorize", JSONObject().apply {
        put("from_device_id", fromDeviceId)
        put("to_device_id", toDeviceId)
        put("device_name", deviceName)
        put("encrypted_data_key", encryptedDataKey)
    })

    // ─── 密码同步 ───

    /** 上传密码记录 */
    suspend fun pushRecords(entries: List<PasswordEntry>): Result<String> {
        val arr = JSONArray()
        entries.forEach { arr.put(entryToJson(it)) }
        return apiPost("/api/sync/push", JSONObject().apply {
            put("records", arr)
        })
    }

    /** 下拉更新记录 */
    suspend fun pullRecords(since: Long): Result<List<PullRecord>> {
        val response = apiGet("/api/sync/pull/$since")
        return response.map { json ->
            val records = json.getJSONArray("records")
            val list = mutableListOf<PullRecord>()
            for (i in 0 until records.length()) {
                list.add(pullRecordFromJson(records.getJSONObject(i)))
            }
            list
        }
    }

    /** 健康检查 */
    suspend fun healthCheck(): Result<String> = apiGet("/api/health").map { it.getString("status") }

    // ─── 内部实现 ───

    private suspend fun apiGet(path: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()
            JSONObject(body)
        }
    }

    private suspend fun apiPost(path: String, json: JSONObject): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Content-Type", "application/json")
            }
            OutputStreamWriter(conn.outputStream).use { it.write(json.toString()); it.flush() }
            val body = BufferedReader(InputStreamReader(conn.inputStream)).readText()
            conn.disconnect()
            JSONObject(body).optString("message", "ok")
        }
    }

    // ─── 序列化 ───

    data class PullRecord(
        val id: String,
        val deviceId: String,
        val title: String,
        val username: String,
        val encryptedData: String,
        val encryptedNotes: String?,
        val url: String?,
        val createdDeviceId: String,
        val lastModifiedDeviceId: String,
        val createdAt: Long,
        val updatedAt: Long,
        val syncVersion: Int,
        val isDeleted: Boolean
    )

    private fun entryToJson(e: PasswordEntry): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("title", e.title)
        put("username", e.username)
        put("encrypted_data", e.encryptedPassword)
        put("encrypted_notes", e.encryptedNotes ?: "")
        put("url", e.url ?: "")
        put("created_device_id", e.createdDeviceId)
        put("last_modified_device_id", e.lastModifiedDeviceId)
        put("sync_version", e.syncVersion)
        put("is_deleted", e.isDeleted)
    }

    private fun pullRecordFromJson(j: JSONObject): PullRecord = PullRecord(
        id = j.getString("id"),
        deviceId = j.getString("device_id"),
        title = j.getString("title"),
        username = j.getString("username"),
        encryptedData = j.getString("encrypted_data"),
        encryptedNotes = j.getString("encrypted_notes").ifBlank { null },
        url = j.getString("url").ifBlank { null },
        createdDeviceId = j.getString("created_device_id"),
        lastModifiedDeviceId = j.getString("last_modified_device_id"),
        createdAt = j.optLong("created_at", System.currentTimeMillis()),
        updatedAt = j.optLong("updated_at", System.currentTimeMillis()),
        syncVersion = j.optInt("sync_version", 1),
        isDeleted = j.optBoolean("is_deleted", false)
    )
}
