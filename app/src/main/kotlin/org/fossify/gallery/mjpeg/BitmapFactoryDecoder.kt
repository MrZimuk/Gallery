package org.fossify.gallery.mjpeg

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.decoder.DecoderException
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.decoder.SimpleDecoder
import java.util.concurrent.ArrayBlockingQueue

@OptIn(UnstableApi::class)
class BitmapFactoryDecoder(buffers: Int) :
    @Suppress("UNCHECKED_CAST")
    SimpleDecoder<DecoderInputBuffer, BitmapDecoderOutputBuffer, DecoderException>(
        arrayOfNulls<DecoderInputBuffer>(buffers) as Array<DecoderInputBuffer>,
        arrayOfNulls<BitmapDecoderOutputBuffer>(buffers) as Array<BitmapDecoderOutputBuffer>
    ) {

    companion object {
        private const val TAG = "BitmapFactoryDecoder"
    }

    private val bitmapQueue = ArrayBlockingQueue<Bitmap>(buffers)
    @Volatile
    private var outputMode: Int = C.VIDEO_OUTPUT_MODE_SURFACE_YUV

    fun recycle(bitmap: Bitmap) {
        if (!bitmap.isRecycled && bitmap.isMutable) {
            bitmapQueue.offer(bitmap)
        }
    }

    override fun createInputBuffer(): DecoderInputBuffer {
        return DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL)
    }

    override fun createOutputBuffer(): BitmapDecoderOutputBuffer {
        return BitmapDecoderOutputBuffer { outputBuffer ->
            if (outputBuffer is BitmapDecoderOutputBuffer) {
                releaseOutputBuffer(outputBuffer)
            }
        }
    }

    override fun createUnexpectedDecodeException(error: Throwable): DecoderException {
        android.util.Log.e(TAG, "Unexpected decode exception", error)
        return DecoderException(error)
    }

    fun setOutputMode(outputMode: Int) {
        this.outputMode = outputMode
    }

    @Suppress("TooGenericExceptionCaught")
    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: BitmapDecoderOutputBuffer,
        reset: Boolean
    ): DecoderException? {
        outputBuffer.init(inputBuffer.timeUs, C.VIDEO_OUTPUT_MODE_SURFACE_YUV, null)
        val inputData = Util.castNonNull(inputBuffer.data)
        val candidateBitmap = bitmapQueue.poll()
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inBitmap = candidateBitmap
            inJustDecodeBounds = outputBuffer.shouldBeSkipped
        }

        val bytes: ByteArray
        val offset: Int
        val length: Int
        if (inputData.hasArray()) {
            bytes = inputData.array()
            offset = inputData.arrayOffset() + inputData.position()
            length = inputData.remaining()
        } else {
            bytes = ByteArray(inputData.remaining())
            val pos = inputData.position()
            inputData.get(bytes)
            inputData.position(pos)
            offset = 0
            length = bytes.size
        }

        if (length <= 0) {
            outputBuffer.shouldBeSkipped = true
            return null
        }

        var bitmap: Bitmap? = null
        try {
            bitmap = BitmapFactory.decodeByteArray(bytes, offset, length, opts)
            if (bitmap == null && opts.inBitmap != null) {
                // inBitmap reuse can return null on some Android versions without throwing
                opts.inBitmap = null
                bitmap = BitmapFactory.decodeByteArray(bytes, offset, length, opts)
            }
        } catch (e: IllegalArgumentException) {
            if (opts.inBitmap != null) {
                android.util.Log.w(TAG, "inBitmap reuse failed, retrying with new allocation", e)
                opts.inBitmap = null
                try {
                    bitmap = BitmapFactory.decodeByteArray(bytes, offset, length, opts)
                } catch (e2: Throwable) {
                    android.util.Log.w(TAG, "Fallback decode failed", e2)
                }
            } else {
                android.util.Log.w(TAG, "decodeByteArray failed with IAE", e)
            }
        } catch (e: OutOfMemoryError) {
            android.util.Log.e(TAG, "OOM decoding frame", e)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Unexpected error decoding frame", e)
        }

        if (bitmap == null || opts.inJustDecodeBounds) {
            outputBuffer.shouldBeSkipped = true
        } else {
            outputBuffer.bitmap = bitmap
            outputBuffer.format = inputBuffer.format
            outputBuffer.width = bitmap.width
            outputBuffer.height = bitmap.height
        }
        return null
    }

    override fun getName(): String = "BitmapFactoryDecoder"
}
