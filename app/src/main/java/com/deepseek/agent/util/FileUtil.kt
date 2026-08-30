package com.deepseek.agent.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileOutputStream

/** 附件文件类型 */
enum class FileKind {
    TEXT,   // 文本类：读内容拼进对话
    PDF,    // PDF：转成图片走视觉通道
    UNSUPPORTED
}

object FileUtil {

    private val textExtensions = setOf(
        "txt", "md", "csv", "log", "json", "xml", "yml", "yaml", "py", "java", "kt",
        "c", "cpp", "h", "hpp", "js", "ts", "html", "css", "sh", "bat", "ini",
        "conf", "sql", "tex", "toml", "properties", "gradle", "kts", "rst", "mm"
    )

    private const val MAX_TEXT_BYTES = 150_000L

    fun kindOf(path: String): FileKind {
        val ext = path.substringAfterLast('.', "").lowercase()
        return when {
            ext in textExtensions -> FileKind.TEXT
            ext == "pdf" -> FileKind.PDF
            else -> FileKind.UNSUPPORTED
        }
    }

    fun fileNameOf(path: String): String = path.substringAfterLast('/')

    /** 读取文本文件内容（超限返回 null，由调用方提示） */
    fun readText(path: String): String? {
        return runCatching {
            val f = File(path)
            if (f.length() > MAX_TEXT_BYTES) return null
            f.readText()
        }.getOrNull()
    }

    /**
     * PDF 前几页渲染并竖向拼接成一张 JPEG。
     * 返回图片路径；失败返回 null。
     */
    fun pdfToImage(context: Context, path: String, maxPages: Int = 3): String? {
        return runCatching {
            val file = File(path)
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val pages = minOf(maxPages, renderer.pageCount)
            if (pages <= 0) {
                renderer.close()
                pfd.close()
                return null
            }
            val bitmaps = mutableListOf<Bitmap>()
            for (i in 0 until pages) {
                val page = renderer.openPage(i)
                val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmaps.add(bmp)
                page.close()
            }
            renderer.close()
            pfd.close()
            val totalH = bitmaps.sumOf { it.height }
            val maxW = bitmaps.maxOf { it.width }
            val merged = Bitmap.createBitmap(maxW, totalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(merged)
            var y = 0f
            bitmaps.forEach { bmp ->
                canvas.drawBitmap(bmp, 0f, y, null)
                y += bmp.height
            }
            val out = File(context.cacheDir, "pdf_${System.currentTimeMillis()}.jpg")
            FileOutputStream(out).use { fos ->
                merged.compress(Bitmap.CompressFormat.JPEG, 82, fos)
            }
            out.absolutePath
        }.getOrNull()
    }
}

/** 待发送的文件附件：文本内容或 PDF 转图路径，二选一 */
data class PendingFile(
    val name: String,
    val textContent: String? = null,
    val imagePath: String? = null
)
