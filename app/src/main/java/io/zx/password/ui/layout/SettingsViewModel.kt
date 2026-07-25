package io.zx.password.ui.layout

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.zx.password.PwdDB
import io.zx.password.SyncManager
import io.zx.password.ui.theme.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    // ---------- 依赖 ----------

    @SuppressLint("StaticFieldLeak")
    private val context = application.applicationContext
    private val prefs = ThemePreferences(context)
    private val db = PwdDB.getInstance(context)

    // ---------- 自动锁定 ----------
    val autoLockEnabled: StateFlow<Boolean> = prefs.autoLockEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = runBlocking { prefs.autoLockEnabledFlow.firstOrNull() ?: true }
        )

    fun setAutoLock(enabled: Boolean) {
        viewModelScope.launch {
            prefs.saveAutoLockEnabled(enabled)
        }
    }

    // ---------- 设备信息 ----------
    private val _deviceInfo = MutableStateFlow("加载中...")
    val deviceInfo: StateFlow<String> = _deviceInfo

    init {
        loadDeviceInfo()
    }

    private fun loadDeviceInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val device = db.DeviceDao().getCurrentDevice()
            _deviceInfo.value = device?.let {
                "ID: ${it.deviceId.take(8)}...\n设备: ${it.deviceName}\n公钥: ${it.publicKey.take(24)}..."
            } ?: "未初始化"
        }
    }

    // ---------- 服务器同步 ----------
    private val _serverUrl = MutableStateFlow(SyncManager.baseUrl)
    val serverUrl: StateFlow<String> = _serverUrl

    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus

    private val _syncLoading = MutableStateFlow(false)
    val syncLoading: StateFlow<Boolean> = _syncLoading

    fun updateServerUrl(url: String) {
        _serverUrl.value = url
        SyncManager.baseUrl = url
    }

    fun registerDevice() {
        _syncLoading.value = true
        _syncStatus.value = "注册中..."
        viewModelScope.launch(Dispatchers.IO) {
            val device = db.DeviceDao().getCurrentDevice()
            if (device == null) {
                _syncStatus.value = "设备未初始化"
                _syncLoading.value = false
                return@launch
            }
            val result = SyncManager.registerDevice(
                deviceId = device.deviceId,
                deviceName = device.deviceName,
                publicKey = device.publicKey,
                encryptedDataKey = device.encryptedDataKey ?: ""
            )
            _syncStatus.value = result.fold(
                onSuccess = { "注册成功: $it" },
                onFailure = { "注册失败: ${it.message}" }
            )
            _syncLoading.value = false
        }
    }

    fun syncData() {
        _syncLoading.value = true
        _syncStatus.value = "同步中..."
        viewModelScope.launch(Dispatchers.IO) {
            // 获取本地所有密码记录（阻塞 collect 第一个值）
            val entries = runBlocking {
                var list = emptyList<io.zx.password.PasswordEntry>()
                db.PwdDao().getAll().collect { list = it; return@collect }
                list
            }
            val pushResult = SyncManager.pushRecords(entries)

            val prefs =
                context.getSharedPreferences("sync_prefs", android.content.Context.MODE_PRIVATE)
            val lastSync = prefs.getLong("last_sync", 0L)
            val pullResult = SyncManager.pullRecords(lastSync)

            _syncStatus.value = buildString {
                pushResult.fold(
                    onSuccess = { append("上传成功; ") },
                    onFailure = { append("上传失败: ${it.message}; ") }
                )
                pullResult.fold(
                    onSuccess = { records ->
                        append("下载 ${records.size} 条")
                        if (records.isNotEmpty()) {
                            prefs.edit { putLong("last_sync", System.currentTimeMillis()) }
                        }
                    },
                    onFailure = { append("下载失败: ${it.message}") }
                )
            }
            _syncLoading.value = false
        }
    }
}
