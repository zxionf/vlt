package io.zx.password

import android.R.color.black
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.zx.password.ui.layout.PwdItemCard

class PwdItemCardTest {
    @Preview(
        showBackground = true,
        backgroundColor = black.toLong(),
        heightDp = 90
    )
    @Composable
    fun PwdItemCardPreview(){
        PwdItemCard(title = "标题", onCopyClick = {}, onEditClick = {}, onInfoClick = {})
    }
}