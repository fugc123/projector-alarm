package com.projectoralarm.sunrise

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Procedural morning birdsong synthesizer using Android AudioTrack.
 * Generates organic multi-syllable chirps, trills, and natural pauses
 * without requiring external audio asset files.
 */
class BirdSoundGenerator {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHUNK_SAMPLES = 2048
        private const val MAX_AMPLITUDE = 14000.0
    }

    @Volatile
    private var isPlaying = false

    @Volatile
    private var volumeFactor = 0.05f

    private var audioThread: Thread? = null

    /**
     * Updates playback gain factor smoothly during sunrise fade-in.
     * Expected range is 0.0f to 1.0f.
     */
    fun setVolumeFactor(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        // Smooth curve from gentle morning whisper (0.05) to full volume (1.0)
        volumeFactor = 0.05f + 0.95f * (clamped * clamped * (3f - 2f * clamped))
    }

    fun start(initialVolumeFactor: Float = 0.05f) {
        if (isPlaying) return
        setVolumeFactor(initialVolumeFactor)
        isPlaying = true

        audioThread = Thread({
            runSynthesisLoop()
        }, "BirdSoundGeneratorThread").apply {
            priority = Thread.NORM_PRIORITY + 1
            start()
        }
    }

    fun stop() {
        isPlaying = false
        val thread = audioThread
        audioThread = null
        if (thread != null && thread !== Thread.currentThread()) {
            try {
                thread.join(350)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }

    private fun runSynthesisLoop() {
        val minBufferBytes = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(CHUNK_SAMPLES * 2)

        val audioTrack = try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (_: Exception) {
            isPlaying = false
            return
        }

        val chunkBuffer = ShortArray(CHUNK_SAMPLES)
        val rng = Random(System.nanoTime())

        try {
            audioTrack.play()
            while (isPlaying) {
                val phrase = generatePhrase(rng)
                writeSamplesInChunks(audioTrack, phrase, chunkBuffer)
                if (!isPlaying) break

                // Natural silence pause between bird calls (shorter as chorus builds)
                val pauseMs = rng.nextInt(320, 1250)
                val silenceSamples = (SAMPLE_RATE * pauseMs) / 1000
                writeSilenceInChunks(audioTrack, silenceSamples, chunkBuffer)
            }
        } catch (_: Exception) {
            // Ignore playback interruption during teardown
        } finally {
            try {
                audioTrack.pause()
                audioTrack.flush()
                audioTrack.release()
            } catch (_: Exception) {
                // Safe cleanup
            }
        }
    }

    private fun generatePhrase(rng: Random): DoubleArray {
        val pattern = rng.nextInt(100)
        return when {
            pattern < 35 -> generateSingleChirpPhrase(rng)
            pattern < 75 -> generateMultiCallPhrase(rng, syllableCount = rng.nextInt(2, 4))
            else -> generateTrillPhrase(rng)
        }
    }

    private fun generateSingleChirpPhrase(rng: Random): DoubleArray {
        val durationMs = rng.nextInt(80, 145)
        val startFreq = rng.nextDouble(2300.0, 3600.0)
        val sweep = if (rng.nextBoolean()) {
            rng.nextDouble(450.0, 1100.0)
        } else {
            -rng.nextDouble(350.0, 900.0)
        }
        return synthesizeSyllable(
            durationMs = durationMs,
            startFreq = startFreq,
            endFreq = (startFreq + sweep).coerceIn(1900.0, 4900.0),
            vibratoHz = rng.nextDouble(6.0, 14.0),
            vibratoDepth = rng.nextDouble(20.0, 55.0),
            gain = rng.nextDouble(0.65, 0.95)
        )
    }

    private fun generateMultiCallPhrase(rng: Random, syllableCount: Int): DoubleArray {
        val baseFreq = rng.nextDouble(2400.0, 3900.0)
        val stepHz = rng.nextDouble(-250.0, 320.0)
        val parts = ArrayList<DoubleArray>(syllableCount * 2)
        var totalLength = 0

        for (i in 0 until syllableCount) {
            val f0 = (baseFreq + i * stepHz).coerceIn(2000.0, 4600.0)
            val f1 = (f0 + rng.nextDouble(220.0, 680.0)).coerceIn(2100.0, 4900.0)
            val syllable = synthesizeSyllable(
                durationMs = rng.nextInt(65, 115),
                startFreq = f0,
                endFreq = f1,
                vibratoHz = rng.nextDouble(8.0, 16.0),
                vibratoDepth = rng.nextDouble(15.0, 45.0),
                gain = rng.nextDouble(0.6, 0.9)
            )
            parts.add(syllable)
            totalLength += syllable.size

            if (i < syllableCount - 1) {
                val gapSamples = (SAMPLE_RATE * rng.nextInt(55, 105)) / 1000
                parts.add(DoubleArray(gapSamples))
                totalLength += gapSamples
            }
        }

        return concatArrays(parts, totalLength)
    }

    private fun generateTrillPhrase(rng: Random): DoubleArray {
        val durationMs = rng.nextInt(190, 320)
        val startFreq = rng.nextDouble(2600.0, 4100.0)
        val endFreq = (startFreq + rng.nextDouble(-300.0, 400.0)).coerceIn(2100.0, 4700.0)
        return synthesizeSyllable(
            durationMs = durationMs,
            startFreq = startFreq,
            endFreq = endFreq,
            vibratoHz = rng.nextDouble(22.0, 34.0),
            vibratoDepth = rng.nextDouble(140.0, 260.0),
            gain = rng.nextDouble(0.55, 0.85)
        )
    }

    private fun synthesizeSyllable(
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        vibratoHz: Double,
        vibratoDepth: Double,
        gain: Double
    ): DoubleArray {
        val sampleCount = (SAMPLE_RATE * durationMs) / 1000
        if (sampleCount <= 0) return DoubleArray(0)

        // Add subtle trailing echo tail for acoustic depth on TV/projector speakers
        val echoDelaySamples = (SAMPLE_RATE * 65) / 1000
        val out = DoubleArray(sampleCount + echoDelaySamples)
        var phase = 0.0

        for (i in 0 until sampleCount) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = i.toDouble() / sampleCount

            // Raised-cosine envelope shaped with quick attack and soft decay
            val attackFraction = 0.16
            val env = if (progress < attackFraction) {
                0.5 * (1.0 - cos(PI * progress / attackFraction))
            } else {
                val decayProgress = (progress - attackFraction) / (1.0 - attackFraction)
                0.5 * (1.0 + cos(PI * decayProgress))
            }

            // Smooth S-curve pitch sweep + subtle FM vibrato
            val easedPitch = progress * progress * (3.0 - 2.0 * progress)
            val instFreq = startFreq + (endFreq - startFreq) * easedPitch +
                vibratoDepth * sin(2.0 * PI * vibratoHz * t)

            phase += 2.0 * PI * instFreq / SAMPLE_RATE
            val fundamental = sin(phase)
            val secondHarmonic = 0.14 * sin(2.0 * phase)
            val sample = (fundamental + secondHarmonic) * env * gain

            out[i] += sample
            out[i + echoDelaySamples] += sample * 0.16
        }

        return out
    }

    private fun concatArrays(arrays: List<DoubleArray>, totalLength: Int): DoubleArray {
        val result = DoubleArray(totalLength)
        var offset = 0
        for (arr in arrays) {
            arr.copyInto(result, destinationOffset = offset)
            offset += arr.size
        }
        return result
    }

    private fun writeSamplesInChunks(
        audioTrack: AudioTrack,
        samples: DoubleArray,
        chunkBuffer: ShortArray
    ) {
        var offset = 0
        while (isPlaying && offset < samples.size) {
            val count = minOf(CHUNK_SAMPLES, samples.size - offset)
            val currentVol = volumeFactor.toDouble()
            for (i in 0 until count) {
                val scaled = (samples[offset + i] * MAX_AMPLITUDE * currentVol)
                    .coerceIn(-32767.0, 32767.0)
                chunkBuffer[i] = scaled.toInt().toShort()
            }
            val written = audioTrack.write(chunkBuffer, 0, count)
            if (written < 0) break
            offset += count
        }
    }

    private fun writeSilenceInChunks(
        audioTrack: AudioTrack,
        totalSilenceSamples: Int,
        chunkBuffer: ShortArray
    ) {
        chunkBuffer.fill(0)
        var remaining = totalSilenceSamples
        while (isPlaying && remaining > 0) {
            val count = minOf(CHUNK_SAMPLES, remaining)
            val written = audioTrack.write(chunkBuffer, 0, count)
            if (written < 0) break
            remaining -= count
        }
    }
}
