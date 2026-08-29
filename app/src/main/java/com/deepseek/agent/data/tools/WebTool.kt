package com.deepseek.agent.data.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** 读取网页并提取纯文本。限制：仅 http/https、响应 ≤ 1MB、15 秒超时。 */
class FetchUrlTool : AgentTool {
    override val name = "fetch_url"
    override val description = "读取指定网页的内容并提取纯文本（仅 http/https，超过 1MB 会被截断）"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("url") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("要读取的网页完整地址，以 http:// 或 https:// 开头"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("url")) }
            }
        )
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val url = runCatching { JSONObject(argsJson).getString("url") }.getOrElse {
            return ToolResult(callId, "参数缺少 url", false)
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(callId, "只支持 http/https 链接", false)
        }
        return try {
            val request = Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) DeepSeekAgent/1.0")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return ToolResult(callId, "网页返回错误: HTTP ${resp.code}", false)
                }
                val body = resp.body ?: return ToolResult(callId, "网页内容为空", false)
                if (body.contentLength() > 1024 * 1024) {
                    return ToolResult(callId, "网页过大（>1MB），已拒绝读取", false)
                }
                val html = body.string().take(1024 * 1024)
                val text = htmlToText(html)
                if (text.isBlank()) return ToolResult(callId, "没有提取到可读内容", false)
                ToolResult(callId, text.take(8000), true)
            }
        } catch (t: Throwable) {
            ToolResult(callId, "读取失败: ${t.javaClass.simpleName}: ${t.message ?: "无详细信息"}", false)
        }
    }

    companion object {
        /** 简单 HTML 转文本：去脚本/样式/标签，解常用实体，压缩空白。 */
        fun htmlToText(html: String): String {
            var s = html
            s = Regex("(?is)<script.*?</script>").replace(s, " ")
            s = Regex("(?is)<style.*?</style>").replace(s, " ")
            s = Regex("(?is)<br\\s*/?>").replace(s, "\n")
            s = Regex("(?is)</(p|div|h[1-6]|li|tr)>").replace(s, "\n")
            s = Regex("<[^>]+>").replace(s, " ")
            s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            s = Regex("&#(\\d+);").replace(s) { m ->
                runCatching { m.groupValues[1].toInt().toChar().toString() }.getOrDefault(" ")
            }
            return s.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        }
    }
}
