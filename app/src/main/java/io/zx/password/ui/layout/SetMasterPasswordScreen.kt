package io.zx.password.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.zx.password.PwdDB
import io.zx.password.KeyPairEntity
import io.zx.password.DeviceEntity
import io.zx.password.crypto.CryptoManager
import io.zx.password.crypto.KeystoreHelper
import io.zx.password.crypto.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetMasterPasswordScreen(onComplete: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var showHint by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(topBar = { TopAppBar(title = { Text("设置主密码") }, windowInsets = WindowInsets(0,0,0,0)) }) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("欢迎使用 PWD", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("请设置您的主密码，用于加密所有密码数据", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(value = password, onValueChange = { password = it; errorText = null }, label = { Text("主密码") }, placeholder = { Text("推荐 8 位以上，含大小写+数字") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it; errorText = null }, label = { Text("确认主密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())

            if (showHint) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("密码提示") }, placeholder = { Text("帮助您记忆密码（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { showHint = true }) { Text("添加密码提示") }
            }

            errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (password.length < 6) { errorText = "主密码至少需要 6 位"; return@Button }
                    if (password != confirmPassword) { errorText = "两次输入的密码不一致"; return@Button }
                    isProcessing = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val (kMaster, salt) = CryptoManager.deriveKey(password)
                                val rsaKeyPair = CryptoManager.generateRsaKeyPair()
                                val publicKeyStr = CryptoManager.publicKeyToString(rsaKeyPair.public)
                                val privateKeyStr = CryptoManager.privateKeyToString(rsaKeyPair.private)

                                val encPriv = CryptoManager.encrypt(privateKeyStr, kMaster)
                                val magicEnc = CryptoManager.encrypt(CryptoManager.MAGIC_TEXT, kMaster)
                                val dataKey = CryptoManager.generateDataKey()
                                val encDk = CryptoManager.rsaEncrypt(dataKey.encoded, rsaKeyPair.public)
                                val encryptedDataKeyStr = "${CryptoManager.bytesToBase64(encDk.iv)}:${CryptoManager.bytesToBase64(encDk.ciphertext)}"
                                val deviceId = UUID.randomUUID().toString()

                                val db = PwdDB.getInstance(context)
                                db.KeyPairDao().insert(KeyPairEntity(
                                    salt = CryptoManager.bytesToBase64(salt),
                                    magicTextIv = CryptoManager.bytesToBase64(magicEnc.iv),
                                    magicTextCipher = CryptoManager.bytesToBase64(magicEnc.ciphertext),
                                    encryptedPrivateKey = "${CryptoManager.bytesToBase64(encPriv.iv)}:${CryptoManager.bytesToBase64(encPriv.ciphertext)}",
                                    privateKeyIv = CryptoManager.bytesToBase64(encPriv.iv),
                                    passwordHint = hint
                                ))
                                db.DeviceDao().insert(DeviceEntity(
                                    deviceId = deviceId, deviceName = android.os.Build.MODEL,
                                    publicKey = publicKeyStr, encryptedDataKey = encryptedDataKeyStr,
                                    isCurrentDevice = true
                                ))
                                SessionManager.dataKey = dataKey
                                KeystoreHelper.storeKmForBiometric(context, kMaster)
                            }
                            onComplete()
                        } catch (e: Exception) {
                            android.util.Log.e("SetMasterPassword", "初始化失败", e)
                            errorText = "初始化失败: ${e.javaClass.simpleName}: ${e.message ?: "未知错误"}"
                        } finally { isProcessing = false }
                    }
                },
                modifier = Modifier.fillMaxWidth(), enabled = !isProcessing, contentPadding = PaddingValues(16.dp)
            ) {
                if (isProcessing) { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(modifier = Modifier.width(8.dp)) }
                Text(if (isProcessing) "正在生成密钥..." else "确认设置")
            }
        }
    }
}
