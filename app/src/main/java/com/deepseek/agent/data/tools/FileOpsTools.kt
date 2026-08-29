package com.deepseek.agent.data.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import java.io.File

private fun toolDef(name: String, description: String, properties: List<Triple<String, String, String>>, required: List<String>) =
    ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    properties.forEach { (key, type, desc) ->
                        putJsonObject(key) {
                            put("type", JsonPrimitive(type))
                            put("description", JsonPrimitive(desc))
                        }
                    }
                }
                putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
            }
        )
    )

/** 移动/重命名文件或目录（危险操作，需确认）。 */
class MoveFileTool(private val approver: suspend (String) -> Boolean) : AgentTool {
    override val name = "move_file"
    override val description = "移动或重命名工作区内的文件/目录（危险操作，需用户确认）"
    override fun buildDefinition() = toolDef(
        name, description,
        listOf(
            Triple("source", "string", "源路径（相对工作区）"),
            Triple("destination", "string", "目标路径（相对工作区，含新文件名）")
        ),
        listOf("source", "destination")
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val jo = try { JSONObject(argsJson) } catch (e: Exception) {
            return ToolResult(callId, "参数解析失败: ${e.message}", false)
        }
        val src = jo.optString("source")
        val dst = jo.optString("destination")
        if (src.isBlank() || dst.isBlank()) return ToolResult(callId, "需要 source 和 destination", false)

        if (!approver("移动: $src → $dst")) return ToolResult(callId, "用户拒绝了移动操作", false)

        return try {
            val srcFile = workspace.resolve(src)
            val dstFile = workspace.resolve(dst)
            if (!srcFile.exists()) return ToolResult(callId, "源不存在: $src", false)
            if (dstFile.exists()) return ToolResult(callId, "目标已存在: $dst（先删除或换名）", false)
            dstFile.parentFile?.mkdirs()
            val ok = if (srcFile.renameTo(dstFile)) {
                true
            } else {
                srcFile.copyRecursively(dstFile, overwrite = false)
                srcFile.deleteRecursively()
                true
            }
            if (ok) ToolResult(callId, "已移动: $src → $dst", true)
            else ToolResult(callId, "移动失败: $src", false)
        } catch (t: Throwable) {
            ToolResult(callId, "移动失败: ${t.message}", false)
        }
    }
}

/** 删除文件或目录（危险操作，需确认）。 */
class DeleteFileTool(private val approver: suspend (String) -> Boolean) : AgentTool {
    override val name = "delete_file"
    override val description = "删除工作区内的文件或目录（危险操作，需用户确认，不可恢复）"
    override fun buildDefinition() = toolDef(
        name, description,
        listOf(Triple("path", "string", "要删除的路径（相对工作区）")),
        listOf("path")
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val path = runCatching { JSONObject(argsJson).getString("path") }.getOrElse {
            return ToolResult(callId, "参数缺少 path", false)
        }
        if (!approver("删除: $path（不可恢复）")) return ToolResult(callId, "用户拒绝了删除操作", false)
        return try {
            val f = workspace.resolve(path)
            if (!f.exists()) return ToolResult(callId, "路径不存在: $path", false)
            val ok = if (f.isDirectory) f.deleteRecursively() else f.delete()
            if (ok) ToolResult(callId, "已删除: $path", true)
            else ToolResult(callId, "删除失败: $path", false)
        } catch (t: Throwable) {
            ToolResult(callId, "删除失败: ${t.message}", false)
        }
    }
}

/** 复制文件或目录。 */
class CopyFileTool : AgentTool {
    override val name = "copy_file"
    override val description = "复制工作区内的文件或目录到另一个位置"
    override fun buildDefinition() = toolDef(
        name, description,
        listOf(
            Triple("source", "string", "源路径（相对工作区）"),
            Triple("destination", "string", "目标路径（相对工作区）")
        ),
        listOf("source", "destination")
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val jo = try { JSONObject(argsJson) } catch (e: Exception) {
            return ToolResult(callId, "参数解析失败: ${e.message}", false)
        }
        val src = jo.optString("source")
        val dst = jo.optString("destination")
        if (src.isBlank() || dst.isBlank()) return ToolResult(callId, "需要 source 和 destination", false)
        return try {
            val srcFile = workspace.resolve(src)
            val dstFile = workspace.resolve(dst)
            if (!srcFile.exists()) return ToolResult(callId, "源不存在: $src", false)
            if (dstFile.exists()) return ToolResult(callId, "目标已存在: $dst", false)
            dstFile.parentFile?.mkdirs()
            if (srcFile.isDirectory) srcFile.copyRecursively(dstFile)
            else srcFile.copyTo(dstFile)
            ToolResult(callId, "已复制: $src → $dst", true)
        } catch (t: Throwable) {
            ToolResult(callId, "复制失败: ${t.message}", false)
        }
    }
}

/** 按名称在工作区内搜索文件。 */
class FindFilesTool : AgentTool {
    override val name = "find_files"
    override val description = "按文件名关键字在工作区内搜索（返回相对路径，最多 100 条）"
    override fun buildDefinition() = toolDef(
        name, description,
        listOf(
            Triple("keyword", "string", "文件名包含的关键字"),
            Triple("path", "string", "起始目录（默认工作区根目录）")
        ),
        listOf("keyword")
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val jo = try { JSONObject(argsJson) } catch (e: Exception) {
            return ToolResult(callId, "参数解析失败: ${e.message}", false)
        }
        val keyword = jo.optString("keyword")
        if (keyword.isBlank()) return ToolResult(callId, "参数缺少 keyword", false)
        return try {
            val start = workspace.resolve(jo.optString("path", "."))
            if (!start.exists()) return ToolResult(callId, "目录不存在: ${jo.optString("path", ".")}", false)
            val results = mutableListOf<String>()
            fun walk(dir: File, depth: Int) {
                if (results.size >= 100 || depth > 8) return
                dir.listFiles()?.forEach { f ->
                    if (results.size >= 100) return
                    if (f.isDirectory) walk(f, depth + 1)
                    else if (f.name.contains(keyword, ignoreCase = true)) {
                        results.add(workspace.rootFile().toRelativeString(f).replace('\\', '/'))
                    }
                }
            }
            walk(start, 0)
            if (results.isEmpty()) ToolResult(callId, "没有找到包含「$keyword」的文件", true)
            else ToolResult(callId, results.joinToString("\n"), true)
        } catch (t: Throwable) {
            ToolResult(callId, "搜索失败: ${t.message}", false)
        }
    }
}
