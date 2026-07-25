package io.zx.password

import android.R.color.black
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.zx.password.ui.layout.PwdItemCard
import io.zx.password.ui.theme.PasswordTheme

class PwdItemCardTest {
    @Preview(
        showBackground = true,
        backgroundColor = black.toLong(),
        heightDp = 110
    )
    @Composable
    fun PwdItemCardPreview(){
        PasswordTheme {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                item {
                    PwdItemCard(
                        title = "标题",
                        subtitle = "sd",
                        onClick = {})
                }
            }
        }
    }
}