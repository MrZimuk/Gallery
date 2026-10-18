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

    private val bitmapQueue = ArrayBlockingQueue<Bitmap>(buffers)
    @Volatile
    private var outputMode: Int = C.VIDEO_OUTPUT_MODE_SURFACE_YUV

    fun recycle(bitmap: Bitmap) {
        bitmapQueue.offer(bitmap)
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
        return DecoderException(error)
    }

    fun setOutputMode(outputMode: Int) {
        this.outputMode = outputMode
    }

    override fun decode(
        inputBuffer: DecoderInputBuffer,
        outputBuffer: BitmapDecoderOutputBuffer,
        reset: Boolean
    ): DecoderException? {
        outputBuffer.init(inputBuffer.timeUs, outputMode, null)
        val inputData = Util.castNonNull(inputBuffer.data)
        val opts = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
            inBitmap = bitmapQueue.poll()
            inJustDecodeBounds = outputBuffer.shouldBeSkipped || outputMode != C.VIDEO_OUTPUT_MODE_SURFACE_YUV
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

        try {
            val bitmap = BitmapFactory.decodeByteArray(bytes, offset, length, opts)
            if (bitmap == null || opts.inJustDecodeBounds) {
                outputBuffer.shouldBeSkipped = true
            } else {
                outputBuffer.bitmap = bitmap
                outputBuffer.format = inputBuffer.format
                outputBuffer.width = bitmap.width
                outputBuffer.height = bitmap.height
            }
        } catch (e: IllegalArgumentException) {
            return DecoderException(e)
        } catch (e: OutOfMemoryError) {
            return DecoderException("Failed to decode frame due to OOM", e)
        }
        return null
    }

    override fun getName(): String = "BitmapFactoryDecoder"
}
