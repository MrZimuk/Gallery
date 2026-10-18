package org.fossify.gallery.svg

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.drawable.PictureDrawable
import android.os.ParcelFileDescriptor
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.module.AppGlideModule
import com.caverock.androidsvg.SVG
import org.fossify.gallery.mjpeg.MjpegAssetFileDescriptorDecoder
import org.fossify.gallery.mjpeg.MjpegByteBufferDecoder
import org.fossify.gallery.mjpeg.MjpegInputStreamDecoder
import org.fossify.gallery.mjpeg.MjpegParcelFileDescriptorDecoder
import java.io.InputStream
import java.nio.ByteBuffer

@GlideModule
class SvgModule : AppGlideModule() {
    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        registry.register(SVG::class.java, PictureDrawable::class.java, SvgDrawableTranscoder())
            .append(InputStream::class.java, SVG::class.java, SvgDecoder())
            .prepend(
                AssetFileDescriptor::class.java,
                Bitmap::class.java,
                MjpegAssetFileDescriptorDecoder(glide.bitmapPool)
            )
            .prepend(
                ParcelFileDescriptor::class.java,
                Bitmap::class.java,
                MjpegParcelFileDescriptorDecoder(glide.bitmapPool)
            )
            .prepend(
                InputStream::class.java,
                Bitmap::class.java,
                MjpegInputStreamDecoder(glide.bitmapPool)
            )
            .prepend(
                ByteBuffer::class.java,
                Bitmap::class.java,
                MjpegByteBufferDecoder(glide.bitmapPool)
            )
    }

    override fun isManifestParsingEnabled() = false
}
