package com.deepseek.agent.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlin.math.max

/** 选图后压缩保存到应用私有目录，返回文件路径（失败返回 null）。 */
object ImageUtil {

    fun compressAndSave(context: Context, uri: Uri, maxDim: Int = 1280, quality: Int = 82): String? {
        return try {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

            var sample = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / sample > maxDim) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            val dir = File(context.filesDir, "images").apply { mkdirs() }
            val out = File(dir, "img_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            bmp.recycle()
            out.absolutePath
        } catch (t: Throwable) {
            null
        }
    }
}
