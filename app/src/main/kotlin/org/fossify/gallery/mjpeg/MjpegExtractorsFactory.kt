package org.fossify.gallery.mjpeg

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MjpegMatroskaExtractor
import androidx.media3.extractor.text.SubtitleParser

@OptIn(UnstableApi::class)
class MjpegExtractorsFactory : ExtractorsFactory {
    private val defaultExtractorsFactory = DefaultExtractorsFactory()

    override fun createExtractors(): Array<Extractor> {
        return buildExtractors(defaultExtractorsFactory.createExtractors())
    }

    override fun createExtractors(uri: Uri, responseHeaders: Map<String, List<String>>): Array<Extractor> {
        return buildExtractors(defaultExtractorsFactory.createExtractors(uri, responseHeaders))
    }

    private fun buildExtractors(defaults: Array<Extractor>): Array<Extractor> {
        val result = arrayOfNulls<Extractor>(defaults.size + 1)
        result[0] = MjpegMatroskaExtractor(SubtitleParser.Factory.UNSUPPORTED)
        System.arraycopy(defaults, 0, result, 1, defaults.size)
        @Suppress("UNCHECKED_CAST")
        return result as Array<Extractor>
    }
}
