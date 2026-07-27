package io.zx.password.ui.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.zx.password.PasswordEntry
import io.zx.password.PwdViewModel
import io.zx.password.PwdViewModelFactory
import io.zx.password.crypto.SessionManager
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePasswordScreen(
    onBack: () -> Unit,
    editItem: PasswordEntry? = null,
    viewModel: PwdViewModel = viewModel(factory = PwdViewModelFactory(LocalContext.current))
) {
    val isEdit = editItem != null
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // ----- 表单状态 -----
    var title by remember { mutableStateOf(editItem?.title ?: "") }
    var username by remember { mutableStateOf(editItem?.username ?: "") }
    var passwd by remember {
        mutableStateOf(
            editItem?.let {
                try {
                    SessionManager.decrypt(it.encryptedPassword)
                } catch (e: Exception) {
                    ""
                }
            } ?: ""
        )
    }
    var url by remember { mutableStateOf(editItem?.url ?: "") }
    var notes by remember {
        mutableStateOf(
            editItem?.encryptedNotes?.let {
                try {
                    SessionManager.decrypt(it)
                } catch (e: Exception) {
                    ""
                }
            } ?: ""
        )
    }
    var tagList by remember { mutableStateOf<List<String>>(emptyList()) }
    var showPassword by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var showTagMenu by remember { mutableStateOf(false) }

    // ----- 实时错误状态 -----
    var titleError by remember { mutableStateOf<String?>(null) }
    var passwdError by remember { mutableStateOf<String?>(null) }

    // ----- 设备 ID -----
    val deviceId by viewModel.currentDeviceId.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    // ----- 编辑时加载标签 -----
    LaunchedEffect(editItem) {
        if (editItem != null) {
            viewModel.tagMap.collect { map ->
                map[editItem.id]?.let { tags ->
                    tagList = tags.map { it.name }
                }
            }
        }
    }

    // ----- 密码强度（zxcvbn4j 评估）-----
    val zxcvbn = remember { com.nulabinc.zxcvbn.Zxcvbn() }
    val passwordStrength = remember(passwd) {
        if (passwd.isBlank()) 0 else zxcvbn.measure(passwd).score
    }

    // ----- 辅助函数 -----
    fun validate(): Boolean {
        var valid = true
        if (title.isBlank()) {
            titleError = "标题不能为空"
            valid = false
        } else {
            titleError = null
        }
        return valid
    }

    fun save() {
        if (isSaving) return
        if (title.isBlank()) {
            titleError = "标题不能为空"
            return
        }
        
        isSaving = true
        scope.launch {
            try {
                val encryptedPassword = SessionManager.encrypt(passwd)
                val encryptedNotes = if (notes.isNotBlank()) SessionManager.encrypt(notes) else null

                if (isEdit) {
                    viewModel.updateItem(
                        editItem!!.copy(
                            title = title.trim(),
                            username = username.trim(),
                            encryptedPassword = encryptedPassword,
                            encryptedNotes = encryptedNotes,
                            url = url.trim().ifBlank { null },
                            updatedAt = System.currentTimeMillis(),
                            lastModifiedDeviceId = deviceId
                        )
                    )
                    viewModel.setTagsForPassword(editItem.id, tagList)
                } else {
                    viewModel.addItem(
                        PasswordEntry(
                            id = UUID.randomUUID().toString(),
                            title = title.trim(),
                            username = username.trim(),
                            encryptedPassword = encryptedPassword,
                            encryptedNotes = encryptedNotes,
                            url = url.trim().ifBlank { null },
                            createdDeviceId = deviceId,
                            lastModifiedDeviceId = deviceId
                        )
                    )
                }
                android.widget.Toast.makeText(context, if (isEdit) "已更新" else "已保存", android.widget.Toast.LENGTH_SHORT).show()
                onBack()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("保存失败: ${e.message}")
            } finally {
                isSaving = false
            }
        }
    }

    // ----- UI 布局 -----
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "编辑记录" else "新建记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { save() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Check, contentDescription = "保存")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ----- 卡片 1：凭证信息 -----
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "凭证信息",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it; titleError = null },
                        label = { Text("标题 *") },
                        placeholder = { Text("如：Google、GitHub") },
                        leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                        isError = titleError != null,
                        supportingText = {
                            if (titleError != null) Text(titleError!!)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("用户名 / 账号") },
                        placeholder = { Text("如：user@gmail.com") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = passwd,
                        onValueChange = { passwd = it; passwdError = null },
                        label = { Text("密码 *") },
                        placeholder = { Text("至少 6 位") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "隐藏密码" else "显示密码"
                                )
                            }
                        },
                        isError = passwdError != null,
                        supportingText = {
                            if (passwdError != null) {
                                Text(passwdError!!)
                            } else if (passwd.isNotBlank()) {
                                val strengthText = when (passwordStrength) {
                                    0, 1 -> "弱"
                                    2 -> "中"
                                    else -> "强"
                                }
                                val color = when (passwordStrength) {
                                    0, 1 -> MaterialTheme.colorScheme.error
                                    2 -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.primary
                                }
                                Text("强度: $strengthText", color = color)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation()
                    )
                }
            }

            // ----- 卡片 2：附加信息 -----
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "附加信息",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("网址") },
                        placeholder = { Text("如：https://github.com") },
                        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("备注") },
                        placeholder = { Text("备注信息（可选）") },
                        leadingIcon = { Icon(Icons.Default.Note, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 5
                    )
                }
            }

            // ----- 卡片 3：标签 -----
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "标签",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    // 已选标签（自动换行）
                    if (tagList.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            tagList.forEach { tag ->
                                InputChip(
                                    selected = false,
                                    onClick = { /* 可扩展：点击筛选 */ },
                                    label = { Text(tag) },
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { tagList = tagList.filter { it != tag } },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "删除标签",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                    // 选择已有标签
                    OutlinedButton(onClick = { showTagMenu = true }) {
                        Text("选择标签")
                    }
                }
            }
            // 底部占位，避免被 FAB 遮挡
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    // 选择标签弹窗
    if (showTagMenu) {
        AlertDialog(
            onDismissRequest = { showTagMenu = false },
            title = { Text("选择标签") },
            text = {
                val availableTags = allTags.filter { it.name !in tagList }
                if (availableTags.isEmpty()) {
                    Text("没有更多标签")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        availableTags.forEach { tag ->
                            TextButton(
                                onClick = {
                                    tagList = tagList + tag.name
                                    showTagMenu = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(tag.name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}