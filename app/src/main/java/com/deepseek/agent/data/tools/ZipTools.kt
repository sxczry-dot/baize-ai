package com.deepseek.agent.data.tools

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 把工作区内的文件/目录打包成 zip。 */
class ZipTool : AgentTool {
    override val name = "zip_files"
    override val description = "把工作区内的文件或目录打包成 zip 压缩包"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("source") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("要打包的路径（相对工作区）"))
                    }
                    putJsonObject("destination") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("zip 文件输出路径（相对工作区，以 .zip 结尾）"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("source")); add(JsonPrimitive("destination")) }
            }
        )
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

            val base = srcFile.parentFile ?: srcFile
            ZipOutputStream(FileOutputStream(dstFile)).use { zos ->
                fun add(file: File) {
                    val rel = base.toRelativeString(file).replace('\\', '/')
                    if (file.isDirectory) {
                        zos.putNextEntry(ZipEntry("$rel/"))
                        zos.closeEntry()
                        file.listFiles()?.forEach { add(it) }
                    } else {
                        zos.putNextEntry(ZipEntry(rel))
                        FileInputStream(file).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
                add(srcFile)
            }
            ToolResult(callId, "已打包: $dst", true)
        } catch (t: Throwable) {
            ToolResult(callId, "打包失败: ${t.message}", false)
        }
    }
}

/** 解压 zip 到工作区（防 zip-slip 路径穿越）。 */
class UnzipTool(private val approver: suspend (String) -> Boolean) : AgentTool {
    override val name = "unzip_file"
    override val description = "解压工作区内的 zip 文件到指定目录（危险操作，可能覆盖文件，需用户确认）"
    override fun buildDefinition() = ToolDefinition(
        function = ToolFunction(
            name = name,
            description = description,
            parameters = buildJsonObject {
                put("type", JsonPrimitive("object"))
                putJsonObject("properties") {
                    putJsonObject("source") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("zip 文件路径（相对工作区）"))
                    }
                    putJsonObject("destination") {
                        put("type", JsonPrimitive("string"))
                        put("description", JsonPrimitive("解压目标目录（相对工作区）"))
                    }
                }
                putJsonArray("required") { add(JsonPrimitive("source")); add(JsonPrimitive("destination")) }
            }
        )
    )

    override suspend fun execute(callId: String, argsJson: String, workspace: Workspace): ToolResult {
        val jo = try { JSONObject(argsJson) } catch (e: Exception) {
            return ToolResult(callId, "参数解析失败: ${e.message}", false)
        }
        val src = jo.optString("source")
        val dst = jo.optString("destination")
        if (src.isBlank() || dst.isBlank()) return ToolResult(callId, "需要 source 和 destination", false)

        if (!approver("解压: $src → $dst（可能覆盖同名文件）")) {
            return ToolResult(callId, "用户拒绝了解压操作", false)
        }
        return try {
            val srcFile = workspace.resolve(src)
            val dstDir = workspace.resolve(dst)
            if (!srcFile.exists() || !srcFile.isFile) return ToolResult(callId, "zip 文件不存在: $src", false)
            dstDir.mkdirs()

            var count = 0
            ZipInputStream(FileInputStream(srcFile)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    val outFile = File(dstDir, entry.name)
                    // 防 zip-slip：解压目标必须在 dstDir 内
                    if (!outFile.canonicalPath.startsWith(dstDir.canonicalPath + File.separator)) {
                        return ToolResult(callId, "压缩包内含非法路径（疑似恶意文件）: ${entry.name}", false)
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        FileOutputStream(outFile).use { zis.copyTo(it) }
                        count++
                    }
                    zis.closeEntry()
                }
            }
            ToolResult(callId, "已解压 $count 个文件到: $dst", true)
        } catch (t: Throwable) {
            ToolResult(callId, "解压失败: ${t.message}", false)
        }
    }
}
