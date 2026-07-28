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
    var baseUrl: String = "http://10.0.2.2:8080"

    // ─── 同步 ───

    suspend fun pushRecords(records: List<PushRecord>): String = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        records.forEach { arr.put(JSONObject().apply {
            put("record_id", it.id); put("encrypted_blob", it.encryptedBlob)
            put("sync_version", it.syncVersion); put("device_id", it.deviceId)
            put("client_updated_at", it.clientUpdatedAt)
        })}
        post("/api/sync/push", JSONObject().apply { put("records", arr) })
    }

    suspend fun pullRecords(since: Long): List<ServerRecord> = withContext(Dispatchers.IO) {
        val body = get("/api/sync/pull/$since")
        if (body.startsWith("{")) { // error response
            return@withContext emptyList()
        }
        val arr = JSONArray(body)
        (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            ServerRecord(
                recordId = obj.getString("record_id"),
                encryptedBlob = obj.getString("encrypted_blob"),
                syncVersion = obj.optInt("sync_version", 1),
                deviceId = obj.getString("device_id"),
                clientUpdatedAt = obj.optLong("client_updated_at", 0L),
                serverUpdatedAt = obj.optLong("server_updated_at", 0L)
            )
        }
    }

    // ─── 内部实现 ───

    private fun get(path: String): String {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 5000; readTimeout = 5000
        }
        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }

    private fun post(path: String, json: JSONObject): String {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true; connectTimeout = 5000; readTimeout = 5000
            setRequestProperty("Content-Type", "application/json")
        }
        OutputStreamWriter(conn.outputStream).use { it.write(json.toString()) }
        return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
    }

    // ─── 数据类 ───

    data class PushRecord(
        val id: String,
        val encryptedBlob: String,
        val syncVersion: Int,
        val deviceId: String,
        val clientUpdatedAt: Long
    )

    data class ServerRecord(
        val recordId: String,
        val encryptedBlob: String,
        val syncVersion: Int,
        val deviceId: String,
        val clientUpdatedAt: Long,
        val serverUpdatedAt: Long
    )
}
