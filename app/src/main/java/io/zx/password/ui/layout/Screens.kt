package io.zx.password.ui.layout

import android.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.zx.password.ui.theme.PasswordTheme
import io.zx.password.ui.theme.PwdTheme

@OptIn(ExperimentalMaterial3Api::class)
@Preview()
@Composable
fun HomeScreen() {
//    Box(
//        modifier = Modifier.fillMaxSize(),
//        contentAlignment = Alignment.Center
//    ) {
//
//        Column(
//            horizontalAlignment = Alignment.CenterHorizontally,
//            verticalArrangement = Arrangement.Center
//        ) {
//            Text(
//                text = "首页",
//                style = MaterialTheme.typography.headlineMedium
//            )
//            Spacer(modifier = Modifier.height(16.dp))
//            Text(
//                text = "欢迎来到首页",
//                style = MaterialTheme.typography.bodyLarge
//            )
//        }
//    }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()


    // 使用 CenterAlignedTopAppBar（不会被弃用）
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = "首页",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        windowInsets = WindowInsets(0, 0, 0, 0),  // 自定义 WindowInsets
        scrollBehavior = scrollBehavior
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text(text = "PWD", style = MaterialTheme.typography.headlineSmall) },
//            colors = TopAppBarDefaults.topAppBarColors(
//                containerColor = MaterialTheme.colorScheme.primary,        // 背景色
//                titleContentColor = MaterialTheme.colorScheme.onPrimary,   // 标题颜色
//                navigationIconContentColor = MaterialTheme.colorScheme.onPrimary, // 导航图标颜色
//                actionIconContentColor = MaterialTheme.colorScheme.onPrimary     // 操作图标颜色
//            ),
            windowInsets = WindowInsets(0, 0, 0, 0))
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item{
                PwdItemCard(
                    title = "了解 PWD",
                    subtitle = "查阅规则文档和常见问题",
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    onClickLabel = "打开 PWD 文档页面",
                    onClick = { print(666) }
                    )
            }
            items(20) {
                index -> Card(modifier = Modifier.fillMaxSize(), onClick = {}){
                    Text(text = "text"+index, modifier = Modifier.padding(16.dp))
            }
            }
            item {
                Card(modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ){
                    Text(text="dibu",modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}


@Composable
fun SearchScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "搜索页面",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
fun SettingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "设置页面",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}

@Composable
private fun PwdItemCard(
    imageVector: ImageVector,
    title: String,
    subtitle: String,
    onClickLabel: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.onClick(label = onClickLabel, action = null)
            },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = PwdTheme.colors.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer   // 对应的内容色
        ),
        onClick = onClick
    ) {
        IconTextCard(
            imageVector = imageVector,
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun IconTextCard(
    imageVector: ImageVector, content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = imageVector,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(8.dp)
                .size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(16.dp))
        content()
    }
}