package com.example.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.example.data.model.ExportedVideo
import com.example.data.model.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

sealed class ExportState {
    object Idle : ExportState()
    data class Exporting(val progress: Float, val statusMessage: String) : ExportState()
    data class Success(val exportedVideo: ExportedVideo) : ExportState()
    data class Error(val message: String) : ExportState()
}

@OptIn(UnstableApi::class)
class VideoExportManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState.asStateFlow()

    private var activeTransformer: Transformer? = null
    private var progressPollingJob: Job? = null
    private var currentTempOutputFile: File? = null

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
        progressPollingJob?.cancel()
        progressPollingJob = null
        currentTempOutputFile?.delete()
        _exportState.value = ExportState.Idle
    }

    fun startExport(project: Project, resolutionPreset: String = "1080p") {
        if (project.clips.isEmpty()) {
            _exportState.value = ExportState.Error("Project contains no video clips to export.")
            return
        }

        coroutineScope.launch {
            _exportState.value = ExportState.Exporting(0f, "Preparing clips and timeline...")

            withContext(Dispatchers.IO) {
                try {
                    val tempOutputDir = File(context.cacheDir, "exports").apply { mkdirs() }
                    val tempOutputFile = File(tempOutputDir, "export_${UUID.randomUUID()}.mp4")
                    currentTempOutputFile = tempOutputFile

                    val editedMediaItems = mutableListOf<EditedMediaItem>()

                    // Sort clips by timeline sequence
                    val sortedClips = project.clips.sortedBy { it.timelineStartMs }

                    for ((index, clip) in sortedClips.withIndex() ) {
                        // Ensure readable media file
                        val cachedFile = SampleMediaProvider.copyToCacheFile(context, clip.sourceUri, "render_clip_${index}_")
                        val mediaItem = MediaItem.Builder()
                            .setUri(Uri.fromFile(cachedFile))
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(clip.trimStartMs.coerceAtLeast(0L))
                                    .setEndPositionMs(clip.trimEndMs.coerceAtLeast(clip.trimStartMs + 100L))
                                    .build()
                            )
                            .build()

                        // Build effects from keyframes if present
                        val effectsList = mutableListOf<androidx.media3.common.Effect>()
                        if (clip.keyframes.isNotEmpty()) {
                            // Calculate average or peak transform for video filter
                            val midTime = (clip.trimEndMs - clip.trimStartMs) / 2
                            val evaluated = KeyframeEngine.evaluate(clip.keyframes, midTime)
                            if (evaluated.scaleX != 1f || evaluated.scaleY != 1f || evaluated.rotation != 0f) {
                                val scaleRotateEffect = ScaleAndRotateTransformation.Builder()
                                    .setScale(evaluated.scaleX, evaluated.scaleY)
                                    .setRotationDegrees(evaluated.rotation)
                                    .build()
                                effectsList.add(scaleRotateEffect)
                            }
                        }

                        val effects = Effects(/* audioProcessors = */ emptyList(), /* videoEffects = */ effectsList)
                        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                            .setEffects(effects)
                            .setRemoveAudio(clip.volume <= 0.01f)
                            .build()

                        editedMediaItems.add(editedMediaItem)
                    }

                    val sequence = EditedMediaItemSequence(editedMediaItems)
                    val composition = Composition.Builder(sequence).build()

                    val targetHeight = if (resolutionPreset == "720p") 720 else 1080

                    val transformer = Transformer.Builder(context)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                progressPollingJob?.cancel()
                                handleExportCompleted(project, tempOutputFile, resolutionPreset)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                progressPollingJob?.cancel()
                                _exportState.value = ExportState.Error("Rendering failed: ${exportException.localizedMessage ?: "Unknown hardware encoder failure"}")
                            }
                        })
                        .build()

                    activeTransformer = transformer

                    withContext(Dispatchers.Main) {
                        transformer.start(composition, tempOutputFile.absolutePath)

                        // Start polling progress
                        progressPollingJob = coroutineScope.launch {
                            val progressHolder = ProgressHolder()
                            while (isActive) {
                                val state = transformer.getProgress(progressHolder)
                                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                                    val progressFraction = progressHolder.progress / 100f
                                    _exportState.value = ExportState.Exporting(
                                        progressFraction,
                                        "Rendering MP4 (${(progressFraction * 100).toInt()}%)..."
                                    )
                                }
                                delay(200)
                            }
                        }
                    }

                } catch (e: Exception) {
                    _exportState.value = ExportState.Error("Failed to initiate export: ${e.message}")
                }
            }
        }
    }

    private fun handleExportCompleted(project: Project, tempFile: File, resolution: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    _exportState.value = ExportState.Error("Exported output is missing or empty.")
                    return@launch
                }

                _exportState.value = ExportState.Exporting(0.95f, "Registering with Android MediaStore...")

                // Save to MediaStore under Movies/COPY EDIT
                val fileName = "COPY_EDIT_${project.title.replace("\\s+".toRegex(), "_")}_${System.currentTimeMillis()}.mp4"
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/COPY EDIT")
                    put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Video.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.IS_PENDING, 1)
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }

                val savedUri = context.contentResolver.insert(collection, contentValues)
                    ?: throw IllegalStateException("Could not create MediaStore entry for video export.")

                context.contentResolver.openOutputStream(savedUri)?.use { outStream ->
                    FileInputStream(tempFile).use { inStream ->
                        inStream.copyTo(outStream)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                    context.contentResolver.update(savedUri, contentValues, null, null)
                }

                val metadata = SampleMediaProvider.inspectVideo(context, savedUri.toString())
                val exportedVideo = ExportedVideo(
                    id = UUID.randomUUID().toString(),
                    projectId = project.id,
                    title = project.title,
                    contentUri = savedUri.toString(),
                    filePath = tempFile.absolutePath,
                    resolution = resolution,
                    fileSizeBytes = tempFile.length(),
                    durationMs = metadata.durationMs,
                    exportedAt = System.currentTimeMillis()
                )

                _exportState.value = ExportState.Success(exportedVideo)

            } catch (e: Exception) {
                _exportState.value = ExportState.Error("Saving to gallery failed: ${e.message}")
            }
        }
    }
}
