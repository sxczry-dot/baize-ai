package com.deepseek.agent.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(Unit) {
        vm.toast.collect { snackbarHostState.showSnackbar(it) }
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
                            Text(
                                model,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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
                                onLongCopy = { text ->
                                    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                            as android.content.ClipboardManager
                                    cm.setPrimaryClip(
                                        android.content.ClipData.newPlainText("DeepSeekAgent", text)
                                    )
                                    scope.launch { snackbarHostState.showSnackbar("已复制到剪贴板") }
                                }
                            )
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
                                    vm.send(input, pendingImage)
                                    input = ""
                                    pendingImage = null
                                },
                                enabled = input.isNotBlank() || pendingImage != null,
                                modifier = Modifier
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "发送",
                                    tint = if (input.isNotBlank() || pendingImage != null) MaterialTheme.colorScheme.primary
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

@Composable
private fun SessionDrawer(
    sessions: List<SessionEntity>,
    currentId: String?,
    onNewSession: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
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
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions) { s ->
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
                        modifier = Modifier.clickable { onSelect(s.id) }
                    )
                }
            }
        }
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
private fun MessageBubble(msg: ChatMessage, useMarkdown: Boolean, onLongCopy: (String) -> Unit) {
    val isUser = msg.role == "user"
    val bubbleShape = RoundedCornerShape(
        topStart = 20.dp, topEnd = 20.dp,
        bottomStart = if (isUser) 20.dp else 6.dp,
        bottomEnd = if (isUser) 6.dp else 20.dp
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AvatarBadge(isUser = false)
            Spacer(Modifier.width(8.dp))
        }
        val longCopy = Modifier.combinedClickable(
            onClick = {},
            onLongClick = {
                val text = if (msg.content.isNotBlank()) msg.content else msg.reasoning
                if (text.isNotBlank()) onLongCopy(text)
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
                            Text(
                                "思考中…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        // 列表结构用纯文本：Markdown 库渲染列表有重叠 bug；
                        // 超长内容也用纯文本：Markdown 解析长文是低配机闪退高发区
                        msg.content.isNotBlank() && useMarkdown && markdownSafe(msg.content) ->
                            Markdown(content = msg.content, modifier = Modifier.fillMaxWidth())
                        msg.content.isNotBlank() ->
                            Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
                    }
                    msg.toolCalls?.forEach { tc ->
                        ToolCallCard(tc)
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
private fun AvatarBadge(isUser: Boolean) {
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
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("💭", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (expanded) "思考过程 · 点击收起" else "思考过程 · 点击展开",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
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
