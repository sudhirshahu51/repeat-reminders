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
 * Reminder pictures. A picked photo is cropped and copied into app storage, upright and downscaled, so it keeps
 * working after the original is moved or deleted and stays small enough for a notification.
 * Alarms store only the file name. Call these off the main thread.
 */
object ImageStore {
    private const val MAX_SIDE = 1080

    private fun dir(context: Context) = File(context.filesDir, "images").apply { mkdirs() }

    /** Decodes the image at [uri] upright, no larger than [maxSide]; null if it cannot be read. Used by the crop screen. */
    fun decodeUpright(context: Context, uri: Uri, maxSide: Int = 2048): Bitmap? = runCatching {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide) }
        var bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val degrees = cr.openInputStream(uri)?.use { exifRotation(ExifInterface(it)) } ?: 0
        if (degrees != 0) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
        bmp
    }.getOrNull()

    /**
     * Saves the part of [source] inside [crop] (fractions 0..1 of width and height: left, top, right, bottom),
     * downscaled to fit [MAX_SIDE]. Returns the stored file name, or null on failure.
     */
    fun saveCropped(context: Context, source: Bitmap, crop: FloatArray = floatArrayOf(0f, 0f, 1f, 1f)): String? = runCatching {
        val l = (crop[0] * source.width).toInt().coerceIn(0, source.width - 1)
        val t = (crop[1] * source.height).toInt().coerceIn(0, source.height - 1)
        val r = (crop[2] * source.width).toInt().coerceIn(l + 1, source.width)
        val b = (crop[3] * source.height).toInt().coerceIn(t + 1, source.height)
        var bmp = Bitmap.createBitmap(source, l, t, r - l, b - t)
        val scale = MAX_SIDE.toFloat() / max(bmp.width, bmp.height)
        if (scale < 1f) bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt().coerceAtLeast(1), (bmp.height * scale).toInt().coerceAtLeast(1), true)

        val name = "${UUID.randomUUID()}.jpg"
        File(dir(context), name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
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
