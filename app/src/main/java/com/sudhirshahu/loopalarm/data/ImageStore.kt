package com.sudhirshahu.loopalarm.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlin.math.max

/**
 * Reminder pictures. A picked photo is copied into app storage, upright and downscaled, so it keeps working
 * after the original is moved or deleted and stays small enough for a notification.
 * Alarms store only the file name. Call these off the main thread.
 */
object ImageStore {
    private const val MAX_SIDE = 1080

    private fun dir(context: Context) = File(context.filesDir, "images").apply { mkdirs() }

    /** Copies the image at [uri]; returns the stored file name, or null if it cannot be read. */
    fun import(context: Context, uri: Uri): String? = runCatching {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE) }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val degrees = cr.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        if (degrees != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        val scale = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)

        val name = "${UUID.randomUUID()}.jpg"
        File(dir(context), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    /** Loads a stored picture no larger than [maxSide] on its longest side, or null if missing. */
    fun load(context: Context, name: String, maxSide: Int = MAX_SIDE): Bitmap? {
        if (name.isBlank()) return null
        val file = File(dir(context), name)
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) })
        }.getOrNull()
    }

    /** Deletes stored pictures that no alarm uses any more. */
    fun cleanup(context: Context, inUse: Set<String>) {
        dir(context).listFiles()?.filter { it.name !in inUse }?.forEach { it.delete() }
    }

    private fun sampleSize(w: Int, h: Int, maxSide: Int): Int {
        var s = 1
        while (max(w, h) / (s * 2) >= maxSide) s *= 2
        return s
    }

    private fun exifRotation(exif: ExifInterface): Int = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
}
