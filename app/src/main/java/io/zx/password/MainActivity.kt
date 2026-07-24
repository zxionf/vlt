package io.zx.password

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.zx.password.crypto.CryptoManager
import io.zx.password.crypto.CryptoSession
import io.zx.password.ui.layout.MainScreen
import io.zx.password.ui.layout.SetMasterPasswordScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.zx.password.ui.theme.LocalThemeState
import io.zx.password.ui.theme.PasswordTheme
import io.zx.password.ui.theme.ThemePreferences
import io.zx.password.ui.theme.rememberThemeState
import javax.crypto.SecretKey

class MainViewModel : ViewModel() {
    var isUnlocked by mutableStateOf(false)
        private set
    var autoLockEnabled by mutableStateOf(true)
    var needsSetup by mutableStateOf(true)
    var needsPasswordInput by mutableStateOf(false)
    var masterKey: SecretKey? = null

    fun unlock() { isUnlocked = true }
    fun lock() {
        if (autoLockEnabled) isUnlocked = false
    }
}

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val themePreferences = ThemePreferences(this)
        lifecycleScope.launch {
            themePreferences.autoLockEnabledFlow.collect { enabled ->
                viewModel.autoLockEnabled = enabled
            }
        }

        // 检查是否是首次启动
        lifecycleScope.launch(Dispatchers.IO) {
            val existingKey = PwdDB.getInstance(this@MainActivity).KeyPairDao().get()
            viewModel.needsSetup = existingKey == null
        }

        enableEdgeToEdge()
        setContent {
            val themePreferences2 = remember { ThemePreferences(this) }
            val themeState = rememberThemeState(themePreferences2)
            CompositionLocalProvider(LocalThemeState provides themeState) {
                PasswordTheme {
                    if (viewModel.needsSetup) {
                        SetMasterPasswordScreen(onComplete = { viewModel.needsSetup = false })
                    } else {
                        MainView()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!viewModel.needsSetup) viewModel.lock()
    }

    override fun onPause() {
        super.onPause()
        if (!viewModel.needsSetup) viewModel.lock()
    }

    @Preview(showBackground = true)
    @Composable
    fun MainView() {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainScreen()
            if (!viewModel.isUnlocked)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    LockContent { authenticateWithBiometric() }
                }
            // 主密码验证弹窗
            if (viewModel.needsPasswordInput) {
                var input by remember { mutableStateOf("") }
                var error by remember { mutableStateOf<String?>(null) }
                AlertDialog(
                    onDismissRequest = {
                        viewModel.needsPasswordInput = false
                        viewModel.lock()
                    },
                    title = { Text("输入主密码") },
                    text = {
                        Column {
                            Text("请输入主密码以解锁加密数据")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it; error = null },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                label = { Text("主密码") }
                            )
                            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        }
                    },
                    confirmButton = {
                        Button(onClick = {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val ok = CryptoSession.verifyAndLoad(this@MainActivity, input)
                                withContext(Dispatchers.Main) {
                                    if (ok) {
                                        viewModel.needsPasswordInput = false
                                        viewModel.unlock()
                                    } else {
                                        error = "密码错误，请重试"
                                    }
                                }
                            }
                        }) { Text("确认") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.needsPasswordInput = false
                            viewModel.lock()
                        }) { Text("取消") }
                    }
                )
            }
        }
    }

    fun authenticateWithBiometric() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(this)
                val biometricPrompt = BiometricPrompt(
                    this, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            viewModel.needsPasswordInput = true
                        }
                        override fun onAuthenticationFailed() {}
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
                    })
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .setTitle("生物识别验证")
                    .setSubtitle("请验证您的身份")
                    .setDescription("使用指纹或人脸进行安全验证")
                    .build()
                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                viewModel.unlock()
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                viewModel.unlock()
            }
            else -> {}
        }
    }

    @Composable
    fun LockContent(click: () -> Unit) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("应用已锁定", style = MaterialTheme.typography.headlineMedium)
                Text("需要验证身份才能访问", style = MaterialTheme.typography.bodyLarge)
                Button(onClick = click, modifier = Modifier.padding(top = 32.dp)) {
                    Text("解锁")
                }
            }
        }
    }
}
