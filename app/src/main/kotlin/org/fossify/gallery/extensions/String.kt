package org.fossify.gallery.extensions

import android.os.Environment
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.commons.helpers.isRPlus
import java.io.File
import java.io.IOException
import java.util.Locale

fun String.isThisOrParentIncluded(includedPaths: MutableSet<String>) =
    includedPaths.any { equals(it, true) } || includedPaths.any { "$this/".startsWith("$it/", true) }

fun String.isThisOrParentExcluded(excludedPaths: MutableSet<String>) =
    excludedPaths.any { equals(it, true) } || excludedPaths.any { "$this/".startsWith("$it/", true) }

// cache which folders contain .nomedia files to avoid checking them over and over again
fun String.shouldFolderBeVisible(
    excludedPaths: MutableSet<String>, includedPaths: MutableSet<String>, showHidden: Boolean,
    folderNoMediaStatuses: HashMap<String, Boolean>, callback: (path: String, hasNoMedia: Boolean) -> Unit
): Boolean {
    if (isEmpty()) {
        return false
    }

    val file = File(this)
    val filename = file.name
    if (filename.startsWith("img_", true) && file.isDirectory) {
        val files = file.list()
        if (files != null) {
            if (files.any { it.contains("burst", true) }) {
                return false
            }
        }
    }

    if (!showHidden && filename.startsWith('.')) {
        return false
    } else if (includedPaths.contains(this)) {
        return true
    }

    val pathToCheck = "$this/$NOMEDIA"
    val containsNoMedia = if (showHidden) {
        false
    } else if (folderNoMediaStatuses.containsKey(pathToCheck)) {
        folderNoMediaStatuses[pathToCheck] == true
    } else {
        val exists = (!isRPlus() || isExternalStorageManager()) && File(this, NOMEDIA).exists()
        callback(pathToCheck, exists)
        exists
    }

    return if (!showHidden && containsNoMedia) {
        false
    } else if (excludedPaths.contains(this)) {
        false
    } else if (isThisOrParentIncluded(includedPaths)) {
        true
    } else if (isThisOrParentExcluded(excludedPaths)) {
        false
    } else if (!showHidden) {
        var containsNoMediaOrDot = containsNoMedia || contains("/.")
        if (!containsNoMediaOrDot) {
            var curPath = this
            for (i in 0 until count { it == '/' } - 1) {
                curPath = curPath.substringBeforeLast('/')
                val parentPathToCheck = "$curPath/$NOMEDIA"
                if (folderNoMediaStatuses.containsKey(parentPathToCheck)) {
                    if (folderNoMediaStatuses[parentPathToCheck] == true) {
                        containsNoMediaOrDot = true
                        break
                    }
                } else {
                    val noMediaExists = File(parentPathToCheck).exists()
                    callback(parentPathToCheck, noMediaExists)
                    if (noMediaExists) {
                        containsNoMediaOrDot = true
                        break
                    }
                }
            }
        }
        !containsNoMediaOrDot
    } else {
        true
    }
}

// recognize /sdcard/DCIM as the same folder as /storage/emulated/0/DCIM
fun String.getDistinctPath(): String {
    val normalized = when {
        startsWith("/sdcard/") -> "/storage/emulated/0/${removePrefix("/sdcard/")}"
        this == "/sdcard" -> "/storage/emulated/0"
        else -> this
    }
    val cleaned = if (normalized.length > 1 && normalized.endsWith('/')) {
        normalized.trimEnd('/')
    } else {
        normalized
    }
    return try {
        if (cleaned.startsWith("/storage/emulated/") || cleaned.startsWith("/storage/")) {
            cleaned.lowercase(Locale.getDefault())
        } else {
            File(cleaned).canonicalPath.lowercase(Locale.getDefault())
        }
    } catch (e: IOException) {
        cleaned.lowercase(Locale.getDefault())
    }
}

fun String.isDownloadsFolder() = equals(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).toString(), true)

fun String.isThisOrParentFolderHidden(): Boolean {
    var curFile = File(this)
    while (true) {
        if (curFile.isHidden) {
            return true
        }

        curFile = curFile.parentFile ?: break
        if (curFile.absolutePath == "/") {
            break
        }
    }
    return false
}
