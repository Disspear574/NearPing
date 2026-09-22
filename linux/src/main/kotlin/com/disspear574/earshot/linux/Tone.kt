package com.disspear574.earshot.linux

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.sin

/**
 * Звук вызова синтезируется, а не лежит файлом: так он одинаков на всех системах
 * и не зависит от набора звуков рабочего стола.
 *
 * Играет в текущее устройство вывода — то есть в наушники, из-за которых всё
 * и затевалось. Системные уведомления здесь ни при чём, поэтому «не беспокоить»
 * ему не помеха.
 */
object Tone {
    private const val RATE = 44100f

    @Volatile private var line: SourceDataLine? = null

    fun play(urgent: Boolean) {
        stop()
        Thread({
            runCatching {
                val format = AudioFormat(RATE, 16, 1, true, false)
                val output = AudioSystem.getSourceDataLine(format)
                output.open(format)
                output.start()
                line = output
                val repeats = if (urgent) 6 else 2
                repeat(repeats) {
                    if (line !== output) return@repeat
                    val data = chirp(if (urgent) 880.0 else 660.0)
                    output.write(data, 0, data.size)
                    val silence = ByteArray((RATE * 0.12f).toInt() * 2)
                    output.write(silence, 0, silence.size)
                }
                output.drain()
                output.close()
                if (line === output) line = null
            }
        }, "earshot-tone").apply { isDaemon = true }.start()
    }

    fun stop() {
        line?.let { runCatching { it.stop(); it.close() } }
        line = null
    }

    private fun chirp(frequency: Double): ByteArray {
        val samples = (RATE * 0.18f).toInt()
        val bytes = ByteArray(samples * 2)
        for (i in 0 until samples) {
            // Плавный вход и выход, иначе на границе слышен щелчок.
            val progress = i.toDouble() / samples
            val envelope = sin(PI * progress)
            val value = (sin(2 * PI * frequency * i / RATE) * envelope * Short.MAX_VALUE * 0.6).toInt()
            bytes[i * 2] = (value and 0xFF).toByte()
            bytes[i * 2 + 1] = ((value shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
