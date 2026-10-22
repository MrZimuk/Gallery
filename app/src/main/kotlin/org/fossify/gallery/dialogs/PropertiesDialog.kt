package org.fossify.gallery.dialogs

import android.annotation.SuppressLint
import android.app.Activity
import android.provider.MediaStore
import android.view.View
import android.widget.LinearLayout
import org.fossify.commons.R
import org.fossify.commons.dialogs.BasePropertiesDialog
import org.fossify.commons.extensions.formatAsResolution
import org.fossify.commons.extensions.formatDate
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getFilenameFromPath
import org.fossify.commons.extensions.getFormattedDuration
import org.fossify.commons.extensions.getIsPathDirectory
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.FileDirItem
import org.fossify.commons.views.MyTextView
import org.fossify.gallery.helpers.Mp4Recoverer
import org.fossify.gallery.mjpeg.MjpegThumbnailExtractor

class PropertiesDialog : BasePropertiesDialog {
    private var mCountHiddenItems = false

    constructor(activity: Activity, path: String, countHiddenItems: Boolean = false) : super(activity) {
        if (!Mp4Recoverer.isMalformedMp4(path)) {
            org.fossify.commons.dialogs.PropertiesDialog(activity, path, countHiddenItems)
            return
        }

        if (!activity.getDoesFilePathExist(path) && !path.startsWith("content://")) {
            activity.toast(String.format(activity.getString(R.string.source_file_doesnt_exist), path))
            return
        }

        mCountHiddenItems = countHiddenItems
        addProperties(path)

        val builder = activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok, null)

        activity.setupDialogStuff(mDialogView.root, builder, R.string.properties)
    }

    constructor(activity: Activity, paths: List<String>, countHiddenItems: Boolean = false) : super(activity) {
        org.fossify.commons.dialogs.PropertiesDialog(activity, paths, countHiddenItems)
    }

    @SuppressLint("StringFormatMatches")
    private fun addProperties(path: String) {
        val fileDirItem = FileDirItem(path, path.getFilenameFromPath(), mActivity.getIsPathDirectory(path))
        addProperty(R.string.name, fileDirItem.name)
        addProperty(R.string.path, fileDirItem.getParentPath())
        addProperty(R.string.size, "…", R.id.properties_size)

        ensureBackgroundThread {
            val size = fileDirItem.getProperSize(mActivity, mCountHiddenItems).formatSize()
            mActivity.runOnUiThread {
                (mDialogView.propertiesHolder.findViewById<LinearLayout>(R.id.properties_size)
                    .findViewById<MyTextView>(R.id.property_value)).text = size
            }

            val projection = arrayOf(MediaStore.Images.Media.DATE_MODIFIED)
            val uri = MediaStore.Files.getContentUri("external")
            val selection = "${MediaStore.MediaColumns.DATA} = ?"
            val selectionArgs = arrayOf(path)
            val cursor = mActivity.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                if (cursor.moveToFirst()) {
                    val dateModified = cursor.getLongValue(MediaStore.Images.Media.DATE_MODIFIED) * 1000L
                    updateLastModified(mActivity, mDialogView.root, dateModified)
                } else {
                    updateLastModified(mActivity, mDialogView.root, fileDirItem.getLastModified(mActivity))
                }
            } ?: updateLastModified(mActivity, mDialogView.root, fileDirItem.getLastModified(mActivity))
        }

        val rawDuration = fileDirItem.getDuration(mActivity)
        val duration = if (rawDuration == null || rawDuration == "00:00") {
            Mp4Recoverer.getVideoDuration(path)?.getFormattedDuration() ?: rawDuration
        } else {
            rawDuration
        }
        duration?.let { addProperty(R.string.duration, it) }

        val resolution = fileDirItem.getResolution(mActivity)
            ?: Mp4Recoverer.getVideoResolution(path)
            ?: MjpegThumbnailExtractor.getVideoResolution(path)
        resolution?.let { addProperty(R.string.resolution, it.formatAsResolution()) }

        fileDirItem.getArtist(mActivity)?.let { addProperty(R.string.artist, it) }
        fileDirItem.getAlbum(mActivity)?.let { addProperty(R.string.album, it) }

        addProperty(R.string.last_modified, "…", R.id.properties_last_modified)
    }

    private fun updateLastModified(activity: Activity, view: View, timestamp: Long) {
        activity.runOnUiThread {
            (view.findViewById<LinearLayout>(R.id.properties_last_modified)
                .findViewById<MyTextView>(R.id.property_value)).text = timestamp.formatDate(activity)
        }
    }
}
