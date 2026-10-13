package com.disspear574.nearping.linux

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import java.io.BufferedInputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.PI
import kotlin.math.sin

/** Plays the normal tone (synthesized) and the urgent siren (shared WAV) on the current output device. */
object Tone {
    private const val RATE = 44100f

    @Volatile private var line: SourceDataLine? = null
    private val generation = AtomicLong()
    @Volatile private var urgentPlaying = false

    fun play(urgent: Boolean) {
        if (!urgent && urgentPlaying) return
        stop()
        val token = generation.get()
        urgentPlaying = urgent
        Thread({
            var opened: SourceDataLine? = null
            runCatching {
                val format = AudioFormat(RATE, 16, 1, true, false)
                val output = AudioSystem.getSourceDataLine(format)
                output.open(format)
                opened = output
                synchronized(this) {
                    if (generation.get() != token) { output.close(); return@runCatching }
                    output.start()
                    line = output
                }
                val siren = if (urgent) {
                    val resource = checkNotNull(javaClass.getResourceAsStream("/urgent-siren.wav"))
                    AudioSystem.getAudioInputStream(BufferedInputStream(resource)).use { it.readBytes() }
                } else null
                val repeats = if (urgent) 4 else 2
                repeat(repeats) {
                    if (line !== output) return@repeat
                    val data = siren ?: chirp(660.0)
                    output.write(data, 0, data.size)
                    if (!urgent) {
                        val silence = ByteArray((RATE * 0.12f).toInt() * 2)
                        output.write(silence, 0, silence.size)
                    }
                }
                output.drain()
                output.close()
                if (line === output) line = null
            }.onFailure { System.err.println("NearPing audio: ${it.message}") }
            runCatching { opened?.close() }
            synchronized(this) {
                if (generation.get() == token) { line = null; urgentPlaying = false }
            }
        }, "nearping-tone").apply { isDaemon = true }.start()
    }

    @Synchronized
    fun stop() {
        generation.incrementAndGet()
        urgentPlaying = false
        line?.let { runCatching { it.stop(); it.close() } }
        line = null
    }

    private fun chirp(frequency: Double): ByteArray {
        val samples = (RATE * 0.18f).toInt()
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            // Fade in and out, otherwise the edges click.
            val progress = i.toDouble() / samples
            val envelope = sin(PI * progress)
            val value = (sin(2 * PI * frequency * i / RATE) * envelope * Short.MAX_VALUE * 0.6).toInt()
            bytes[i * 2] = (value and 0xFF).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
