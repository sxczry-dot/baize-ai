package com.deepseek.agent.data.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** 用 Bing 网页版搜索（无需 API Key），解析出标题/链接/摘要。 */
class WebSearchTool : AgentTool {
    override val name = "web_search"
    override val description = "联网搜索网页（返回前 8 条结果的标题、链接和摘要）"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("query") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("搜索关键词"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("query")) }
            }
        )
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val query = runCatching { JSONObject(argsJson).getString("query") }.getOrElse {
            return ToolResult(callId, "参数缺少 query", false)
        }
        return try {
            val url = "https://cn.bing.com/search?q=" + URLEncoder.encode(query, "UTF-8") +
                "&mkt=zh-CN&setlang=zh-hans&format=rss"
            val request = Request.Builder().url(url)
                // 桌面 UA：Bing 对移动 UA 返回不同结构，桌面版结构稳定
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return ToolResult(callId, "搜索请求失败: HTTP ${resp.code}", false)
                val body = resp.body?.string() ?: return ToolResult(callId, "搜索返回为空", false)
                val results = parseResults(body)
                if (results.isEmpty()) return ToolResult(callId, "没有解析到搜索结果（搜索引擎可能临时拒绝）", false)
                ToolResult(callId, results.joinToString("\n\n"), true)
            }
        } catch (t: Throwable) {
            ToolResult(callId, "搜索失败: ${t.javaClass.simpleName}: ${t.message ?: "无详细信息"}", false)
        }
    }

    private fun parseResults(html: String): List<String> {
        // 模式1：Bing RSS 输出（format=rss）：<item><title>..</title><link>..</link><description>..</description></item>
        val rssItems = Regex("(?s)<item>.*?</item>").findAll(html).toList()
        if (rssItems.isNotEmpty()) {
            return rssItems.take(8).mapNotNull { item ->
                val title = Regex("(?s)<title>(.*?)</title>").find(item.value)
                    ?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: return@mapNotNull null
                val link = Regex("(?s)<link>(.*?)</link>").find(item.value)
                    ?.groupValues?.get(1)?.trim() ?: ""
                val desc = Regex("(?s)<description>(.*?)</description>").find(item.value)
                    ?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()?.take(200) ?: ""
                "【$title】\n$link\n$desc"
            }
        }
        // 模式2：Bing 标准网页结构 b_algo
        val algo = Regex("(?s)<li class=\"b_algo\".*?</li>").findAll(html).toList()
            .take(8).mapNotNull { block ->
                val title = Regex("(?s)<h2[^>]*>.*?<a[^>]*>(.*?)</a>").find(block.value)
                    ?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim() ?: return@mapNotNull null
                val link = Regex("(?s)<h2[^>]*>.*?<a[^>]*href=\"([^\"]+)\"").find(block.value)
                    ?.groupValues?.get(1)?.replace("&amp;", "&") ?: ""
                "【$title】\n$link"
            }
        if (algo.isNotEmpty()) return algo
        // 模式3：宽松兜底——任意 h2 内链接
        return Regex("(?s)<h2[^>]*>\\s*<a[^>]*href=\"(https?://[^\"]+)\"[^>]*>(.*?)</a>")
            .findAll(html).take(8).map { m ->
                val url = m.groupValues[1].replace("&amp;", "&")
                val title = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                "【$title】\n$url"
            }.toList()
    }
}
