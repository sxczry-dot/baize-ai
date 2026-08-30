package com.deepseek.agent.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.deepseek.agent.data.local.entity.SessionEntity
import com.deepseek.agent.domain.model.ChatMessage
import com.deepseek.agent.ui.components.ToolCallCard
import com.deepseek.agent.viewmodel.ChatViewModel
import com.deepseek.agent.viewmodel.SessionsViewModel
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    sessionsVm: SessionsViewModel,
    onOpenSettings: () -> Unit
) {
    val messages by vm.messages.collectAsState()
    val isStreaming by vm.isStreaming.collectAsState()
    val pending by vm.pendingConfirmation.collectAsState()
    val sessions by sessionsVm.sessions.collectAsState()
    val currentSessionId by vm.currentSessionId.collectAsState()
    var input by remember { mutableStateOf("") }
    var pendingImage by remember { mutableStateOf<String?>(null) }
    var pendingFile by remember { mutableStateOf<com.deepseek.agent.util.PendingFile?>(null) }
    var menuTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var menuText by remember { mutableStateOf("") }
    var menuCanRegenerate by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val path = com.deepseek.agent.util.ImageUtil.compressAndSave(context, uri)
            if (path != null) pendingImage = path
            else scope.launch { snackbarHostState.showSnackbar("图片处理失败，换一张试试") }
        }
    }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val path = com.deepseek.agent.util.ImageUtil.copyToCache(context, uri) ?: run {
                scope.launch { snackbarHostState.showSnackbar("读取文件失败") }
                return@rememberLauncherForActivityResult
            }
            when (com.deepseek.agent.util.FileUtil.kindOf(path)) {
                com.deepseek.agent.util.FileKind.TEXT -> {
                    val text = com.deepseek.agent.util.FileUtil.readText(path)
                    if (text == null) {
                        scope.launch { snackbarHostState.showSnackbar("文本文件太大（限 150KB），请先拆分") }
                    } else {
                        pendingFile = com.deepseek.agent.util.PendingFile(
                            name = com.deepseek.agent.util.FileUtil.fileNameOf(path),
                            textContent = text
                        )
                    }
                }
                com.deepseek.agent.util.FileKind.PDF -> {
                    val img = com.deepseek.agent.util.FileUtil.pdfToImage(context, path)
                    if (img == null) {
                        scope.launch { snackbarHostState.showSnackbar("PDF 解析失败") }
                    } else {
                        pendingFile = com.deepseek.agent.util.PendingFile(
                            name = com.deepseek.agent.util.FileUtil.fileNameOf(path),
                            imagePath = img
                        )
                    }
                }
                else -> scope.launch {
                    snackbarHostState.showSnackbar("暂不支持该类型，请转成 PDF 或文本后重试")
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        vm.toast.collect { snackbarHostState.showSnackbar(it) }
    }

    if (showModelPicker) {
        val model by vm.currentModel.collectAsState()
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("切换模型") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    com.deepseek.agent.data.remote.Provider.entries.forEach { provider ->
                        val defs = com.deepseek.agent.data.remote.ModelCatalog.all.filter { it.provider == provider }
                        if (defs.isEmpty()) return@forEach
                        Text(
                            provider.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                        defs.forEach { def ->
                            val sel = model == def.id
                            ListItem(
                                headlineContent = { Text(def.id) },
                                supportingContent = { Text(def.label) },
                                colors = ListItemDefaults.colors(
                                    containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        vm.setModel(def.id)
                                        showModelPicker = false
                                    }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelPicker = false }) { Text("关闭") }
            }
        )
    }

    showFullImage?.let { path ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullImage = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            ZoomableImage(path, onClose = { showFullImage = null })
        }
    }

    menuTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text("消息操作") },
            text = {
                Column {
                    Text(menuText.take(80), maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(target.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                        cm.setPrimaryClip(
                            android.content.ClipData.newPlainText("DeepSeekAgent", menuText)
                        )
                        menuTarget = null
                        scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                    }
                ) { Text("复制") }
            },
            dismissButton = {
                Row {
                    if (menuCanRegenerate) {
                        TextButton(
                            onClick = {
                                menuTarget = null
                                vm.regenerate()
                            }
                        ) { Text("重新生成") }
                    }
                    TextButton(onClick = { menuTarget = null }) { Text("关闭") }
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            SessionDrawer(
                sessions = sessions,
                currentId = currentSessionId,
                onNewSession = {
                    vm.startNewSession()
                    scope.launch { drawerState.close() }
                },
                onSelect = { id ->
                    vm.startSession(id)
                    scope.launch { drawerState.close() }
                },
                onDelete = { id ->
                    sessionsVm.deleteSession(id)
                    if (id == currentSessionId) vm.startNewSession()
                },
                onRename = { id, title ->
                    sessionsVm.renameSession(id, title)
                }
            )
        }
    ) {
        Scaffold(
            topBar = {
                val model by vm.currentModel.collectAsState()
                TopAppBar(
                    title = {
                        Column {
                            Text("白泽", style = MaterialTheme.typography.titleMedium)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showModelPicker = true }
                            ) {
                                Text(
                                    model,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    Icons.Filled.KeyboardArrowDown,
                                    contentDescription = "切换模型",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "会话列表")
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.startNewSession() }) {
                            Icon(Icons.Filled.Add, contentDescription = "新建会话")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    // 键盘弹出时整体垫高，输入条永远在键盘上方
                    .imePadding()
            ) {
                if (messages.none { it.role == "user" || it.role == "assistant" }) {
                    // 欢迎页（上下左右全居中）
                    WelcomeHint(modifier = Modifier.weight(1f).fillMaxSize().padding(24.dp))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages, key = { it.id }) { msg ->
                            // system 是给 AI 的内部设定；tool 结果已显示在工具卡片里
                            if (msg.role == "system" || msg.role == "tool") return@items
                            val isLastAssistant = msg.role == "assistant" && msg == messages.lastOrNull { it.role != "system" }
                            MessageBubble(
                                msg,
                                useMarkdown = !(isLastAssistant && isStreaming),
                                isStreamingLast = isLastAssistant && isStreaming,
                                modifier = Modifier.animateItem(),
                                onLongPress = { text, canRegenerate ->
                                    menuTarget = msg
                                    menuCanRegenerate = canRegenerate
                                    menuText = text
                                }
                            )
                        }
                    }
                }
                // 待发送文件预览
                pendingFile?.let { pf ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (pf.imagePath != null) {
                            MessageImage(pf.imagePath, Modifier.width(88.dp).heightIn(max = 88.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Description,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Text(
                            if (pf.imagePath != null) "已选 PDF（前 3 页转图，发送后视觉模型查看）" else "已选文件「${pf.name}」（发送后 AI 读取内容分析）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = { pendingFile = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "移除文件")
                        }
                    }
                }
                // 待发送图片预览
                pendingImage?.let { path ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MessageImage(path, Modifier.width(88.dp).heightIn(max = 88.dp))
                        Text(
                            "已选图片（发送后 AI 用视觉模型看图）",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        IconButton(onClick = { pendingImage = null }) {
                            Icon(Icons.Filled.Close, contentDescription = "移除图片")
                        }
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                pickImage.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isStreaming
                        ) {
                            Icon(
                                Icons.Filled.AddPhotoAlternate,
                                contentDescription = "选择图片",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                pickFile.launch(arrayOf("*/*"))
                            },
                            enabled = !isStreaming
                        ) {
                            Icon(
                                Icons.Filled.AttachFile,
                                contentDescription = "上传文件",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text("问点什么，或让它干活…") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        if (isStreaming) {
                            IconButton(onClick = { vm.stop() }) {
                                Icon(
                                    Icons.Filled.StopCircle,
                                    contentDescription = "停止",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val pf = pendingFile
                                    if (pf != null && pf.textContent != null) {
                                        val prompt = buildString {
                                            append(input.ifBlank { "请分析这个文件" })
                                            appendLine()
                                            appendLine()
                                            append("【文件：${pf.name}】")
                                            appendLine()
                                            append("---")
                                            appendLine()
                                            append(pf.textContent)
                                            appendLine()
                                            append("---")
                                        }
                                        vm.send(prompt, pendingImage)
                                    } else {
                                        vm.send(input, pendingImage ?: pf?.imagePath)
                                    }
                                    input = ""
                                    pendingImage = null
                                    pendingFile = null
                                },
                                enabled = input.isNotBlank() || pendingImage != null || pendingFile != null,
                                modifier = Modifier
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = if (input.isNotBlank() || pendingImage != null || pendingFile != null) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 危险操作确认弹窗
    pending?.let { req ->
        AlertDialog(
            onDismissRequest = { vm.resolveConfirmation(false) },
            title = { Text("确认执行「${req.toolName}」？") },
            text = {
                Column {
                    Text("以下操作需要你的许可：")
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text(
                            text = req.argsPreview,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.resolveConfirmation(true) }) { Text("允许") }
            },
            dismissButton = {
                TextButton(onClick = { vm.resolveConfirmation(false) }) { Text("拒绝") }
            }
        )
    }

    // 自动滚到底（用户正在翻看历史时不打扰，只在贴近底部时跟随）
    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) return@LaunchedEffect
        val visibleLast = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val atBottom = visibleLast >= messages.lastIndex - 2
        if (atBottom) listState.scrollToItem(messages.lastIndex)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionDrawer(
    sessions: List<SessionEntity>,
    currentId: String?,
    onNewSession: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit
) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
    var renameTarget by remember { mutableStateOf<SessionEntity?>(null) }
    var renameInput by remember { mutableStateOf("") }
    ModalDrawerSheet {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "会话",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp)
            )
            TextButton(
                onClick = onNewSession,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("新建会话")
            }
            val now = System.currentTimeMillis()
            val todayStart = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
            val yesterdayStart = todayStart - 86_400_000L
            fun groupOf(ts: Long): String = when {
                ts >= todayStart -> "今天"
                ts >= yesterdayStart -> "昨天"
                else -> "更早"
            }
            val grouped = sessions.groupBy { groupOf(it.updatedAt) }
            LazyColumn(modifier = Modifier.weight(1f)) {
                listOf("今天", "昨天", "更早").forEach { label ->
                    val list = grouped[label] ?: return@forEach
                    item(key = "h_$label") {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                        )
                    }
                    items(list, key = { "s_${it.id}" }) { s ->
                    val selected = s.id == currentId
                    ListItem(
                        headlineContent = { Text(s.title, maxLines = 1) },
                        supportingContent = { Text(fmt.format(Date(s.updatedAt))) },
                        trailingContent = {
                            IconButton(onClick = { onDelete(s.id) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "删除会话",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.combinedClickable(
                            onClick = { onSelect(s.id) },
                            onLongClick = {
                                renameTarget = s
                                renameInput = s.title
                            }
                        )
                    )
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("重命名会话") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("会话名称") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renameInput.trim()
                        if (name.isNotBlank()) {
                            onRename(target.id, name)
                        }
                        renameTarget = null
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun WelcomeHint(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 两行结构逐位相同（两字+逗号+四字），宽度数学上必然相等
        Text(
            text = androidx.compose.ui.text.buildAnnotatedString {
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                ) { append("你好，我是白泽") }
                append("\n")
                withStyle(
                    androidx.compose.ui.text.SpanStyle(
                        fontSize = MaterialTheme.typography.titleMedium.fontSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) { append("请问，需要什么") }
            },
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    msg: ChatMessage,
    useMarkdown: Boolean,
    isStreamingLast: Boolean = false,
    modifier: Modifier = Modifier,
    onLongPress: (String, Boolean) -> Unit = { _, _ -> }
) {
    val isUser = msg.role == "user"
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 20.dp
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AvatarBadge(isUser = false, active = isStreamingLast)
            Spacer(Modifier.width(8.dp))
        }
        val longCopy = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val text = if (msg.content.isNotBlank()) msg.content else msg.reasoning
                if (text.isNotBlank()) {
                    onLongPress(text, !isUser)
                }
            }
        )
        if (isUser) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(bubbleShape)
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .then(longCopy)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    msg.imagePath?.let { path ->
                        MessageImage(path, Modifier.fillMaxWidth().heightIn(max = 320.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (msg.content.isNotBlank()) {
                        Text(
                            text = msg.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.createdAt)),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
                        modifier = Modifier.align(Alignment.End).padding(top = 3.dp)
                    )
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = bubbleShape,
                shadowElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .then(longCopy)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    msg.imagePath?.let { path ->
                        MessageImage(path, Modifier.fillMaxWidth().heightIn(max = 320.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                    if (msg.reasoning.isNotBlank()) {
                        ThinkingCard(msg.reasoning)
                    }
                    when {
                        msg.content.isBlank() && msg.toolCalls.isNullOrEmpty() ->
                            ThreeDots()
                        // 列表结构用纯文本：Markdown 库渲染列表有重叠 bug；
                        // 超长内容也用纯文本：Markdown 解析长文是低配机闪退高发区
                        msg.content.isNotBlank() && useMarkdown && markdownSafe(msg.content) ->
                            MarkdownContent(msg.content)
                        msg.content.isNotBlank() ->
                            Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (isStreamingLast && msg.content.isNotBlank()) {
                        TypingCursor()
                    }
                    msg.toolCalls?.forEach { tc ->
                        ToolCallCard(tc)
                    }
                    if (!isStreamingLast) {
                        Text(
                            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.createdAt)),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.End).padding(top = 3.dp)
                        )
                    }
                }
            }
        }
        if (isUser) {
            Spacer(Modifier.width(8.dp))
            AvatarBadge(isUser = true)
        }
    }
}

/** 头像徽章：AI 是蓝紫渐变"深"，用户是柔和渐变"我"。 */
@Composable
private fun AvatarBadge(isUser: Boolean, active: Boolean = false) {
    if (active) {
        val ringAlpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "ring")
            .animateFloat(
                initialValue = 0.15f,
                targetValue = 0.6f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(900),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "ring_a"
            )
        Box(modifier = Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha))
            )
            AvatarInner(isUser)
        }
        return
    }
    AvatarInner(isUser)
}

@Composable
private fun AvatarInner(isUser: Boolean) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    if (isUser) listOf(Color(0xFF9FB0E8), Color(0xFF7C8FD0))
                    else listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isUser) "我" else "深",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

/** 三点跳动的"思考中"动画 */
@Composable
private fun ThreeDots() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "dots")
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
        repeat(3) { i ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(
                        500,
                        delayMillis = i * 160,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    ),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
            )
        }
    }
}

/** 流式输出的打字光标（闪烁小竖条） */
@Composable
private fun TypingCursor() {
    val alpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "cursor")
        .animateFloat(
            initialValue = 1f,
            targetValue = 0f,
            animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(600),
                repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "cursor_a"
        )
    Box(
        modifier = Modifier
            .padding(start = 2.dp, top = 2.dp)
            .size(width = 3.dp, height = 16.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}

/** 渲染带代码块的内容：``` 围起来的部分用卡片展示，其余交给 Markdown。 */
@Composable
private fun MarkdownContent(content: String) {
    val parts = content.split("```")
    Column(Modifier.fillMaxWidth()) {
        parts.forEachIndexed { i, part ->
            if (i % 2 == 0) {
                if (part.isNotBlank()) {
                    Markdown(content = part, modifier = Modifier.fillMaxWidth())
                }
            } else {
                CodeBlockCard(part.removePrefix("kotlin").removePrefix("python").removePrefix("java")
                    .removePrefix("json").removePrefix("bash").removePrefix("sh")
                    .removePrefix("text").removePrefix("sql"))
            }
        }
    }
}

/** 代码块卡片：独立背景 + 一键复制 */
@Composable
private fun CodeBlockCard(code: String) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "代码",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(
                        android.content.ClipData.newPlainText("code", code)
                    )
                }
            ) { Text("复制", fontSize = 11.sp) }
        }
        SelectionContainer {
            Text(
                code.trim(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** 全屏查看图片：双指缩放、拖动、点空白关闭 */
@Composable
private fun ZoomableImage(path: String, onClose: () -> Unit) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val bmp = remember(path) {
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 2048) sample *= 2
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                .let { opts -> android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap() }
        }.getOrNull()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    offset += pan
                }
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        bmp?.let {
            Image(
                bitmap = it,
                contentDescription = "图片预览",
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "关闭", tint = Color.White)
        }
    }
}

/** 从本地文件加载并显示消息图片（按显示尺寸采样，避免大图撑爆内存）。 */
@Composable
private fun MessageImage(path: String, modifier: Modifier = Modifier) {
    val bmp = remember(path) {
        runCatching {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            while (longest / sample > 1080) sample *= 2
            android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                .let { opts -> android.graphics.BitmapFactory.decodeFile(path, opts)?.asImageBitmap() }
        }.getOrNull()
    }
    bmp?.let {
        Image(
            bitmap = it,
            contentDescription = "消息图片",
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

/** 只有简单文本才走 Markdown 渲染：列表/超长内容降级纯文本。 */
private fun markdownSafe(content: String): Boolean =
    content.length <= 3000 &&
        !Regex("(?m)^\\s*[-*+]\\s").containsMatchIn(content) &&
        !Regex("(?m)^\\s*\\d+[.)]\\s").containsMatchIn(content) &&
        !content.contains("```")

/** 思考过程卡片：默认折叠，点按展开，类似 Claude Code 的 thinking 块。 */
@Composable
private fun ThinkingCard(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val arrow by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "think_arrow"
    )
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable { expanded = !expanded }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "思考过程",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    cm.setPrimaryClip(
                        android.content.ClipData.newPlainText("reasoning", reasoning)
                    )
                }
            ) { Text("复制", fontSize = 10.sp) }
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(arrow)
            )
        }
        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = reasoning,
                        style = MaterialTheme.typography.bodySmall.copy(fontStyle = FontStyle.Italic),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}
