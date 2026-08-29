package com.deepseek.agent.data.tools

import android.content.Context
import android.os.Build
import android.os.Environment
import com.deepseek.agent.domain.model.ChatMessage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 确认闸门：危险工具执行前挂起等待用户决定。
 * 由 ChatViewModel 实现，桥接到 UI 弹窗。
 */
fun interface ConfirmationGate {
    /** 挂起直到用户确认（true）或拒绝（false）/超时。 */
    suspend fun awaitApproval(toolName: String, argsPreview: String): Boolean
}

/** 工具执行结果 */
data class ToolResult(val callId: String, val output: String, val ok: Boolean)

/**
 * 工作区：默认是应用内置沙盒；用户开启"完整文件访问"权限后，
 * 工作区变成整个手机内部存储（/storage/emulated/0），AI 可以操作任何文件夹。
 * 相对路径仍不允许用 ../ 跳出根目录（安全底线保留）。
 */
class Workspace(private val context: Context) {
    private val sandboxDir = File(context.filesDir, "workspace").apply { mkdirs() }

    @Volatile
    var root: File = sandboxDir
        private set

    @Volatile
    var hasFullAccess: Boolean = false
        private set

    /** 检查系统权限，有完整文件访问权就把工作区切到整个手机存储。 */
    fun refresh() {
        val granted = if (Build.VERSION.SDK_INT >= 30) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        hasFullAccess = granted
        root = if (granted) Environment.getExternalStorageDirectory() else sandboxDir
    }

    fun rootFile(): File = root

    fun resolve(relative: String): File {
        val base = root.canonicalFile
        val target = File(root, relative).canonicalFile
        if (!target.path.startsWith(base.path + File.separator) && target != base) {
            error("路径越界，禁止访问工作区外文件")
        }
        return target
    }
}

/** 工具接口 */
interface AgentTool {
    val name: String
    val description: String
    fun buildDefinition(): ToolDefinition
    suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult
}

@Serializable
data class ToolDefinition(
    val type: String = "function",
    val function: ToolFunction
)

@Serializable
data class ToolFunction(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

class ToolRegistry(context: Context) {

    val workspace = Workspace(context).apply { refresh() }

    private var gate: ConfirmationGate = ConfirmationGate { _, _ -> false }
    private var allowWrite = false
    private var allowShell = false

    /** 由 ChatViewModel 在启动时注入（确认弹窗桥 + 当前设置）。 */
    fun attach(gate: ConfirmationGate, allowWrite: Boolean, allowShell: Boolean) {
        this.gate = gate
        this.allowWrite = allowWrite
        this.allowShell = allowShell
    }

    fun setAllowWrite(v: Boolean) { allowWrite = v }
    fun setAllowShell(v: Boolean) { allowShell = v }

    private val tools: List<AgentTool> = listOf(
        ListDirectoryTool(),
        ReadFileTool(),
        WriteFileTool { gate.awaitApproval("write_file", it) },
        ExecuteShellTool { gate.awaitApproval("execute_shell", it) },
        FetchUrlTool(),
        EvalExprTool(),
        MoveFileTool { gate.awaitApproval("move_file", it) },
        DeleteFileTool { gate.awaitApproval("delete_file", it) },
        CopyFileTool(),
        FindFilesTool(),
        GetTimeTool(),
        GetClipboardTool(context),
        SetClipboardTool(context),
        ZipTool(),
        UnzipTool { gate.awaitApproval("unzip_file", it) },
        WebSearchTool(),
        OpenUrlTool(context),
        NotifyTool(context)
    )

    fun definitions(): List<ToolDefinition> = tools.map { it.buildDefinition() }

    fun find(name: String): AgentTool? = tools.firstOrNull { it.name == name }

    fun checkWriteEnabled() = allowWrite
    fun checkShellEnabled() = allowShell

    companion object {
        fun defaultSystemMessage(userProfile: String = ""): ChatMessage {
            val profileSection = if (userProfile.isBlank()) "" else
                "\n\n【关于用户】（用户自己填写，请据此调整交流方式）\n$userProfile"
            return ChatMessage(
                role = "system",
                content = "你是用户手机上的私人 AI 助手「白泽」，运行在安卓设备上。\n\n" +
                    "## 你的能力\n" +
                    "- 工作区内的文件管理：列目录、读写、移动、复制、删除、按名搜索、打包 zip、解压 zip。" +
                    "所有路径都相对于工作区根目录。工作区默认是 App 内置沙盒；若用户在设置中开启了完整文件访问，" +
                    "工作区就是整个手机存储的根目录。不确定的话先用 list_directory 看根目录内容判断。\n" +
                    "- 联网：web_search 搜索网页、fetch_url 读取网页内容、open_url 用浏览器打开链接\n" +
                    "- 手机能力：get_time 看当前时间、读写剪贴板（get_clipboard/set_clipboard）、notify 发系统通知\n" +
                    "- eval_expr 做数学计算\n" +
                    "- 在设置开启后可用 execute_shell 执行 shell 命令\n\n" +
                    "## 交流风格\n" +
                    "- 始终使用简体中文，除非用户用其他语言\n" +
                    "- 回答简洁、直击重点，不啰嗦；复杂内容用 Markdown 排版（列表、代码块、小标题）\n" +
                    "- 语气自然、像靠谱的朋友，不要机械感，不要每句都以「好的」开头\n" +
                    "- 用户没要求时不要长篇大论，先给结论再给解释\n\n" +
                    "## 使用工具的原则\n" +
                    "- 动手前先用一句话说明你要做什么，再调用工具\n" +
                    "- 写文件或执行命令是危险操作，会向用户弹窗确认，被拒绝就换方案\n" +
                    "- 读取到的用户文件内容保持原样，不要自行改写\n" +
                    "- 一步步来：先看（列目录、读文件），再动手（写文件）\n\n" +
                    "## 边界\n" +
                    "- 只操作工作区内的内容，不要尝试访问工作区之外\n" +
                    "- 不确定的事直接说不知道，不要编造" +
                    profileSection
            )
        }
    }
}

class ListDirectoryTool : AgentTool {
    override val name = "list_directory"
    override val description = "列出工作区指定目录下的文件和子目录（默认 path=\".\"）"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("相对工作区根目录的路径"))
                    }
                }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val path = runCatching { JSONObject(argsJson).optString("path", ".") }.getOrDefault(".")
        val dir = try { workspace.resolve(path) } catch (e: Exception) {
            return ToolResult(callId, "路径不合法: ${e.message}", false)
        }
        if (!dir.exists() || !dir.isDirectory) {
            return ToolResult(callId, "目录不存在: $path", false)
        }
        // 系统目录（如 Android/data）可能禁止访问，listFiles 会抛异常——容错处理
        val items = runCatching { dir.listFiles() }.getOrNull()
            ?.sortedBy { it.name }
            ?.take(200)
            ?.joinToString("\n") {
                "${if (it.isDirectory) "[目录]" else "[文件]"} ${it.name} (${it.length()}字节)"
            }
            .orEmpty().ifEmpty { "(空目录或无法读取)" }
        val suffix = if (items.lines().size >= 200) "\n（只显示前 200 项）" else ""
        return ToolResult(callId, items.take(8 * 1024) + suffix, true)
    }
}

class ReadFileTool : AgentTool {
    override val name = "read_file"
    override val description = "读取工作区内文本文件内容"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("相对工作区根目录的文件路径"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("path")) }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val path = runCatching { JSONObject(argsJson).getString("path") }.getOrElse {
            return ToolResult(callId, "参数缺少 path", false)
        }
        val file = try { workspace.resolve(path) } catch (e: Exception) {
            return ToolResult(callId, "路径不合法: ${e.message}", false)
        }
        if (!file.exists() || !file.isFile) return ToolResult(callId, "文件不存在: $path", false)
        if (file.length() > 1024 * 1024) return ToolResult(callId, "文件过大(>1MB): $path", false)
        return ToolResult(callId, file.readText(Charsets.UTF_8), true)
    }
}

class WriteFileTool(private val approver: suspend (String) -> Boolean) : AgentTool {
    override val name = "write_file"
    override val description = "写入文本文件（覆盖）。危险操作，需用户确认。"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("path") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("相对工作区根目录的文件路径"))
                    }
                    putJsonObject("content") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("要写入的文件内容"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("path")); add(JsonPrimitive("content")) }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val jo = try { JSONObject(argsJson) } catch (e: Exception) {
            return ToolResult(callId, "参数解析失败: ${e.message}", false)
        }
        val path = jo.optString("path")
        val content = jo.optString("content")
        if (path.isBlank()) return ToolResult(callId, "参数缺少 path", false)

        val approved = approver("path=$path\n内容长度=${content.length} 字符")
        if (!approved) return ToolResult(callId, "用户拒绝了写入操作", false)

        val file = try { workspace.resolve(path) } catch (e: Exception) {
            return ToolResult(callId, "路径不合法: ${e.message}", false)
        }
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
        return ToolResult(callId, "已写入: $path (${content.length} 字符)", true)
    }
}

class ExecuteShellTool(private val approver: suspend (String) -> Boolean) : AgentTool {
    override val name = "execute_shell"
    override val description = "在工作区目录内执行 shell 命令（sh -c），限时 15 秒。危险操作，需用户确认。"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("command") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("要执行的 shell 命令"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("command")) }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val cmd = runCatching { JSONObject(argsJson).getString("command") }.getOrElse {
            return ToolResult(callId, "参数缺少 command", false)
        }
        val approved = approver(cmd.take(500))
        if (!approved) return ToolResult(callId, "用户拒绝了命令执行", false)

        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", cmd), null, workspace.rootFile())
            // 先等超时再读输出：命令输出超过管道缓冲时，先读会永久阻塞
            val finished = proc.waitFor(15, TimeUnit.SECONDS)
            if (!finished) proc.destroyForcibly()
            val out = runCatching { proc.inputStream.bufferedReader().readText() }.getOrDefault("")
            val err = runCatching { proc.errorStream.bufferedReader().readText() }.getOrDefault("")
            if (!finished) {
                return ToolResult(callId, "命令超时（15秒），已终止", false)
            }
            val merged = (out + (if (err.isNotEmpty()) "\n[stderr]\n$err" else "")).take(8 * 1024)
            ToolResult(callId, merged.ifEmpty { "(无输出)" }, true)
        } catch (t: Throwable) {
            ToolResult(callId, "执行失败: ${t.message}", false)
        }
    }
}
