package org.fossify.gallery.mjpeg

import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.ResourceDecoder
import com.bumptech.glide.load.engine.Resource
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapResource
import java.io.InputStream
import java.nio.ByteBuffer

import org.fossify.gallery.helpers.Mp4Recoverer

class MjpegParcelFileDescriptorDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<ParcelFileDescriptor, Bitmap> {

    override fun handles(source: ParcelFileDescriptor, options: Options): Boolean {
        return MjpegThumbnailExtractor.isMjpeg(source) || Mp4Recoverer.isMalformedMp4(source)
    }

    override fun decode(
        source: ParcelFileDescriptor,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bitmap = if (MjpegThumbnailExtractor.isMjpeg(source)) {
            MjpegThumbnailExtractor.extractBitmap(source, width, height)
        } else {
            Mp4Recoverer.extractBitmap(source, width, height)
        } ?: return null
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}

class MjpegAssetFileDescriptorDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<AssetFileDescriptor, Bitmap> {

    override fun handles(source: AssetFileDescriptor, options: Options): Boolean {
        return MjpegThumbnailExtractor.isMjpeg(source) || Mp4Recoverer.isMalformedMp4(source)
    }

    override fun decode(
        source: AssetFileDescriptor,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bitmap = if (MjpegThumbnailExtractor.isMjpeg(source)) {
            MjpegThumbnailExtractor.extractBitmap(source, width, height)
        } else {
            Mp4Recoverer.extractBitmap(source, width, height)
        } ?: return null
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}

class MjpegInputStreamDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<InputStream, Bitmap> {

    override fun handles(source: InputStream, options: Options): Boolean {
        return MjpegThumbnailExtractor.isMjpeg(source)
    }

    override fun decode(
        source: InputStream,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bitmap = MjpegThumbnailExtractor.extractBitmap(source, width, height) ?: return null
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}

class MjpegByteBufferDecoder(
    private val bitmapPool: BitmapPool
) : ResourceDecoder<ByteBuffer, Bitmap> {

    override fun handles(source: ByteBuffer, options: Options): Boolean {
        return MjpegThumbnailExtractor.isMjpeg(source)
    }

    override fun decode(
        source: ByteBuffer,
        width: Int,
        height: Int,
        options: Options
    ): Resource<Bitmap>? {
        val bitmap = MjpegThumbnailExtractor.extractBitmap(source, width, height) ?: return null
        return BitmapResource.obtain(bitmap, bitmapPool)
    }
}
