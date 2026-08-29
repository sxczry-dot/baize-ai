package com.deepseek.agent.data.remote

import com.deepseek.agent.data.tools.ToolDefinition
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

/**
 * DeepSeek OpenAI 兼容端点：https://api.deepseek.com/chat/completions
 */
interface DeepSeekApiService {

    @Streaming
    @POST("chat/completions")
    suspend fun streamChat(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): ResponseBody
}

@Serializable
data class ChatCompletionRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val tools: List<ToolDefinition>? = null,
    val stream: Boolean = true,
    @SerialName("max_tokens") val maxTokens: Int? = 16384
)

@Serializable
data class ApiMessage(
    val role: String, // system / user / assistant / tool
    /** 纯文本消息是字符串；带图片的消息是多模态内容块数组 */
    val content: JsonElement? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ApiToolCall>? = null
)

@Serializable
data class ApiToolCall(
    val id: String,
    val type: String = "function",
    val function: ApiFunctionCall
)

@Serializable
data class ApiFunctionCall(
    val name: String,
    val arguments: String // JSON 字符串
)
