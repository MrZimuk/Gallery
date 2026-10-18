package org.fossify.gallery.mjpeg

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer

@Suppress("TooManyFunctions")
object MjpegThumbnailExtractor {
    private const val TAG = "MjpegThumbnailExtractor"
    private const val HEADER_SCAN_LIMIT = 65536
    private const val FRAME_SCAN_LIMIT = 8 * 1024 * 1024 // 8 MB max buffer for first frame
    private const val MIN_HEADER_SIZE = 16
    private const val BUFFER_CHUNK_SIZE = 16384
    private const val BYTE_MASK = 0xFF
    private const val MARKER_PREFIX = 0xFF
    private const val MARKER_EOI_BYTE = 0xD9
    private const val STUFFED_BYTE = 0x00

    private val EBML_HEADER = byteArrayOf(0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte())
    private val CODEC_ID_MJPEG_BYTES = "V_MJPEG".toByteArray(Charsets.US_ASCII)
    private val SOI_MARKER = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val SOS_MARKER = byteArrayOf(0xFF.toByte(), 0xDA.toByte())

    fun isMjpegHeader(bytes: ByteArray, length: Int): Boolean {
        if (length < MIN_HEADER_SIZE) return false
        for (i in EBML_HEADER.indices) {
            if (bytes[i] != EBML_HEADER[i]) return false
        }
        val scanLimit = minOf(length, HEADER_SCAN_LIMIT)
        return indexOfSequence(bytes, 0, scanLimit, CODEC_ID_MJPEG_BYTES) != -1
    }

    @Suppress("TooGenericExceptionCaught")
    fun isMjpeg(pfd: ParcelFileDescriptor): Boolean {
        return try {
            val channel = FileInputStream(pfd.fileDescriptor).channel
            val savedPos = channel.position()
            channel.position(0)
            val buffer = ByteBuffer.allocate(HEADER_SCAN_LIMIT)
            val read = channel.read(buffer)
            channel.position(savedPos)
            if (read > 0) isMjpegHeader(buffer.array(), read) else false
        } catch (e: Exception) {
            Log.d(TAG, "isMjpeg(pfd) failed: ${e.message}")
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun isMjpeg(afd: AssetFileDescriptor): Boolean {
        return try {
            val channel = FileInputStream(afd.fileDescriptor).channel
            val savedPos = channel.position()
            channel.position(afd.startOffset)
            val buffer = ByteBuffer.allocate(HEADER_SCAN_LIMIT)
            val read = channel.read(buffer)
            channel.position(savedPos)
            if (read > 0) isMjpegHeader(buffer.array(), read) else false
        } catch (e: Exception) {
            Log.d(TAG, "isMjpeg(afd) failed: ${e.message}")
            false
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun isMjpeg(stream: InputStream): Boolean {
        return try {
            if (!stream.markSupported()) return false
            stream.mark(HEADER_SCAN_LIMIT)
            val buffer = ByteArray(HEADER_SCAN_LIMIT)
            var totalRead = 0
            while (totalRead < HEADER_SCAN_LIMIT) {
                val r = stream.read(buffer, totalRead, HEADER_SCAN_LIMIT - totalRead)
                if (r <= 0) break
                totalRead += r
            }
            stream.reset()
            isMjpegHeader(buffer, totalRead)
        } catch (e: Exception) {
            Log.d(TAG, "isMjpeg(stream) failed: ${e.message}")
            false
        }
    }

    fun isMjpeg(byteBuffer: ByteBuffer): Boolean {
        if (byteBuffer.remaining() < MIN_HEADER_SIZE) return false
        val savedPos = byteBuffer.position()
        val len = minOf(byteBuffer.remaining(), HEADER_SCAN_LIMIT)
        val temp = ByteArray(len)
        byteBuffer.get(temp)
        byteBuffer.position(savedPos)
        return isMjpegHeader(temp, len)
    }

    @Suppress("TooGenericExceptionCaught")
    fun extractBitmap(pfd: ParcelFileDescriptor, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val channel = FileInputStream(pfd.fileDescriptor).channel
            val savedPos = channel.position()
            channel.position(0)
            val size = try { channel.size() } catch (_: Exception) { 0L }
            val readSize = if (size > 0) minOf(size, FRAME_SCAN_LIMIT.toLong()).toInt() else FRAME_SCAN_LIMIT
            val buffer = ByteBuffer.allocate(readSize)
            while (buffer.hasRemaining()) {
                val r = channel.read(buffer)
                if (r <= 0) break
            }
            val totalRead = buffer.position()
            channel.position(savedPos)
            val jpeg = findJpegBytes(buffer.array(), totalRead) ?: return null
            decodeJpeg(jpeg, targetWidth, targetHeight)
        } catch (e: Exception) {
            Log.e(TAG, "extractBitmap(pfd) failed", e)
            null
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun extractBitmap(afd: AssetFileDescriptor, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val channel = FileInputStream(afd.fileDescriptor).channel
            val savedPos = channel.position()
            channel.position(afd.startOffset)
            val maxLen = calculateAfdScanLength(afd, channel)
            val buffer = ByteBuffer.allocate(maxLen)
            while (buffer.hasRemaining()) {
                val r = channel.read(buffer)
                if (r <= 0) break
            }
            val totalRead = buffer.position()
            channel.position(savedPos)
            val jpeg = findJpegBytes(buffer.array(), totalRead) ?: return null
            decodeJpeg(jpeg, targetWidth, targetHeight)
        } catch (e: Exception) {
            Log.e(TAG, "extractBitmap(afd) failed", e)
            null
        }
    }

    private fun calculateAfdScanLength(afd: AssetFileDescriptor, channel: java.nio.channels.FileChannel): Int {
        return if (afd.length != AssetFileDescriptor.UNKNOWN_LENGTH) {
            minOf(afd.length, FRAME_SCAN_LIMIT.toLong()).toInt()
        } else {
            val size = try { channel.size() } catch (_: Exception) { 0L }
            val remaining = size - afd.startOffset
            if (remaining > 0) {
                minOf(remaining, FRAME_SCAN_LIMIT.toLong()).toInt()
            } else {
                FRAME_SCAN_LIMIT
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun extractBitmap(stream: InputStream, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val bytes = readStreamBytes(stream)
            val jpeg = findJpegBytes(bytes, bytes.size) ?: return null
            decodeJpeg(jpeg, targetWidth, targetHeight)
        } catch (e: Exception) {
            Log.e(TAG, "extractBitmap(stream) failed", e)
            null
        }
    }

    private fun readStreamBytes(stream: InputStream): ByteArray {
        val baos = ByteArrayOutputStream()
        val buf = ByteArray(BUFFER_CHUNK_SIZE)
        var totalRead = 0
        while (totalRead < FRAME_SCAN_LIMIT) {
            val r = stream.read(buf, 0, buf.size)
            if (r <= 0) break
            baos.write(buf, 0, r)
            totalRead += r
        }
        return baos.toByteArray()
    }

    @Suppress("TooGenericExceptionCaught")
    fun extractBitmap(byteBuffer: ByteBuffer, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val savedPos = byteBuffer.position()
            val len = minOf(byteBuffer.remaining(), FRAME_SCAN_LIMIT)
            val bytes = ByteArray(len)
            byteBuffer.get(bytes)
            byteBuffer.position(savedPos)
            val jpeg = findJpegBytes(bytes, len) ?: return null
            decodeJpeg(jpeg, targetWidth, targetHeight)
        } catch (e: Exception) {
            Log.e(TAG, "extractBitmap(byteBuffer) failed", e)
            null
        }
    }

    fun findJpegBytes(buf: ByteArray, length: Int): ByteArray? {
        val soi = indexOfSequence(buf, 0, length, SOI_MARKER)
        if (soi == -1) return null

        val sos = indexOfSequence(buf, soi, length, SOS_MARKER)
        if (sos == -1) return null

        val eoi = findEoiOffset(buf, sos + 2, length)
        if (eoi == -1) return null

        val result = ByteArray(eoi - soi)
        System.arraycopy(buf, soi, result, 0, result.size)
        return result
    }

    private fun findEoiOffset(buf: ByteArray, startOffset: Int, length: Int): Int {
        var i = startOffset
        while (i < length - 1) {
            val b0 = buf[i].toInt() and BYTE_MASK
            val b1 = buf[i + 1].toInt() and BYTE_MASK
            if (b0 == MARKER_PREFIX && b1 == MARKER_EOI_BYTE) {
                return i + 2
            } else if (b0 == MARKER_PREFIX && b1 == STUFFED_BYTE) {
                i += 2
            } else {
                i++
            }
        }
        return -1
    }

    private fun decodeJpeg(jpegBytes: ByteArray, targetWidth: Int, targetHeight: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
        if (opts.outWidth <= 0 || opts.outHeight <= 0) return null

        opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, targetWidth, targetHeight)
        opts.inJustDecodeBounds = false
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888
        return BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size, opts)
    }

    private fun calculateInSampleSize(srcWidth: Int, srcHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        if (reqWidth <= 0 || reqHeight <= 0) return 1
        var inSampleSize = 1
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            val halfHeight = srcHeight / 2
            val halfWidth = srcWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun indexOfSequence(src: ByteArray, start: Int, end: Int, target: ByteArray): Int {
        if (target.isEmpty() || end - start < target.size) return -1
        val maxStart = end - target.size
        for (i in start..maxStart) {
            var match = true
            for (j in target.indices) {
                if (src[i + j] != target[j]) {
                    match = false
                    break
                }
            }
            if (match) return i
        }
        return -1
    }
}
