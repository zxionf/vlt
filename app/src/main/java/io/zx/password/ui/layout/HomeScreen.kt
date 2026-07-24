package io.zx.password.ui.layout

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import io.zx.password.PasswdEntity
import io.zx.password.PwdViewModel
import io.zx.password.PwdViewModelFactory
import io.zx.password.ui.component.CommonDialog
import io.zx.password.ui.component.PasswordDetailDialog
import io.zx.password.ui.theme.PwdTheme

@SuppressLint("ViewModelConstructorInComposable")
@OptIn(ExperimentalMaterial3Api::class)
@Preview()
@Composable
fun HomeScreen(
    onAddClick: () -> Unit = {},
    onEditItem: (PasswdEntity) -> Unit = {},
    viewModel: PwdViewModel = viewModel(factory = PwdViewModelFactory(LocalContext.current))
) {
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
            HomeScaffold(viewModel, items, onAddClick, onEditItem)
        }
        is PwdViewModel.UiState.Error -> {
            // 显示错误信息
            Text(text = (uiState as PwdViewModel.UiState.Error).message)
        }
    }

    val items by viewModel.items.collectAsState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScaffold(
    viewModel: PwdViewModel,
    items: List<PasswdEntity>,
    onAddClick: () -> Unit,
    onEditItem: (PasswdEntity) -> Unit
) {
    var detailItem by remember { mutableStateOf<PasswdEntity?>(null) }
    var showHelp by remember { mutableStateOf<Boolean>(false) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "PWD", style = MaterialTheme.typography.headlineSmall) },
                actions = {
                    IconButton(onClick = {  }) {
                        Icon( imageVector = Icons.Default.DownloadForOffline, "update", modifier = Modifier.size(30.dp))
                    }
                    IconButton(onClick = onAddClick) {
                        Icon( imageVector = Icons.Default.Add, "add", modifier = Modifier.size(36.dp))
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0))
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            item{
                PwdItemIconCard(
                    title = "了解 PWD",
                    subtitle = "查看常见问题",
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    onClickLabel = "打开 PWD 文档页面",
                    onClick = { showHelp = true }
                )
            }
            items(items, key={it.id}) {item ->
                PwdItemCard(
                    title = item.title,
                    subtitle = item.username,
                    onClick = { detailItem = item }
                )
            }
        }

        val tagMap by viewModel.tagMap.collectAsState()
        detailItem?.let { item ->
            PasswordDetailDialog(
                item = item,
                onDismiss = { detailItem = null },
                onEdit = { editItem ->
                    detailItem = null
                    onEditItem(editItem)
                },
                onDelete = { deleteItem ->
                    viewModel.deleteItem(deleteItem)
                },
                tags = tagMap[item.id]?.map { it.name } ?: emptyList()
            )
        }

        if (showHelp) CommonDialog("提示", "什么都没有", { showHelp = false })

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
fun PwdItemCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(
            containerColor = PwdTheme.colors.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}