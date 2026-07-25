package io.zx.password.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    var title by remember { mutableStateOf(editItem?.title ?: "") }
    var username by remember { mutableStateOf(editItem?.username ?: "") }
    var passwd by remember {
        mutableStateOf(
            editItem?.let {
                try { SessionManager.decrypt(it.encryptedPassword) } catch (e: Exception) { "" }
            } ?: ""
        )
    }
    var url by remember { mutableStateOf(editItem?.url ?: "") }
    var notes by remember {
        mutableStateOf(
            editItem?.encryptedNotes?.let {
                try { SessionManager.decrypt(it) } catch (e: Exception) { "" }
            } ?: ""
        )
    }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val deviceId by remember {
        mutableStateOf(
            viewModel.items.value.firstOrNull()?.createdDeviceId ?: ""
        )
    }

    var tagList by remember { mutableStateOf<List<String>>(emptyList()) }
    var tagInput by remember { mutableStateOf("") }

    LaunchedEffect(editItem) {
        if (editItem != null) {
            viewModel.tagMap.collect { map ->
                val tags = map[editItem.id]
                if (tags != null) tagList = tags.map { it.name }
                return@collect
            }
        }
    }

    fun save() {
        if (title.isBlank() || passwd.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("标题和密码不能为空") }
            return
        }
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
        onBack()
    }

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
                actions = {
                    IconButton(onClick = { save() }) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题 *") }, placeholder = { Text("如：Google、GitHub") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名 / 账号") }, placeholder = { Text("如：user@gmail.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = passwd, onValueChange = { passwd = it }, label = { Text("密码 *") }, placeholder = { Text("输入密码") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("网址") }, placeholder = { Text("如：https://github.com") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("备注") }, placeholder = { Text("备注信息（可选）") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)

            Spacer(modifier = Modifier.height(4.dp))
            Text("标签", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (tagList.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tagList.forEach { tag ->
                        InputChip(selected = false, onClick = { }, label = { Text(tag) }, trailingIcon = {
                            IconButton(onClick = { tagList = tagList.filter { it != tag } }, modifier = Modifier.size(16.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "删除标签", modifier = Modifier.size(12.dp))
                            }
                        })
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = tagInput, onValueChange = { tagInput = it }, placeholder = { Text("添加标签") }, modifier = Modifier.weight(1f), singleLine = true)
                IconButton(onClick = {
                    val t = tagInput.trim()
                    if (t.isNotBlank() && t !in tagList) { tagList = tagList + t; tagInput = "" }
                }) { Icon(Icons.Default.Add, contentDescription = "添加标签") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) { Text("保存记录") }
        }
    }
}
