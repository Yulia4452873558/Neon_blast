package ru.yulia.neonblast.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicIntegerArray
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class Sound { Pick, Place, Drop, Clear, Blast, Perfect, GameOver, Revive, Click }

/**
 * Retro synth sound effects generated procedurally at startup (no audio assets),
 * rendered to WAV files in the cache and played through a SoundPool.
 */
class Sfx(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(10)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val ids = AtomicIntegerArray(Sound.entries.size)

    @Volatile
    var enabled = true

    init {
        val dir = File(context.cacheDir, "sfx").apply { mkdirs() }
        Thread {
            for (s in Sound.entries) {
                val file = File(dir, "v1_${s.name.lowercase()}.wav")
                if (!file.exists()) writeWav(file, render(s))
                ids.set(s.ordinal, pool.load(file.path, 1))
            }
        }.apply { isDaemon = true }.start()
    }

    fun play(sound: Sound, volume: Float = 1f, pitch: Float = 1f) {
        if (!enabled) return
        val id = ids.get(sound.ordinal)
        if (id != 0) pool.play(id, volume, volume, 1, 0, pitch.coerceIn(0.5f, 2f))
    }

    fun release() = pool.release()
}

// --- synthesis -----------------------------------------------------------------

private const val RATE = 44100
private const val TAU = (2 * PI).toFloat()

private fun note(semitonesFromA4: Int): Float = 440f * 2f.pow(semitonesFromA4 / 12f)

private fun buffer(seconds: Float) = FloatArray((seconds * RATE).toInt())

/** Adds a tone with a linear frequency sweep and an attack/exponential-decay envelope. */
private fun FloatArray.tone(
    start: Float, length: Float, f0: Float, f1: Float = f0,
    amp: Float = 0.5f, decay: Float = 8f, attack: Float = 0.004f,
    wave: (Float) -> Float = ::sine,
) {
    val s0 = (start * RATE).toInt()
    val n = (length * RATE).toInt()
    var phase = 0f
    for (i in 0 until n) {
        val idx = s0 + i
        if (idx >= size) break
        val t = i / RATE.toFloat()
        val f = f0 + (f1 - f0) * (i / n.toFloat())
        phase += f / RATE
        val env = (t / attack).coerceAtMost(1f) * exp(-t * decay)
        this[idx] += wave(phase) * env * amp
    }
}

private fun FloatArray.noise(start: Float, length: Float, amp: Float, decay: Float, smooth: Float = 0f, seed: Int = 1) {
    val r = Random(seed)
    val s0 = (start * RATE).toInt()
    var last = 0f
    for (i in 0 until (length * RATE).toInt()) {
        val idx = s0 + i
        if (idx >= size) break
        val t = i / RATE.toFloat()
        val k = (smooth * (1f - t / length)).coerceIn(0f, 0.98f) // low-pass that opens up over time
        last = last * k + (r.nextFloat() * 2f - 1f) * (1f - k)
        this[idx] += last * amp * exp(-t * decay) * (t / 0.01f).coerceAtMost(1f)
    }
}

private fun sine(p: Float) = sin(p * TAU)
private fun tri(p: Float): Float { val x = p - kotlin.math.floor(p); return 4f * kotlin.math.abs(x - 0.5f) - 1f }
private fun bright(p: Float) = sine(p) * 0.7f + sine(p * 2f) * 0.2f + sine(p * 3f) * 0.1f
private fun square(p: Float) = if (p - kotlin.math.floor(p) < 0.5f) 0.6f else -0.6f

private fun render(s: Sound): FloatArray = when (s) {
    Sound.Pick -> buffer(0.09f).apply { tone(0f, 0.09f, 700f, 1400f, 0.35f, 35f) }
    Sound.Click -> buffer(0.06f).apply { tone(0f, 0.06f, 1800f, 1200f, 0.3f, 70f) }
    Sound.Place -> buffer(0.2f).apply {
        tone(0f, 0.2f, 240f, 70f, 0.8f, 22f)
        noise(0f, 0.04f, 0.25f, 90f)
        tone(0f, 0.08f, 1200f, 900f, 0.12f, 50f, wave = ::tri)
    }
    Sound.Drop -> buffer(0.16f).apply { tone(0f, 0.16f, 330f, 200f, 0.25f, 20f, wave = ::tri) }
    Sound.Clear -> buffer(0.75f).apply {
        // bright major arpeggio C5-E5-G5-C6-E6
        listOf(3, 7, 10, 15, 19).forEachIndexed { i, n ->
            tone(i * 0.045f, 0.6f, note(n), amp = 0.28f, decay = 7f, wave = ::bright)
            tone(i * 0.045f, 0.3f, note(n + 12), amp = 0.06f, decay = 14f)
        }
    }
    Sound.Blast -> buffer(0.7f).apply {
        noise(0f, 0.7f, 0.55f, 5f, smooth = 0.95f, seed = 3)
        tone(0f, 0.5f, 110f, 38f, 0.9f, 7f)
    }
    Sound.Perfect -> buffer(1.6f).apply {
        listOf(3, 7, 10, 15, 19, 22, 27).forEachIndexed { i, n ->
            tone(i * 0.06f, 0.5f, note(n), amp = 0.22f, decay = 8f, wave = ::bright)
        }
        for (n in listOf(15, 19, 22, 27)) tone(0.45f, 1.15f, note(n), note(n) * 1.003f, 0.14f, 2.6f, 0.03f, ::bright)
        noise(0.45f, 1f, 0.08f, 4f, smooth = 0.6f, seed = 9)
    }
    Sound.GameOver -> buffer(1.5f).apply {
        listOf(-2, -6, -9).forEachIndexed { i, n -> tone(i * 0.22f, 0.4f, note(n), amp = 0.35f, decay = 6f, wave = ::tri) }
        tone(0.66f, 0.84f, note(-14), note(-15), 0.4f, 3f, 0.01f, ::square)
        tone(0.66f, 0.84f, note(-26), amp = 0.4f, decay = 3f)
    }
    Sound.Revive -> buffer(1f).apply {
        tone(0f, 0.8f, 180f, 1500f, 0.3f, 3f, 0.05f, ::square)
        listOf(15, 19, 22, 27).forEachIndexed { i, n -> tone(0.5f + i * 0.05f, 0.45f, note(n), amp = 0.2f, decay = 8f, wave = ::bright) }
        noise(0f, 0.8f, 0.12f, 3f, smooth = 0.9f, seed = 5)
    }
}

private fun writeWav(file: File, samples: FloatArray) {
    val peak = samples.maxOf { kotlin.math.abs(it) }.coerceAtLeast(1f)
    val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
    for (v in samples) data.putShort(((v / peak) * 0.9f * Short.MAX_VALUE).toInt().toShort())
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray()); putInt(36 + samples.size * 2); put("WAVE".toByteArray())
        put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
        putInt(RATE); putInt(RATE * 2); putShort(2); putShort(16)
        put("data".toByteArray()); putInt(samples.size * 2)
    }
    val tmp = File(file.path + ".tmp")
    FileOutputStream(tmp).use { it.write(header.array()); it.write(data.array()) }
    tmp.renameTo(file)
}
