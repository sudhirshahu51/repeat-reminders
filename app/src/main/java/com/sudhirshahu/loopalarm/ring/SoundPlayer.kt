package com.sudhirshahu.loopalarm.ring

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import com.sudhirshahu.loopalarm.data.SoundType

/** Plays an alarm sound on the alarm stream, looping, with adjustable volume. */
class SoundPlayer(private val context: Context) {
    private var track: AudioTrack? = null
    private var media: MediaPlayer? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun start(type: SoundType, value: String, volume: Float) {
        stop()
        when (type) {
            SoundType.SILENT -> Unit
            SoundType.BEEP -> startBeep(value, volume)
            SoundType.RINGTONE, SoundType.FILE -> {
                val ok = startUri(Uri.parse(value), volume) ||
                    startUri(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), volume)
                if (!ok) startBeep("classic", volume)
            }
        }
    }

    private fun startBeep(id: String, volume: Float) {
        val pcm = BeepSynth.render(id)
        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(BeepSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setLoopPoints(0, pcm.size, -1)
        t.setVolume(volume)
        t.play()
        track = t
    }

    private fun startUri(uri: Uri?, volume: Float): Boolean {
        if (uri == null) return false
        return try {
            media = MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(context, uri)
                isLooping = true
                setVolume(volume, volume)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.w("SoundPlayer", "Cannot play $uri", e)
            media?.release()
            media = null
            false
        }
    }

    fun setVolume(v: Float) {
        val c = v.coerceIn(0f, 1f)
        track?.setVolume(c)
        media?.setVolume(c, c)
    }

    fun stop() {
        track?.runCatching { stop(); release() }
        track = null
        media?.runCatching { stop(); release() }
        media = null
    }
}
