package io.zx.password.ui.component

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import io.zx.password.PasswordEntry
import io.zx.password.crypto.SessionManager
import kotlinx.coroutines.delay

@Composable
fun PasswordDetailDialog(
    item: PasswordEntry,
    onDismiss: () -> Unit,
    onEdit: (PasswordEntry) -> Unit,
    onDelete: (PasswordEntry) -> Unit,
    tags: List<String> = emptyList()
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    val decryptedPassword = remember(item.id, item.encryptedPassword) {
        try { SessionManager.decrypt(item.encryptedPassword) } catch (e: Exception) { "[解密失败]" }
    }
    val decryptedNotes = remember(item.id, item.encryptedNotes) {
        item.encryptedNotes?.let { enc ->
            try { SessionManager.decrypt(enc) } catch (e: Exception) { "[解密失败]" }
        }
    }

    var showPassword by remember { mutableStateOf(false) }
    // 修改点 1：密码隐藏时固定显示 6 个点
    val displayPassword = if (showPassword) decryptedPassword else "●●●●●●"

    fun authenticateThenDelete() {
        val activity = context as FragmentActivity
        val biometricManager = BiometricManager.from(activity)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            onDelete(item)
            onDismiss()
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onDelete(item)
                    onDismiss()
                }
                override fun onAuthenticationFailed() {}
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {}
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .setTitle("验证身份")
            .setSubtitle("删除需要验证身份")
            .setDescription("请使用指纹或人脸验证")
            .build()
        prompt.authenticate(info)
    }

    fun copyToClipboard(text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true
        )
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // 标题
                Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(16.dp))

                // ----- 基本信息 -----
                Text("基本信息", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Divider(modifier = Modifier.padding(vertical = 4.dp))

                DetailField(
                    label = "用户名",
                    value = item.username,
                    onCopy = { copyToClipboard(item.username) }
                )

                DetailField(
                    label = "密码",
                    value = displayPassword,
                    onCopy = { copyToClipboard(decryptedPassword) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                            )
                        }
                    }
                )

                if (!item.url.isNullOrBlank()) {
                    DetailField(
                        label = "网址",
                        value = item.url,
                        onCopy = { copyToClipboard(item.url!!) }
                    )
                }

                if (!decryptedNotes.isNullOrBlank()) {
                    DetailField(
                        label = "备注",
                        value = decryptedNotes,
                        onCopy = { copyToClipboard(decryptedNotes!!) }
                    )
                }

                // ----- 元数据 -----
                Spacer(modifier = Modifier.height(12.dp))
                Text("元数据", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Divider(modifier = Modifier.padding(vertical = 4.dp))

                // 修改点 2：onCopy 传入 null，不显示复制按钮
                DetailField(
                    label = "创建时间",
                    value = formatTimestamp(item.createdAt),
                    onCopy = null
                )
                DetailField(
                    label = "更新时间",
                    value = formatTimestamp(item.updatedAt),
                    onCopy = null
                )

                // ----- 标签 -----
                if (tags.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tags.take(3).forEach { tag ->
                            SuggestionChip(
                                onClick = { /* 可扩展 */ },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                        if (tags.size > 3) {
                            Text(
                                text = "+${tags.size - 3}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 编辑按钮
                OutlinedButton(onClick = { onEdit(item) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("编辑")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 删除按钮
                Button(
                    onClick = { authenticateThenDelete() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除")
                }
            }
        }
    }
}

@Composable
private fun DetailField(
    label: String,
    value: String,
    onCopy: (() -> Unit)?,          // 改为可空，null 时不显示复制按钮
    trailingIcon: @Composable (() -> Unit)? = null
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(text = value, style = MaterialTheme.typography.bodyLarge)
        }

        if (trailingIcon != null) {
            trailingIcon()
        }

        // 只有当 onCopy 不为 null 时才显示复制按钮
        if (onCopy != null) {
            IconButton(onClick = {
                onCopy()
                copied = true
            }) {
                Icon(
                    imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = if (copied) "已复制" else "复制",
                    modifier = Modifier.size(20.dp),
                    tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    return try {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        sdf.format(java.util.Date(timestamp))
    } catch (e: Exception) {
        timestamp.toString()
    }
}