package com.deepseek.agent.data.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 获取当前日期时间（AI 不知道自己身处何时）。 */
class GetTimeTool : AgentTool {
    override val name = "get_time"
    override val description = "获取当前的日期、时间和星期"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") { }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val fmt = SimpleDateFormat("yyyy年MM月dd日 EEEE HH:mm:ss", Locale.CHINA)
        return ToolResult(callId, fmt.format(Date()), true)
    }
}

/** 读取系统剪贴板。 */
class GetClipboardTool(private val context: Context) : AgentTool {
    override val name = "get_clipboard"
    override val description = "读取手机剪贴板中的文本内容"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") { }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
            if (text.isNullOrBlank()) ToolResult(callId, "(剪贴板为空)", true)
            else ToolResult(callId, text.take(8000), true)
        } catch (t: Throwable) {
            ToolResult(callId, "读取剪贴板失败: ${t.message}", false)
        }
    }
}

/** 把文本写入系统剪贴板。 */
class SetClipboardTool(private val context: Context) : AgentTool {
    override val name = "set_clipboard"
    override val description = "把文本写入手机剪贴板（方便用户粘贴）"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("要写入剪贴板的文本"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("text")) }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val text = runCatching { JSONObject(argsJson).getString("text") }.getOrElse {
            return ToolResult(callId, "参数缺少 text", false)
        }
        return try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("DeepSeekAgent", text))
            ToolResult(callId, "已写入剪贴板（${text.length} 字符）", true)
        } catch (t: Throwable) {
            ToolResult(callId, "写入剪贴板失败: ${t.message}", false)
        }
    }
}

/** 用系统浏览器打开网页。 */
class OpenUrlTool(private val context: Context) : AgentTool {
    override val name = "open_url"
    override val description = "用手机浏览器打开一个网页链接"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("url") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("完整网址，以 http:// 或 https:// 开头"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("url")) }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val url = runCatching { JSONObject(argsJson).getString("url") }.getOrElse {
            return ToolResult(callId, "参数缺少 url", false)
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(callId, "只支持 http/https 链接", false)
        }
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ToolResult(callId, "已在浏览器打开: $url", true)
        } catch (t: Throwable) {
            ToolResult(callId, "打开失败: ${t.message}", false)
        }
    }
}

/** 发送一条系统通知。 */
class NotifyTool(private val context: Context) : AgentTool {
    override val name = "notify"
    override val description = "发送一条手机系统通知（用于任务完成后提醒用户）"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("title") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("通知标题"))
                    }
                    putJsonObject("content") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("通知内容"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("title")); add(JsonPrimitive("content")) }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val jo = try { JSONObject(argsJson) } catch (e: Exception) {
            return ToolResult(callId, "参数解析失败: ${e.message}", false)
        }
        return try {
            val notification = NotificationCompat.Builder(context, "agent_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(jo.optString("title", "DeepSeek Agent"))
                .setContentText(jo.optString("content", ""))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(
                (System.currentTimeMillis() % 100000).toInt(), notification
            )
            ToolResult(callId, "通知已发送", true)
        } catch (t: Throwable) {
            ToolResult(callId, "发送通知失败: ${t.message}", false)
        }
    }
}
