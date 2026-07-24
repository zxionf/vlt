package io.zx.password.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.zx.password.PasswdEntity

@Composable
fun EditPwdDialog(
    item: PasswdEntity,
    onDismiss: () -> Unit,
    onConfirm: (PasswdEntity) -> Unit,
    onDelete: (PasswdEntity) -> Unit
) {
    // 对话框内部状态，用于临时编辑
    var editedTitle by remember { mutableStateOf(item.title) }
    var editedContent by remember { mutableStateOf(item.passwd) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑") },
        text = {
            Column {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedContent,
                    onValueChange = { editedContent = it },
                    label = { Text("内容") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        // 左边：删除按钮（危险操作，用红色）
        dismissButton = {
            TextButton(
                onClick = {
                    onDelete(item)   // ← 你需要传入的删除回调
                    onDismiss()
                },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Text("删除")
            }
        },
        // 右边：取消 + 保存
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
                TextButton(
                    onClick = {
                        val updated = item.copy(
                            title = editedTitle,
                            passwd = editedContent
                        )
                        onConfirm(updated)
                        onDismiss()
                    }
                ) {
                    Text("保存")
                }
            }
        }
    )
}