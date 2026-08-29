package com.deepseek.agent.domain.model

import kotlinx.serialization.Serializable

/**
 * 一条对话消息。role: system/user/assistant/tool。
 * 当 role=assistant 且 model 返回 tool_calls 时，[toolCalls] 保存待执行的工具调用；
 * 当 role=tool 时，[toolCallId] 对应被回传结果的 tool_call。
 */
@Serializable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val role: String,
    val content: String = "",
    /** AI 的思考过程（思考模型的思维链，可折叠展示） */
    val reasoning: String = "",
    /** 用户消息附带的图片（应用私有目录下的文件路径，发送给视觉模型） */
    val imagePath: String? = null,
    val toolCallId: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Serializable
data class ToolCall(
    val id: String,
    var name: String,
    var argumentsJson: String,
    var status: ToolCallStatus = ToolCallStatus.Pending,
    var result: String? = null
)

@Serializable
enum class ToolCallStatus { Pending, AwaitingConfirmation, Confirmed, Rejected, Running, Success, Failed }

data class Session(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "新会话",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
