package io.zx.password

import android.R.color.black
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.zx.password.ui.layout.EditPwdDialog

class EditPwdDialogTest {
    @Preview(showBackground = true, backgroundColor = black.toLong())
    @Composable
    fun EditPwdDialogPreview(){
        val mockItem = Pwd(
            id = 1,
            description = "B站",
            passwd = "xswl"
        )

        EditPwdDialog(
            item = mockItem,
            onDismiss = {},
            onConfirm = {},
            onDelete = {}
        )
    }
}