package io.zx.password

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.zx.password.crypto.KeystoreHelper
import io.zx.password.crypto.SessionManager
import io.zx.password.ui.layout.MainScreen
import io.zx.password.ui.layout.SetMasterPasswordScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.zx.password.ui.theme.LocalThemeState
import io.zx.password.ui.theme.PasswordTheme
import io.zx.password.ui.theme.ThemePreferences
import io.zx.password.ui.theme.rememberThemeState

class MainViewModel : ViewModel() {
    var isUnlocked by mutableStateOf(false)
        private set
    var autoLockEnabled by mutableStateOf(true)
    var needsSetup by mutableStateOf(true)
    var needsPasswordInput by mutableStateOf(false)

    fun unlock() { isUnlocked = true }
    fun lock() { if (autoLockEnabled) isUnlocked = false }
}

class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val themePreferences = ThemePreferences(this)
        lifecycleScope.launch {
            themePreferences.autoLockEnabledFlow.collect { viewModel.autoLockEnabled = it }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val existing = PwdDB.getInstance(this@MainActivity).KeyPairDao().get()
            viewModel.needsSetup = existing == null
        }
        enableEdgeToEdge()
        setContent {
            val themePreferences2 = remember { ThemePreferences(this) }
            val themeState = rememberThemeState(themePreferences2)
            CompositionLocalProvider(LocalThemeState provides themeState) {
                PasswordTheme {
                    if (viewModel.needsSetup) SetMasterPasswordScreen { viewModel.needsSetup = false }
                    else MainView()
                }
            }
        }
    }

    override fun onResume() { super.onResume(); if (!viewModel.needsSetup) viewModel.lock() }
    override fun onPause() { super.onPause(); if (!viewModel.needsSetup) viewModel.lock() }

    @Composable
    fun MainView() {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            MainScreen()
            if (!viewModel.isUnlocked)
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    LockContent { authenticateWithBiometric() }
                }
            if (viewModel.needsPasswordInput) {
                var input by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                AlertDialog(
                    onDismissRequest = { viewModel.needsPasswordInput = false; viewModel.lock() },
                    title = { Text("输入主密码") },
                    text = {
                        Column {
                            Text("请输入主密码以解锁加密数据")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it; error = null },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                label = { Text("主密码") }
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                if (SessionManager.unlock(this@MainActivity, input)) {
                                    withContext(Dispatchers.Main) {
                                        viewModel.needsPasswordInput = false
                                        viewModel.unlock()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) { error = "密码错误，请重试" }
                                }
                            }
                        }) { Text("确认") }
                    },
                    dismissButton = {
                        TextButton(onClick = { viewModel.needsPasswordInput = false; viewModel.lock() }) { Text("取消") }
                    }
                )
            }
        }
    }

    fun authenticateWithBiometric() {
        val ctx = this
        val bm = BiometricManager.from(this)
        when (bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                            val hasKey = KeystoreHelper.hasBiometricKey(ctx)
                            android.util.Log.d("AUTH", "生物识别成功, hasCachedKmaster=$hasKey")
                            val ok = if (hasKey) SessionManager.unlockWithBiometric(ctx) else false
                            android.util.Log.d("AUTH", "unlockWithBiometric=$ok")
                            if (ok) viewModel.unlock()
                            else { android.util.Log.d("AUTH", "降级到主密码输入"); viewModel.needsPasswordInput = true }
                        }
                        override fun onAuthenticationFailed() {}
                        override fun onAuthenticationError(ec: Int, es: CharSequence) {
                            viewModel.needsPasswordInput = true
                        }
                    })
                prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .setTitle("验证身份")
                    .setSubtitle("请验证您的身份")
                    .setDescription("使用指纹或人脸解锁")
                    .build())
            }
            else -> viewModel.needsPasswordInput = true
        }
    }

    @Composable
    fun LockContent(click: () -> Unit) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("应用已锁定", style = MaterialTheme.typography.headlineMedium)
                Text("需要验证身份才能访问", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = click, modifier = Modifier.padding(top = 32.dp)) { Text("解锁") }
            }
        }
    }
}
