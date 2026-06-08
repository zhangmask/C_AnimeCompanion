package com.companion.chat.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal object WavEncoder {
    fun encodePcm16Mono(audio: RecordedAudio): ByteArray {
        val pcmBytes = audio.pcm16.size * Short.SIZE_BYTES
        val out = ByteArrayOutputStream(WAV_HEADER_BYTES + pcmBytes)
        out.writeAscii("RIFF")
        out.writeIntLe(36 + pcmBytes)
        out.writeAscii("WAVE")
        out.writeAscii("fmt ")
        out.writeIntLe(16)
        out.writeShortLe(1)
        out.writeShortLe(1)
        out.writeIntLe(audio.sampleRate)
        out.writeIntLe(audio.sampleRate * Short.SIZE_BYTES)
        out.writeShortLe(Short.SIZE_BYTES)
        out.writeShortLe(16)
        out.writeAscii("data")
        out.writeIntLe(pcmBytes)
        audio.pcm16.forEach { out.writeShortLe(it.toInt()) }
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeIntLe(value: Int) {
        write(ByteBuffer.allocate(Int.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())
    }

    private fun ByteArrayOutputStream.writeShortLe(value: Int) {
        write(ByteBuffer.allocate(Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())
    }

    private const val WAV_HEADER_BYTES = 44
}
