package org.fossify.gallery.mjpeg

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.CryptoConfig
import androidx.media3.decoder.VideoDecoderOutputBuffer
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.video.DecoderVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

@OptIn(UnstableApi::class)
@Suppress("TooManyFunctions")
class BitmapFactoryVideoRenderer(
    allowedJoiningTimeMs: Long,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
    maxDroppedFramesToNotify: Int
) : DecoderVideoRenderer(allowedJoiningTimeMs, eventHandler, eventListener, maxDroppedFramesToNotify) {

    companion object {
        private const val DECODER_BUFFER_COUNT = 4
        private val SUPPORTED_MIME_TYPES = setOf(
            MimeTypes.VIDEO_MJPEG,
            "video/mpng",
            "video/png",
            "video/x-png"
        )
    }

    private val rect = Rect()
    private var decoder: BitmapFactoryDecoder? = null
    private var hasRenderedFirstFrame = false

    override fun createDecoder(format: Format, cryptoConfig: CryptoConfig?): BitmapFactoryDecoder {
        val newDecoder = BitmapFactoryDecoder(DECODER_BUFFER_COUNT)
        decoder = newDecoder
        return newDecoder
    }

    override fun renderOutputBufferToSurface(outputBuffer: VideoDecoderOutputBuffer, surface: Surface) {
        hasRenderedFirstFrame = true
        if (outputBuffer is BitmapDecoderOutputBuffer) {
            val bitmap = outputBuffer.bitmap
            if (bitmap != null) {
                drawBitmapToSurface(bitmap, surface)
                decoder?.recycle(bitmap)
            }
        }
        outputBuffer.release()
    }

    override fun isReady(): Boolean {
        return if (hasRenderedFirstFrame && isSourceReady) true else super.isReady()
    }

    override fun onDisabled() {
        hasRenderedFirstFrame = false
        super.onDisabled()
    }

    override fun onReset() {
        hasRenderedFirstFrame = false
        super.onReset()
    }

    @Suppress("SwallowedException", "TooGenericExceptionCaught")
    private fun drawBitmapToSurface(bitmap: Bitmap, surface: Surface) {
        var canvas: Canvas? = null
        try {
            canvas = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    surface.lockHardwareCanvas()
                } else {
                    surface.lockCanvas(null)
                }
            } catch (e: Exception) {
                surface.lockCanvas(null)
            }
            if (canvas != null) {
                rect.set(0, 0, canvas.width, canvas.height)
                canvas.drawBitmap(bitmap, null, rect, null)
            }
        } catch (e: Exception) {
            // Surface may be invalid or released
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas)
                } catch (e: Exception) {
                    // Surface may be invalid or released
                }
            }
        }
    }

    override fun setDecoderOutputMode(outputMode: Int) {
        decoder?.setOutputMode(outputMode)
    }

    override fun getName(): String = "BitmapFactoryVideoRenderer"

    override fun supportsFormat(format: Format): Int {
        return if (SUPPORTED_MIME_TYPES.contains(format.sampleMimeType)) {
            RendererCapabilities.create(C.FORMAT_HANDLED)
        } else {
            RendererCapabilities.create(C.FORMAT_UNSUPPORTED_TYPE)
        }
    }
}
