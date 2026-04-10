package io.zx.password

import android.os.Bundle
import android.view.WindowManager
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import io.zx.password.ui.layout.MainScreen
import io.zx.password.ui.theme.PasswordTheme


class MainViewModel : ViewModel() {
    var isUnlocked by mutableStateOf(false)
        private set
    fun unlock() { isUnlocked = true }
    fun lock() { isUnlocked = false }
}

class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

//        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)

        enableEdgeToEdge()
        setContent {
            PasswordTheme {
                MainView()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.lock()
    }

    override fun onPause() {
        super.onPause()
        viewModel.lock()
    }

    @Preview(showBackground = true)
    @Composable
    fun MainView(){

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
        }

    }


    fun authenticateWithBiometric(){
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // 设备支持，可以进行认证
                val executor = ContextCompat.getMainExecutor(this)
                val biometricPrompt = BiometricPrompt(
                    this, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(
                            result: BiometricPrompt.AuthenticationResult
                        ) {
                            // 认证成功！在此处处理你的业务逻辑，如登录、支付授权等
                            viewModel.unlock()
                        }

                        override fun onAuthenticationFailed() {
                            // 认证失败，例如指纹/人脸匹配不上
                        }

                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {
                            // 发生不可恢复的错误，如被锁定、硬件不可用等
                        }
                    })
                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .setTitle("生物识别验证")
                    .setSubtitle("请验证您的身份")
                    .setDescription("使用指纹或人脸进行安全验证")
//                    .setNegativeButtonText("取消")
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                // 设备没有生物识别硬件
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                // 用户尚未录入生物特征，引导用户去设置
            }
            else -> { /* 其他错误 */ }
        }
    }

    @Composable
    fun LockContent(
        click : () -> Unit
    ) {
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
                Button(onClick = click , modifier = Modifier.padding(top = 32.dp)) {
                    Text("解锁")
                }
            }
        }
    }
}