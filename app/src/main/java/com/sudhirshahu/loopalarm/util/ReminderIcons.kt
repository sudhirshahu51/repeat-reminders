package com.sudhirshahu.loopalarm.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.text.BreakIterator

/** Emoji icons for reminders. Emoji render everywhere, including notifications, with no artwork to ship. */
object ReminderIcons {
    val presets = listOf(
        "⏰", "💧", "💊", "🎂", "📚", "🏋️", "🧘", "🚶",
        "🍽️", "☕", "💼", "🙏", "😴", "🐕", "🌱", "📞",
        "🧹", "🛒", "💰", "🎉", "❤️", "🎓", "🚗", "✈️",
    )

    /** First user-perceived character of [text], so "👍🏽abc" keeps the whole skin-toned emoji. */
    fun firstSymbol(text: String): String {
        val t = text.trim()
        if (t.isEmpty()) return ""
        val it = BreakIterator.getCharacterInstance().apply { setText(t) }
        return t.substring(0, it.next().takeIf { it > 0 } ?: t.length)
    }

    /** The emoji drawn centred on a transparent square, for a notification's large icon. */
    fun bitmap(emoji: String, sizePx: Int = 192): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sizePx * 0.78f; textAlign = Paint.Align.CENTER }
        val fm = paint.fontMetrics
        Canvas(bmp).drawText(emoji, sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2, paint)
        return bmp
    }
}
