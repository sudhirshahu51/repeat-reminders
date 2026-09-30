package com.sudhirshahu.loopalarm.ring

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Built-in beep tones, synthesised at runtime so the app ships no audio files.
 * Each tone renders one loopable cycle of 16-bit mono PCM.
 */
object BeepSynth {
    const val SAMPLE_RATE = 44_100

    data class Beep(val id: String, val title: String)

    val all = listOf(
        Beep("classic", "Classic beep"),
        Beep("digital", "Digital watch"),
        Beep("chime", "Chime"),
        Beep("pulse", "Pulse"),
        Beep("siren", "Siren"),
        Beep("soft", "Soft rise"),
    )

    fun title(id: String) = all.firstOrNull { it.id == id }?.title ?: all.first().title

    private class Buf {
        val data = ArrayList<Short>(SAMPLE_RATE * 3)
        private var phase = 0.0

        /** A tone sweeping from [f0] to [f1]; [decay] > 0 gives a bell-like fade. */
        fun tone(f0: Double, f1: Double, ms: Int, harmonics: Boolean = false, decay: Double = 0.0, fade: Boolean = false) {
            val n = SAMPLE_RATE * ms / 1000
            val edge = SAMPLE_RATE * 6 / 1000 // 6 ms attack/release to avoid clicks
            for (i in 0 until n) {
                val f = f0 + (f1 - f0) * i / n
                phase += 2 * PI * f / SAMPLE_RATE
                var s = sin(phase)
                if (harmonics) s = (s + 0.3 * sin(3 * phase) + 0.15 * sin(5 * phase)) / 1.45
                var env = min(1.0, min(i.toDouble() / edge, (n - i).toDouble() / edge))
                if (decay > 0) env *= exp(-decay * i / n)
                if (fade) env *= sin(PI * i / n)
                data += (s * env * 0.9 * Short.MAX_VALUE).toInt().toShort()
            }
        }

        fun silence(ms: Int) {
            repeat(SAMPLE_RATE * ms / 1000) { data += 0 }
            phase = 0.0
        }
    }

    fun render(id: String): ShortArray {
        val b = Buf()
        when (id) {
            "digital" -> {
                repeat(4) { b.tone(2600.0, 2600.0, 60, harmonics = true); b.silence(60) }
                b.silence(520)
            }
            "chime" -> {
                b.tone(659.25, 659.25, 350, decay = 3.0)
                b.tone(783.99, 783.99, 350, decay = 3.0)
                b.tone(987.77, 987.77, 700, decay = 4.0)
                b.silence(500)
            }
            "pulse" -> {
                b.tone(700.0, 700.0, 400, harmonics = true)
                b.silence(400)
            }
            "siren" -> {
                b.tone(600.0, 1400.0, 600)
                b.tone(1400.0, 600.0, 600)
            }
            "soft" -> {
                b.tone(523.25, 523.25, 800, fade = true)
                b.silence(300)
                b.tone(659.25, 659.25, 800, fade = true)
                b.silence(700)
            }
            else -> { // classic
                b.tone(1000.0, 1000.0, 150, harmonics = true)
                b.silence(100)
                b.tone(1000.0, 1000.0, 150, harmonics = true)
                b.silence(600)
            }
        }
        return b.data.toShortArray()
    }
}
