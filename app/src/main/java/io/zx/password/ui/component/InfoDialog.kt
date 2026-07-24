package io.zx.password.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.zx.password.PasswdEntity

@Composable
fun InfoDialog(
    item: PasswdEntity,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = true
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text("提示")
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "ID: ${item.id}")
                Text(text = "标题: ${item.title}")
                Text(text = "用户名: ${item.username}")
                Text(text = "密码: ${item.passwd}")
                if (!item.notes.isNullOrBlank()) Text(text = "备注: ${item.notes}")
                if (!item.url.isNullOrBlank()) Text(text = "网址: ${item.url}")
            }
        }
    }
}
