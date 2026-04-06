package io.zx.password.ui.layout

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.zx.password.Pwd
import io.zx.password.PwdViewModel
import io.zx.password.PwdViewModelFactory
import io.zx.password.ui.theme.PwdTheme

@SuppressLint("ViewModelConstructorInComposable")
@OptIn(ExperimentalMaterial3Api::class)
@Preview()
@Composable
fun HomeScreen(viewModel: PwdViewModel = viewModel(factory = PwdViewModelFactory(LocalContext.current))) {
//    val factory = remember { PwdViewModelFactory(LocalContext.current) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    when (uiState) {
        is PwdViewModel.UiState.Loading-> {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator()
            }
        }
        is PwdViewModel.UiState.Success -> {
            val items = (uiState as PwdViewModel.UiState.Success).items
            HomeScaffold(viewModel,items)
        }
        is PwdViewModel.UiState.Error -> {
            // 显示错误信息
            Text(text = (uiState as PwdViewModel.UiState.Error).message)
        }
    }

    val items by viewModel.items.collectAsState()
    var i:Int=0
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold(
    viewModel: PwdViewModel,
    items:List<Pwd>
){
    var slectedItem by remember { mutableStateOf<Pwd?>(null) }
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
                PwdItemIconCard(
                    title = "了解 PWD",
                    subtitle = "查阅规则文档和常见问题",
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    onClickLabel = "打开 PWD 文档页面",
                    onClick = { viewModel.addItem(Pwd( description = "test", passwd = "dddddd")) }
                )
            }
//            items(20) {
//                index -> Card(modifier = Modifier.fillMaxSize(), onClick = {}) {
//                Text(text = "text" + index, modifier = Modifier.padding(16.dp))
//                }
//            }
            items(items, key={it.id}) {item ->
                PwdItemCard(title = item.description, onClick = {slectedItem = item})
            }

            item {
                Card(modifier = Modifier.fillMaxSize(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                ){
                    Text(text="dibu",modifier = Modifier.padding(16.dp))
                }
            }
        }

        slectedItem?.let {
                item -> EditPwdDialog(
            item = item,
            onDismiss = {slectedItem = null},
            onConfirm = { updated -> viewModel.updateItem(updated)}
        )
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
private fun PwdItemIconCard(
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

@Composable
private fun PwdItemCard(
    title: String,
    onClick: () -> Unit
){
    Card(modifier = Modifier.fillMaxSize(),onClick = onClick){
        Text(text = title, modifier = Modifier.padding(16.dp))
    }
}

@Composable
fun EditPwdDialog(
    item: Pwd,
    onDismiss: () -> Unit,
    onConfirm: (Pwd) -> Unit
) {
    // 对话框内部状态，用于临时编辑
    var editedTitle by remember { mutableStateOf(item.description) }
    var editedContent by remember { mutableStateOf(item.passwd) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑密码项") },
        text = {
            Column {
                OutlinedTextField(
                    value = editedTitle,
                    onValueChange = { editedTitle = it },
                    label = { Text("标题") },
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
        confirmButton = {
            TextButton(
                onClick = {
                    val updated = item.copy(description = editedTitle, passwd = editedContent)
                    onConfirm(updated)
                    onDismiss()
                }
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}