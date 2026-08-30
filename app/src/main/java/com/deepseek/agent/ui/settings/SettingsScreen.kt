package com.deepseek.agent.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.deepseek.agent.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val model by vm.model.collectAsState()
    val allowWrite by vm.allowWrite.collectAsState()
    val allowShell by vm.allowShell.collectAsState()
    val userProfile by vm.userProfile.collectAsState()
    var keyInputs by remember { mutableStateOf(mapOf<String, String>()) }
    var keyFlags by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var expandedKeys by rememberSaveable { mutableStateOf(false) }
    var expandedModels by rememberSaveable { mutableStateOf(false) }
    var profileInput by remember { mutableStateOf(userProfile) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- 各供应商 API Key（折叠） ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedKeys = !expandedKeys },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("API Key", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expandedKeys) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(if (expandedKeys) 180f else 0f)
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = expandedKeys,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Spacer(Modifier.height(4.dp))
            listOf(
                Triple("DEEPSEEK", "DeepSeek API Key", "在 platform.deepseek.com 注册充值后，在「API Keys」页面创建密钥。"),
                Triple("KIMI", "Kimi API Key", "在 platform.kimi.com 注册充值后，在「API Keys」页面创建密钥。"),
                Triple("GLM", "GLM API Key", "在 open.bigmodel.cn 注册充值后，在「API 密钥」页面创建密钥。"),
                Triple("QWEN", "千问 API Key", "在 bailian.console.aliyun.com 开通百炼后，在「API-KEY 管理」页面创建密钥。")
            ).forEach { (provider, title, guide) ->
                val input = keyInputs[provider] ?: ""
                val hasKey = vm.hasApiKey(provider)
                val saved = keyFlags[provider] ?: false
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = input,
                            onValueChange = { v -> keyInputs = keyInputs + (provider to v) },
                            label = { Text("sk-…") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = {
                                    if (input.isNotBlank()) {
                                        vm.saveApiKey(provider, input)
                                        keyInputs = keyInputs + (provider to "")
                                        keyFlags = keyFlags + (provider to true)
                                    }
                                },
                                enabled = input.isNotBlank()
                            ) { Text("保存") }
                            if (hasKey) {
                                TextButton(onClick = {
                                    vm.clearApiKey(provider)
                                    keyFlags = keyFlags + (provider to false)
                                }) {
                                    Text("清除已存 Key")
                                }
                            }
                            if (saved) {
                                Text("已保存", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            } else if (hasKey) {
                                Text("已存有 Key", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Text(
                            guide + "Key 只保存在你手机的加密存储中，不会上传到任何服务器。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                        }
                    }
                }
            }
            }

            // ---- 默认模型（按供应商分组，折叠） ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedModels = !expandedModels },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("默认模型", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (expandedModels) "收起" else "展开",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(if (expandedModels) 180f else 0f)
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = expandedModels,
                        enter = androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Spacer(Modifier.height(4.dp))
                    com.deepseek.agent.data.remote.Provider.entries.forEach { provider ->
                        val defs = com.deepseek.agent.data.remote.ModelCatalog.all.filter { it.provider == provider }
                        if (defs.isEmpty()) return@forEach
                        Text(
                            provider.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        defs.forEach { def ->
                            ModelChoice(def.id, def.label, model, vm::setModel)
                        }
                    }
                        }
                    }
                }
            }

            // ---- 关于你 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("关于你（让 AI 更懂你）", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = profileInput,
                        onValueChange = { profileInput = it },
                        placeholder = { Text("例如：我叫小王，做电商运营，喜欢直接给结论，别太啰嗦") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(
                        onClick = { vm.setUserProfile(profileInput) },
                        enabled = profileInput != userProfile
                    ) { Text("保存画像") }
                    Text(
                        "保存后会告诉 AI，让它按你的习惯交流。内容只存在手机本地。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 危险操作开关 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Agent 工具开关", style = MaterialTheme.typography.titleMedium)
                    SwitchRow(
                        title = "允许写入文件",
                        subtitle = "关闭时 AI 只能读文件和列目录",
                        checked = allowWrite,
                        onCheckedChange = vm::setAllowWrite
                    )
                    SwitchRow(
                        title = "允许执行 Shell 命令",
                        subtitle = "关闭时 AI 无法运行任何命令",
                        checked = allowShell,
                        onCheckedChange = vm::setAllowShell
                    )
                    Text(
                        "开启后，AI 每次写文件或执行命令前仍会弹出确认框，由你逐次把关。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 文件访问权限 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("AI 文件访问权限", style = MaterialTheme.typography.titleMedium)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var hasFullAccess by remember {
                        mutableStateOf(checkFullAccess(context))
                    }
                    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                        hasFullAccess = checkFullAccess(context)
                        onPauseOrDispose { }
                    }
                    if (hasFullAccess) {
                        Text(
                            "已开启 ✓ AI 可以读写整个手机存储",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        Text(
                            "未开启：AI 目前只能操作 App 内置的小沙盒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = {
                                context.startActivity(
                                    android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                )
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        ) { Text("去系统设置开启") }
                    }
                    Text(
                        "开启后 AI 就能帮你管理手机里的任何文件夹和文件（下载、文档、图片等），" +
                            "写文件和执行命令仍会弹窗让你逐次确认。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 崩溃日志 ----
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("崩溃日志", style = MaterialTheme.typography.titleMedium)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var crashInfo by remember { mutableStateOf(readCrashInfo(context)) }
                    Text(
                        crashInfo,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("crash", crashInfo)
                        )
                        crashInfo = readCrashInfo(context)
                    }) { Text("复制日志") }
                    Text(
                        "如果 App 闪退，把这里的日志复制发给开发者，可以精确定位问题。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- 关于 ----
            Text(
                "本应用为独立开发的第三方客户端，直连 DeepSeek、Kimi、GLM、千问各家开放平台的官方 API，与任何一家均无隶属关系。" +
                    "使用产生的费用由你对应平台的账户承担。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "版本 v${com.deepseek.agent.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val context = androidx.compose.ui.platform.LocalContext.current
            Text(
                "开发者：xiuchenshen.com",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://xiuchenshen.com")
                        )
                    )
                }
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun readCrashInfo(context: android.content.Context): String {
    val file = java.io.File(context.filesDir, "crash.log")
    return if (file.exists()) file.readText().takeLast(4000) else "（暂无崩溃记录）"
}

@Composable
private fun ModelChoice(label: String, subtitle: String, current: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(label) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = current == label, onClick = { onSelect(label) })
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun checkFullAccess(context: android.content.Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        android.os.Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
