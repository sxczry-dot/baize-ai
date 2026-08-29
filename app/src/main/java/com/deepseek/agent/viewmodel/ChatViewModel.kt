package com.deepseek.agent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepseek.agent.data.local.MessageDao
import com.deepseek.agent.data.local.SessionDao
import com.deepseek.agent.data.local.SettingsStore
import com.deepseek.agent.data.local.entity.MessageEntity
import com.deepseek.agent.data.local.entity.SessionEntity
import com.deepseek.agent.data.remote.DeepSeekRepository
import com.deepseek.agent.data.remote.SseChunk
import com.deepseek.agent.data.tools.ConfirmationGate
import com.deepseek.agent.data.tools.ToolRegistry
import com.deepseek.agent.domain.model.ChatMessage
import com.deepseek.agent.domain.model.ToolCall
import com.deepseek.agent.domain.model.ToolCallStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

/** 等待用户决定的确认请求（UI 层据此弹窗）。 */
data class PendingConfirmation(
    val toolName: String,
    val argsPreview: String,
    val respond: (Boolean) -> Unit
)

class ChatViewModel(
    private val repository: DeepSeekRepository,
    private val toolRegistry: ToolRegistry,
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    settingsStore: SettingsStore
) : ViewModel(), ConfirmationGate {

    private val json = Json { ignoreUnknownKeys = true }

    private var sessionId: String? = null
    private var agentJob: Job? = null
    private var userProfile = ""

    private val _messages = MutableStateFlow<List<ChatMessage>>(listOf(ToolRegistry.defaultSystemMessage()))
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId

    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming

    private val _pendingConfirmation = MutableStateFlow<PendingConfirmation?>(null)
    val pendingConfirmation: StateFlow<PendingConfirmation?> = _pendingConfirmation

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast: SharedFlow<String> = _toast.asSharedFlow()

    val currentModel: StateFlow<String> = settingsStore.model
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsStore.MODEL_PRO)

    init {
        viewModelScope.launch {
            settingsStore.allowWrite.collect { toolRegistry.setAllowWrite(it) }
        }
        viewModelScope.launch {
            settingsStore.allowShell.collect { toolRegistry.setAllowShell(it) }
        }
        viewModelScope.launch {
            settingsStore.userProfile.collect { profile ->
                userProfile = profile
                val idx = _messages.value.indexOfFirst { it.role == "system" }
                if (idx >= 0) {
                    _messages.value = _messages.value.toMutableList().also {
                        it[idx] = ToolRegistry.defaultSystemMessage(profile)
                    }
                }
            }
        }
        toolRegistry.attach(this, false, false)
    }

    // ---------- 会话管理 ----------

    fun startSession(id: String) {
        if (sessionId == id) return
        agentJob?.cancel()
        sessionId = id
        _currentSessionId.value = id
        viewModelScope.launch {
            val entities = messageDao.observeBySession(id).first()
            _messages.value = listOf(ToolRegistry.defaultSystemMessage(userProfile)) + entities.map { it.toDomain(json) }
        }
    }

    fun startNewSession() {
        agentJob?.cancel()
        sessionId = null
        _currentSessionId.value = null
        _messages.value = listOf(ToolRegistry.defaultSystemMessage(userProfile))
    }

    // ---------- 发送与 Agent 循环 ----------

    fun send(userInput: String, imagePath: String? = null) {
        if ((userInput.isBlank() && imagePath == null) || _isStreaming.value) return
        if (!repository.hasApiKey()) {
            _toast.tryEmit("请先到设置中填写 DeepSeek API Key")
            return
        }
        agentJob = viewModelScope.launch {
            if (sessionId == null) {
                val title = userInput.replace("\n", " ").take(20).ifBlank { "图片" }
                val s = SessionEntity(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                sessionDao.insert(s)
                sessionId = s.id
                _currentSessionId.value = s.id
            }
            val userMsg = ChatMessage(role = "user", content = userInput, imagePath = imagePath)
            val list = sanitizeHistory(_messages.value + userMsg)
            _messages.value = list
            messageDao.insertAll(listOf(userMsg.toEntity(sessionId!!, json)))

            // 带图消息自动用视觉模型（视觉模型不带工具，纯问答）
            val model = if (imagePath != null) {
                _toast.tryEmit("已自动切换为视觉模型")
                SettingsStore.MODEL_VISION
            } else currentModel.value

            runAgentLoop(list, model)
            updateSessionTimestamp()
        }
    }

    /**
     * 修复残缺历史：assistant 带 tool_calls 但后续工具结果不齐全（闪退/中断遗留）时，
     * 整段剔除，否则 DeepSeek 会拒绝请求。
     */
    private fun sanitizeHistory(messages: List<ChatMessage>): List<ChatMessage> {
        val result = messages.toMutableList()
        var i = 0
        while (i < result.size) {
            val m = result[i]
            if (m.role == "assistant" && !m.toolCalls.isNullOrEmpty()) {
                val ids = m.toolCalls.map { it.id }.toSet()
                var j = i + 1
                var responded = 0
                while (j < result.size && result[j].role == "tool") {
                    if (result[j].toolCallId in ids) responded++
                    j++
                }
                if (responded < ids.size) {
                    result.subList(i, j).clear()
                    continue
                }
            }
            i++
        }
        return result
    }

    private suspend fun runAgentLoop(initial: List<ChatMessage>, model: String) {
        _isStreaming.value = true
        try {
            var working = initial
            var round = 0
            while (round < 30) {
                round++
                val assistantMsg = ChatMessage(role = "assistant")
                // 占位消息只用于界面流式显示；请求列表里不能有空 assistant 消息
                // （DeepSeek 会拒绝既无 content 也无 tool_calls 的 assistant 消息）
                _messages.value = working + assistantMsg

                // StringBuilder 累积：思考流式分片极密，+= 拼接会越拼越慢（O(n²)）
                val accText = StringBuilder()
                val accReasoning = StringBuilder()
                val accToolCalls = LinkedHashMap<String, ToolCall>()
                var lastUiUpdate = 0L
                repository.stream(model, working).collect { chunk ->
                    when (chunk) {
                        is SseChunk.ReasoningDelta -> {
                            accReasoning.append(chunk.text)
                            if (shouldUpdateUi(lastUiUpdate)) {
                                lastUiUpdate = System.currentTimeMillis()
                                updateLastAssistant(accText.toString(), accReasoning.toString(), accToolCalls.values.toList())
                            }
                        }
                        is SseChunk.TextDelta -> {
                            accText.append(chunk.text)
                            if (shouldUpdateUi(lastUiUpdate)) {
                                lastUiUpdate = System.currentTimeMillis()
                                updateLastAssistant(accText.toString(), accReasoning.toString(), accToolCalls.values.toList())
                            }
                        }
                        is SseChunk.ToolCallDelta -> {
                            // DeepSeek 只有首个分片带 id/name，后续分片只带 arguments 增量
                            val tc = if (chunk.id.isBlank()) {
                                accToolCalls.values.lastOrNull()
                            } else {
                                accToolCalls.getOrPut(chunk.id) {
                                    ToolCall(id = chunk.id, name = chunk.name, argumentsJson = "")
                                }
                            }
                            if (tc != null) {
                                if (tc.name.isBlank() && chunk.name.isNotBlank()) tc.name = chunk.name
                                tc.argumentsJson += chunk.argumentsChunk
                                updateLastAssistant(accText.toString(), accReasoning.toString(), accToolCalls.values.toList())
                            }
                        }
                        else -> Unit
                    }
                }

                val finalAssistant = assistantMsg.copy(
                    content = accText.toString(),
                    reasoning = accReasoning.toString(),
                    toolCalls = accToolCalls.values.toList().ifEmpty { null }
                )
                working = working + finalAssistant
                _messages.value = working
                messageDao.insertAll(listOf(finalAssistant.toEntity(sessionId!!, json)))

                if (accToolCalls.isEmpty()) break

                val toolMsgs = executeToolCalls(accToolCalls.values.toList())
                working = working + toolMsgs
                _messages.value = working
                // 工具结果 + 更新后的 assistant（工具状态已原地更新）一次性落库
                val updatedAssistant = _messages.value.last { it.role == "assistant" }
                messageDao.insertAll(
                    (toolMsgs + updatedAssistant).map { it.toEntity(sessionId!!, json) }
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            val detail = "${t.javaClass.simpleName}: ${t.message ?: "无详细信息"}"
            _toast.tryEmit("请求失败: $detail")
            appendErrorToLastAssistant(detail)
        } finally {
            _isStreaming.value = false
        }
    }

    private suspend fun executeToolCalls(calls: List<ToolCall>): List<ChatMessage> {
        return calls.map { call ->
            val tool = toolRegistry.find(call.name)
            val disabled = when (call.name) {
                "write_file" -> !toolRegistry.checkWriteEnabled()
                "execute_shell" -> !toolRegistry.checkShellEnabled()
                else -> false
            }
            when {
                tool == null -> {
                    call.status = ToolCallStatus.Failed
                    call.result = "未知工具: ${call.name}"
                    updateToolCallInUi(call)
                    ChatMessage(role = "tool", content = call.result!!, toolCallId = call.id)
                }
                disabled -> {
                    call.status = ToolCallStatus.Rejected
                    call.result = "该操作未在设置中开启，请到设置页打开对应开关"
                    updateToolCallInUi(call)
                    ChatMessage(role = "tool", content = call.result!!, toolCallId = call.id)
                }
                else -> {
                    // 执行前先显示"等你确认"（危险工具内部会弹窗挂起）
                    call.status = ToolCallStatus.AwaitingConfirmation
                    updateToolCallInUi(call)
                    // 每次执行前刷新工作区（用户可能刚在系统设置里开了完整文件访问）
                    toolRegistry.workspace.refresh()
                    // 工具执行统一切到后台线程：网络/文件/命令都不能碰主线程
                    val result = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        tool.execute(call.id, call.argumentsJson, toolRegistry.workspace)
                    }
                    call.result = result.output
                    call.status = if (result.ok) ToolCallStatus.Success else ToolCallStatus.Failed
                    updateToolCallInUi(call)
                    ChatMessage(role = "tool", content = result.output, toolCallId = call.id)
                }
            }
        }
    }

    // ---------- 确认闸门（ConfirmationGate） ----------

    override suspend fun awaitApproval(toolName: String, argsPreview: String): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _pendingConfirmation.value = PendingConfirmation(toolName, argsPreview) { ok ->
            deferred.complete(ok)
        }
        return withTimeoutOrNull(120_000) { deferred.await() } ?: false.also {
            _pendingConfirmation.value = null
        }
    }

    fun resolveConfirmation(ok: Boolean) {
        val req = _pendingConfirmation.value ?: return
        _pendingConfirmation.value = null
        req.respond(ok)
    }

    fun stop() {
        agentJob?.cancel()
        agentJob = null
        _isStreaming.value = false
    }

    // ---------- 内部工具 ----------

    private suspend fun updateSessionTimestamp() {
        val id = sessionId ?: return
        sessionDao.getById(id)?.let {
            sessionDao.update(it.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    /** 流式更新节流：思考模型每秒几十个分片，全部刷 UI 会拖垮低配手机。 */
    private fun shouldUpdateUi(lastUpdate: Long): Boolean =
        System.currentTimeMillis() - lastUpdate >= 250

    private fun updateLastAssistant(text: String, reasoning: String, toolCalls: List<ToolCall>) {
        val idx = _messages.value.indexOfLast { it.role == "assistant" }
        if (idx < 0) return
        val old = _messages.value[idx]
        _messages.value = _messages.value.toMutableList().also {
            it[idx] = old.copy(content = text, reasoning = reasoning, toolCalls = toolCalls.ifEmpty { null })
        }
    }

    private fun appendErrorToLastAssistant(message: String?) {
        val idx = _messages.value.indexOfLast { it.role == "assistant" }
        if (idx < 0) return
        val old = _messages.value[idx]
        val updated = old.copy(content = old.content + "\n\n[出错: ${message ?: "未知错误"}]")
        _messages.value = _messages.value.toMutableList().also { it[idx] = updated }
        val sid = sessionId
        if (sid != null) {
            viewModelScope.launch { messageDao.insertAll(listOf(updated.toEntity(sid, json))) }
        }
    }

    private fun updateToolCallInUi(call: ToolCall) {
        val idx = _messages.value.indexOfLast { it.role == "assistant" }
        if (idx < 0) return
        val old = _messages.value[idx]
        val newCalls = old.toolCalls?.map { if (it.id == call.id) call else it }
        _messages.value = _messages.value.toMutableList().also {
            it[idx] = old.copy(toolCalls = newCalls)
        }
    }
}

// ---------- 数据库实体与领域模型互转 ----------

fun ChatMessage.toEntity(sessionId: String, json: Json): MessageEntity = MessageEntity(
    id = id,
    sessionId = sessionId,
    role = role,
    content = content,
    reasoningContent = reasoning,
    imagePath = imagePath,
    toolCallId = toolCallId,
    toolCallsJson = toolCalls?.let { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ToolCall.serializer()), it) },
    createdAt = createdAt
)

fun MessageEntity.toDomain(json: Json): ChatMessage = ChatMessage(
    id = id,
    role = role,
    content = content,
    reasoning = reasoningContent,
    imagePath = imagePath,
    toolCallId = toolCallId,
    toolCalls = toolCallsJson?.let {
        runCatching {
            json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ToolCall.serializer()), it)
        }.getOrNull()
    },
    createdAt = createdAt
)
