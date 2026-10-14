package com.example.media

import android.content.Context
import android.net.Uri
import com.example.data.model.AspectRatio
import com.example.data.model.InterpolationType
import com.example.data.model.Keyframe
import com.example.data.model.Project
import com.example.data.model.TimelineClip
import com.example.data.model.VNImportReport
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ArchiveImporter {

    /**
     * Safely inspects and imports a project file or archive (ZIP or .vnproj/.json).
     * Strictly verifies canonical paths against Zip Slip vulnerabilities.
     */
    fun importArchiveOrFile(context: Context, uri: Uri, fileName: String): VNImportReport {
        val destDir = File(context.cacheDir, "imported_archives/${UUID.randomUUID()}").apply { mkdirs() }
        val isArchive = fileName.endsWith(".zip", ignoreCase = true) ||
                fileName.endsWith(".7z", ignoreCase = true) ||
                fileName.endsWith(".vnproj", ignoreCase = true)

        return try {
            val extractedFiles = mutableListOf<File>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                if (isArchive) {
                    // Try parsing as Zip archive
                    extractZipSafely(inputStream, destDir, extractedFiles)
                } else {
                    // Single project file or json
                    val singleFile = File(destDir, fileName)
                    FileOutputStream(singleFile).use { out ->
                        inputStream.copyTo(out)
                    }
                    extractedFiles.add(singleFile)
                }
            } ?: return errorReport(fileName, isArchive, "Cannot read file stream from ContentResolver.")

            // Inspect extracted files for project definitions (JSON, .vnproj, XML)
            val projectFile = extractedFiles.firstOrNull {
                it.name.endsWith(".json", ignoreCase = true) ||
                it.name.endsWith(".vnproj", ignoreCase = true) ||
                it.name.contains("project", ignoreCase = true)
            }

            // Find media assets (mp4, m4v, mp3, etc.)
            val mediaFiles = extractedFiles.filter {
                it.extension.lowercase() in listOf("mp4", "m4v", "mov", "mkv", "mp3", "wav", "aac")
            }

            if (projectFile != null && projectFile.exists()) {
                parseProjectFile(context, projectFile, mediaFiles, fileName, isArchive)
            } else if (mediaFiles.isNotEmpty()) {
                // If it's a media package without dedicated JSON, build a sequential clip project
                buildSequentialMediaProject(context, mediaFiles, fileName, isArchive)
            } else {
                VNImportReport(
                    sourceFileName = fileName,
                    isArchive = isArchive,
                    recognizedClips = 0,
                    recognizedCuts = 0,
                    recognizedKeyframes = 0,
                    recognizedAudioTracks = 0,
                    supportedEffects = emptyList(),
                    unsupportedEffects = emptyList(),
                    warnings = listOf("Archive extracted safely, but no recognized project descriptor (.json, .vnproj) or media clips were found."),
                    importedProject = null
                )
            }
        } catch (e: Exception) {
            errorReport(fileName, isArchive, "Extraction failed: ${e.message}")
        }
    }

    private fun extractZipSafely(inputStream: InputStream, destDir: File, outFiles: MutableList<File>) {
        val canonicalDestDirPath = destDir.canonicalPath
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                val canonicalNewFilePath = newFile.canonicalPath

                // Zip Slip protection: Ensure entry is within destDir
                if (!canonicalNewFilePath.startsWith(canonicalDestDirPath + File.separator)) {
                    throw SecurityException("Security violation: Zip entry '${entry.name}' attempts path traversal.")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    outFiles.add(newFile)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun parseProjectFile(
        context: Context,
        projectFile: File,
        mediaFiles: List<File>,
        sourceFileName: String,
        isArchive: Boolean
    ): VNImportReport {
        val rawJson = projectFile.readText()
        val warnings = mutableListOf<String>()
        val supportedEffects = mutableListOf<String>()
        val unsupportedEffects = mutableListOf<String>()

        return try {
            val root = JSONObject(rawJson)
            val title = root.optString("name", root.optString("title", "Imported Project"))
            val aspectRatioStr = root.optString("aspectRatio", "16:9")
            val aspectRatio = AspectRatio.fromLabel(aspectRatioStr)

            val clipsList = mutableListOf<TimelineClip>()
            var recognizedKeyframesCount = 0
            var recognizedCutsCount = 0
            var recognizedAudioCount = 0

            // Try standard arrays: "clips", "tracks", "materials", "segments" (common in VN / mobile editors)
            val clipsArray = root.optJSONArray("clips")
                ?: root.optJSONArray("segments")
                ?: root.optJSONArray("materials")
                ?: JSONArray()

            var currentTimelineMs = 0L

            for (i in 0 until clipsArray.length()) {
                val clipObj = clipsArray.optJSONObject(i) ?: continue
                val clipDuration = clipObj.optLong("duration", clipObj.optLong("durationMs", 3000L))
                val trimIn = clipObj.optLong("trimStart", clipObj.optLong("inPoint", 0L))
                val trimOut = clipObj.optLong("trimEnd", clipObj.optLong("outPoint", clipDuration))
                val clipName = clipObj.optString("name", "Clip ${i + 1}")
                val speed = clipObj.optDouble("speed", 1.0).toFloat()

                // Check media reference
                val sourceRef = clipObj.optString("source", clipObj.optString("path", ""))
                val matchedMediaFile = mediaFiles.firstOrNull { it.name.equals(sourceRef, ignoreCase = true) }
                val resolvedUri = when {
                    matchedMediaFile != null -> Uri.fromFile(matchedMediaFile).toString()
                    sourceRef.isNotBlank() && File(sourceRef).exists() -> Uri.fromFile(File(sourceRef)).toString()
                    else -> {
                        warnings.add("Media reference '$sourceRef' for '$clipName' not bundled in archive; linked to sample media.")
                        SampleMediaProvider.getSampleMotionUri(context)
                    }
                }

                // Parse keyframes
                val keyframes = mutableListOf<Keyframe>()
                val kfArray = clipObj.optJSONArray("keyframes")
                if (kfArray != null) {
                    for (k in 0 until kfArray.length()) {
                        val kfObj = kfArray.optJSONObject(k) ?: continue
                        val time = kfObj.optLong("time", kfObj.optLong("timestampMs", 0L))
                        val scale = kfObj.optDouble("scale", 1.0).toFloat()
                        val rot = kfObj.optDouble("rotation", 0.0).toFloat()
                        val posX = kfObj.optDouble("posX", 0.0).toFloat()
                        val posY = kfObj.optDouble("posY", 0.0).toFloat()
                        val opacity = kfObj.optDouble("opacity", 1.0).toFloat()
                        val curveStr = kfObj.optString("curve", "LINEAR")

                        keyframes.add(
                            Keyframe(
                                timestampMs = time,
                                posX = posX,
                                posY = posY,
                                scaleX = scale,
                                scaleY = scale,
                                rotation = rot,
                                opacity = opacity,
                                interpolation = InterpolationType.fromString(curveStr)
                            )
                        )
                        recognizedKeyframesCount++
                    }
                }

                if (keyframes.isNotEmpty()) {
                    supportedEffects.add("Transform Keyframes (Clip ${i + 1})")
                }

                // Check for unsupported proprietary fields
                if (clipObj.has("lutFilter") || clipObj.has("colorFilter")) {
                    unsupportedEffects.add("Custom LUT Color Grading (${clipObj.optString("lutFilter")})")
                }
                if (clipObj.has("chromaKey")) {
                    unsupportedEffects.add("Chroma Key Green Screen")
                }
                if (clipObj.has("curveSpeed") || clipObj.has("speedRamping")) {
                    unsupportedEffects.add("Nonlinear Speed Ramping (reverted to constant ${speed}x)")
                }

                val clipDurationReal = (trimOut - trimIn).coerceAtLeast(500L)
                clipsList.add(
                    TimelineClip(
                        id = UUID.randomUUID().toString(),
                        sourceUri = resolvedUri,
                        displayName = clipName,
                        sourceDurationMs = clipDuration,
                        trimStartMs = trimIn,
                        trimEndMs = trimOut,
                        timelineStartMs = currentTimelineMs,
                        speed = speed,
                        keyframes = keyframes
                    )
                )
                currentTimelineMs += clipDurationReal
                recognizedCutsCount++
            }

            if (clipsList.isEmpty() && mediaFiles.isNotEmpty()) {
                return buildSequentialMediaProject(context, mediaFiles, sourceFileName, isArchive)
            }

            val project = Project(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                aspectRatio = aspectRatio,
                durationMs = currentTimelineMs,
                clips = clipsList
            )

            VNImportReport(
                sourceFileName = sourceFileName,
                isArchive = isArchive,
                recognizedClips = clipsList.size,
                recognizedCuts = recognizedCutsCount,
                recognizedKeyframes = recognizedKeyframesCount,
                recognizedAudioTracks = recognizedAudioCount,
                supportedEffects = supportedEffects.distinct(),
                unsupportedEffects = unsupportedEffects.distinct(),
                warnings = warnings,
                importedProject = project
            )
        } catch (e: Exception) {
            // If JSON parsing encountered schema variance, report diagnostics
            warnings.add("Project descriptor had non-standard format: ${e.message}. Fallback to media file discovery.")
            if (mediaFiles.isNotEmpty()) {
                buildSequentialMediaProject(context, mediaFiles, sourceFileName, isArchive, warnings)
            } else {
                VNImportReport(
                    sourceFileName = sourceFileName,
                    isArchive = isArchive,
                    recognizedClips = 0,
                    recognizedCuts = 0,
                    recognizedKeyframes = 0,
                    recognizedAudioTracks = 0,
                    supportedEffects = emptyList(),
                    unsupportedEffects = listOf("Proprietary/Encrypted VN Project Schema"),
                    warnings = listOf("Could not parse '${projectFile.name}': ${e.message}"),
                    importedProject = null
                )
            }
        }
    }

    private fun buildSequentialMediaProject(
        context: Context,
        mediaFiles: List<File>,
        sourceFileName: String,
        isArchive: Boolean,
        additionalWarnings: List<String> = emptyList()
    ): VNImportReport {
        val clips = mutableListOf<TimelineClip>()
        var timelineOffset = 0L

        for ((index, file) in mediaFiles.withIndex()) {
            val uri = Uri.fromFile(file).toString()
            val meta = SampleMediaProvider.inspectVideo(context, uri)
            val clipDuration = meta.durationMs.coerceAtLeast(2000L)

            clips.add(
                TimelineClip(
                    id = UUID.randomUUID().toString(),
                    sourceUri = uri,
                    displayName = file.nameWithoutExtension,
                    sourceDurationMs = clipDuration,
                    trimStartMs = 0L,
                    trimEndMs = clipDuration,
                    timelineStartMs = timelineOffset
                )
            )
            timelineOffset += clipDuration
        }

        val project = Project(
            id = UUID.randomUUID().toString(),
            title = sourceFileName.substringBeforeLast("."),
            aspectRatio = AspectRatio.RATIO_16_9,
            durationMs = timelineOffset,
            clips = clips
        )

        return VNImportReport(
            sourceFileName = sourceFileName,
            isArchive = isArchive,
            recognizedClips = clips.size,
            recognizedCuts = clips.size,
            recognizedKeyframes = 0,
            recognizedAudioTracks = 1,
            supportedEffects = listOf("Sequenced Multi-Clip Timeline"),
            unsupportedEffects = emptyList(),
            warnings = additionalWarnings + listOf("Extracted ${mediaFiles.size} media clip(s) and structured them into a continuous timeline."),
            importedProject = project
        )
    }

    private fun errorReport(fileName: String, isArchive: Boolean, error: String): VNImportReport {
        return VNImportReport(
            sourceFileName = fileName,
            isArchive = isArchive,
            recognizedClips = 0,
            recognizedCuts = 0,
            recognizedKeyframes = 0,
            recognizedAudioTracks = 0,
            supportedEffects = emptyList(),
            unsupportedEffects = emptyList(),
            warnings = listOf(error),
            importedProject = null
        )
    }
}
