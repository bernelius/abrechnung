package com.bernelius.abrechnung.audioplayer

import org.lwjgl.stb.STBVorbis.stb_vorbis_close
import org.lwjgl.stb.STBVorbis.stb_vorbis_get_info
import org.lwjgl.stb.STBVorbis.stb_vorbis_get_sample_offset
import org.lwjgl.stb.STBVorbis.stb_vorbis_get_samples_short_interleaved
import org.lwjgl.stb.STBVorbis.stb_vorbis_open_memory
import org.lwjgl.stb.STBVorbis.stb_vorbis_seek
import org.lwjgl.stb.STBVorbis.stb_vorbis_seek_start
import org.lwjgl.stb.STBVorbis.stb_vorbis_stream_length_in_samples
import org.lwjgl.stb.STBVorbisInfo
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ShortBuffer

class OggStream private constructor(
    private var handle: Long,
    val channels: Int,
    val sampleRate: Int,
    val totalSamples: Int,
    private val encodedData: ByteBuffer,
) {
    val durationSeconds: Float
        get() = totalSamples.toFloat() / sampleRate

    val sampleOffset: Int
        get() = stb_vorbis_get_sample_offset(handle)

    /**
     * Decode interleaved PCM into [pcm]. Returns number of frames decoded (0 = EOF).
     * Total shorts written = return value * channels.
     */
    fun getSamples(pcm: ShortBuffer): Int = stb_vorbis_get_samples_short_interleaved(handle, channels, pcm)

    fun seek(sampleIndex: Int) {
        stb_vorbis_seek(handle, sampleIndex)
    }

    fun rewind() {
        stb_vorbis_seek_start(handle)
    }

    fun close() {
        if (handle == 0L) return
        stb_vorbis_close(handle)
        MemoryUtil.memFree(encodedData)
        handle = 0
    }

    companion object {
        /**
         * Open an OGG file from classpath resources.
         * The entire file is loaded into a direct ByteBuffer for stb_vorbis.
         */
        fun open(resourcePath: String): OggStream {
            val bytes =
                OggStream::class.java.classLoader
                    .getResourceAsStream(resourcePath)
                    ?.readAllBytes()
                    ?: throw IllegalArgumentException("Resource not found: $resourcePath")

            val encodedData = MemoryUtil.memAlloc(bytes.size).put(bytes).flip() as ByteBuffer

            MemoryStack.stackPush().use { stack ->
                val error = stack.mallocInt(1)
                val handle = stb_vorbis_open_memory(encodedData, error, null)
                if (handle == 0L) {
                    MemoryUtil.memFree(encodedData)
                    throw RuntimeException("Failed to open Vorbis stream: error ${error.get(0)}")
                }

                val info = STBVorbisInfo.malloc(stack)
                stb_vorbis_get_info(handle, info)

                return OggStream(
                    handle = handle,
                    channels = info.channels(),
                    sampleRate = info.sample_rate(),
                    totalSamples = stb_vorbis_stream_length_in_samples(handle),
                    encodedData = encodedData,
                )
            }
        }
    }
}
