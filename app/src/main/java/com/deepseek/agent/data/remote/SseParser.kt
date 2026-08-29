package com.deepseek.agent.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody
import okio.BufferedSource
import okio.use

/**
 * 解析 DeepSeek 流式响应（SSE）：
 * - 每行 `data: {...}` 为一个 JSON 事件
 * - `[DONE]` 表示流结束
 * 产出 [SseChunk]：文本增量 或 工具调用增量 或 完成信号。
 */
class SseParser(private val json: Json = Json { ignoreUnknownKeys = true }) {

    fun parse(body: ResponseBody): Flow<SseChunk> = flow {
        body.source().use { source ->
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") {
                    emit(SseChunk.Done)
                    break
                }
                runCatching { json.decodeFromString(StreamEnvelope.serializer(), payload) }
                    .onSuccess { env ->
                        val choice = env.choices.firstOrNull() ?: return@onSuccess
                        val delta = choice.delta
                        if (!delta.reasoningContent.isNullOrEmpty()) {
                            emit(SseChunk.ReasoningDelta(delta.reasoningContent))
                        }
                        if (!delta.content.isNullOrEmpty()) {
                            emit(SseChunk.TextDelta(delta.content))
                        }
                        delta.toolCalls?.forEach { tc ->
                            emit(
                                SseChunk.ToolCallDelta(
                                    id = tc.id,
                                    name = tc.function.name,
                                    argumentsChunk = tc.function.arguments
                                )
                            )
                        }
                        if (choice.finishReason != null) {
                            emit(SseChunk.Finish(choice.finishReason))
                        }
                    }
            }
        }
        emit(SseChunk.Done)
    }

    @kotlinx.serialization.Serializable
    private data class StreamEnvelope(
        val choices: List<StreamChoice>
    )

    @kotlinx.serialization.Serializable
    private data class StreamChoice(
        val delta: StreamDelta,
        @kotlinx.serialization.SerialName("finish_reason")
        val finishReason: String? = null
    )

    @kotlinx.serialization.Serializable
    private data class StreamDelta(
        val content: String? = null,
        @kotlinx.serialization.SerialName("reasoning_content")
        val reasoningContent: String? = null,
        @kotlinx.serialization.SerialName("tool_calls")
        val toolCalls: List<StreamToolCall>? = null
    )

    @kotlinx.serialization.Serializable
    private data class StreamToolCall(
        val id: String = "",
        val function: StreamFunction
    )

    @kotlinx.serialization.Serializable
    private data class StreamFunction(
        val name: String = "",
        val arguments: String = ""
    )
}

sealed interface SseChunk {
    data class TextDelta(val text: String) : SseChunk
    data class ReasoningDelta(val text: String) : SseChunk
    data class ToolCallDelta(
        val id: String,
        val name: String,
        val argumentsChunk: String
    ) : SseChunk
    data class Finish(val reason: String?) : SseChunk
    data object Done : SseChunk
}
