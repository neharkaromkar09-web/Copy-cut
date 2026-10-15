package com.example.media

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.util.Locale

data class DocumentMetadata(
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val extension: String
) {
    val formattedSize: String
        get() = DocumentUtils.formatFileSize(sizeBytes)
}

object DocumentUtils {

    private const val TAG = "DocumentUtils"

    /**
     * Inspects a document Uri using ContentResolver, extracting the true display name,
     * file size, MIME type, and extension. Never converts content URIs into fake filesystem paths.
     */
    fun queryDocumentMetadata(context: Context, uri: Uri): DocumentMetadata {
        var displayName = "imported_project"
        var sizeBytes = -1L
        val mimeType = try {
            context.contentResolver.getType(uri)
        } catch (_: Exception) {
            null
        }

        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            cursor.getString(nameIndex)?.let { name ->
                                if (name.isNotBlank()) displayName = name
                            }
                        }
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            sizeBytes = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "ContentResolver query failed for $uri: ${e.message}")
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.lastPathSegment?.let { segment ->
                displayName = segment.substringAfterLast('/')
            }
        }

        // If displayName still doesn't have an extension, try extracting from lastPathSegment
        if (!displayName.contains('.') && uri.lastPathSegment != null) {
            val lastSegment = uri.lastPathSegment.orEmpty().substringAfterLast('/')
            if (lastSegment.contains('.')) {
                displayName = lastSegment
            }
        }

        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)

        return DocumentMetadata(
            displayName = displayName,
            sizeBytes = sizeBytes,
            mimeType = mimeType,
            extension = extension
        )
    }

    /**
     * Takes persistable URI permission safely if supported by the provider.
     */
    fun takePersistableUriPermissionSafely(context: Context, uri: Uri) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            Log.d(TAG, "Successfully acquired persistable URI permission for $uri")
        } catch (e: SecurityException) {
            Log.i(TAG, "Provider does not support persistable URI permission: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Could not take persistable URI permission: ${e.message}")
        }
    }

    /**
     * Formats bytes into human-readable size (e.g. 14.5 MB).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "Unknown size"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }
}
