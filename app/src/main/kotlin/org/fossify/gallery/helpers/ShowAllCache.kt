package org.fossify.gallery.helpers

import android.content.Context
import android.util.Log
import org.fossify.commons.extensions.getLatestMediaByDateId
import org.fossify.commons.extensions.getLatestMediaId
import org.fossify.gallery.extensions.getMediaRootsLastModifiedHash
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem

object ShowAllCache {
    private var cachedMedia: ArrayList<ThumbnailItem>? = null
    private var cachedFilterMedia: Int = 0
    private var cachedSorting: Int = 0
    private var cachedGrouping: Int = 0
    private var latestMediaId: Long = 0L
    private var latestMediaDateId: Long = 0L
    private var mediaCount: Long = 0L
    private var latestFsHash: Long = 0L

    fun hasValidCache(filterMedia: Int, sorting: Int, grouping: Int): Boolean {
        val items = cachedMedia
        return items != null &&
            items.isNotEmpty() &&
            cachedFilterMedia == filterMedia &&
            cachedSorting == sorting &&
            cachedGrouping == grouping
    }

    fun getItems(): ArrayList<ThumbnailItem>? {
        val items = cachedMedia ?: return null
        return if (items.isNotEmpty()) ArrayList(items) else null
    }

    fun setCache(
        items: ArrayList<ThumbnailItem>,
        filterMedia: Int,
        sorting: Int,
        grouping: Int,
        mediaId: Long,
        mediaDateId: Long,
        count: Long,
        fsHash: Long = 0L
    ) {
        cachedMedia = ArrayList(items)
        cachedFilterMedia = filterMedia
        cachedSorting = sorting
        cachedGrouping = grouping
        latestMediaId = mediaId
        latestMediaDateId = mediaDateId
        mediaCount = count
        latestFsHash = fsHash
        Log.e("GALLERY_BENCH", "ShowAllCache: cached ${items.size} items (count=$count, mediaId=$mediaId)")
    }

    @Suppress("TooGenericExceptionCaught")
    fun checkDelta(context: Context, onResult: (hasChanged: Boolean) -> Unit) {
        try {
            val currentMediaId = context.getLatestMediaId()
            val currentMediaDateId = context.getLatestMediaByDateId()
            val currentCount = context.mediaDB.getMediaCount()
            val currentFsHash = context.getMediaRootsLastModifiedHash()
            val changed = currentMediaId != latestMediaId ||
                currentMediaDateId != latestMediaDateId ||
                currentCount != mediaCount ||
                (latestFsHash != 0L && currentFsHash != latestFsHash)
            if (changed) {
                Log.e(
                    "GALLERY_BENCH",
                    "ShowAllCache: delta detected! mediaId: $latestMediaId->$currentMediaId, " +
                        "dateId: $latestMediaDateId->$currentMediaDateId, count: $mediaCount->$currentCount, " +
                        "fsHash: $latestFsHash->$currentFsHash"
                )
            }
            onResult(changed)
        } catch (e: Exception) {
            Log.e("GALLERY_BENCH", "ShowAllCache checkDelta error: $e")
            onResult(false)
        }
    }

    fun removeMedia(path: String) {
        val items = cachedMedia ?: return
        items.removeAll { (it as? Medium)?.path == path }
        if (mediaCount > 0) {
            mediaCount--
        }
    }

    fun updateFavorite(path: String, isFavorite: Boolean) {
        val items = cachedMedia ?: return
        items.firstOrNull { (it as? Medium)?.path == path }?.let {
            (it as? Medium)?.isFavorite = isFavorite
        }
    }

    fun clear() {
        Log.e("GALLERY_BENCH", "ShowAllCache: cleared cache")
        cachedMedia = null
        cachedFilterMedia = 0
        cachedSorting = 0
        cachedGrouping = 0
        latestMediaId = 0L
        latestMediaDateId = 0L
        mediaCount = 0L
        latestFsHash = 0L
    }
}
