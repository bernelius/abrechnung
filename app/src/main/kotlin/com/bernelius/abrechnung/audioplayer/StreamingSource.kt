package com.bernelius.abrechnung.audioplayer

import org.lwjgl.openal.AL10.AL_BUFFERS_PROCESSED
import org.lwjgl.openal.AL10.AL_BUFFERS_QUEUED
import org.lwjgl.openal.AL10.AL_FORMAT_MONO16
import org.lwjgl.openal.AL10.AL_FORMAT_STEREO16
import org.lwjgl.openal.AL10.AL_GAIN
import org.lwjgl.openal.AL10.alBufferData
import org.lwjgl.openal.AL10.alDeleteBuffers
import org.lwjgl.openal.AL10.alDeleteSources
import org.lwjgl.openal.AL10.alGenBuffers
import org.lwjgl.openal.AL10.alGenSources
import org.lwjgl.openal.AL10.alGetSourcef
import org.lwjgl.openal.AL10.alGetSourcei
import org.lwjgl.openal.AL10.alSourcePlay
import org.lwjgl.openal.AL10.alSourceQueueBuffers
import org.lwjgl.openal.AL10.alSourceStop
import org.lwjgl.openal.AL10.alSourceUnqueueBuffers
import org.lwjgl.openal.AL10.alSourcef
import org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET
import org.lwjgl.system.MemoryUtil

class StreamingSource {
    companion object {
        /** Total shorts in the PCM staging buffer. 8192 shorts ≈ 93ms at 44.1kHz stereo. */
        const val BUFFER_SIZE = 1024 * 8
    }

    var currentFile: String = ""
    private val pcm = MemoryUtil.memAllocShort(BUFFER_SIZE)
    private val bufferIds = IntArray(2)
    val sourceId: Int = alGenSources()

    /** Cumulative frames played from fully-consumed buffers. */
    var bufferOffset: Int = 0
        private set

    /** Sample offset from the last seek, so samplePosition reflects song position. */
    private var seekOffset: Int = 0

    var stream: OggStream? = null
        private set

    private var format: Int = 0

    init {
        alGenBuffers(bufferIds)
    }

    fun attach(
        ogg: OggStream,
        file: String,
    ) {
        if (file != currentFile) {
            stop()
            stream?.close()

            currentFile = file
            stream = ogg
            format = if (ogg.channels == 1) AL_FORMAT_MONO16 else AL_FORMAT_STEREO16
            bufferOffset = 0
            seekOffset = 0
        } else {
            ogg.close()
        }
    }

    /** Seek the underlying stream and record the offset so samplePosition stays correct. */
    fun seekTo(sampleIndex: Int) {
        stream?.seek(sampleIndex)
        seekOffset = sampleIndex
    }

    /** Fill one OpenAL buffer from the current OggStream. Returns shorts decoded, 0 = EOF. */
    private fun fillBuffer(bufferId: Int): Int {
        val ogg = stream ?: return 0
        var samples = 0

        while (samples < BUFFER_SIZE) {
            pcm.position(samples)
            val frames = ogg.getSamples(pcm)
            if (frames == 0) break
            samples += frames * ogg.channels
        }

        if (samples > 0) {
            pcm.position(0)
            pcm.limit(samples)
            alBufferData(bufferId, format, pcm, ogg.sampleRate)
            pcm.limit(BUFFER_SIZE)
        }

        return samples
    }

    /** Fill both buffers, queue them, and start playback. Returns false if no data. */
    fun play(): Boolean {
        val filled0 = fillBuffer(bufferIds[0])
        val filled1 = fillBuffer(bufferIds[1])
        if (filled0 == 0 && filled1 == 0) return false

        val count = if (filled1 > 0) 2 else 1
        alSourceQueueBuffers(sourceId, bufferIds.sliceArray(0 until count))
        alSourcePlay(sourceId)
        return true
    }

    /**
     * Pump the double-buffer stream. Call at ~5ms intervals.
     * If [loop] is true, rewinds on EOF. Returns false if EOF and not looping.
     */
    fun update(loop: Boolean): Boolean {
        val ogg = stream ?: return false
        val processed = alGetSourcei(sourceId, AL_BUFFERS_PROCESSED)

        for (i in 0 until processed) {
            bufferOffset += BUFFER_SIZE / ogg.channels
            val buf = alSourceUnqueueBuffers(sourceId)
            var filled = fillBuffer(buf)

            if (filled == 0) {
                if (loop) {
                    ogg.rewind()
                    // Don't reset bufferOffset/seekOffset — let samplePosition
                    // grow monotonically so absolute crossfade timing works.
                    filled = fillBuffer(buf)
                }
                if (filled == 0) return false
            }

            alSourceQueueBuffers(sourceId, buf)
        }

        // Stall recovery: if both buffers drained, restart
        if (processed == 2) {
            alSourcePlay(sourceId)
        }

        return true
    }

    /** Current playback position in PCM frames (samples per channel) within the song. */
    val samplePosition: Int
        get() = seekOffset + bufferOffset + alGetSourcei(sourceId, AL_SAMPLE_OFFSET)

    var gain: Float
        get() = alGetSourcef(sourceId, AL_GAIN)
        set(value) = alSourcef(sourceId, AL_GAIN, value)

    fun stop() {
        alSourceStop(sourceId)
        // Unqueue all buffers
        val queued = alGetSourcei(sourceId, AL_BUFFERS_QUEUED)
        for (i in 0 until queued) {
            alSourceUnqueueBuffers(sourceId)
        }
        bufferOffset = 0
    }

    fun destroy() {
        stop()
        stream?.close()
        stream = null
        alDeleteSources(sourceId)
        alDeleteBuffers(bufferIds)
        MemoryUtil.memFree(pcm)
    }
}
