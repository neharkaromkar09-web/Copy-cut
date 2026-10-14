package com.example.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object SampleMediaProvider {

    fun getSampleMotionUri(context: Context): String {
        val resId = context.resources.getIdentifier("sample_clip_motion", "raw", context.packageName)
        return "android.resource://${context.packageName}/$resId"
    }

    fun getSamplePortraitUri(context: Context): String {
        val resId = context.resources.getIdentifier("sample_clip_portrait", "raw", context.packageName)
        return "android.resource://${context.packageName}/$resId"
    }

    data class VideoMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val rotation: Int,
        val hasAudio: Boolean
    )

    fun inspectVideo(context: Context, uriString: String): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            val uri = Uri.parse(uriString)
            if (uriString.startsWith("android.resource://") || uriString.startsWith("content://")) {
                retriever.setDataSource(context, uri)
            } else {
                retriever.setDataSource(uriString)
            }

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val widthStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            val heightStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            val hasAudioStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)

            val durationMs = durationStr?.toLongOrNull() ?: 5000L
            val width = widthStr?.toIntOrNull() ?: 1280
            val height = heightStr?.toIntOrNull() ?: 720
            val rotation = rotationStr?.toIntOrNull() ?: 0
            val hasAudio = hasAudioStr.equals("yes", ignoreCase = true) || hasAudioStr.equals("true", ignoreCase = true)

            VideoMetadata(
                durationMs = durationMs,
                width = width,
                height = height,
                rotation = rotation,
                hasAudio = hasAudio
            )
        } catch (e: Exception) {
            VideoMetadata(
                durationMs = 5000L,
                width = 1280,
                height = 720,
                rotation = 0,
                hasAudio = true
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    /**
     * Copies a content:// or android.resource:// URI to a cache file for robust access by Media3 Transformer.
     */
    fun copyToCacheFile(context: Context, uriString: String, prefix: String = "clip_source_"): File {
        val uri = Uri.parse(uriString)
        val file = File(context.cacheDir, "${prefix}${System.currentTimeMillis()}_${fileCounter++}.mp4")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        } ?: run {
            // Fallback for file:// or direct path
            val src = File(uri.path ?: uriString)
            if (src.exists()) {
                src.copyTo(file, overwrite = true)
            }
        }
        return file
    }

    private var fileCounter = 0
}
