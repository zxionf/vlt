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
import io.zx.password.crypto.CryptoManager
import io.zx.password.crypto.CryptoSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetMasterPasswordScreen(
    onComplete: () -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordHint by remember { mutableStateOf("") }
    var showHintField by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置主密码") },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "欢迎使用 PWD",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "请设置您的主密码，用于加密所有密码数据",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; errorText = null },
                label = { Text("主密码") },
                placeholder = { Text("推荐 8 位以上，含大小写+数字") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; errorText = null },
                label = { Text("确认主密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            if (showHintField) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = passwordHint,
                    onValueChange = { passwordHint = it },
                    label = { Text("密码提示") },
                    placeholder = { Text("帮助您记忆密码（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = { showHintField = true }) {
                    Text("添加密码提示")
                }
            }

            if (errorText != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    if (password.length < 6) {
                        errorText = "主密码至少需要 6 位"
                        return@Button
                    }
                    if (password != confirmPassword) {
                        errorText = "两次输入的密码不一致"
                        return@Button
                    }
                    isProcessing = true
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                // 派生 AES 密钥
                                val (aesKey, salt) = CryptoManager.deriveKey(password)
                                CryptoSession.key = aesKey
                                // 生成 RSA 密钥对
                                val rsaKeyPair = CryptoManager.generateRsaKeyPair()
                                val publicKeyStr = CryptoManager.publicKeyToString(rsaKeyPair.public)
                                val privateKeyStr = CryptoManager.privateKeyToString(rsaKeyPair.private)
                                // 加密私钥
                                val encPriv = CryptoManager.encrypt(privateKeyStr, aesKey)
                                // 加密 magic text 用于验证
                                val magicEnc = CryptoManager.encrypt(CryptoManager.MAGIC_TEXT, aesKey)
                                // 保存到数据库
                                val db = PwdDB.getInstance(context)
                                db.KeyPairDao().insert(KeyPairEntity(
                                    publicKey = publicKeyStr,
                                    salt = CryptoManager.bytesToBase64(salt),
                                    magicTextIv = CryptoManager.bytesToBase64(magicEnc.iv),
                                    magicTextCipher = CryptoManager.bytesToBase64(magicEnc.ciphertext),
                                    encryptedPrivateKey = CryptoManager.bytesToBase64(encPriv.ciphertext),
                                    privateKeyIv = CryptoManager.bytesToBase64(encPriv.iv),
                                    passwordHint = passwordHint.ifBlank { "未设置" }
                                ))
                            }
                            onComplete()
                        } catch (e: Exception) {
                            errorText = "初始化失败: ${e.message}"
                        } finally {
                            isProcessing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isProcessing,
                contentPadding = PaddingValues(16.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isProcessing) "正在生成密钥..." else "确认设置")
            }
        }
    }
}
