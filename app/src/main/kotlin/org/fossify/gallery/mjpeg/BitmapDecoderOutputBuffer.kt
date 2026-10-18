package org.fossify.gallery.mjpeg

import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.VideoDecoderOutputBuffer

@OptIn(UnstableApi::class)
class BitmapDecoderOutputBuffer(owner: Owner<VideoDecoderOutputBuffer>) : VideoDecoderOutputBuffer(owner) {
    var bitmap: Bitmap? = null

    override fun release() {
        super.release()
        bitmap = null
    }
}
