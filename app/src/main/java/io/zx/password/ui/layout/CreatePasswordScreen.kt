package io.zx.password.ui.layout

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.zx.password.PasswdEntity
import io.zx.password.PwdViewModel
import io.zx.password.PwdViewModelFactory
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePasswordScreen(
    onBack: () -> Unit,
    editItem: PasswdEntity? = null,
    viewModel: PwdViewModel = viewModel(factory = PwdViewModelFactory(LocalContext.current))
) {
    val isEdit = editItem != null
    var title by remember { mutableStateOf(editItem?.title ?: "") }
    var username by remember { mutableStateOf(editItem?.username ?: "") }
    var passwd by remember { mutableStateOf(editItem?.passwd ?: "") }
    var url by remember { mutableStateOf(editItem?.url ?: "") }
    var notes by remember { mutableStateOf(editItem?.notes ?: "") }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun save() {
        if (title.isBlank() || passwd.isBlank()) {
            scope.launch { snackbarHostState.showSnackbar("标题和密码不能为空") }
            return
        }
        if (isEdit) {
            viewModel.updateItem(
                editItem!!.copy(
                    title = title.trim(),
                    username = username.trim(),
                    passwd = passwd,
                    url = url.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null },
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            viewModel.addItem(
                PasswdEntity(
                    title = title.trim(),
                    username = username.trim(),
                    encryptedPasswd = "",
                    iv = "",
                    passwd = passwd,
                    url = url.trim().ifBlank { null },
                    notes = notes.trim().ifBlank { null }
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
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题 *") },
                placeholder = { Text("如：Google、GitHub") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名 / 账号") },
                placeholder = { Text("如：user@gmail.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = passwd,
                onValueChange = { passwd = it },
                label = { Text("密码 *") },
                placeholder = { Text("输入密码") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("网址") },
                placeholder = { Text("如：https://github.com") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("备注") },
                placeholder = { Text("备注信息（可选）") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { save() },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp)
            ) {
                Text("保存记录")
            }
        }
    }
}
