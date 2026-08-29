package com.deepseek.agent.data.remote

import com.deepseek.agent.data.tools.ToolRegistry
import com.deepseek.agent.domain.model.ChatMessage
import com.deepseek.agent.security.KeystoreHelper
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

class DeepSeekRepository(
    private val keystore: KeystoreHelper,
    private val toolRegistry: ToolRegistry
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
        // 有默认值的字段（如 tools 的 type="function"）也必须输出
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MINUTES) // SSE 长连接，禁用读超时
        .addInterceptor { chain ->
            val req = chain.request()
            val apiKey = keystore.getApiKey()
            val newReq = if (apiKey != null) {
                req.newBuilder().header("Authorization", "Bearer $apiKey").build()
            } else req
            chain.proceed(newReq)
        }
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://api.deepseek.com/")
        .client(okHttpClient)
        .addConverterFactory(
            json.asConverterFactory("application/json".toMediaType())
        )
        .build()

    val api: DeepSeekApiService = retrofit.create(DeepSeekApiService::class.java)

    fun hasApiKey(): Boolean = keystore.getApiKey() != null

    /**
     * 将领域消息转为 API 消息；assistant 消息若带 tool_calls 一并带上。
     * 非视觉模型不支持图片：带图消息自动剥离图片、只发文本，避免中途切模型后报错。
     */
    fun toApiMessages(messages: List<ChatMessage>, model: String): List<ApiMessage> =
        messages.map { m ->
            ApiMessage(
                role = m.role,
                content = buildContent(m, model),
                toolCallId = m.toolCallId,
                toolCalls = m.toolCalls?.map {
                    ApiToolCall(it.id, "function", ApiFunctionCall(it.name, it.argumentsJson))
                }
            )
        }

    /** 构造消息内容：视觉模型下带图消息用多模态数组，其余用字符串。 */
    private fun buildContent(m: ChatMessage, model: String): JsonElement? {
        if (m.imagePath != null && model.contains("vision")) {
            val base64 = runCatching { java.io.File(m.imagePath).readBytes() }
                .getOrNull()?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
            if (base64 != null) {
                return buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(m.content.ifBlank { "请看这张图片" }))
                    })
                    add(buildJsonObject {
                        put("type", JsonPrimitive("image_url"))
                        putJsonObject("image_url") {
                            put("url", JsonPrimitive("data:image/jpeg;base64,$base64"))
                        }
                    })
                }
            }
        }
        // tool 消息必须带 content 字段；只有 assistant 允许为空（转 null 后省略）
        return when {
            m.role == "tool" -> JsonPrimitive(m.content)
            m.content.isNotBlank() -> JsonPrimitive(m.content)
            else -> null
        }
    }

    fun stream(
        model: String,
        messages: List<ChatMessage>
    ): Flow<SseChunk> = flow {
        val request = ChatCompletionRequest(
            model = model,
            messages = toApiMessages(messages, model),
            // 视觉实验模型不支持工具调用
            tools = toolRegistry.definitions().takeIf { it.isNotEmpty() && !model.contains("vision") },
            stream = true
        )
        val auth = "Bearer ${keystore.getApiKey().orEmpty()}"
        val body: ResponseBody = try {
            api.streamChat(auth, request)
        } catch (e: HttpException) {
            val raw = runCatching { e.response()?.errorBody()?.string() }.getOrNull()
            throw java.io.IOException("服务器错误: ${extractServerError(raw) ?: e.message()}")
        }
        SseParser(json).parse(body).collect { emit(it) }
    }.flowOn(kotlinx.coroutines.Dispatchers.IO) // 阻塞式 SSE 读取远离主线程，防卡顿/ANR

    /** 从 DeepSeek 错误响应体里取出具体原因（如 "Model Not Exist"）。 */
    private fun extractServerError(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return runCatching {
            val err = org.json.JSONObject(body).optJSONObject("error")
            err?.optString("message")?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: body.take(300)
    }
}
