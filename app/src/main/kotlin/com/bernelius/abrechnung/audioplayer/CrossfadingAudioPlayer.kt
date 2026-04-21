package com.bernelius.abrechnung.audioplayer

import java.lang.UnsatisfiedLinkError
import org.lwjgl.openal.AL
import org.lwjgl.openal.ALC
import org.lwjgl.openal.ALC10.alcCloseDevice
import org.lwjgl.openal.ALC10.alcCreateContext
import org.lwjgl.openal.ALC10.alcDestroyContext
import org.lwjgl.openal.ALC10.alcMakeContextCurrent
import org.lwjgl.openal.ALC10.alcOpenDevice
import java.nio.ByteBuffer
import java.nio.IntBuffer
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CrossfadingAudioPlayer {
    companion object {
        /** Duration of a normal crossfade in seconds. Easily tweakable. */
        const val CROSSFADE_DURATION = 1.0f

        /** Duration of the emergency mini-crossfade when swapping during an active crossfade. */
        const val MINI_CROSSFADE_DURATION = 0.05f

        /** How many equal divisions of the song duration form crossfade boundaries. */
        const val BOUNDARY_DIVISIONS = 8

        /** Audio thread poll interval in milliseconds. */
        const val POLL_INTERVAL_MS = 5L
    }

    private enum class State {
        PLAYING,
        WAITING_FOR_CROSSFADE,
        CROSSFADING,
    }

    // --- OpenAL device/context (owned by audio thread) ---
    private var device: Long = 0
    private var context: Long = 0

    // --- Two streaming sources ---
    private lateinit var sourceA: StreamingSource
    private lateinit var sourceB: StreamingSource

    // --- State ---
    private var state = State.PLAYING
    private var crossfadeStartSample = 0
    private var crossfadeDurationSamples = 0
    private var currentSampleRate = 44100

    // For mid-crossfade swap (mini-crossfade)
    private var miniCrossfadeActive = false
    private var miniCrossfadeStartSample = 0
    private var miniCrossfadeDurationSamples = 0

    // --- Thread-safe communication ---
    private val pendingEnqueue = AtomicReference<String?>(null)

    @Volatile
    private var running = false

    private var audioThread: Thread? = null

    /**
     * Play a song. If nothing is playing, starts immediately.
     * If a song is already playing, crossfades into it at the next 1/8th boundary.
     * Call from any thread.
     */
    fun play(resourcePath: String) {
        pendingEnqueue.set(resourcePath)
    }

    /**
     * Start the audio thread. Must be called once before play/enqueue.
     */
    fun start() {
        if (running) return
        running = true
        audioThread =
            Thread({
                try {
                    initOpenAL()
                    try {
                        audioLoop()
                    } finally {
                        destroyOpenAL()
                    }
                } catch (e: Throwable) {
                    System.err.println("Audio disabled: ${e.message}")
                }
            }, "AudioPlayer").apply {
                isDaemon = true
                start()
            }
    }

    /**
     * Stop playback and shut down the audio thread.
     */
    fun stop() {
        running = false
        audioThread?.join(1000)
        audioThread = null
    }

    // ========== Audio Thread ==========

    private fun initOpenAL() {
        device = alcOpenDevice(null as ByteBuffer?)
        if (device == 0L) throw RuntimeException("Failed to open OpenAL device")
        context = alcCreateContext(device, null as IntBuffer?)
        if (context == 0L) throw RuntimeException("Failed to create OpenAL context")
        alcMakeContextCurrent(context)

        val alcCaps = ALC.createCapabilities(device)
        AL.createCapabilities(alcCaps)

        sourceA = StreamingSource()
        sourceB = StreamingSource()
    }

    private fun destroyOpenAL() {
        sourceA.destroy()
        sourceB.destroy()
        alcMakeContextCurrent(0)
        alcDestroyContext(context)
        alcCloseDevice(device)
    }

    private fun audioLoop() {
        while (running) {
            pendingEnqueue.getAndSet(null)?.let { path ->
                handleEnqueue(path)
            }

            updateStreaming()
            updateCrossfade()

            Thread.sleep(POLL_INTERVAL_MS)
        }

        sourceA.stop()
        sourceB.stop()
    }

    private fun handleEnqueue(path: String) {
        // Guards
        if (sourceA.currentFile == path && state == State.PLAYING) return
        if (sourceB.currentFile == path && (state == State.CROSSFADING || state == State.WAITING_FOR_CROSSFADE)) return

        // If nothing is playing, we handle it and exit
        if (sourceA.stream == null) {
            val ogg = OggStream.open(path)
            currentSampleRate = ogg.sampleRate
            sourceA.attach(ogg, path)
            sourceA.gain = 1.0f
            sourceA.play()
            state = State.PLAYING
            return
        }

        // Now we know we ARE playing something else, so we need a new stream for sourceB
        val ogg = OggStream.open(path)

        when (state) {
            State.PLAYING, State.WAITING_FOR_CROSSFADE -> {
                sourceB.attach(ogg, path)
                computeCrossfadeTiming(sourceA, CROSSFADE_DURATION)
                state = State.WAITING_FOR_CROSSFADE
            }

            State.CROSSFADING -> {
                // TODO: we just ignore this for now. would need a sourceC to be able to swap out b while in the middle of a crossfade
                //
                // val ogg = OggStream.open(path)
                // val currentPos = sourceA.samplePosition % (sourceA.stream?.totalSamples ?: 1)
                //
                // sourceB.stop()
                // sourceB.attach(ogg, path)
                // sourceB.seekTo(currentPos)
                // sourceB.play()
                //
                // miniCrossfadeActive = true
                // miniCrossfadeStartSample = sourceA.samplePosition
                // miniCrossfadeDurationSamples = (MINI_CROSSFADE_DURATION * currentSampleRate).toInt()
            }
        }
    }

    private fun computeCrossfadeTiming(
        activeSource: StreamingSource,
        duration: Float,
    ) {
        val ogg = activeSource.stream ?: return
        currentSampleRate = ogg.sampleRate
        val totalSamples = ogg.totalSamples
        val interval = totalSamples / BOUNDARY_DIVISIONS
        val crossfadeSamples = (duration * currentSampleRate).toInt()

        val currentAbsolute = activeSource.samplePosition
        val posInSong = currentAbsolute % totalSamples

        // Find the next 1/8th boundary (in song-space) that allows a full crossfade
        var boundaryInSong = ((posInSong / interval) + 1) * interval
        while (boundaryInSong - crossfadeSamples <= posInSong) {
            boundaryInSong += interval
        }

        // Convert to absolute space: how many samples from now until crossfade starts
        crossfadeStartSample = currentAbsolute + (boundaryInSong - crossfadeSamples - posInSong)
        crossfadeDurationSamples = crossfadeSamples
    }

    private fun updateStreaming() {
        if (sourceA.stream != null) {
            sourceA.update(loop = true)
        }

        if (sourceB.stream != null && state == State.CROSSFADING) {
            sourceB.update(loop = true)
        }
    }

    private fun updateCrossfade() {
        when (state) {
            State.PLAYING -> { // nothing
            }

            State.WAITING_FOR_CROSSFADE -> {
                if (sourceA.samplePosition >= crossfadeStartSample) {
                    val totalSamples = sourceA.stream?.totalSamples ?: 1
                    val songPos = sourceA.samplePosition % totalSamples

                    state = State.CROSSFADING

                    sourceB.seekTo(songPos)
                    sourceB.gain = 0.0f
                    sourceB.play()
                }
            }

            State.CROSSFADING -> {
                val currentPos = sourceA.samplePosition
                val progress =
                    ((currentPos - crossfadeStartSample).toFloat() / crossfadeDurationSamples)
                        .coerceIn(0.0f, 1.0f)
                val angle = progress * (PI.toFloat() / 2.0f) // 0 to 90 degrees (π/2 radians)

                val gainOut = cos(angle)
                var gainIn = sin(angle)

                if (miniCrossfadeActive) {
                    val miniProgress =
                        ((currentPos - miniCrossfadeStartSample).toFloat() / miniCrossfadeDurationSamples)
                            .coerceIn(0.0f, 1.0f)
                    if (miniProgress >= 1.0f) {
                        miniCrossfadeActive = false
                    }
                    gainIn *= miniProgress
                }

                sourceA.gain = gainOut
                sourceB.gain = gainIn

                if (progress >= 1.0f) {
                    finishCrossfade()
                }
            }
        }
    }

    private fun finishCrossfade() {
        sourceA.stop()

        val temp = sourceA
        sourceA = sourceB
        sourceB = temp
        sourceA.gain = 1.0f

        state = State.PLAYING
        miniCrossfadeActive = false
    }
}
