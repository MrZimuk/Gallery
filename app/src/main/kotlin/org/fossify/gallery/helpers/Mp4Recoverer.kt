package org.fossify.gallery.helpers

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.Point
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.roundToInt

object Mp4Recoverer {
    private const val TAG = "Mp4Recoverer"
    private const val HEADER_SCAN_LIMIT = 65536

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun isMalformedMp4(path: String): Boolean {
        return try {
            val file = File(path)
            if (!file.exists() || file.length() < 16) return false
            FileInputStream(file).use { fis ->
                isMalformedMp4(fis.channel, 0L, file.length())
            }
        } catch (e: Exception) {
            Log.d(TAG, "isMalformedMp4(path) failed: ${e.message}")
            false
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun isMalformedMp4(pfd: ParcelFileDescriptor): Boolean {
        return try {
            val fis = FileInputStream(pfd.fileDescriptor)
            val channel = fis.channel
            val size = try { channel.size() } catch (_: Exception) { 0L }
            if (size < 16) return false
            val savedPos = channel.position()
            channel.position(0)
            val result = isMalformedMp4(channel, 0L, size)
            channel.position(savedPos)
            result
        } catch (e: Exception) {
            Log.d(TAG, "isMalformedMp4(pfd) failed: ${e.message}")
            false
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun isMalformedMp4(afd: AssetFileDescriptor): Boolean {
        return try {
            val fis = FileInputStream(afd.fileDescriptor)
            val channel = fis.channel
            val savedPos = channel.position()
            channel.position(afd.startOffset)
            val len = if (afd.length != AssetFileDescriptor.UNKNOWN_LENGTH) afd.length else channel.size() - afd.startOffset
            if (len < 16) return false
            val result = isMalformedMp4(channel, afd.startOffset, len)
            channel.position(savedPos)
            result
        } catch (e: Exception) {
            Log.d(TAG, "isMalformedMp4(afd) failed: ${e.message}")
            false
        }
    }

    private fun isMalformedMp4(channel: FileChannel, startOffset: Long, length: Long): Boolean {
        val scanLen = minOf(length, HEADER_SCAN_LIMIT.toLong()).toInt()
        val buffer = ByteBuffer.allocate(scanLen)
        channel.position(startOffset)
        while (buffer.hasRemaining()) {
            val r = channel.read(buffer)
            if (r <= 0) break
        }
        val bytes = buffer.array()
        val total = buffer.position()
        if (total < 16) return false

        // Check if file is MP4/ISOBMFF ('ftyp' at offset 4)
        if (bytes[4] != 'f'.code.toByte() || bytes[5] != 't'.code.toByte() ||
            bytes[6] != 'y'.code.toByte() || bytes[7] != 'p'.code.toByte()
        ) {
            return false
        }

        // Look for 'ctts' box in the scanned header
        for (i in 0 until total - 8) {
            if (bytes[i] == 'c'.code.toByte() && bytes[i + 1] == 't'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() && bytes[i + 3] == 's'.code.toByte()
            ) {
                val version = bytes[i + 4].toInt() and 0xFF
                val flags = ((bytes[i + 5].toInt() and 0xFF) shl 16) or
                    ((bytes[i + 6].toInt() and 0xFF) shl 8) or
                    (bytes[i + 7].toInt() and 0xFF)
                // ctts box with version 0 and non-zero flags breaks Android's SampleTable
                if (version == 0 && flags != 0) {
                    return true
                }
            }
        }
        return false
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun getVideoResolution(path: String): Point? {
        if (!isMalformedMp4(path)) return null
        var fis: FileInputStream? = null
        var ds: CttsFixingMediaDataSource? = null
        var mmr: MediaMetadataRetriever? = null
        return try {
            val file = File(path)
            fis = FileInputStream(file)
            ds = CttsFixingMediaDataSource(fis.channel, 0L, file.length())
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(ds)
            val w = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val h = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (w != null && h != null && w > 0 && h > 0) Point(w, h) else null
        } catch (e: Exception) {
            Log.d(TAG, "getVideoResolution failed: ${e.message}")
            null
        } finally {
            try { mmr?.release() } catch (_: Exception) {}
            try { ds?.close() } catch (_: Exception) {}
            try { fis?.close() } catch (_: Exception) {}
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun getVideoDuration(path: String): Int? {
        if (!isMalformedMp4(path)) return null
        var fis: FileInputStream? = null
        var ds: CttsFixingMediaDataSource? = null
        var mmr: MediaMetadataRetriever? = null
        return try {
            val file = File(path)
            fis = FileInputStream(file)
            ds = CttsFixingMediaDataSource(fis.channel, 0L, file.length())
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(ds)
            val durMs = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durMs != null && durMs > 0) {
                maxOf(1, (durMs / 1000.0).roundToInt())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d(TAG, "getVideoDuration failed: ${e.message}")
            null
        } finally {
            try { mmr?.release() } catch (_: Exception) {}
            try { ds?.close() } catch (_: Exception) {}
            try { fis?.close() } catch (_: Exception) {}
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun extractBitmap(pfd: ParcelFileDescriptor, targetWidth: Int, targetHeight: Int): Bitmap? {
        var ds: CttsFixingMediaDataSource? = null
        var mmr: MediaMetadataRetriever? = null
        var savedPos = -1L
        var channel: FileChannel? = null
        return try {
            val fis = FileInputStream(pfd.fileDescriptor)
            channel = fis.channel
            val size = try { channel.size() } catch (_: Exception) { 0L }
            savedPos = try { channel.position() } catch (_: Exception) { -1L }
            ds = CttsFixingMediaDataSource(channel, 0L, size)
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(ds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetWidth > 0 && targetHeight > 0) {
                mmr.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetWidth, targetHeight)
                    ?: mmr.getFrameAtTime(0L)
            } else {
                mmr.getFrameAtTime(0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractBitmap(pfd) failed", e)
            null
        } finally {
            try { mmr?.release() } catch (_: Exception) {}
            try { ds?.close() } catch (_: Exception) {}
            if (channel != null && savedPos >= 0L) {
                try { channel.position(savedPos) } catch (_: Exception) {}
            }
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    fun extractBitmap(afd: AssetFileDescriptor, targetWidth: Int, targetHeight: Int): Bitmap? {
        var ds: CttsFixingMediaDataSource? = null
        var mmr: MediaMetadataRetriever? = null
        var savedPos = -1L
        var channel: FileChannel? = null
        return try {
            val fis = FileInputStream(afd.fileDescriptor)
            channel = fis.channel
            savedPos = try { channel.position() } catch (_: Exception) { -1L }
            val len = if (afd.length != AssetFileDescriptor.UNKNOWN_LENGTH) afd.length else channel.size() - afd.startOffset
            ds = CttsFixingMediaDataSource(channel, afd.startOffset, len)
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(ds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 && targetWidth > 0 && targetHeight > 0) {
                mmr.getScaledFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, targetWidth, targetHeight)
                    ?: mmr.getFrameAtTime(0L)
            } else {
                mmr.getFrameAtTime(0L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "extractBitmap(afd) failed", e)
            null
        } finally {
            try { mmr?.release() } catch (_: Exception) {}
            try { ds?.close() } catch (_: Exception) {}
            if (channel != null && savedPos >= 0L) {
                try { channel.position(savedPos) } catch (_: Exception) {}
            }
        }
    }

    private class CttsFixingMediaDataSource(
        private val channel: FileChannel,
        private val startOffset: Long,
        private val length: Long
    ) : MediaDataSource() {
        private var cttsFlagsOffset: Long = -1L

        init {
            findCtts()
        }

        private fun findCtts() {
            try {
                val scanLen = minOf(length, HEADER_SCAN_LIMIT.toLong()).toInt()
                val buffer = ByteBuffer.allocate(scanLen)
                synchronized(channel) {
                    channel.position(startOffset)
                    while (buffer.hasRemaining()) {
                        val r = channel.read(buffer)
                        if (r <= 0) break
                    }
                }
                val bytes = buffer.array()
                val total = buffer.position()
                for (i in 0 until total - 8) {
                    if (bytes[i] == 'c'.code.toByte() && bytes[i + 1] == 't'.code.toByte() &&
                        bytes[i + 2] == 't'.code.toByte() && bytes[i + 3] == 's'.code.toByte()
                    ) {
                        cttsFlagsOffset = (i + 4).toLong()
                        break
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "findCtts error: ${e.message}")
            }
        }

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            synchronized(channel) {
                if (position >= length) {
                    return -1
                }
                val toRead = minOf(size.toLong(), length - position).toInt()
                channel.position(startOffset + position)
                val byteBuffer = ByteBuffer.wrap(buffer, offset, toRead)
                val bytesRead = channel.read(byteBuffer)
                if (bytesRead > 0 && cttsFlagsOffset != -1L) {
                    // Mask the 3 non-zero flag bytes of ctts (keeping version byte)
                    for (j in 1..3) {
                        val target = cttsFlagsOffset + j
                        if (target >= position && target < position + bytesRead) {
                            val bufIdx = (offset + (target - position)).toInt()
                            buffer[bufIdx] = 0
                        }
                    }
                }
                return bytesRead
            }
        }

        override fun getSize(): Long = length

        override fun close() {
            // Channel is managed by caller
        }
    }
}
